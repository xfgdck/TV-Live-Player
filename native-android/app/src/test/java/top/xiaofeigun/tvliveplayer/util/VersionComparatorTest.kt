package top.xiaofeigun.tvliveplayer.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionComparatorTest {

    @Test
    fun testDateBasedVersions() {
        assertTrue(VersionComparator.isNewer("2026.10.08", "v2026.10.11"))
        assertTrue(VersionComparator.isNewer("v2026.10.08", "2026.10.11"))
        assertFalse(VersionComparator.isNewer("2026.10.11", "2026.10.11"))
        assertFalse(VersionComparator.isNewer("v2026.10.11", "2026.10.11"))
        assertFalse(VersionComparator.isNewer("2026.10.11", "2026.10.08"))
    }

    @Test
    fun testVersionConsistencyAndHasUpdate() {
        // When remote and local are equal: consistent, no update
        assertFalse(VersionComparator.hasUpdate("2026.10.11", "v2026.10.11"))
        assertFalse(VersionComparator.hasUpdate("v2026.10.11", "2026.10.11"))
        assertTrue(VersionComparator.isSameVersion("2026.10.11", "v2026.10.11"))

        // When remote has older release (e.g. user deleted latest remote release):
        // Local is 2026.10.11, remote is v2026.06.10 -> inconsistent, hasUpdate is TRUE!
        assertTrue(VersionComparator.hasUpdate("2026.10.11", "v2026.06.10"))
        assertFalse(VersionComparator.isSameVersion("2026.10.11", "v2026.06.10"))
        assertFalse(VersionComparator.isNewer("2026.10.11", "v2026.06.10"))

        // When remote has newer release: inconsistent, hasUpdate is TRUE!
        assertTrue(VersionComparator.hasUpdate("2026.10.11", "v2026.10.12"))
        assertTrue(VersionComparator.isNewer("2026.10.11", "v2026.10.12"))
    }

    @Test
    fun testSemanticVersions() {
        assertTrue(VersionComparator.isNewer("1.0.0", "1.0.1"))
        assertTrue(VersionComparator.isNewer("v1.0.0", "v1.1.0"))
        assertTrue(VersionComparator.isNewer("1.9.9", "2.0.0"))
        assertFalse(VersionComparator.isNewer("1.0.1", "1.0.0"))
        assertFalse(VersionComparator.isNewer("1.0.0", "1.0.0"))
    }

    @Test
    fun testDifferentLengthVersions() {
        assertTrue(VersionComparator.isNewer("1.0", "1.0.1"))
        assertFalse(VersionComparator.isNewer("1.0.0", "1.0"))
        assertFalse(VersionComparator.isNewer("1.0", "1.0.0"))
    }

    @Test
    fun testTransitionFromOldSemanticToDateVersion() {
        // e.g. Current local debug is 1.0.0, remote release is 2026.10.11
        assertTrue(VersionComparator.isNewer("1.0.0", "v2026.10.11"))
        assertFalse(VersionComparator.isNewer("2026.10.11", "1.0.0"))
    }

    @Test
    fun testAppConfigDateFormat() {
        val version = AppConfig.currentVersionName
        // Format should be YYYY.MM.DD
        assertTrue("Version name should match date format: $version", version.matches(Regex("""^\d{4}\.\d{2}\.\d{2}$""")))
        assertTrue("Version code should be positive: ${AppConfig.currentVersionCode}", AppConfig.currentVersionCode > 20260000)
    }
}
