package com.firmmy.dashcam.core.media

import com.firmmy.dashcam.core.common.DashCamCommand
import com.firmmy.dashcam.core.common.DashCamError
import com.firmmy.dashcam.core.common.DashCamResult
import com.firmmy.dashcam.core.common.RecordingMode
import com.firmmy.dashcam.core.common.RecordingStatus

class DashCamStateMachine(
    initialState: DashCamRecorderState = DashCamRecorderState(),
) {
    var state: DashCamRecorderState = initialState
        private set

    fun dispatch(command: DashCamCommand): DashCamResult<DashCamRecorderState> {
        val nextState = when (command) {
            DashCamCommand.StartDrivingMode -> startDriving()
            DashCamCommand.StartParkingMode -> startParking()
            DashCamCommand.TakePhoto -> takePhoto()
            DashCamCommand.EnableAudio -> state.copy(audioEnabled = true)
            DashCamCommand.DisableAudio -> state.copy(audioEnabled = false)
            DashCamCommand.StartHotspot -> state.copy(hotspotEnabled = true)
            DashCamCommand.StopHotspot -> state.copy(hotspotEnabled = false)
            DashCamCommand.LockCurrentClip -> lockCurrentClip()
            DashCamCommand.StopRecording -> stopRecording()
        }

        return if (nextState == null) {
            val commandName = command.displayName()
            DashCamResult.Failure(
                DashCamError.InvalidState(
                    state = "${state.status.storedValue} + $commandName",
                    message = "Cannot run $commandName from ${state.status.label}",
                ),
            )
        } else {
            state = nextState
            DashCamResult.Success(state)
        }
    }

    private fun startDriving(): DashCamRecorderState? =
        when (state.status) {
            RecordingStatus.IDLE,
            RecordingStatus.RECORDING_PARKING,
            RecordingStatus.PAUSED,
            RecordingStatus.EVENT_BOOST_RECORDING,
            -> state.copy(
                status = RecordingStatus.RECORDING_DRIVING,
                mode = RecordingMode.DRIVING,
                currentClipLocked = false,
                errorMessage = null,
            )

            RecordingStatus.RECORDING_DRIVING -> state
            RecordingStatus.ERROR -> null
        }

    private fun startParking(): DashCamRecorderState? =
        when (state.status) {
            RecordingStatus.IDLE,
            RecordingStatus.RECORDING_DRIVING,
            RecordingStatus.PAUSED,
            RecordingStatus.EVENT_BOOST_RECORDING,
            -> state.copy(
                status = RecordingStatus.RECORDING_PARKING,
                mode = RecordingMode.PARKING,
                currentClipLocked = false,
                errorMessage = null,
            )

            RecordingStatus.RECORDING_PARKING -> state
            RecordingStatus.ERROR -> null
        }

    private fun takePhoto(): DashCamRecorderState? =
        when (state.status) {
            RecordingStatus.IDLE,
            RecordingStatus.RECORDING_DRIVING,
            RecordingStatus.RECORDING_PARKING,
            RecordingStatus.PAUSED,
            RecordingStatus.EVENT_BOOST_RECORDING,
            -> state.copy(photoCount = state.photoCount + 1)

            RecordingStatus.ERROR -> null
        }

    private fun lockCurrentClip(): DashCamRecorderState? =
        when (state.status) {
            RecordingStatus.RECORDING_DRIVING -> state.copy(currentClipLocked = true)
            RecordingStatus.RECORDING_PARKING -> state.copy(
                status = RecordingStatus.EVENT_BOOST_RECORDING,
                mode = RecordingMode.EVENT,
                currentClipLocked = true,
            )

            RecordingStatus.EVENT_BOOST_RECORDING -> state.copy(currentClipLocked = true)
            RecordingStatus.IDLE,
            RecordingStatus.PAUSED,
            RecordingStatus.ERROR,
            -> null
        }

    private fun stopRecording(): DashCamRecorderState? =
        when (state.status) {
            RecordingStatus.RECORDING_DRIVING,
            RecordingStatus.RECORDING_PARKING,
            RecordingStatus.PAUSED,
            RecordingStatus.EVENT_BOOST_RECORDING,
            RecordingStatus.ERROR,
            -> state.copy(
                status = RecordingStatus.IDLE,
                mode = null,
                currentClipLocked = false,
                errorMessage = null,
            )

            RecordingStatus.IDLE -> state
        }

    private fun DashCamCommand.displayName(): String =
        when (this) {
            DashCamCommand.StartDrivingMode -> "StartDrivingMode"
            DashCamCommand.StartParkingMode -> "StartParkingMode"
            DashCamCommand.TakePhoto -> "TakePhoto"
            DashCamCommand.EnableAudio -> "EnableAudio"
            DashCamCommand.DisableAudio -> "DisableAudio"
            DashCamCommand.StartHotspot -> "StartHotspot"
            DashCamCommand.StopHotspot -> "StopHotspot"
            DashCamCommand.LockCurrentClip -> "LockCurrentClip"
            DashCamCommand.StopRecording -> "StopRecording"
        }
}
