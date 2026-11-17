package cc.microblock.TGStickerProvider.hook

import android.app.Application
import android.database.CursorWindow
import android.database.sqlite.SQLiteCursor
import android.database.sqlite.SQLiteDatabase
import cc.microblock.TGStickerProvider.*
import cc.microblock.TGStickerProvider.hook.TelegramTLParser.SerializedData
import cc.microblock.TGStickerProvider.hook.TelegramTLParser.TLRPC
import cc.microblock.TGStickerProvider.utils.CachePathHelper
import cc.microblock.TGStickerProvider.utils.HookExportStore
import com.highcapable.yukihookapi.annotation.xposed.InjectYukiHookWithXposed
import com.highcapable.yukihookapi.hook.factory.configs
import com.highcapable.yukihookapi.hook.factory.encase
import com.highcapable.yukihookapi.hook.log.YLog
import com.highcapable.yukihookapi.hook.xposed.proxy.IYukiHookXposedInit
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

@InjectYukiHookWithXposed(entryClassName = "TGStickerProvider", isUsingXposedModuleStatus = true)
class HookEntry : IYukiHookXposedInit {
    companion object {
        private val running = AtomicBoolean(false)
        private const val MAX_BLOB_BYTES = 8 * 1024 * 1024
        private const val MAX_SETS_PER_ROW = 10_000
    }

    override fun onInit() = configs {
        debugLog { tag = "TGStickerProvider"; isEnable = BuildConfig.DEBUG }
    }

    override fun onHook() = encase {
        val hostPackage = packageName
        if (hostPackage == BuildConfig.APPLICATION_ID || hostPackage.startsWith("android") ||
            Application.getProcessName() != hostPackage || !running.compareAndSet(false, true)) return@encase
        thread(name = "tgsp-export", isDaemon = true) {
            try {
                while (!Thread.currentThread().isInterrupted) {
                    try {
                        // Retry initialization rather than permanently losing exports if storage/app is not ready.
                        val application = appContext
                        if (application == null) {
                            Thread.sleep(1_000)
                            continue
                        }
                        scan(application)
                    } catch (error: InterruptedException) {
                        throw error
                    } catch (error: Exception) {
                        YLog.error("Export scan failed in $hostPackage; will retry", error)
                    }
                    Thread.sleep(40_000)
                }
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            } finally {
                running.set(false)
            }
        }
    }

    private fun scan(application: Application) {
        val files = application.filesDir
        val databases = listOf(File(files, "cache4.db")) + files.listFiles().orEmpty()
            .filter { it.isDirectory && it.name.matches(Regex("account[0-9]+")) }
            .map { File(it, "cache4.db") }
        val caches = CachePathHelper.getCachePaths(application)
        for (database in databases.filter { it.isFile }) {
            try { scanDatabase(database, caches) }
            catch (error: Exception) { YLog.error("Cannot scan ${database.name} in ${database.parentFile?.name}", error) }
        }
    }

