package com.firmmy.dashcam

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.firmmy.dashcam.core.common.RecordingMode
import com.firmmy.dashcam.core.common.RecordingStatus
import com.firmmy.dashcam.feature.recorder.RecorderScreen
import com.firmmy.dashcam.feature.recorder.RecorderUiState

@Composable
fun CameraBackedRecorderScreen(
    context: Context,
    modifier: Modifier = Modifier,
    onViewFilesClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
) {
    var state by remember { mutableStateOf(RecorderUiState()) }

    RecorderScreen(
        modifier = modifier,
        state = state,
        onStartStopClick = {
            state = if (state.recordingStatus == RecordingStatus.IDLE) {
                val action = when (state.mode) {
                    RecordingMode.PARKING -> RecorderForegroundService.ACTION_START_PARKING
                    else -> RecorderForegroundService.ACTION_START_DRIVING
                }
                context.startRecorderForegroundService(action, state.audioEnabled)
                state.toRecordingState()
            } else {
                context.startRecorderForegroundService(RecorderForegroundService.ACTION_STOP)
                state.copy(
                    recordingStatus = RecordingStatus.IDLE,
                    currentSegmentMillis = 0L,
                )
            }
        },
        onDrivingModeClick = {
            if (state.recordingStatus != RecordingStatus.IDLE) {
                context.startRecorderForegroundService(RecorderForegroundService.ACTION_SWITCH_DRIVING)
            }
            state = state.copy(
                mode = RecordingMode.DRIVING,
                recordingStatus = if (state.recordingStatus == RecordingStatus.IDLE) {
                    RecordingStatus.IDLE
                } else {
                    RecordingStatus.RECORDING_DRIVING
                },
            )
        },
        onParkingModeClick = {
            if (state.recordingStatus != RecordingStatus.IDLE) {
                context.startRecorderForegroundService(RecorderForegroundService.ACTION_SWITCH_PARKING)
            }
            state = state.copy(
                mode = RecordingMode.PARKING,
                recordingStatus = if (state.recordingStatus == RecordingStatus.IDLE) {
                    RecordingStatus.IDLE
                } else {
                    RecordingStatus.RECORDING_PARKING
                },
            )
        },
        onTakePhotoClick = {
            context.startRecorderForegroundService(RecorderForegroundService.ACTION_TAKE_PHOTO)
            state = state.copy(photoCount = state.photoCount + 1)
        },
        onAudioToggleClick = {
            state = state.copy(audioEnabled = !state.audioEnabled)
        },
        onHotspotToggleClick = {
            state = state.copy(hotspotEnabled = !state.hotspotEnabled)
        },
        onViewFilesClick = onViewFilesClick,
        onSettingsClick = onSettingsClick,
    )
}

private fun RecorderUiState.toRecordingState(): RecorderUiState =
    copy(
        recordingStatus = when (mode) {
            RecordingMode.PARKING -> RecordingStatus.RECORDING_PARKING
            else -> RecordingStatus.RECORDING_DRIVING
        },
        currentSegmentMillis = 1_000L,
    )

private fun Context.startRecorderForegroundService(
    action: String,
    audioEnabled: Boolean? = null,
) {
    ContextCompat.startForegroundService(
        applicationContext,
        RecorderForegroundService.commandIntent(applicationContext, action, audioEnabled),
    )
}
