package com.firmmy.dashcam.feature.recorder

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    val voiceStatus: String = "Off",
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
    val recording = state.recordingStatus != RecordingStatus.IDLE
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF111820), Color(0xFF0B1117), Color(0xFF05080B)),
                ),
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            TopBar(state)
            StatusCards(state)
            SpeedBlock(recording)
            RecordingTimer(state)
            VoiceHint(state.voiceStatus)
            ControlCluster(
                state = state,
                recording = recording,
                onStartStopClick = onStartStopClick,
                onTakePhotoClick = onTakePhotoClick,
                onAudioToggleClick = onAudioToggleClick,
                onHotspotToggleClick = onHotspotToggleClick,
                onSettingsClick = onSettingsClick,
            )
            ModeControls(
                state = state,
                onDrivingModeClick = onDrivingModeClick,
                onParkingModeClick = onParkingModeClick,
                onViewFilesClick = onViewFilesClick,
            )
            RemoteAccessPanel(state)
            Spacer(Modifier.height(12.dp))
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
private fun TopBar(state: RecorderUiState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "▣ DroidDash",
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            fontSize = 24.sp,
        )
        HudChip("42 C", MaterialTheme.colorScheme.secondary)
        HudChip(if (state.batteryPercent > 20) "Charging" else "Battery low", MaterialTheme.colorScheme.tertiary)
    }
}

@Composable
private fun StatusCards(state: RecorderUiState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        GlassPanel(modifier = Modifier.weight(1f)) {
            Text("GPS SIGNAL", color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
            Text("LOCKED · 12 SAT", color = MaterialTheme.colorScheme.secondary, fontFamily = FontFamily.Monospace)
        }
        GlassPanel(modifier = Modifier.weight(1.35f)) {
            Text("STORAGE", color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
            Text(
                DashCamFormatters.formatFileSize(state.remainingStorageBytes),
                color = MaterialTheme.colorScheme.onSurface,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

@Composable
private fun SpeedBlock(recording: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = if (recording) "67" else "0",
            color = Color.White,
            fontSize = 92.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 92.sp,
        )
        Text(
            text = "KM/H",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun RecordingTimer(state: RecorderUiState) {
    val active = state.recordingStatus != RecordingStatus.IDLE
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (active) Color(0xFF6E2630) else Color(0xFF252A31))
            .padding(horizontal = 22.dp, vertical = 10.dp)
            .testTag("recording_status_text"),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(11.dp)
                .clip(CircleShape)
                .background(if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.outline),
        )
        Text(
            text = DashCamFormatters.formatDuration(state.currentSegmentMillis),
            color = Color.White,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
        )
    }
    Text(
        modifier = Modifier.testTag("current_mode_text"),
        text = "${state.mode.label} · ${state.recordingStatus.label}",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun VoiceHint(voiceStatus: String) {
    Text(
        text = if (voiceStatus == "Off") "Say \"Take Photo\" to capture" else "Voice $voiceStatus",
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0x77262A31))
            .padding(12.dp)
            .testTag("voice_status_text"),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun ControlCluster(
    state: RecorderUiState,
    recording: Boolean,
    onStartStopClick: () -> Unit,
    onTakePhotoClick: () -> Unit,
    onAudioToggleClick: () -> Unit,
    onHotspotToggleClick: () -> Unit,
    onSettingsClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SquareControl("CAM", "take_photo_button", onTakePhotoClick)
        SquareControl(if (state.audioEnabled) "MIC" else "MUTE", "audio_toggle_button", onAudioToggleClick)
        Button(
            modifier = Modifier
                .size(96.dp)
                .testTag("recorder_start_button"),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = Color(0xFF1D0900),
            ),
            onClick = onStartStopClick,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (recording) "■" else "●", fontSize = 22.sp)
                Text(if (recording) "STOP" else "REC", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
        SquareControl(if (state.hotspotEnabled) "LINK" else "WIFI", "hotspot_toggle_button", onHotspotToggleClick)
        SquareControl("SET", "recorder_settings_button", onSettingsClick)
    }
}

@Composable
private fun ModeControls(
    state: RecorderUiState,
    onDrivingModeClick: () -> Unit,
    onParkingModeClick: () -> Unit,
    onViewFilesClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ModeButton(
            label = "Driving",
            selected = state.mode == RecordingMode.DRIVING,
            tag = "mode_driving_button",
            onClick = onDrivingModeClick,
            modifier = Modifier.weight(1f),
        )
        ModeButton(
            label = "Parking",
            selected = state.mode == RecordingMode.PARKING,
            tag = "mode_parking_button",
            onClick = onParkingModeClick,
            modifier = Modifier.weight(1f),
        )
        ModeButton(
            label = "Files",
            selected = false,
            tag = "view_files_button",
            onClick = onViewFilesClick,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun RemoteAccessPanel(state: RecorderUiState) {
    GlassPanel(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Active Remote Viewers", fontWeight = FontWeight.Bold)
            Text(
                if (state.hotspotEnabled) "READY" else "OFF",
                color = if (state.hotspotEnabled) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outline,
                fontWeight = FontWeight.Bold,
            )
        }
        if (state.hotspotSsid.isNotBlank()) {
            StatusRow("SSID", state.hotspotSsid, Modifier.testTag("hotspot_ssid_text"))
        }
        if (state.hotspotPassword.isNotBlank()) {
            StatusRow("Password", state.hotspotPassword, Modifier.testTag("hotspot_password_text"))
        }
        if (state.remoteServerUrl.isNotBlank()) {
            StatusRow("Server", state.remoteServerUrl, Modifier.testTag("remote_server_url_text"))
        }
        if (state.hotspotError.isNotBlank()) {
            StatusRow("Hotspot", state.hotspotError, Modifier.testTag("hotspot_error_text"))
        }
        StatusRow("Photos", state.photoCount.toString())
        if (state.remoteQrText.isNotBlank()) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Scan to connect", color = MaterialTheme.colorScheme.onSurfaceVariant)
                QrCodeImage(
                    text = state.remoteQrText,
                    modifier = Modifier.testTag("hotspot_qr_code"),
                )
            }
        }
    }
}

@Composable
private fun SquareControl(
    label: String,
    tag: String,
    onClick: () -> Unit,
) {
    OutlinedButton(
        modifier = Modifier
            .size(56.dp)
            .testTag(tag),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, Color(0x33FFFFFF)),
        onClick = onClick,
        contentPadding = ButtonDefaults.ContentPadding,
    ) {
        Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ModeButton(
    label: String,
    selected: Boolean,
    tag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedButton(
        modifier = modifier.testTag(tag),
        onClick = onClick,
        border = BorderStroke(
            1.dp,
            if (selected) MaterialTheme.colorScheme.primary else Color(0x33FFFFFF),
        ),
    ) {
        Text(label)
    }
}

@Composable
private fun HudChip(
    text: String,
    color: Color,
) {
    Text(
        text = text,
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0x99262A31))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        color = color,
        fontSize = 12.sp,
        fontFamily = FontFamily.Monospace,
    )
}

@Composable
private fun GlassPanel(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0x66181C22))
            .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(8.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
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
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, color = MaterialTheme.colorScheme.onSurface, fontFamily = FontFamily.Monospace)
    }
}

private fun RecordingMode.toRecordingStatus(): RecordingStatus =
    when (this) {
        RecordingMode.PARKING -> RecordingStatus.RECORDING_PARKING
        RecordingMode.EVENT -> RecordingStatus.EVENT_BOOST_RECORDING
        else -> RecordingStatus.RECORDING_DRIVING
    }
