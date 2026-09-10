package io.github.maxgiup.aaidrive.setup

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class BundleCatalogTest {
    private fun entry() = JSONObject().apply {
        put("id", "aaidrive")
        put("name", "AAIdrive")
        put("file", "apps/AAIdrive-MaxGiuP.apk")
        put("packageName", "me.hufman.androidautoidrive")
        put("versionCode", 2147483650L)
        put("versionName", "1.2")
        put("sha256", "AB".repeat(32))
        put("signerSha256", "CD".repeat(32))
        put("minSdk", 23)
    }

    @Test fun parsesLongVersionsAndNormalizesChecksums() {
        val app = BundleCatalog.parse(JSONArray().put(entry()).toString()).single()
        assertEquals(2147483650L, app.versionCode)
        assertEquals("ab".repeat(32), app.sha256)
        assertEquals("cd".repeat(32), app.signerSha256)
    }

    @Test fun rejectsUnsafePathsAndInvalidIdentity() {
        for ((field, value) in listOf("file" to "apps/../../outside.apk", "id" to "../outside",
            "packageName" to "not a package", "sha256" to "bad", "signerSha256" to "bad")) {
            assertThrows(IllegalArgumentException::class.java) {
                BundleCatalog.parse(JSONArray().put(entry().put(field, value)).toString())
            }
        }
    }

    @Test fun rejectsDuplicateAppsAndEmptyCatalog() {
        assertThrows(IllegalArgumentException::class.java) { BundleCatalog.parse("[]") }
        assertThrows(IllegalArgumentException::class.java) {
            BundleCatalog.parse(JSONArray().put(entry()).put(entry()).toString())
        }
    }

    @Test fun rejectsFractionalOrNegativeVersions() {
        for (version in listOf<Any>(1.5, -1)) {
            assertThrows(IllegalArgumentException::class.java) {
                BundleCatalog.parse(JSONArray().put(entry().put("versionCode", version)).toString())
            }
        }
    }
}
