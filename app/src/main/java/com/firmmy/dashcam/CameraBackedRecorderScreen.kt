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
import com.firmmy.dashcam.core.network.AndroidLocalOnlyHotspotStarter
import com.firmmy.dashcam.core.network.EmbeddedHttpServer
import com.firmmy.dashcam.core.network.HotspotController
import com.firmmy.dashcam.core.network.HotspotState
import com.firmmy.dashcam.core.network.RemoteConnectionPayload
import com.firmmy.dashcam.feature.recorder.RecorderScreen
import com.firmmy.dashcam.feature.recorder.RecorderUiState

@Composable
fun CameraBackedRecorderScreen(
    context: Context,
    modifier: Modifier = Modifier,
    onHotspotCredentialsChanged: (ssid: String, password: String) -> Unit = { _, _ -> },
    onViewFilesClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
) {
    var state by remember { mutableStateOf(RecorderUiState()) }
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
