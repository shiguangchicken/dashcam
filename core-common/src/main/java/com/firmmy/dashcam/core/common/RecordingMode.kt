package com.firmmy.dashcam.core.common

enum class RecordingMode(
    val storedValue: String,
    val label: String,
) {
    DRIVING("driving", "Driving"),
    PARKING("parking", "Parking"),
    MANUAL("manual", "Manual"),
    EVENT("event", "Event"),
    ;

    companion object {
        fun fromStoredValue(value: String): RecordingMode? = entries.firstOrNull { it.storedValue == value }
    }
}
