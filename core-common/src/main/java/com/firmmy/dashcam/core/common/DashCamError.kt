package com.firmmy.dashcam.core.common

sealed interface DashCamError {
    val message: String

    data class PermissionDenied(
        val permission: String,
        override val message: String = "$permission permission denied",
    ) : DashCamError

    data class StorageUnavailable(
        override val message: String = "Storage is unavailable",
    ) : DashCamError

    data class CameraUnavailable(
        override val message: String = "Camera is unavailable",
    ) : DashCamError

    data class UnsupportedEncoder(
        val codec: String,
        override val message: String = "$codec encoder is unsupported",
    ) : DashCamError

    data class InvalidState(
        val state: String,
        override val message: String = "Invalid state: $state",
    ) : DashCamError

    data class InvalidSetting(
        val key: String,
        val value: String,
        override val message: String = "Invalid setting $key=$value",
    ) : DashCamError

    data class Unknown(
        override val message: String,
    ) : DashCamError
}
