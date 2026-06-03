package com.firmmy.dashcam.feature.remote

import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.firmmy.dashcam.core.common.DashCamCommand
import com.firmmy.dashcam.core.common.DashCamFormatters
import com.firmmy.dashcam.core.common.MediaType
import com.firmmy.dashcam.core.common.RecordingMode
import com.firmmy.dashcam.core.network.RemoteMediaItem
import com.firmmy.dashcam.core.network.RemoteSettings
import com.firmmy.dashcam.core.network.RemoteStatus
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

interface RemoteViewerClient {
    suspend fun connect(manualHost: String): Boolean

    suspend fun status(): RemoteStatus

    suspend fun media(type: MediaType? = null): List<RemoteMediaItem>

    suspend fun send(command: DashCamCommand): Boolean

    suspend fun deleteMedia(id: Long): Boolean

    suspend fun settings(): RemoteSettings

    suspend fun saveSettings(settings: RemoteSettings): Boolean

    fun streamUrl(id: Long): String
}

data class RemoteViewerUiState(
    val connected: Boolean = false,
    val connecting: Boolean = false,
    val manualHost: String = "",
    val status: RemoteStatus = RemoteStatus(),
    val videos: List<RemoteMediaItem> = emptyList(),
    val photos: List<RemoteMediaItem> = emptyList(),
    val selectedType: MediaType = MediaType.VIDEO,
    val selectedItem: RemoteMediaItem? = null,
    val message: String = "",
    val settings: RemoteSettings? = null,
) {
    companion object
}

@Composable
fun RemoteViewerScreen(
    client: RemoteViewerClient,
    modifier: Modifier = Modifier,
    initialManualHost: String = "",
) {
    var state by remember {
        mutableStateOf(RemoteViewerUiState(manualHost = initialManualHost))
    }
    val scope = rememberCoroutineScope()

    fun refresh() {
        scope.launch {
            runCatching {
                state = state.copy(
                    status = client.status(),
                    videos = client.media(MediaType.VIDEO),
                    photos = client.media(MediaType.PHOTO),
                    settings = client.settings(),
                    message = "",
                )
            }.onFailure {
                state = state.copy(message = it.message ?: "Remote refresh failed")
            }
        }
    }

    LaunchedEffect(state.connected) {
        if (state.connected) refresh()
    }

    val selectedItem = state.selectedItem
    if (selectedItem != null) {
        RemoteViewerDetailContent(
            item = selectedItem,
            streamUrl = client.streamUrl(selectedItem.id),
            onBackClick = { state = state.copy(selectedItem = null) },
            onDeleteClick = {
                scope.launch {
                    if (client.deleteMedia(selectedItem.id)) {
                        state = state.copy(selectedItem = null)
                        refresh()
                    } else {
                        state = state.copy(message = "Delete failed")
                    }
                }
            },
            modifier = modifier,
        )
        return
    }

    RemoteViewerContent(
        state = state,
        modifier = modifier,
        onHostChanged = { state = state.copy(manualHost = it) },
        onConnectClick = {
            scope.launch {
                state = state.copy(connecting = true, message = "")
                val connected = runCatching { client.connect(state.manualHost) }.getOrDefault(false)
                state = state.copy(
                    connected = connected,
                    connecting = false,
                    message = if (connected) "" else "Connection failed",
                )
                if (connected) refresh()
            }
        },
        onRefreshClick = ::refresh,
        onCommand = { command ->
            scope.launch {
                val ok = client.send(command)
                state = state.copy(message = if (ok) "" else "Command failed")
                refresh()
            }
        },
        onTypeSelected = { state = state.copy(selectedType = it) },
        onItemSelected = { state = state.copy(selectedItem = it) },
        onSettingsChanged = { state = state.copy(settings = it) },
        onSaveSettingsClick = {
            scope.launch {
                val settings = state.settings ?: return@launch
                val ok = client.saveSettings(settings)
                state = state.copy(message = if (ok) "" else "Settings save failed")
            }
        },
    )
}

