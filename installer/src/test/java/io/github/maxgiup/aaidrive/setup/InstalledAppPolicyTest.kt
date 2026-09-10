package io.github.maxgiup.aaidrive.setup

import org.junit.Assert.*
import org.junit.Test

class InstalledAppPolicyTest {
    private val app = bundledTestApp()

    @Test fun absentCompatibleAppCanBeInstalled() {
        assertEquals(InstalledAppState.MISSING, InstalledAppPolicy.inspect(app, null, 23).state)
    }

    @Test fun signedOlderAppCanBeUpdated() {
        val status = InstalledAppPolicy.inspect(app, InstalledPackage(11, setOf(app.signerSha256)), 35)
        assertEquals(InstalledAppState.UPDATE, status.state)
        assertEquals(11L, status.installedVersionCode)
    }

    @Test fun signedEqualAndNewerVersionsAreKept() {
        for (version in listOf(12L, 13L, Int.MAX_VALUE.toLong() + 10)) {
            assertEquals(InstalledAppState.READY,
                InstalledAppPolicy.inspect(app, InstalledPackage(version, setOf(app.signerSha256)), 35).state)
        }
    }

    @Test fun higherVersionNeverOverridesUnknownSigner() {
        for (signers in listOf(emptySet(), setOf("cd".repeat(32)), setOf(app.signerSha256, "cd".repeat(32)))) {
            for (version in listOf(11L, 12L, 13L)) {
                assertEquals(InstalledAppState.CONFLICT,
                    InstalledAppPolicy.inspect(app, InstalledPackage(version, signers), 35).state)
            }
        }
    }

    @Test fun androidRequirementIsCheckedBeforeOfferingInstallOrUpdate() {
        assertEquals(InstalledAppState.UNSUPPORTED, InstalledAppPolicy.inspect(app.copy(minSdk = 29), null, 28).state)
        assertEquals(InstalledAppState.UNSUPPORTED, InstalledAppPolicy.inspect(app.copy(minSdk = 29),
            InstalledPackage(11, setOf(app.signerSha256)), 28).state)
    }
}
