package cc.microblock.TGStickerProvider.utils

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.FileTime
import java.security.MessageDigest

/** Atomic exports shared by cooperating Hook processes. Telegram's source writer is not locked. */
object HookExportStore {
    // Media-compatible suffix matches the export's .txt.jpg convention on Android 11+.
    private const val LOCK_NAME = ".tgsp-export.lock.jpg"
    private const val TEMP_PREFIX = ".tgsp-publish-"
    private val monitor = Any()
    private val heldRoot = ThreadLocal<String>()

    /**
     * Serializes a complete export transaction in this JVM and across cooperating processes.
     * A busy/unavailable OS lock fails explicitly instead of writing without protection.
     * Reentry for the same canonical root is allowed; nesting different roots is rejected to
     * avoid cross-process lock ordering deadlocks. Never delete/replace the persistent lock file.
     * The .jpg suffix does not bypass ownership/access checks: another Telegram client may still
     * be denied access by Android scoped storage. Such failures remain explicit and fail safely.
     */
    fun <T> withLock(root: File, block: () -> T): T = synchronized(monitor) {
        val directory = directory(root)
        val key = directory.canonicalPath
        val held = heldRoot.get()
        if (held != null) {
            check(held == key) { "Nested export locks must use the same root" }
            return@synchronized block()
        }
        val lockFile = File(directory, LOCK_NAME)
        checkTarget(lockFile)
        RandomAccessFile(lockFile, "rw").use { handle ->
            handle.channel.use { channel ->
                val lock = channel.tryLock() ?: throw IOException("Export store is busy: $key")
                lock.use {
                    heldRoot.set(key)
                    try {
                        block()
                    } finally {
                        heldRoot.remove()
                    }
                }
            }
        }
    }

    /**
     * Publishes a nonempty media source, returning true only if destination content changed.
     * Existing incomplete/different exports are repaired. Source hash, length, mtime and identity
     * must remain stable across hashing/copying; a source change leaves the previous export intact.
     * Invoke inside [withLock]. [fileName] must be a single basename, never a relative path.
     */
    fun publishFile(source: File, destinationDirectory: File, fileName: String): Boolean {
        requireLock()
        val target = target(destinationDirectory, fileName)
        val before = stamp(source)
        check(before.size > 0L) { "Source media is empty: ${source.name}" }
        val sourceHash = fingerprint(source)
        check(stamp(source) == before) { "Source changed while hashing: ${source.name}" }
        if (matches(target, sourceHash)) {
            verifySource(source, before, sourceHash)
            return false
        }
        val temporary = File.createTempFile(TEMP_PREFIX, ".tmp", target.parentFile)
        try {
            val copiedHash = copyAndFingerprint(source, temporary)
            check(copiedHash.contentEquals(sourceHash)) { "Source changed while copying: ${source.name}" }
            verifySource(source, before, sourceHash)
            // No non-atomic fallback: if this filesystem cannot publish atomically, keep the old file.
            Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING)
            return true
        } finally {
            temporary.delete()
        }
    }

    /** Publishes UTF-8 metadata only when its bytes differ, keeping old metadata on failure. */
    fun publishText(destinationDirectory: File, fileName: String, text: String): Boolean {
        requireLock()
        val target = target(destinationDirectory, fileName)
        val bytes = text.toByteArray(Charsets.UTF_8)
        val hash = MessageDigest.getInstance("SHA-256").digest(bytes)
        if (matches(target, hash)) return false
        val temporary = File.createTempFile(TEMP_PREFIX, ".tmp", target.parentFile)
        try {
            FileOutputStream(temporary).use { output ->
                output.write(bytes)
                output.fd.sync()
            }
            Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING)
            return true
        } finally {
            temporary.delete()
        }
    }

    private data class Stamp(val size: Long, val modified: FileTime, val key: Any?)

    private fun stamp(file: File): Stamp {
        val attributes = Files.readAttributes(file.toPath(), BasicFileAttributes::class.java)
        check(attributes.isRegularFile) { "Source is not a regular file: ${file.name}" }
        return Stamp(attributes.size(), attributes.lastModifiedTime(), attributes.fileKey())
    }

    private fun verifySource(source: File, expected: Stamp, hash: ByteArray) {
        check(stamp(source) == expected && fingerprint(source).contentEquals(hash) && stamp(source) == expected) {
            "Source changed during export: ${source.name}"
        }
    }

    private fun fingerprint(file: File): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest()
    }

    private fun copyAndFingerprint(source: File, destination: File): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        source.inputStream().buffered().use { input ->
            FileOutputStream(destination).use { output ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    output.write(buffer, 0, count)
                    digest.update(buffer, 0, count)
                }
                output.fd.sync()
            }
        }
        return digest.digest()
    }

    private fun matches(file: File, hash: ByteArray): Boolean =
        file.isFile && fingerprint(file).contentEquals(hash)

    private fun requireLock() {
        check(heldRoot.get() != null) { "Export publication requires withLock" }
    }

    private fun directory(file: File): File {
        if (!file.isDirectory && !file.mkdirs() && !file.isDirectory) {
            throw IOException("Cannot create export directory: $file")
        }
        return file
    }

    private fun target(destinationDirectory: File, name: String): File {
        require(name.isNotEmpty() && name != "." && name != ".." &&
            name.all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it in "._-" } &&
            name != LOCK_NAME && !name.startsWith(TEMP_PREFIX)) { "Unsafe export filename" }
        return File(directory(destinationDirectory), name).also(::checkTarget)
    }

    private fun checkTarget(file: File) {
        if (Files.isSymbolicLink(file.toPath()) || (file.exists() && !file.isFile)) {
            throw IOException("Export target must be a regular file: ${file.name}")
        }
    }
}
