package io.github.maxgiup.aaidrive.setup

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File
import java.util.concurrent.CancellationException

class VerifiedApkStagerTest {
    @get:Rule val temporary = TemporaryFolder()
    private val data = ByteArray(150_000) { (it % 251).toByte() }
    private val app get() = bundledTestApp(data)
    private val directory get() = File(temporary.root, "apks")

    @Test fun publishesCompleteBytesOnlyAfterChecksumAndArchiveValidation() {
        var verified = false
        val stager = VerifiedApkStager(directory, { ByteArrayInputStream(data) }) { staged, metadata ->
            assertTrue(staged.name.endsWith(".partial.apk"))
            assertEquals(app, metadata)
            assertArrayEquals(data, staged.readBytes())
            assertFalse(File(directory, "${app.id}-${app.versionCode}-${app.sha256}.apk").exists())
            verified = true
        }
        val file = stager.prepare(app) { false }
        assertTrue(verified)
        assertArrayEquals(data, file.readBytes())
        assertEquals(listOf(file), directory.listFiles()!!.toList())
    }

    @Test fun cachedApkIsVerifiedAgainWithoutRecopying() {
        var copies = 0
        var validations = 0
        val stager = VerifiedApkStager(directory, { copies++; ByteArrayInputStream(data) }) { _, _ -> validations++ }
        val first = stager.prepare(app) { false }
        val next = stager.prepare(app) { false }
        assertEquals(first, next)
        assertEquals(1, copies)
        assertEquals(2, validations)
    }

    @Test fun tamperedAssetIsNeverParsedOrPublishedAndInputIsClosed() {
        var validated = false
        var closed = false
        val input = object : ByteArrayInputStream(data.copyOf().apply { this[0] = 99 }) {
            override fun close() { closed = true; super.close() }
        }
        val stager = VerifiedApkStager(directory, { input }) { _, _ -> validated = true }
        assertThrows(BundleVerificationException::class.java) { stager.prepare(app) { false } }
        assertTrue(closed)
        assertFalse(validated)
        assertTrue(directory.listFiles()!!.isEmpty())
    }

    @Test fun tamperedCacheIsRebuiltFromAuthenticatedBundledBytes() {
        var copies = 0
        val stager = VerifiedApkStager(directory, { copies++; ByteArrayInputStream(data) }) { _, _ -> }
        val file = stager.prepare(app) { false }
        file.writeBytes(byteArrayOf(9))
        assertEquals(file, stager.prepare(app) { false })
        assertArrayEquals(data, file.readBytes())
        assertEquals(2, copies)
    }

    @Test fun wrongArchiveIdentityLeavesNoInstallableFile() {
        val stager = VerifiedApkStager(directory, { ByteArrayInputStream(data) }) { _, _ ->
            throw BundleVerificationException("Wrong package or signer")
        }
        assertThrows(BundleVerificationException::class.java) { stager.prepare(app) { false } }
        assertTrue(directory.listFiles()!!.isEmpty())
    }

    @Test fun cancellationDuringCopyClosesInputAndDeletesPartialData() {
        var cancelled = false
        var closed = false
        val input = object : ByteArrayInputStream(data) {
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                cancelled = true
                return super.read(buffer, offset, length)
            }
            override fun close() { closed = true; super.close() }
        }
        val stager = VerifiedApkStager(directory, { input }) { _, _ -> fail("Cancelled archive reached verifier") }
        assertThrows(CancellationException::class.java) { stager.prepare(app) { cancelled } }
        assertTrue(closed)
        assertTrue(directory.listFiles()!!.isEmpty())
    }

    @Test fun cancellationBeforeStartDoesNotOpenAssetOrCreateCache() {
        val stager = VerifiedApkStager(directory, { fail("Cancelled copy opened asset"); ByteArrayInputStream(data) }) { _, _ -> }
        assertThrows(CancellationException::class.java) { stager.prepare(app) { true } }
        assertFalse(directory.exists())
    }

    @Test fun cancellationDuringArchiveVerificationPreventsPublication() {
        var cancelled = false
        val stager = VerifiedApkStager(directory, { ByteArrayInputStream(data) }) { _, _ -> cancelled = true }
        assertThrows(CancellationException::class.java) { stager.prepare(app) { cancelled } }
        assertTrue(directory.listFiles()!!.isEmpty())
    }
}
