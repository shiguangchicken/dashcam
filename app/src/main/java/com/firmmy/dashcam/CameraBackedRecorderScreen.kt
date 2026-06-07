package com.firmmy.dashcam

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.firmmy.dashcam.core.common.DashCamCommand
import com.firmmy.dashcam.core.common.RecordingMode
import com.firmmy.dashcam.core.common.RecordingStatus
import com.firmmy.dashcam.core.database.DashCamSettings
import com.firmmy.dashcam.core.network.AndroidLocalOnlyHotspotStarter
import com.firmmy.dashcam.core.network.EmbeddedHttpServer
import com.firmmy.dashcam.core.network.HotspotController
import com.firmmy.dashcam.core.network.HotspotState
import com.firmmy.dashcam.core.network.RemoteConnectionPayload
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
    modifier: Modifier = Modifier,
    onHotspotCredentialsChanged: (ssid: String, password: String) -> Unit = { _, _ -> },
    onViewFilesClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
) {
    var state by remember { mutableStateOf(RecorderUiState()) }
    var recordingStartedAtMillis by remember { mutableStateOf<Long?>(null) }
    var lastRuntimeStatus by remember { mutableStateOf(RecordingStatus.IDLE) }
    val applicationContext = context.applicationContext
    val hotspotController = remember(applicationContext) {
        HotspotController(AndroidLocalOnlyHotspotStarter(applicationContext))
    }
    val remoteServerController = remember(applicationContext) {
        AppRemoteServerController(applicationContext) { command ->
            when (command) {
                DashCamCommand.StartHotspot -> {
                    hotspotController.start().isSuccess
                }

                DashCamCommand.StopHotspot -> {
                    hotspotController.stop()
                    true
                }

                else -> false
            }
        }
    }
    val hotspotState by hotspotController.state.collectAsState()
    var addressesBeforeHotspot by remember { mutableStateOf(emptySet<String>()) }
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

    DisposableEffect(remoteServerController) {
        onDispose {
            remoteServerController.stop()
            hotspotController.stop()
        }
    }

    LaunchedEffect(hotspotState) {
        state = when (val currentHotspotState = hotspotState) {
            HotspotState.Stopped -> {
                remoteServerController.stop()
                RecorderRuntimeState.updateHotspot(enabled = false, ssid = "")
                state.copy(
                    hotspotEnabled = false,
                    remoteServerUrl = "",
                    remoteQrText = "",
                    hotspotError = "",
                )
            }

            HotspotState.Starting -> state.copy(
                hotspotEnabled = false,
                remoteServerUrl = "",
                remoteQrText = "",
                hotspotError = "Starting",
            )

            is HotspotState.Started -> {
                remoteServerController.start()
                RecorderRuntimeState.updateHotspot(
                    enabled = true,
                    ssid = currentHotspotState.credentials.ssid,
                )
                val baseUrl = HotspotEndpointResolver.resolveBaseUrl(addressesBeforeHotspot)
                val qrText = if (baseUrl.isNotBlank()) {
                    RemoteConnectionPayload(
                        ssid = currentHotspotState.credentials.ssid,
                        password = currentHotspotState.credentials.password,
                        baseUrl = baseUrl,
                        port = EmbeddedHttpServer.DEFAULT_PORT,
                    ).toQrText()
                } else {
                    ""
                }
                onHotspotCredentialsChanged(
                    currentHotspotState.credentials.ssid,
                    currentHotspotState.credentials.password,
                )
                state.copy(
                    hotspotEnabled = true,
                    hotspotSsid = currentHotspotState.credentials.ssid,
                    hotspotPassword = currentHotspotState.credentials.password,
                    remoteServerUrl = baseUrl,
                    remoteQrText = qrText,
                    hotspotError = "",
                )
            }

            is HotspotState.Failed -> state.copy(
                hotspotEnabled = false,
                remoteServerUrl = "",
                remoteQrText = "",
                hotspotError = currentHotspotState.message,
            )
        }
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
            delay(1_000L)
        }
    }

    RecorderScreen(
        modifier = modifier,
        state = state,
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
        onHotspotToggleClick = {
            if (hotspotState is HotspotState.Started || hotspotState is HotspotState.Starting) {
                hotspotController.stop()
            } else {
                addressesBeforeHotspot = HotspotEndpointResolver.privateIpv4Addresses()
                hotspotController.start()
            }
        },
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
