package cc.microblock.TGStickerProvider.utils

import java.io.File

enum class SourceQuality(val suffix: String) { HIGH("_high"), LOW("_low") }
enum class AnimationFormat { GIF, APNG }

data class StickerSyncOptions(
    val sourceQuality: SourceQuality = SourceQuality.HIGH,
    val animationFormat: AnimationFormat = AnimationFormat.GIF,
    val highQualityGif: Boolean = false
)

/** One task per complete sticker ID, with deterministic fallback for older metadata. */
object StickerSyncPlan {
    fun positions(metadata: File): Map<String, Int> = runCatching {
        metadata.readLines().drop(4).mapIndexedNotNull { index, line ->
            line.trim().toLongOrNull()?.let { it.toString() to index }
        }.toMap()
    }.getOrDefault(emptyMap())

    fun select(files: Array<out File>, quality: SourceQuality, positions: Map<String, Int>): List<File> =
        files.filter { it.isFile && it.extension.lowercase() in setOf("webp", "webm") }
            .groupBy { StickerFileIndex.stickerId(it) }
            .values.map { candidates ->
                candidates.sortedWith(compareByDescending<File> { it.nameWithoutExtension.endsWith(quality.suffix) }
                    .thenByDescending { it.extension.equals("webm", ignoreCase = true) }
                    .thenBy { it.name }).first()
            }.sortedWith(compareBy<File> { positions[StickerFileIndex.stickerId(it)] ?: Int.MAX_VALUE }
                .thenBy { StickerFileIndex.stickerId(it) })

    fun decoder(codec: String?): String = when (codec) {
        "vp9" -> "libvpx-vp9"
        "vp8" -> "libvpx"
        else -> error("Unsupported WebM codec: $codec (VP8/VP9 required)")
    }

    fun videoRecipe(options: StickerSyncOptions): String =
        "video-v2-ffmpeg-kit-video-6.0.6-libvpx-vp8-vp9-threads1-" + when {
            options.animationFormat == AnimationFormat.APNG -> "apng-rgba-full"
            options.highQualityGif -> "gif-palette-full"
            else -> "gif-palette-width320-fps10"
        }

    fun videoArguments(input: File, output: File, decoder: String, options: StickerSyncOptions): Array<String> {
        val arguments = mutableListOf("-y", "-threads", "1", "-c:v", decoder, "-i", input.absolutePath,
            "-an", "-filter_threads", "1", "-filter_complex_threads", "1")
        if (options.animationFormat == AnimationFormat.APNG) {
            arguments += listOf("-vsync", "0", "-pix_fmt", "rgba", "-c:v", "apng", "-plays", "0", "-f", "apng")
        } else {
            val filter = (if (options.highQualityGif) "" else "fps=10,scale=320:-1,") +
                "split[v],palettegen,[v]paletteuse"
            arguments += listOf("-lavfi", filter, "-f", "gif")
        }
        arguments += listOf("-threads", "1", output.absolutePath)
        return arguments.toTypedArray()
    }
}
