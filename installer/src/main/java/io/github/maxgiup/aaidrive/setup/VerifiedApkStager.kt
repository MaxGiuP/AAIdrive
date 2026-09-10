package io.github.maxgiup.aaidrive.setup

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.util.concurrent.CancellationException

class BundleVerificationException(message: String) : IOException(message)

/** Copies privately and exposes a final APK filename only after both verifiers succeed. */
internal class VerifiedApkStager(
    private val directory: File,
    private val openAsset: (String) -> InputStream,
    private val verifyArchive: (File, BundledApp) -> Unit
) {
    @Synchronized
    fun prepare(app: BundledApp, cancelled: () -> Boolean): File {
        checkCancelled(cancelled)
        if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Cannot create private APK cache")
        val target = target(app)
        if (target.isFile) {
            val checksum = target.inputStream().use { digest(it, cancelled) }
            if (checksum == app.sha256) {
                verifyArchive(target, app)
                checkCancelled(cancelled)
                return target
            }
        }
        val temporary = File.createTempFile("${app.id}-stage-", ".partial.apk", directory)
        try {
            val checksum = MessageDigest.getInstance("SHA-256")
            openAsset(app.file).use { input ->
                FileOutputStream(temporary).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        checkCancelled(cancelled)
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        checksum.update(buffer, 0, count)
                    }
                    output.fd.sync()
                }
            }
            if (checksum.digest().toHex() != app.sha256) {
                throw BundleVerificationException("${app.name}: the bundled APK checksum does not match.")
            }
            checkCancelled(cancelled)
            verifyArchive(temporary, app)
            checkCancelled(cancelled)
            // Same-directory rename is atomic: an incomplete/unverified copy is never offered.
            if (!temporary.renameTo(target)) throw IOException("Cannot finalize the verified APK")
            return target
        } finally {
            temporary.delete()
        }
    }

    fun discard(app: BundledApp) { target(app).delete() }

    private fun target(app: BundledApp) = File(directory, "${app.id}-${app.versionCode}-${app.sha256}.apk")

    private fun digest(input: InputStream, cancelled: () -> Boolean): String {
        val checksum = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        while (true) {
            checkCancelled(cancelled)
            val count = input.read(buffer)
            if (count < 0) break
            checksum.update(buffer, 0, count)
        }
        return checksum.digest().toHex()
    }

    private fun checkCancelled(cancelled: () -> Boolean) {
        if (cancelled()) throw CancellationException("Installation preparation was cancelled.")
    }
}

internal fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it.toInt() and 0xff) }
