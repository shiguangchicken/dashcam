package com.firmmy.dashcam.core.common

enum class MediaType(
    val storedValue: String,
) {
    VIDEO("video"),
    PHOTO("photo"),
    ;

    companion object {
        fun fromStoredValue(value: String): MediaType? = entries.firstOrNull { it.storedValue == value }
    }
}
