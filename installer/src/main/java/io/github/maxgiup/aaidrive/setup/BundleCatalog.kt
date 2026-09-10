package io.github.maxgiup.aaidrive.setup

import org.json.JSONArray
import java.util.Locale

data class BundledApp(
    val id: String,
    val name: String,
    val file: String,
    val packageName: String,
    val versionCode: Long,
    val versionName: String,
    val sha256: String,
    val signerSha256: String,
    val minSdk: Int
) {
    init {
        require(id.matches(Regex("[a-z][a-z0-9_-]*"))) { "Invalid bundled app ID" }
        require(name.isNotBlank() && versionName.isNotBlank()) { "Missing bundled app name or version" }
        require(file.matches(Regex("apps/[A-Za-z0-9][A-Za-z0-9._-]*\\.apk"))) { "Invalid bundled APK path" }
        require(packageName.matches(Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+"))) { "Invalid package name" }
        require(versionCode >= 0 && minSdk > 0) { "Invalid bundled version or Android requirement" }
        require(sha256.matches(Regex("[0-9a-f]{64}")) && signerSha256.matches(Regex("[0-9a-f]{64}"))) {
            "Invalid bundled APK or signing certificate checksum"
        }
    }
}

object BundleCatalog {
    fun parse(text: String): List<BundledApp> {
        val entries = JSONArray(text)
        require(entries.length() > 0) { "The setup package contains no apps" }
        val apps = (0 until entries.length()).map { index ->
            val entry = entries.getJSONObject(index)
            BundledApp(
                entry.getString("id"), entry.getString("name"), entry.getString("file"),
                entry.getString("packageName"), entry.get("versionCode").toString().toLong(),
                entry.getString("versionName"), entry.getString("sha256").lowercase(Locale.ROOT),
                entry.getString("signerSha256").lowercase(Locale.ROOT),
                entry.get("minSdk").toString().toInt()
            )
        }
        require(apps.map { it.id }.distinct().size == apps.size &&
            apps.map { it.packageName }.distinct().size == apps.size &&
            apps.map { it.file }.distinct().size == apps.size) { "The setup catalog contains duplicate apps" }
        return apps
    }
}
