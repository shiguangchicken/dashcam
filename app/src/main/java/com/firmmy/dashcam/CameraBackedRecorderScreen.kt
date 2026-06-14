package com.firmmy.dashcam

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.firmmy.dashcam.core.common.RecordingMode
import com.firmmy.dashcam.core.common.RecordingStatus
import com.firmmy.dashcam.core.database.DashCamSettings
import com.firmmy.dashcam.core.voice.VoiceListeningStatus
import com.firmmy.dashcam.core.voice.VoiceRuntimeConditions
import com.firmmy.dashcam.core.voice.VoiceSafetyPolicy
import com.firmmy.dashcam.feature.recorder.RecorderScreen
import com.firmmy.dashcam.feature.recorder.RecorderUiState
import kotlinx.coroutines.delay

@Composable
fun CameraBackedRecorderScreen(
    context: Context,
    settings: DashCamSettings,
    hotspot: RecorderHotspotUiState,
    modifier: Modifier = Modifier,
    onHotspotClick: () -> Unit = {},
    onViewFilesClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
) {
    var state by remember { mutableStateOf(RecorderUiState()) }
    var recordingStartedAtMillis by remember { mutableStateOf<Long?>(null) }
    var lastRuntimeStatus by remember { mutableStateOf(RecordingStatus.IDLE) }
    val voiceStatus = remember(
        settings.voiceWakeupEnabled,
        state.audioEnabled,
        state.batteryPercent,
        state.temperatureCelsius,
    ) {
        val pauseReason = VoiceSafetyPolicy.pauseReasonFor(
            VoiceRuntimeConditions(
                voiceWakeupEnabled = settings.voiceWakeupEnabled,
                audioRecordingActive = state.audioEnabled,
                batteryPercent = state.batteryPercent,
                temperatureCelsius = state.temperatureCelsius,
            ),
        )
        when {
            pauseReason == null -> VoiceListeningStatus.LISTENING.name
            !settings.voiceWakeupEnabled -> "Off"
            else -> "${VoiceListeningStatus.PAUSED.name}: ${pauseReason.name}"
        }
    }

    LaunchedEffect(voiceStatus) {
        state = state.copy(voiceStatus = voiceStatus)
    }

    LaunchedEffect(state.recordingStatus, recordingStartedAtMillis) {
        val startedAt = recordingStartedAtMillis ?: return@LaunchedEffect
        while (state.recordingStatus != RecordingStatus.IDLE) {
            state = state.copy(currentSegmentMillis = System.currentTimeMillis() - startedAt)
            delay(1_000L)
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            val runtimeStatus = RecorderRuntimeState.status()
            val runtimeRecordingStatus = runtimeStatus.recordingStatus
            if (runtimeRecordingStatus != lastRuntimeStatus) {
                recordingStartedAtMillis = if (runtimeRecordingStatus == RecordingStatus.IDLE) {
                    null
                } else {
                    System.currentTimeMillis()
                }
                lastRuntimeStatus = runtimeRecordingStatus
            }
            state = state.copy(
                mode = runtimeStatus.mode,
                recordingStatus = runtimeRecordingStatus,
                audioEnabled = runtimeStatus.audioEnabled,
                remainingStorageBytes = runtimeStatus.freeSpaceBytes.takeIf { it > 0L }
                    ?: state.remainingStorageBytes,
                currentSegmentMillis = if (runtimeRecordingStatus == RecordingStatus.IDLE) {
                    0L
                } else {
                    state.currentSegmentMillis
                },
            )
            delay(250L)
        }
    }

    RecorderScreen(
        modifier = modifier,
        state = state.copy(
            hotspotEnabled = hotspot.enabled,
            hotspotSsid = hotspot.ssid,
            hotspotPassword = hotspot.password,
            remoteServerUrl = hotspot.remoteServerUrl,
            remoteQrText = hotspot.remoteQrText,
            hotspotError = hotspot.error,
            remoteViewers = hotspot.remoteViewers,
        ),
        recordingBackground = {
            RecorderPreviewBackground()
        },
        onStartStopClick = {
            if (state.recordingStatus == RecordingStatus.IDLE) {
                val action = when (state.mode) {
                    RecordingMode.PARKING -> RecorderForegroundService.ACTION_START_PARKING
                    else -> RecorderForegroundService.ACTION_START_DRIVING
                }
                context.startRecorderForegroundService(action, state.audioEnabled)
            } else {
                context.startRecorderForegroundService(RecorderForegroundService.ACTION_STOP)
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
        onHotspotToggleClick = onHotspotClick,
        onViewFilesClick = onViewFilesClick,
        onSettingsClick = onSettingsClick,
    )
}

private fun Context.startRecorderForegroundService(
    action: String,
    audioEnabled: Boolean? = null,
) {
    ContextCompat.startForegroundService(
        applicationContext,
        RecorderForegroundService.commandIntent(applicationContext, action, audioEnabled),
    )
}
