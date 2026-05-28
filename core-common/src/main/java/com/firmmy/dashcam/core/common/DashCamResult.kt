package com.firmmy.dashcam.core.common

sealed interface DashCamResult<out T> {
    data class Success<T>(val value: T) : DashCamResult<T>
    data class Failure(val error: DashCamError) : DashCamResult<Nothing>

    val isSuccess: Boolean
        get() = this is Success<T>
}