@Composable
fun RemoteViewerContent(
    state: RemoteViewerUiState,
    modifier: Modifier = Modifier,
    onHostChanged: (String) -> Unit = {},
    onConnectClick: () -> Unit = {},
    onRefreshClick: () -> Unit = {},
    onCommand: (DashCamCommand) -> Unit = {},
    onTypeSelected: (MediaType) -> Unit = {},
    onItemSelected: (RemoteMediaItem) -> Unit = {},
    onSettingsChanged: (RemoteSettings) -> Unit = {},
    onSaveSettingsClick: () -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Remote", style = MaterialTheme.typography.headlineMedium)

        RemoteConnectPanel(
            manualHost = state.manualHost,
            connecting = state.connecting,
            connected = state.connected,
            message = state.message,
            onHostChanged = onHostChanged,
            onConnectClick = onConnectClick,
        )

        if (state.connected) {
            RemoteStatusPanel(
                status = state.status,
                onRefreshClick = onRefreshClick,
            )
            RemoteControlPanel(
                audioEnabled = state.status.audioEnabled,
                onCommand = onCommand,
            )
            RemoteMediaPanel(
                state = state,
                onTypeSelected = onTypeSelected,
                onItemSelected = onItemSelected,
            )
            RemoteSettingsPanel(
                settings = state.settings,
                onSettingsChanged = onSettingsChanged,
                onSaveClick = onSaveSettingsClick,
            )
        }
    }
}

@Composable
private fun RemoteConnectPanel(
    manualHost: String,
    connecting: Boolean,
    connected: Boolean,
    message: String,
    onHostChanged: (String) -> Unit,
    onConnectClick: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("remote_manual_ip_field"),
            value = manualHost,
            onValueChange = onHostChanged,
            label = { Text("Manual IP") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            singleLine = true,
        )
        Button(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("remote_connect_button"),
            onClick = onConnectClick,
            enabled = !connecting,
        ) {
            Text(
                when {
                    connecting -> "Connecting"
                    connected -> "Reconnect"
                    else -> "Connect"
                },
            )
        }
        if (message.isNotBlank()) {
            Text(
                modifier = Modifier.testTag("remote_message_text"),
                text = message,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun RemoteStatusPanel(
    status: RemoteStatus,
    onRefreshClick: () -> Unit,
) {
    Column(
        modifier = Modifier.testTag("remote_status_screen"),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Recorder status", style = MaterialTheme.typography.titleMedium)
            OutlinedButton(
                modifier = Modifier.testTag("remote_refresh_button"),
                onClick = onRefreshClick,
            ) {
                Text("Refresh")
            }
        }
        RemoteStatusRow("Mode", status.mode.label)
        RemoteStatusRow("Status", status.recordingStatus.label)
        RemoteStatusRow("Space", DashCamFormatters.formatFileSize(status.freeSpaceBytes))
        RemoteStatusRow("Audio", if (status.audioEnabled) "On" else "Off")
        RemoteStatusRow("Hotspot", if (status.hotspotEnabled) status.hotspotSsid.ifBlank { "On" } else "Off")
        RemoteStatusRow("Battery", status.batteryPercent?.let { "$it%" } ?: "--")
        RemoteStatusRow("Temperature", status.temperatureCelsius?.let { "%.1f C".format(it) } ?: "--")
    }
}

@Composable
private fun RemoteStatusRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label)
        Text(value)
    }
}

@Composable
private fun RemoteControlPanel(
    audioEnabled: Boolean,
    onCommand: (DashCamCommand) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                modifier = Modifier
                    .weight(1f)
                    .testTag("remote_take_photo_button"),
                onClick = { onCommand(DashCamCommand.TakePhoto) },
            ) {
                Text("Photo")
            }
            OutlinedButton(
                modifier = Modifier
                    .weight(1f)
                    .testTag("remote_audio_toggle_button"),
                onClick = {
                    onCommand(
                        if (audioEnabled) DashCamCommand.DisableAudio else DashCamCommand.EnableAudio,
                    )
                },
            ) {
                Text(if (audioEnabled) "Mute" else "Audio")
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                modifier = Modifier
                    .weight(1f)
                    .testTag("remote_mode_driving_button"),
                onClick = { onCommand(DashCamCommand.StartDrivingMode) },
            ) {
                Text("Driving")
            }
            OutlinedButton(
                modifier = Modifier
                    .weight(1f)
                    .testTag("remote_mode_parking_button"),
                onClick = { onCommand(DashCamCommand.StartParkingMode) },
            ) {
                Text("Parking")
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                modifier = Modifier
                    .weight(1f)
                    .testTag("remote_hotspot_on_button"),
                onClick = { onCommand(DashCamCommand.StartHotspot) },
            ) {
                Text("Hotspot on")
            }
            OutlinedButton(
                modifier = Modifier
                    .weight(1f)
                    .testTag("remote_hotspot_off_button"),
                onClick = { onCommand(DashCamCommand.StopHotspot) },
            ) {
                Text("Hotspot off")
            }
        }
    }
}

