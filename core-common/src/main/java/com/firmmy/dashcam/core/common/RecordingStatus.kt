package com.firmmy.dashcam.core.common

enum class RecordingStatus(
    val storedValue: String,
    val label: String,
) {
    IDLE("idle", "Idle"),
    RECORDING_DRIVING("recording_driving", "Recording driving"),
    RECORDING_PARKING("recording_parking", "Recording parking"),
    PAUSED("paused", "Paused"),
    EVENT_BOOST_RECORDING("event_boost_recording", "Event boost recording"),
    ERROR("error", "Error"),
    ;

    companion object {
        fun fromStoredValue(value: String): RecordingStatus? = entries.firstOrNull { it.storedValue == value }
    }
}
