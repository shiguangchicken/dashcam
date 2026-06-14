package com.firmmy.dashcam.core.media

import org.junit.Assert.assertEquals
import org.junit.Test

class CameraFpsSelectorTest {
    @Test
    fun prefersSixtyFpsWhenAvailable() {
        val selected = CameraFpsSelector.select(
            listOf(
                CameraFpsRange(15, 30),
                CameraFpsRange(30, 60),
                CameraFpsRange(24, 24),
            ),
        )

        assertEquals(CameraFpsRange(30, 60), selected)
    }

    @Test
    fun fallsBackToHighestSupportedRange() {
        val selected = CameraFpsSelector.select(
            listOf(
                CameraFpsRange(15, 30),
                CameraFpsRange(24, 24),
                CameraFpsRange(30, 30),
            ),
        )

        assertEquals(CameraFpsRange(30, 30), selected)
    }

    @Test
    fun choosesMostStableSixtyFpsRange() {
        val selected = CameraFpsSelector.select(
            listOf(
                CameraFpsRange(15, 60),
                CameraFpsRange(30, 60),
                CameraFpsRange(60, 60),
            ),
        )

        assertEquals(CameraFpsRange(60, 60), selected)
    }
}
