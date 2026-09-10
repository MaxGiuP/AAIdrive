package io.github.maxgiup.aaidrive.setup

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.CancellationException

data class PreparedApk(val app: BundledApp, val file: File)

/** Blocking local IO and PackageManager checks; call from the setup activity's worker executor. */
class BundleInstallerRepository(context: Context) {
    private val context = context.applicationContext
    private val packageManager = this.context.packageManager
    private val stager = VerifiedApkStager(File(this.context.cacheDir, "apks"),
        { this.context.assets.open(it) }, ::verifyArchive)

    fun loadCatalog(): List<BundledApp> = context.assets.open("bundled-apps.json").bufferedReader().use {
        BundleCatalog.parse(it.readText())
    }

    fun inspect(app: BundledApp): InstalledAppStatus {
        val installed = try {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(app.packageName, signatureFlags()).let {
                InstalledPackage(versionCode(it), currentSigners(it))
            }
        } catch (_: PackageManager.NameNotFoundException) {
            null
        } catch (_: RuntimeException) {
            return InstalledAppStatus(InstalledAppState.CONFLICT,
                "Android could not verify the installed app. Setup will keep it unchanged.")
        }
        return InstalledAppPolicy.inspect(app, installed, Build.VERSION.SDK_INT)
    }

    fun prepare(app: BundledApp,
                cancelled: () -> Boolean = { Thread.currentThread().isInterrupted }): PreparedApk {
        requireInstallNeeded(app)
        val file = stager.prepare(app, cancelled)
        // Another installer may have finished while a large APK was being copied.
        requireInstallNeeded(app)
        if (cancelled()) throw CancellationException("Installation preparation was cancelled.")
        return PreparedApk(app, file)
    }

    private fun requireInstallNeeded(app: BundledApp) {
        val status = inspect(app)
        if (status.state != InstalledAppState.MISSING && status.state != InstalledAppState.UPDATE) {
            throw BundleVerificationException(status.message)
        }
    }

    /** Only call after Android's installer has returned and no longer needs the content URI. */
    fun discard(prepared: PreparedApk) = stager.discard(prepared.app)

    /** Also permits cleanup after process recreation, when only the catalog item was retained. */
    fun discard(app: BundledApp) = stager.discard(app)

    private fun verifyArchive(file: File, app: BundledApp) {
        @Suppress("DEPRECATION")
        val archive = packageManager.getPackageArchiveInfo(file.absolutePath, signatureFlags())
            ?: throw BundleVerificationException("${app.name}: Android cannot read the bundled APK.")
        if (archive.packageName != app.packageName || versionCode(archive) != app.versionCode ||
            archive.versionName != app.versionName || currentSigners(archive) != setOf(app.signerSha256)) {
            throw BundleVerificationException("${app.name}: APK package, version, or signing certificate does not match the setup catalog.")
        }
        if (Build.VERSION.SDK_INT >= 24 && archive.applicationInfo?.minSdkVersion != app.minSdk) {
            throw BundleVerificationException("${app.name}: APK Android requirement does not match the setup catalog.")
        }
    }

    private fun signatureFlags(): Int = if (Build.VERSION.SDK_INT >= 28) {
        PackageManager.GET_SIGNING_CERTIFICATES
    } else {
        @Suppress("DEPRECATION")
        PackageManager.GET_SIGNATURES
    }

    private fun versionCode(info: PackageInfo): Long = if (Build.VERSION.SDK_INT >= 28) {
        info.longVersionCode
    } else {
        @Suppress("DEPRECATION")
        info.versionCode.toLong()
    }

    private fun currentSigners(info: PackageInfo): Set<String> {
        val signatures = if (Build.VERSION.SDK_INT >= 28) {
            // Historical certificates are deliberately not treated as the current signer.
            info.signingInfo?.apkContentsSigners
        } else {
            @Suppress("DEPRECATION")
            info.signatures
        }
        return signatures?.map { MessageDigest.getInstance("SHA-256").digest(it.toByteArray()).toHex() }
            ?.toSet() ?: emptySet()
    }
}
