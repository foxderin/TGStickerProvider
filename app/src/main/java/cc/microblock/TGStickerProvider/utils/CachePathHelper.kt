package cc.microblock.TGStickerProvider.utils

import android.app.Application
import android.content.Context
import java.io.File

/** Known storage adapters; an existing path is not a promise of client/schema compatibility. */
object CachePathHelper {
    fun getCachePaths(context: Application): List<File> = buildList {
        // Read the host's preferences through Context, including secondary Android users.
        val configured = context.getSharedPreferences("nkmrcfg", Context.MODE_PRIVATE)
            .getString("cache_path", null)
        if (!configured.isNullOrBlank() && File(configured).isAbsolute && '\u0000' !in configured) {
            add(File(configured, "caches"))
        }
        context.externalCacheDirs.filterNotNull().forEach { add(it) }
        add(context.cacheDir)
    }.distinctBy { it.absolutePath }

    @JvmStatic
    fun getCachePath(context: Application?, packageName: String): String =
        context?.let { getCachePaths(it).firstOrNull()?.absolutePath }
            ?: "/storage/self/primary/Android/data/$packageName/cache"
}
