package com.firmmy.dashcam.feature.recorder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.firmmy.dashcam.core.common.DashCamFormatters
import com.firmmy.dashcam.core.common.RecordingMode
import com.firmmy.dashcam.core.common.RecordingStatus

data class RecorderUiState(
    val mode: RecordingMode = RecordingMode.DRIVING,
    val recordingStatus: RecordingStatus = RecordingStatus.IDLE,
    val currentSegmentMillis: Long = 0L,
    val remainingStorageBytes: Long = 42L * 1024L * 1024L * 1024L,
    val batteryPercent: Int = 82,
    val temperatureCelsius: Float = 36.5f,
    val audioEnabled: Boolean = true,
    val hotspotEnabled: Boolean = false,
    val hotspotSsid: String = "",
    val hotspotPassword: String = "",
    val remoteServerUrl: String = "",
    val remoteQrText: String = "",
    val hotspotError: String = "",
    val photoCount: Int = 0,
)

@Composable
fun rememberFakeRecorderState(
    initialState: RecorderUiState = RecorderUiState(),
): FakeRecorderStateHolder {
    var state by remember { mutableStateOf(initialState) }
    return remember {
        FakeRecorderStateHolder(
            stateProvider = { state },
            stateUpdater = { state = it },
        )
    }
}

class FakeRecorderStateHolder(
    private val stateProvider: () -> RecorderUiState,
    private val stateUpdater: (RecorderUiState) -> Unit,
) {
    val state: RecorderUiState
        get() = stateProvider()

    fun toggleRecording() {
        val current = state
        stateUpdater(
            if (current.recordingStatus == RecordingStatus.IDLE) {
                current.copy(
                    recordingStatus = current.mode.toRecordingStatus(),
                    currentSegmentMillis = 15_000L,
                )
            } else {
                current.copy(
                    recordingStatus = RecordingStatus.IDLE,
                    currentSegmentMillis = 0L,
                )
            },
        )
    }

    fun switchMode(mode: RecordingMode) {
        val current = state
        stateUpdater(
            current.copy(
                mode = mode,
                recordingStatus = if (current.recordingStatus == RecordingStatus.IDLE) {
                    RecordingStatus.IDLE
                } else {
                    mode.toRecordingStatus()
                },
            ),
        )
    }

    fun takePhoto() {
        stateUpdater(state.copy(photoCount = state.photoCount + 1))
    }

    fun toggleAudio() {
        stateUpdater(state.copy(audioEnabled = !state.audioEnabled))
    }

    fun toggleHotspot() {
        stateUpdater(state.copy(hotspotEnabled = !state.hotspotEnabled))
    }
}

