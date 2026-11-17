package cc.microblock.TGStickerProvider.utils

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.os.Build
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFprobeKit
import com.arthenica.ffmpegkit.Packages
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Parallel preparation, ordered publication. All app-owned storage mutations use the same lock. */
class StickerSynchronizer(context: Context) {
    private val app = context.applicationContext
    private val memory = app.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    private val cache = StickerConversionCache(File(app.filesDir, "sticker-conversions"))

    private data class Prepared(
        val source: File, val sourceHash: String, val output: File,
        val recipe: String, val temporary: File?
    )

    fun synchronize(
        sourceDirectory: File,
        outputDirectory: File,
        metadata: File,
        options: StickerSyncOptions,
        onProgress: (Int, Int) -> Unit
    ): List<String> = BoundedStickerWork.withStorageLock {
        check(outputDirectory.isDirectory || outputDirectory.mkdirs()) { "Cannot create sticker directory" }
        // A killed process cannot run finally; no live job can own these files while this lock is held.
        outputDirectory.listFiles().orEmpty().filter {
            it.isFile && it.name.startsWith(".convert-") && it.name.endsWith(".tmp")
        }.forEach { check(it.delete()) { "Cannot remove abandoned conversion: ${it.name}" } }
        val positions = StickerSyncPlan.positions(metadata)
        val sources = StickerSyncPlan.select(sourceDirectory.listFiles().orEmpty(), options.sourceQuality, positions)
        if (sources.isEmpty()) return@withStorageLock listOf("没有可同步的表情文件")
        val index = StickerFileIndex(outputDirectory.listFiles().orEmpty())
        val failures = mutableListOf<String>()
        val orderingFailures = mutableListOf<String>()
        val oldestTimestamp = System.currentTimeMillis() - sources.size * 1000L
        BoundedStickerWork.run(
            items = sources,
            maxInFlight = if (memory.isLowRamDevice) 2 else BoundedStickerWork.MAX_WORKERS,
            prepare = { source -> prepare(source, outputDirectory, options) },
            consume = { position, source, result ->
                val id = StickerFileIndex.stickerId(source)
                result.fold(onSuccess = { prepared ->
                    try {
                        // Also recheck cached hits: the external Telegram exporter is outside our lock.
                        check(cache.fingerprint(source) == prepared.sourceHash) { "Source changed; retry synchronization" }
                        if (prepared.temporary != null) {
                            Files.move(prepared.temporary.toPath(), prepared.output.toPath(),
                                StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
                            cache.record(source, prepared.sourceHash, prepared.output, prepared.recipe)
                        }
                        val obsolete = index.filesFor(id).filter {
                            it.name != prepared.output.name && it.extension.lowercase() in setOf("png", "gif", "webp")
                        }
                        index.published(prepared.output)
                        for (old in obsolete) {
                            if (old.delete()) index.deleted(old)
                            else failures += "$id: 无法清理旧版本 ${old.name}"
                        }
                        // Original Telegram ordering is available only after the Hook has exported ID metadata.
                        if (id in positions && !prepared.output.setLastModified(oldestTimestamp + position * 1000L)) {
                            orderingFailures += id
                        }
                    } catch (exception: Exception) {
                        failures += "$id: ${exception.message ?: "保存失败"}"
                    } finally {
                        prepared.temporary?.delete()
                    }
                }, onFailure = { exception -> failures += "$id: ${exception.message ?: "转换失败"}" })
                onProgress(position + 1, sources.size)
            },
            discard = { prepared -> prepared.temporary?.delete(); Unit }
        )
        if (orderingFailures.isNotEmpty()) failures += "部分贴纸已保存，但无法设置显示顺序：${orderingFailures.joinToString()}"
        failures
    }

    /** Call on a background thread. The same planner/recipe defines both sync and pending status. */
    fun isCurrent(sourceDirectory: File, outputDirectory: File, metadata: File, options: StickerSyncOptions): Boolean =
        BoundedStickerWork.withStorageLock {
            val positions = StickerSyncPlan.positions(metadata)
            val sources = StickerSyncPlan.select(sourceDirectory.listFiles().orEmpty(), options.sourceQuality, positions)
            val index = StickerFileIndex(outputDirectory.listFiles().orEmpty())
            val outputsCurrent = sources.isNotEmpty() && sources.all { source ->
                val output = outputFor(source, outputDirectory, options)
                index.filesFor(StickerFileIndex.stickerId(source)).none {
                    it.name != output.name && it.extension.lowercase() in setOf("png", "gif", "webp")
                } && cache.isFresh(source, cache.fingerprint(source), output, recipeFor(source, options))
            }
            val ordered = sources.filter { StickerFileIndex.stickerId(it) in positions }
                .map { outputFor(it, outputDirectory, options) }
            outputsCurrent && ordered.zipWithNext().all { (before, after) -> before.lastModified() < after.lastModified() }
        }

    private fun outputFor(source: File, directory: File, options: StickerSyncOptions): File {
        val gif = source.extension.equals("webm", ignoreCase = true) && options.animationFormat == AnimationFormat.GIF
        return File(directory, "${source.nameWithoutExtension}.${if (gif) "gif" else "png"}")
    }

    private fun recipeFor(source: File, options: StickerSyncOptions): String =
        if (source.extension.equals("webm", ignoreCase = true)) StickerSyncPlan.videoRecipe(options)
        else "png-v1-android-${Build.VERSION.SDK_INT}-${Build.FINGERPRINT}"

    private fun prepare(source: File, outputDirectory: File, options: StickerSyncOptions): Prepared {
        val video = source.extension.equals("webm", ignoreCase = true)
        val output = outputFor(source, outputDirectory, options)
        val sourceHash = cache.fingerprint(source)
        val recipe = recipeFor(source, options)
        if (cache.isFresh(source, sourceHash, output, recipe)) return Prepared(source, sourceHash, output, recipe, null)
        val info = ActivityManager.MemoryInfo().also(memory::getMemoryInfo)
        check(!info.lowMemory) { "系统内存不足，已保留旧文件，请稍后重试" }
        val temporary = File.createTempFile(".convert-", ".tmp", outputDirectory)
        try {
            if (video) {
                check("libvpx" in Packages.getExternalLibraries()) { "当前 FFmpeg 包缺少 libvpx，无法保证透明解码" }
                val probe = FFprobeKit.getMediaInformation(source.absolutePath)
                val codec = probe.mediaInformation?.streams?.firstOrNull { it.type == "video" }?.codec
                val decoder = StickerSyncPlan.decoder(codec)
                val session = FFmpegKit.executeWithArguments(StickerSyncPlan.videoArguments(source, temporary, decoder, options))
                check(session.returnCode?.isValueSuccess == true) { "FFmpeg 转换失败：${session.returnCode}" }
            } else {
                val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(source)) { decoder, _, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
                try {
                    temporary.outputStream().buffered().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
                } finally {
                    bitmap.recycle()
                }
            }
            check(temporary.length() > 0L) { "转换结果为空" }
            check(cache.fingerprint(source) == sourceHash) { "Source changed during conversion; retry synchronization" }
            return Prepared(source, sourceHash, output, recipe, temporary)
        } catch (failure: Throwable) {
            temporary.delete()
            throw failure
        }
    }
}
