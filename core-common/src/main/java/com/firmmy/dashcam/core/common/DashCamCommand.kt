package com.firmmy.dashcam.core.common

sealed interface DashCamCommand {
    data object StartDrivingMode : DashCamCommand
    data object StartParkingMode : DashCamCommand
    data object TakePhoto : DashCamCommand
    data object EnableAudio : DashCamCommand
    data object DisableAudio : DashCamCommand
    data object StartHotspot : DashCamCommand
    data object StopHotspot : DashCamCommand
    data object LockCurrentClip : DashCamCommand
    data object StopRecording : DashCamCommand
}
