package com.firmmy.dashcam.core.common

import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreModelsTest {
    @Test
    fun commandObjectsAreStable() {
        assertEquals(DashCamCommand.TakePhoto, DashCamCommand.TakePhoto)
        assertEquals(DashCamCommand.StartParkingMode, DashCamCommand.StartParkingMode)
    }

    @Test
    fun enumsParseStoredValues() {
        assertEquals(MediaType.VIDEO, MediaType.fromStoredValue("video"))
        assertEquals(RecordingMode.PARKING, RecordingMode.fromStoredValue("parking"))
        assertEquals(RecordingStatus.IDLE, RecordingStatus.fromStoredValue("idle"))
        assertEquals(null, MediaType.fromStoredValue("audio"))
    }

    @Test
    fun resultReportsSuccessState() {
        val success = DashCamResult.Success("ok")
        val failure = DashCamResult.Failure(DashCamError.StorageUnavailable())

        assertTrue(success.isSuccess)
        assertFalse(failure.isSuccess)
    }

    @Test
    fun formattersHandleCommonValues() {
        assertEquals("1970-01-01 08:00:00", DashCamFormatters.formatTimestamp(0L, ZoneId.of("Asia/Shanghai")))
        assertEquals("01:05", DashCamFormatters.formatDuration(65_000L))
        assertEquals("1:01:01", DashCamFormatters.formatDuration(3_661_000L))
        assertEquals("512 B", DashCamFormatters.formatFileSize(512L))
        assertEquals("1.5 KB", DashCamFormatters.formatFileSize(1536L))
        assertEquals("8.0 Mbps", DashCamFormatters.formatBitrate(8000))
    }
}
