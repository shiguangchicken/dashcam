package com.firmmy.dashcam.core.common

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToLong

object DashCamFormatters {
    private val timestampFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    fun formatTimestamp(epochMillis: Long, zoneId: ZoneId = ZoneId.systemDefault()): String =
        Instant.ofEpochMilli(epochMillis).atZone(zoneId).format(timestampFormatter)

    fun formatDuration(millis: Long): String {
        val totalSeconds = (millis.coerceAtLeast(0L) / 1000L)
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L
        return if (hours > 0L) {
            "%d:%02d:%02d".format(Locale.US, hours, minutes, seconds)
        } else {
            "%02d:%02d".format(Locale.US, minutes, seconds)
        }
    }

    fun formatFileSize(bytes: Long): String {
        val safeBytes = bytes.coerceAtLeast(0L).toDouble()
        val units = listOf("B", "KB", "MB", "GB", "TB")
        var value = safeBytes
        var unitIndex = 0
        while (value >= 1024.0 && unitIndex < units.lastIndex) {
            value /= 1024.0
            unitIndex += 1
        }
        return if (unitIndex == 0) {
            "${value.roundToLong()} ${units[unitIndex]}"
        } else {
            "%.1f %s".format(Locale.US, value, units[unitIndex])
        }
    }

    fun formatBitrate(kbps: Int): String =
        if (kbps >= 1000) {
            "%.1f Mbps".format(Locale.US, kbps / 1000.0)
        } else {
            "$kbps kbps"
        }
}