    private fun scanDatabase(file: File, caches: List<File>) {
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { database ->
            database.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name='stickers_v2'", null).use {
                check(it.moveToFirst()) { "Unsupported sticker database schema: stickers_v2 missing" }
            }
            val columns = database.rawQuery("PRAGMA table_info(stickers_v2)", null).use { cursor ->
                buildSet { while (cursor.moveToNext()) add(cursor.getString(cursor.getColumnIndexOrThrow("name"))) }
            }
            check("id" in columns && "data" in columns) { "Unsupported stickers_v2 columns" }
            database.rawQuery("SELECT count(*) FROM stickers_v2 WHERE length(data) > $MAX_BLOB_BYTES", null).use {
                if (it.moveToFirst() && it.getInt(0) > 0) YLog.error("Skipped oversized sticker rows in ${file.parentFile?.name}")
            }
            // A bounded cursor-owned window, never mutate Telegram's global CursorWindow settings.
            database.rawQuery("SELECT id,data FROM stickers_v2 WHERE length(data) <= $MAX_BLOB_BYTES", null).use { cursor ->
                (cursor as? SQLiteCursor)?.setWindow(CursorWindow("TGSP stickers", 16L * 1024 * 1024))
                val seen = HashSet<Long>()
                while (cursor.moveToNext()) {
                    val rowId = cursor.getLong(0)
                    try {
                        // Parse the complete row before publishing anything from it.
                        val sets = parseRow(cursor.getBlob(1))
                        for (set in sets) {
                            if (seen.add(set.set.id)) {
                                try { export(set, caches) }
                                catch (error: Exception) { YLog.error("Export failed for pack ${set.set.id}", error) }
                            }
                        }
                    } catch (error: Exception) {
                        // TL objects are not length-delimited: never resume at an unknown byte offset.
                        YLog.error("Rejected sticker row $rowId in ${file.parentFile?.name}", error)
                    }
                }
            }
        }
    }

    private fun parseRow(bytes: ByteArray): List<TLRPC.TL_messages_stickerSet> {
        require(bytes.size in 4..MAX_BLOB_BYTES) { "Invalid sticker row size" }
        val stream = SerializedData(bytes)
        val first = stream.readInt32(true)
        val single = TLRPC.TL_messages_stickerSet.isStickerSetConstructor(first)
        val count = if (single) 1 else first
        require(count in 0..MAX_SETS_PER_ROW && (single || count <= stream.remaining() / 4)) { "Invalid sticker set count: $count" }
        val sets = ArrayList<TLRPC.TL_messages_stickerSet>(count)
        repeat(count) {
            val constructor = if (single) first else stream.readInt32(true)
            val set = TLRPC.TL_messages_stickerSet.TLdeserialize(stream, constructor, true)
            require(set?.set != null && set.documents != null) { "Incomplete/NotModified sticker set in database" }
            sets.add(set)
        }
        require(stream.remaining() == 0) { "Unexpected trailing sticker data" }
        return sets
    }

    private fun export(pack: TLRPC.TL_messages_stickerSet, caches: List<File>) {
        val shortName = pack.set.short_name.orEmpty()
        require(shortName.matches(Regex("[A-Za-z0-9_]+"))) { "Unsafe or missing sticker short name" }
        HookExportStore.withLock(File(tgspDataPath)) {
            HookExportStore.publishText(File(exposedPath), ".nomedia", "")
            val destination = File(destDataPath, "tgSync_$shortName")
            check(destination.isDirectory || destination.mkdirs()) { "Cannot create export directory" }
            for (document in pack.documents) {
                val extension = when (document.mime_type) { "image/webp" -> "webp"; "video/webm" -> "webm"; else -> continue }
                val highName = "${document.dc_id}_${document.id}.$extension"
                val high = caches.asSequence().map { File(it, highName) }.firstOrNull { it.isFile && it.length() > 0 }
                if (high != null) HookExportStore.publishFile(high, destination, "${document.id}_high.$extension")
                // Keep both qualities when available. Preference belongs to the conversion planner.
                if (extension == "webp") {
                    val low = caches.asSequence().map { File(it, "-${document.id}_1109.webp") }
                        .firstOrNull { it.isFile && it.length() > 0 }
                    if (low != null) HookExportStore.publishFile(low, destination, "${document.id}_low.webp")
                }
            }
            val metadata = listOf(shortName, pack.set.title.orEmpty().replace('\n', ' ').replace('\r', ' '),
                pack.set.count.toString(), pack.documents.firstOrNull()?.mime_type ?: "unknown") + pack.documents.map { it.id.toString() }
            // Stable identity; retain legacy files and directories, UI shadows their stale metadata.
            HookExportStore.publishText(File(stickerDataPath), "pack_${pack.set.id}.stickerData.txt.jpg", metadata.joinToString("\n"))
        }
    }
}