@Composable
private fun RemoteMediaPanel(
    state: RemoteViewerUiState,
    onTypeSelected: (MediaType) -> Unit,
    onItemSelected: (RemoteMediaItem) -> Unit,
) {
    val items = remoteMediaForSelectedType(state)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TabRow(selectedTabIndex = if (state.selectedType == MediaType.VIDEO) 0 else 1) {
            Tab(
                selected = state.selectedType == MediaType.VIDEO,
                onClick = { onTypeSelected(MediaType.VIDEO) },
                text = { Text("Videos") },
            )
            Tab(
                selected = state.selectedType == MediaType.PHOTO,
                onClick = { onTypeSelected(MediaType.PHOTO) },
                text = { Text("Photos") },
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .testTag("remote_media_filter_mode"),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf(null, RecordingMode.DRIVING, RecordingMode.PARKING, RecordingMode.EVENT, RecordingMode.MANUAL)
                .forEach { mode ->
                    FilterChip(
                        selected = false,
                        onClick = {},
                        label = { Text(mode?.label ?: "All") },
                    )
                }
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .testTag(if (state.selectedType == MediaType.VIDEO) "remote_video_list" else "remote_photo_list"),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(items, key = { it.id }) { item ->
                RemoteMediaRow(item = item, onClick = { onItemSelected(item) })
            }
        }
    }
}

@Composable
private fun RemoteMediaRow(
    item: RemoteMediaItem,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("remote_media_item_${item.id}"),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(DashCamFormatters.formatTimestamp(item.createdAt), style = MaterialTheme.typography.titleMedium)
            Text("${item.mode.label} · ${DashCamFormatters.formatFileSize(item.sizeBytes)}")
            Text(
                listOfNotNull(
                    item.durationMs?.let(DashCamFormatters::formatDuration),
                    if (item.locked) "Locked" else null,
                ).joinToString(" · ").ifBlank { item.type.storedValue },
            )
        }
    }
}

@Composable
fun RemoteViewerDetailContent(
    item: RemoteMediaItem,
    streamUrl: String,
    onBackClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(onClick = onBackClick) {
                Text("Back")
            }
            Button(
                modifier = Modifier.testTag("remote_delete_media_button"),
                onClick = onDeleteClick,
            ) {
                Text("Delete")
            }
        }
        Text(DashCamFormatters.formatTimestamp(item.createdAt), style = MaterialTheme.typography.titleMedium)
        if (item.type == MediaType.VIDEO) {
            AndroidView(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .testTag("remote_video_player"),
                factory = { context ->
                    VideoView(context).apply {
                        val controller = MediaController(context)
                        controller.setAnchorView(this)
                        setMediaController(controller)
                        setVideoPath(streamUrl)
                    }
                },
                update = { view -> view.setVideoPath(streamUrl) },
            )
        } else {
            Text(
                modifier = Modifier.testTag("remote_photo_view"),
                text = streamUrl,
            )
        }
    }
}

@Composable
private fun RemoteSettingsPanel(
    settings: RemoteSettings?,
    onSettingsChanged: (RemoteSettings) -> Unit,
    onSaveClick: () -> Unit,
) {
    val current = settings ?: return
    Column(
        modifier = Modifier.testTag("remote_settings_screen"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Remote settings", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("remote_wake_word_field"),
            value = current.wakeWord,
            onValueChange = { onSettingsChanged(current.copy(wakeWord = it)) },
            label = { Text("Wake word") },
            singleLine = true,
        )
        OutlinedButton(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("remote_save_settings_button"),
            onClick = onSaveClick,
        ) {
            Text("Save settings")
        }
    }
}

fun remoteDateKey(
    createdAt: Long,
    zoneId: ZoneId = ZoneId.systemDefault(),
): String =
    Instant.ofEpochMilli(createdAt).atZone(zoneId).format(remoteDateFormatter)

fun remoteMediaForSelectedType(state: RemoteViewerUiState): List<RemoteMediaItem> =
    if (state.selectedType == MediaType.VIDEO) state.videos else state.photos

private val remoteDateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
