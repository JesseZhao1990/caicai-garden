package com.caicai.garden.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppUpdateManagerTest {
    @Test
    fun newerVersionIsDetected() {
        assertEquals(1, AppUpdateManager.compareVersions("1.2.0", "1.1.9"))
    }

    @Test
    fun missingVersionPartsAreTreatedAsZero() {
        assertEquals(0, AppUpdateManager.compareVersions("v1.1", "1.1.0"))
    }

    @Test
    fun olderVersionIsRejected() {
        assertEquals(-1, AppUpdateManager.compareVersions("1.0.9", "1.1.0"))
    }

    @Test
    fun downloadProgressUsesDownloadedAndTotalBytes() {
        assertEquals(0, AppUpdateManager.downloadProgress(0, 1_000))
        assertEquals(42, AppUpdateManager.downloadProgress(420, 1_000))
        assertEquals(100, AppUpdateManager.downloadProgress(1_500, 1_000))
    }

    @Test
    fun downloadProgressIsUnknownUntilTotalSizeIsAvailable() {
        assertNull(AppUpdateManager.downloadProgress(200, 0))
        assertNull(AppUpdateManager.downloadProgress(200, -1))
    }
}
