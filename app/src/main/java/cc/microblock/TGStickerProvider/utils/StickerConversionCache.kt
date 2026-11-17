package cc.microblock.TGStickerProvider.utils

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

/** Successful conversion receipts live in app-private storage, not the sticker directory. */
class StickerConversionCache(private val directory: File) {
    fun fingerprint(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun isFresh(source: File, sourceHash: String, output: File, recipe: String): Boolean = try {
        val receipt = receiptFor(output).readLines()
        output.isFile && output.length() > 0L && receipt == listOf(
            "1", source.absolutePath, sourceHash, recipe, fingerprint(output)
        )
    } catch (_: Exception) {
        false
    }

    /** Called only after output publication succeeds. Failure merely disables the cache hit. */
    fun record(source: File, sourceHash: String, output: File, recipe: String): Boolean {
        var temporary: File? = null
        return try {
            check(directory.isDirectory || directory.mkdirs())
            val receipt = receiptFor(output)
            val temp = File.createTempFile("receipt-", ".tmp", directory)
            temporary = temp
            temp.writeText(listOf("1", source.absolutePath, sourceHash, recipe, fingerprint(output)).joinToString("\n"))
            Files.move(temp.toPath(), receipt.toPath(), StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING)
            true
        } catch (_: Exception) {
            false
        } finally {
            temporary?.delete()
        }
    }

    private fun receiptFor(output: File): File {
        val key = MessageDigest.getInstance("SHA-256").digest(output.absolutePath.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return File(directory, "$key.receipt")
    }
}
