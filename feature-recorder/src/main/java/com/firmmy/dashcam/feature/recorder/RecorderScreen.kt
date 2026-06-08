package com.firmmy.dashcam.feature.recorder

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
                    listOf(Color(0xFF10141A), Color(0xFF0A0E14), Color(0xFF05080B)),
                ),
            )
            .testTag("recorder_dashboard"),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            DroidDashTopBar(state)
            RecorderTelemetryRow(state)
            CameraPreviewHud(recording)
            RecordingTimer(state)
            VoiceHint(state.voiceStatus)
            RecorderControls(
                state = state,
                recording = recording,
                onStartStopClick = onStartStopClick,
                onTakePhotoClick = onTakePhotoClick,
                onAudioToggleClick = onAudioToggleClick,
                onHotspotToggleClick = onHotspotToggleClick,
                onSettingsClick = onSettingsClick,
            )
            RecorderBottomNav(
                state = state,
                onDrivingModeClick = onDrivingModeClick,
                onParkingModeClick = onParkingModeClick,
                onViewFilesClick = onViewFilesClick,
                onSettingsClick = onSettingsClick,
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
private fun DroidDashTopBar(state: RecorderUiState) {
    GlassPanel(
        modifier = Modifier.fillMaxWidth(),
        padding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("CAM", color = SafetyOrange, fontWeight = FontWeight.Bold)
                Text(
                    text = "DroidDash",
                    color = SafetyOrange,
                    fontWeight = FontWeight.Bold,
                    fontSize = 24.sp,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HudChip("${state.temperatureCelsius.toInt()} C", CyberBlue)
                HudChip(if (state.batteryPercent > 20) "Charging" else "Battery low", SignalGreen)
            }
        }
    }
}

@Composable
private fun RecorderTelemetryRow(state: RecorderUiState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        GlassPanel(modifier = Modifier.weight(1f)) {
            Text("GPS SIGNAL", color = MutedText, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Text("LOCKED / 12 SAT", color = CyberBlue, fontFamily = FontFamily.Monospace, fontSize = 14.sp)
        }
        GlassPanel(modifier = Modifier.weight(1.2f)) {
            Text("STORAGE", color = MutedText, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Text(
                DashCamFormatters.formatFileSize(state.remainingStorageBytes),
                color = Foreground,
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp,
            )
        }
    }
}

@Composable
private fun CameraPreviewHud(recording: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(8.dp))
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF172331), Color(0xFF0A0E14), Color(0xFF1E1510)),
                ),
            )
            .border(1.dp, Color(0x1FFFFFFF), RoundedCornerShape(8.dp)),
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            MiniChip(if (recording) "REC ACTIVE" else "REC READY", if (recording) SafetyOrange else MutedText)
            MiniChip("FRONT 4K / WIDE", CyberBlue)
        }
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = if (recording) "29" else "0",
                color = Color.White,
                fontSize = 88.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 88.sp,
            )
            Text("KM/H", color = MutedText, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        Text(
            text = "LANE / OBJECT / IMPACT MONITOR",
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(12.dp),
            color = MutedText,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
        )
    }
}

@Composable
private fun RecordingTimer(state: RecorderUiState) {
    val active = state.recordingStatus != RecordingStatus.IDLE
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(24.dp))
            .background(if (active) Color(0x66300000) else SurfaceHigh)
            .border(1.dp, if (active) SafetyOrange.copy(alpha = 0.35f) else Color(0x22FFFFFF), RoundedCornerShape(24.dp))
            .padding(horizontal = 22.dp, vertical = 10.dp)
            .testTag("recording_status_text"),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(11.dp)
                .clip(CircleShape)
                .background(if (active) SafetyOrange else MutedText),
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
        text = "${state.mode.label} / ${state.recordingStatus.label}",
        color = MutedText,
        fontFamily = FontFamily.Monospace,
    )
}

