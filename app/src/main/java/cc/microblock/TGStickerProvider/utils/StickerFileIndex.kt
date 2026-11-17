package cc.microblock.TGStickerProvider.utils

import java.io.File

/** One directory scan per pack; keep it in sync with successful publication/deletion. */
class StickerFileIndex(files: Array<File>) {
    private val byId = files.groupBy { stickerId(it) }
        .mapValues { (_, entries) -> entries.toMutableList() }.toMutableMap()

    fun filesFor(id: String): List<File> = byId[id]?.toList().orEmpty()

    fun published(file: File) {
        val entries = byId.getOrPut(stickerId(file)) { mutableListOf() }
        entries.removeAll { it.name == file.name }
        entries.add(file)
    }

    fun deleted(file: File) {
        byId[stickerId(file)]?.removeAll { it.name == file.name }
    }

    companion object {
        fun stickerId(file: File): String = file.name.substringBefore(".").substringBefore("_")
    }
}