@Composable
fun RecorderScreen(
    modifier: Modifier = Modifier,
    state: RecorderUiState,
    onStartStopClick: () -> Unit,
    onDrivingModeClick: () -> Unit,
    onParkingModeClick: () -> Unit,
    onTakePhotoClick: () -> Unit,
    onAudioToggleClick: () -> Unit,
    onHotspotToggleClick: () -> Unit,
    onViewFilesClick: () -> Unit,
    onSettingsClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text(
            text = "Recorder",
            style = MaterialTheme.typography.headlineMedium,
        )

        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            StatusRow(
                label = "Mode",
                value = state.mode.label,
                modifier = Modifier.testTag("current_mode_text"),
            )
            StatusRow(
                label = "Status",
                value = state.recordingStatus.label,
                modifier = Modifier.testTag("recording_status_text"),
            )
            StatusRow("Segment", DashCamFormatters.formatDuration(state.currentSegmentMillis))
            StatusRow("Remaining", DashCamFormatters.formatFileSize(state.remainingStorageBytes))
            StatusRow("Battery", "${state.batteryPercent.coerceIn(0, 100)}%")
            StatusRow("Temperature", "%.1f C".format(state.temperatureCelsius))
            StatusRow("Audio", if (state.audioEnabled) "On" else "Off")
            StatusRow("Hotspot", if (state.hotspotEnabled) "On" else "Off")
            if (state.hotspotSsid.isNotBlank()) {
                StatusRow(
                    label = "Hotspot SSID",
                    value = state.hotspotSsid,
                    modifier = Modifier.testTag("hotspot_ssid_text"),
                )
            }
            if (state.hotspotPassword.isNotBlank()) {
                StatusRow(
                    label = "Hotspot password",
                    value = state.hotspotPassword,
                    modifier = Modifier.testTag("hotspot_password_text"),
                )
            }
            if (state.remoteServerUrl.isNotBlank()) {
                StatusRow(
                    label = "Remote server",
                    value = state.remoteServerUrl,
                    modifier = Modifier.testTag("remote_server_url_text"),
                )
            }
            if (state.hotspotError.isNotBlank()) {
                StatusRow(
                    label = "Hotspot error",
                    value = state.hotspotError,
                    modifier = Modifier.testTag("hotspot_error_text"),
                )
            }
            StatusRow("Photos", state.photoCount.toString())
        }

        if (state.remoteQrText.isNotBlank()) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "Scan to connect",
                    style = MaterialTheme.typography.titleMedium,
                )
                QrCodeImage(
                    text = state.remoteQrText,
                    modifier = Modifier.testTag("hotspot_qr_code"),
                )
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Button(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("recorder_start_button"),
                onClick = onStartStopClick,
            ) {
                Text(if (state.recordingStatus == RecordingStatus.IDLE) "Start" else "Stop")
            }
            FilledTonalButton(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("mode_driving_button"),
                onClick = onDrivingModeClick,
            ) {
                Text("Driving")
            }
            FilledTonalButton(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("mode_parking_button"),
                onClick = onParkingModeClick,
            ) {
                Text("Parking")
            }
            OutlinedButton(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("take_photo_button"),
                onClick = onTakePhotoClick,
            ) {
                Text("Photo")
            }
            OutlinedButton(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("audio_toggle_button"),
                onClick = onAudioToggleClick,
            ) {
                Text(if (state.audioEnabled) "Mute" else "Audio")
            }
            OutlinedButton(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("hotspot_toggle_button"),
                onClick = onHotspotToggleClick,
            ) {
                Text(if (state.hotspotEnabled) "Hotspot off" else "Hotspot on")
            }
            OutlinedButton(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("view_files_button"),
                onClick = onViewFilesClick,
            ) {
                Text("Files")
            }
            OutlinedButton(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("recorder_settings_button"),
                onClick = onSettingsClick,
            ) {
                Text("Settings")
            }
        }
    }
}

@Composable
fun FakeRecorderScreen(
    modifier: Modifier = Modifier,
    onViewFilesClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
) {
    val stateHolder = rememberFakeRecorderState()
    RecorderScreen(
        modifier = modifier,
        state = stateHolder.state,
        onStartStopClick = stateHolder::toggleRecording,
        onDrivingModeClick = { stateHolder.switchMode(RecordingMode.DRIVING) },
        onParkingModeClick = { stateHolder.switchMode(RecordingMode.PARKING) },
        onTakePhotoClick = stateHolder::takePhoto,
        onAudioToggleClick = stateHolder::toggleAudio,
        onHotspotToggleClick = stateHolder::toggleHotspot,
        onViewFilesClick = onViewFilesClick,
        onSettingsClick = onSettingsClick,
    )
}

@Composable
private fun StatusRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

private fun RecordingMode.toRecordingStatus(): RecordingStatus =
    when (this) {
        RecordingMode.DRIVING -> RecordingStatus.RECORDING_DRIVING
        RecordingMode.PARKING -> RecordingStatus.RECORDING_PARKING
        RecordingMode.MANUAL -> RecordingStatus.RECORDING_DRIVING
        RecordingMode.EVENT -> RecordingStatus.EVENT_BOOST_RECORDING
    }
