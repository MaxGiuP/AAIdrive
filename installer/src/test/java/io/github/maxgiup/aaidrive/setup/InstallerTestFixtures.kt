package io.github.maxgiup.aaidrive.setup

import java.security.MessageDigest

internal fun bundledTestApp(bytes: ByteArray = byteArrayOf(1, 2, 3)) = BundledApp(
    "aaidrive", "AAIdrive", "apps/AAIdrive-MaxGiuP.apk", "me.hufman.androidautoidrive",
    12L, "1.2", MessageDigest.getInstance("SHA-256").digest(bytes).toHex(), "ab".repeat(32), 23
)
