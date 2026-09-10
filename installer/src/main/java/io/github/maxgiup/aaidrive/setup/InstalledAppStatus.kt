package io.github.maxgiup.aaidrive.setup

enum class InstalledAppState { MISSING, READY, UPDATE, CONFLICT, UNSUPPORTED }

data class InstalledAppStatus(
    val state: InstalledAppState,
    val message: String,
    val installedVersionCode: Long? = null
)

internal data class InstalledPackage(val versionCode: Long, val currentSigners: Set<String>)

internal object InstalledAppPolicy {
    fun inspect(app: BundledApp, installed: InstalledPackage?, sdk: Int): InstalledAppStatus {
        if (sdk < app.minSdk) return InstalledAppStatus(InstalledAppState.UNSUPPORTED,
            "Requires Android API ${app.minSdk}; this phone uses API $sdk.", installed?.versionCode)
        if (installed == null) return InstalledAppStatus(InstalledAppState.MISSING, "Ready to install.")
        // Version alone cannot establish compatibility, including a newer unrelated build.
        if (installed.currentSigners != setOf(app.signerSha256)) {
            return InstalledAppStatus(InstalledAppState.CONFLICT,
                "The installed app has a different or unverifiable signing certificate. Setup will keep it unchanged.",
                installed.versionCode)
        }
        return if (installed.versionCode >= app.versionCode) {
            InstalledAppStatus(InstalledAppState.READY,
                "A compatible version is already installed.", installed.versionCode)
        } else {
            InstalledAppStatus(InstalledAppState.UPDATE,
                "An update to ${app.versionName} is available in this setup package.", installed.versionCode)
        }
    }
}