@Composable
private fun VoiceHint(voiceStatus: String) {
    GlassPanel(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("voice_status_text"),
        padding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(
            text = if (voiceStatus == "Off") "Say \"Take Photo\" to capture" else "Voice $voiceStatus",
            color = MutedText,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun RecorderControls(
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
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RoundControl("CAM", "take_photo_button", onTakePhotoClick)
        RoundControl(if (state.audioEnabled) "MIC" else "MUTE", "audio_toggle_button", onAudioToggleClick)
        Button(
            modifier = Modifier
                .size(96.dp)
                .testTag("recorder_start_button"),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = SafetyOrange,
                contentColor = Color(0xFF351000),
            ),
            onClick = onStartStopClick,
            contentPadding = PaddingValues(0.dp),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (recording) "STOP" else "REC", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(if (recording) "ACTIVE" else "READY", fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
        RoundControl(if (state.hotspotEnabled) "LINK" else "WIFI", "hotspot_toggle_button", onHotspotToggleClick)
        RoundControl("SET", "recorder_settings_button", onSettingsClick)
    }
}

@Composable
private fun RecorderBottomNav(
    state: RecorderUiState,
    onDrivingModeClick: () -> Unit,
    onParkingModeClick: () -> Unit,
    onViewFilesClick: () -> Unit,
    onSettingsClick: () -> Unit,
) {
    GlassPanel(
        modifier = Modifier.fillMaxWidth(),
        padding = PaddingValues(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            NavButton(
                label = "Recorder",
                selected = state.mode == RecordingMode.DRIVING,
                tag = "mode_driving_button",
                onClick = onDrivingModeClick,
                modifier = Modifier.weight(1f),
            )
            NavButton(
                label = "Parking",
                selected = state.mode == RecordingMode.PARKING,
                tag = "mode_parking_button",
                onClick = onParkingModeClick,
                modifier = Modifier.weight(1f),
            )
            NavButton(
                label = "Media",
                selected = false,
                tag = "view_files_button",
                onClick = onViewFilesClick,
                modifier = Modifier.weight(1f),
            )
            NavButton(
                label = "Settings",
                selected = false,
                tag = "dashboard_settings_nav_button",
                onClick = onSettingsClick,
                modifier = Modifier.weight(1f),
            )
        }
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
            Column {
                Text("Active Remote Viewers", color = Foreground, fontWeight = FontWeight.Bold)
                Text(
                    if (state.hotspotEnabled) "Hotspot ready for remote clients" else "Hotspot is off",
                    color = MutedText,
                    fontSize = 12.sp,
                )
            }
            Text(
                if (state.hotspotEnabled) "READY" else "OFF",
                color = if (state.hotspotEnabled) SafetyOrange else MutedText,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
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
                Text("Scan to connect", color = MutedText)
                QrCodeImage(
                    text = state.remoteQrText,
                    modifier = Modifier.testTag("hotspot_qr_code"),
                )
            }
        }
    }
}

@Composable
private fun RoundControl(
    label: String,
    tag: String,
    onClick: () -> Unit,
) {
    OutlinedButton(
        modifier = Modifier
            .size(58.dp)
            .testTag(tag),
        shape = CircleShape,
        border = BorderStroke(1.dp, Color(0x33FFFFFF)),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Foreground),
        onClick = onClick,
        contentPadding = PaddingValues(0.dp),
    ) {
        Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun NavButton(
    label: String,
    selected: Boolean,
    tag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedButton(
        modifier = modifier.testTag(tag),
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, if (selected) SafetyOrange else Color(0x22FFFFFF)),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (selected) SafetyOrange.copy(alpha = 0.18f) else Color.Transparent,
            contentColor = if (selected) SafetyOrange else MutedText,
        ),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
    ) {
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
            .background(SurfaceHigh)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        color = color,
        fontSize = 12.sp,
        fontFamily = FontFamily.Monospace,
    )
}

@Composable
private fun MiniChip(
    text: String,
    color: Color,
) {
    Text(
        text = text,
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0x99000000))
            .border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(18.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        color = color,
        fontSize = 11.sp,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
    )
}

@Composable
private fun GlassPanel(
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(14.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0x77181C22))
            .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(8.dp))
            .padding(padding),
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
        Text(label, color = MutedText, fontSize = 13.sp)
        Spacer(Modifier.width(12.dp))
        Text(
            text = value,
            color = Foreground,
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            textAlign = TextAlign.End,
        )
    }
}

private fun RecordingMode.toRecordingStatus(): RecordingStatus =
    when (this) {
        RecordingMode.PARKING -> RecordingStatus.RECORDING_PARKING
        RecordingMode.EVENT -> RecordingStatus.EVENT_BOOST_RECORDING
        else -> RecordingStatus.RECORDING_DRIVING
    }

private val Foreground = Color(0xFFDFE2EB)
private val MutedText = Color(0xFFE2BFB0)
private val SurfaceHigh = Color(0xFF262A31)
private val SafetyOrange = Color(0xFFFF6B00)
private val CyberBlue = Color(0xFF98CBFF)
private val SignalGreen = Color(0xFF4AE183)
