package com.caicai.garden.update

import org.junit.Assert.assertEquals
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
}
