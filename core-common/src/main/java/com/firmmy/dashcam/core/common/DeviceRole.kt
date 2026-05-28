package com.firmmy.dashcam.core.common

enum class DeviceRole(
    val storedValue: String,
    val label: String,
) {
    RECORDER("recorder", "Recorder"),
    REMOTE("remote", "Remote viewer"),
    ;

    companion object {
        fun fromStoredValue(value: String): DeviceRole? = entries.firstOrNull { it.storedValue == value }
    }
}
