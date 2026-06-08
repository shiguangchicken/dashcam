package com.firmmy.dashcam.feature.remote

import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.firmmy.dashcam.core.common.DashCamCommand
import com.firmmy.dashcam.core.common.DashCamFormatters
import com.firmmy.dashcam.core.common.MediaType
import com.firmmy.dashcam.core.common.RecordingMode
import com.firmmy.dashcam.core.common.RecordingStatus
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

    fun thumbnailUrl(id: Long): String

    fun downloadUrl(id: Long): String

    fun liveStreamUrl(): String
}

data class RemoteViewerUiState(
    val connected: Boolean = false,
    val connecting: Boolean = false,
    val manualHost: String = "",
    val status: RemoteStatus = RemoteStatus(),
    val videos: List<RemoteMediaItem> = emptyList(),
    val photos: List<RemoteMediaItem> = emptyList(),
    val selectedType: MediaType = MediaType.VIDEO,
    val selectedMode: RecordingMode? = null,
    val searchQuery: String = "",
    val selectedItem: RemoteMediaItem? = null,
    val destination: RemoteDestination = RemoteDestination.Live,
    val message: String = "",
    val settings: RemoteSettings? = null,
) {
    companion object
}

enum class RemoteDestination(
    val label: String,
) {
    Live("Live"),
    Media("Media"),
    Events("Events"),
    Settings("Settings"),
}

@Composable
fun RemoteViewerScreen(
    client: RemoteViewerClient,
    modifier: Modifier = Modifier,
    initialManualHost: String = "",
    autoConnect: Boolean = false,
) {
    var state by remember {
        mutableStateOf(RemoteViewerUiState(manualHost = initialManualHost))
    }
    var autoConnectAttempted by remember(initialManualHost, autoConnect) { mutableStateOf(false) }
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

    fun connect(host: String) {
        scope.launch {
            state = state.copy(connecting = true, message = "")
            val connected = runCatching { client.connect(host) }.getOrDefault(false)
            state = state.copy(
                connected = connected,
                connecting = false,
                manualHost = host,
                message = if (connected) "" else "Connection failed",
            )
            if (connected) refresh()
        }
    }

    LaunchedEffect(state.connected) {
        if (state.connected) refresh()
    }

    LaunchedEffect(autoConnect, initialManualHost) {
        if (!autoConnect || autoConnectAttempted || initialManualHost.isBlank()) return@LaunchedEffect
        autoConnectAttempted = true
        connect(initialManualHost)
    }

    state.selectedItem?.let { selectedItem ->
        RemoteViewerDetailContent(
            item = selectedItem,
            streamUrl = client.streamUrl(selectedItem.id),
            downloadUrl = client.downloadUrl(selectedItem.id),
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
        onConnectClick = { connect(state.manualHost) },
        onRefreshClick = ::refresh,
        onCommand = { command ->
            scope.launch {
                val ok = client.send(command)
                state = state.copy(message = if (ok) "" else "Command failed")
                refresh()
            }
        },
        onTypeSelected = { state = state.copy(selectedType = it) },
        onModeSelected = { state = state.copy(selectedMode = it) },
        onSearchChanged = { state = state.copy(searchQuery = it) },
        onDestinationSelected = { state = state.copy(destination = it) },
        onItemSelected = { state = state.copy(selectedItem = it) },
        onSettingsChanged = { state = state.copy(settings = it) },
        onSaveSettingsClick = {
            scope.launch {
                val settings = state.settings ?: return@launch
                val ok = client.saveSettings(settings)
                state = state.copy(message = if (ok) "Settings saved" else "Settings save failed")
            }
        },
        liveStreamUrl = { client.liveStreamUrl() },
        thumbnailUrl = { client.thumbnailUrl(it) },
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
    onModeSelected: (RecordingMode?) -> Unit = {},
    onSearchChanged: (String) -> Unit = {},
    onDestinationSelected: (RemoteDestination) -> Unit = {},
    onItemSelected: (RemoteMediaItem) -> Unit = {},
    onSettingsChanged: (RemoteSettings) -> Unit = {},
    onSaveSettingsClick: () -> Unit = {},
    liveStreamUrl: () -> String = { "" },
    thumbnailUrl: (Long) -> String = { "" },
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF10141A)),
    ) {
        if (!state.connected) {
            RemoteManualConnectScreen(
                state = state,
                onHostChanged = onHostChanged,
                onConnectClick = onConnectClick,
            )
            return@Box
        }

        Column(modifier = Modifier.fillMaxSize()) {
            RemoteTopBar(
                status = state.status,
                modifier = Modifier.testTag("remote_status_screen"),
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                when (state.destination) {
                    RemoteDestination.Live -> RemoteHomeScreen(
                        state = state,
                        onRefreshClick = onRefreshClick,
                        onCommand = onCommand,
                        liveStreamUrl = liveStreamUrl(),
                    )

                    RemoteDestination.Media -> RemoteMediaBrowserScreen(
                        state = state,
                        eventsOnly = false,
                        onTypeSelected = onTypeSelected,
                        onModeSelected = onModeSelected,
                        onSearchChanged = onSearchChanged,
                        onItemSelected = onItemSelected,
                        thumbnailUrl = thumbnailUrl,
                    )

                    RemoteDestination.Events -> RemoteMediaBrowserScreen(
                        state = state.copy(selectedType = MediaType.VIDEO),
                        eventsOnly = true,
                        onTypeSelected = onTypeSelected,
                        onModeSelected = onModeSelected,
                        onSearchChanged = onSearchChanged,
                        onItemSelected = onItemSelected,
                        thumbnailUrl = thumbnailUrl,
                    )

                    RemoteDestination.Settings -> RemoteSettingsScreen(
                        status = state.status,
                        settings = state.settings,
                        message = state.message,
                        onSettingsChanged = onSettingsChanged,
                        onSaveClick = onSaveSettingsClick,
                    )
                }
            }
            RemoteBottomNav(
                selected = state.destination,
                onDestinationSelected = onDestinationSelected,
            )
        }
    }
}

@Composable
private fun RemoteManualConnectScreen(
    state: RemoteViewerUiState,
    onHostChanged: (String) -> Unit,
    onConnectClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        BrandHeader(trailing = "GPS: OK")
        ConnectionIllustration()
        Text(
            text = "Manual connection",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
        )
        OutlinedTextField(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("remote_manual_ip_field"),
            value = state.manualHost,
            onValueChange = onHostChanged,
            label = { Text("Recorder server") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            singleLine = true,
        )
        Button(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("remote_connect_button"),
            enabled = !state.connecting,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            onClick = onConnectClick,
        ) {
            Text(if (state.connecting) "CONNECTING" else "CONNECT")
        }
        if (state.message.isNotBlank()) {
            Text(
                modifier = Modifier.testTag("remote_message_text"),
                text = state.message,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun RemoteTopBar(
    status: RemoteStatus,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(Color(0xEE10141A))
            .border(1.dp, Color(0x1AFFFFFF))
            .padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "DroidDash",
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = status.temperatureCelsius?.let { "%.0f C".format(it) } ?: "-- C",
                color = MaterialTheme.colorScheme.onSurface,
                fontFamily = FontFamily.Monospace,
            )
            Text(
                text = if (status.hotspotEnabled) "ONLINE" else "LOCAL",
                color = if (status.hotspotEnabled) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.secondary,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun RemoteHomeScreen(
    state: RemoteViewerUiState,
    onRefreshClick: () -> Unit,
    onCommand: (DashCamCommand) -> Unit,
    liveStreamUrl: String,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .testTag("remote_home_screen")
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        RemoteLivePreviewPanel(
            status = state.status,
            streamUrl = liveStreamUrl,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TelemetryCard(
                label = "GPS SIGNAL",
                value = "LOCKED",
                detail = "12 SAT",
                modifier = Modifier.weight(1f),
            )
            StorageCard(
                freeSpaceBytes = state.status.freeSpaceBytes,
                modifier = Modifier.weight(1f),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = if (state.status.recordingStatus == RecordingStatus.IDLE) "0" else "59",
                style = MaterialTheme.typography.displayLarge,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
            )
            Text("KM/H", color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
            StatusPill(state.status.recordingStatus.label.uppercase())
        }
        RemoteControlPanel(
            audioEnabled = state.status.audioEnabled,
            onCommand = onCommand,
        )
        ConnectedPanel(
            status = state.status,
            onRefreshClick = onRefreshClick,
        )
        if (state.message.isNotBlank()) {
            Text(state.message, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun RemoteLivePreviewPanel(
    status: RemoteStatus,
    streamUrl: String,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF0A0E14))
            .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center,
    ) {
        if (status.liveStreamAvailable && streamUrl.isNotBlank()) {
            AndroidView(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("remote_live_preview"),
                factory = { context ->
                    WebView(context).apply {
                        setBackgroundColor(android.graphics.Color.BLACK)
                        webViewClient = WebViewClient()
                        settings.loadWithOverviewMode = true
                        settings.useWideViewPort = true
                    }
                },
                update = { view ->
                    if (view.tag != streamUrl) {
                        view.tag = streamUrl
                        view.loadUrl(streamUrl)
                    }
                },
                onRelease = { view ->
                    view.stopLoading()
                    view.destroy()
                },
            )
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("LIVE PREVIEW", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                Text(
                    modifier = Modifier.testTag("remote_live_preview_placeholder"),
                    text = if (status.recordingStatus == RecordingStatus.IDLE) "Start recording to open live view" else "Waiting for stream",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun TelemetryCard(
    label: String,
    value: String,
    detail: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0x77181C22))
            .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(8.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, color = MaterialTheme.colorScheme.secondary, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
        Text(detail, color = MaterialTheme.colorScheme.secondary, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun StorageCard(
    freeSpaceBytes: Long,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0x77181C22))
            .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(8.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("STORAGE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(DashCamFormatters.formatFileSize(freeSpaceBytes), color = MaterialTheme.colorScheme.onSurface, fontFamily = FontFamily.Monospace)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(5.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0x22FFFFFF)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.62f)
                    .height(5.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer),
            )
        }
    }
}

@Composable
private fun StatusPill(text: String) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0x6693000A))
            .border(1.dp, Color(0x33FFB4AB), RoundedCornerShape(24.dp))
            .padding(horizontal = 18.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
        )
        Text(text, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun RemoteControlPanel(
    audioEnabled: Boolean,
    onCommand: (DashCamCommand) -> Unit,
) {
    Column(
        modifier = Modifier.testTag("remote_control_panel"),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CommandButton("PHOTO", "remote_take_photo_button", Modifier.weight(1f)) {
                onCommand(DashCamCommand.TakePhoto)
            }
            CommandButton(if (audioEnabled) "MUTE" else "AUDIO", "remote_audio_toggle_button", Modifier.weight(1f)) {
                onCommand(if (audioEnabled) DashCamCommand.DisableAudio else DashCamCommand.EnableAudio)
            }
            CommandButton("LOCK", "remote_lock_clip_button", Modifier.weight(1f)) {
                onCommand(DashCamCommand.LockCurrentClip)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CommandButton("DRIVING", "remote_mode_driving_button", Modifier.weight(1f)) {
                onCommand(DashCamCommand.StartDrivingMode)
            }
            CommandButton("PARKING", "remote_mode_parking_button", Modifier.weight(1f)) {
                onCommand(DashCamCommand.StartParkingMode)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CommandButton("HOTSPOT ON", "remote_hotspot_on_button", Modifier.weight(1f)) {
                onCommand(DashCamCommand.StartHotspot)
            }
            CommandButton("HOTSPOT OFF", "remote_hotspot_off_button", Modifier.weight(1f)) {
                onCommand(DashCamCommand.StopHotspot)
            }
        }
    }
}

@Composable
private fun CommandButton(
    label: String,
    tag: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    OutlinedButton(
        modifier = modifier.testTag(tag),
        onClick = onClick,
    ) {
        Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ConnectedPanel(
    status: RemoteStatus,
    onRefreshClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0x99181C22))
            .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(8.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("ACTIVE REMOTE VIEWERS", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            OutlinedButton(
                modifier = Modifier.testTag("remote_refresh_button"),
                onClick = onRefreshClick,
            ) {
                Text("REFRESH")
            }
        }
        RemoteStatusRow("Mode", status.mode.label)
        RemoteStatusRow("Audio", if (status.audioEnabled) "On" else "Off")
        RemoteStatusRow("Hotspot", if (status.hotspotEnabled) status.hotspotSsid.ifBlank { "On" } else "Off")
        RemoteStatusRow("Battery", status.batteryPercent?.let { "$it%" } ?: "--")
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
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, color = MaterialTheme.colorScheme.onSurface, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun RemoteMediaBrowserScreen(
    state: RemoteViewerUiState,
    eventsOnly: Boolean,
    onTypeSelected: (MediaType) -> Unit,
    onModeSelected: (RecordingMode?) -> Unit,
    onSearchChanged: (String) -> Unit,
    onItemSelected: (RemoteMediaItem) -> Unit,
    thumbnailUrl: (Long) -> String,
) {
    val items = remoteMediaForSelectedType(state)
        .filter { !eventsOnly || it.locked || it.mode == RecordingMode.EVENT }
        .filter { state.selectedMode == null || it.mode == state.selectedMode }
        .filter {
            state.searchQuery.isBlank() ||
                it.path.substringAfterLast('/').contains(state.searchQuery, ignoreCase = true) ||
                it.mode.label.contains(state.searchQuery, ignoreCase = true)
        }
        .sortedByDescending { it.createdAt }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        RemoteMediaHeader(state)
        OutlinedTextField(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("remote_media_search_field"),
            value = state.searchQuery,
            onValueChange = onSearchChanged,
            label = { Text("Search filename or event type") },
            singleLine = true,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = state.selectedMode == null,
                onClick = { onModeSelected(null) },
                label = { Text("All") },
            )
            listOf(RecordingMode.DRIVING, RecordingMode.PARKING, RecordingMode.EVENT, RecordingMode.MANUAL).forEach { mode ->
                FilterChip(
                    selected = state.selectedMode == mode,
                    onClick = { onModeSelected(mode) },
                    label = { Text(mode.label) },
                )
            }
        }
        if (!eventsOnly) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MediaTabButton(
                    label = "VIDEOS (${state.videos.size})",
                    selected = state.selectedType == MediaType.VIDEO,
                    modifier = Modifier.weight(1f),
                    onClick = { onTypeSelected(MediaType.VIDEO) },
                )
                MediaTabButton(
                    label = "PHOTOS (${state.photos.size})",
                    selected = state.selectedType == MediaType.PHOTO,
                    modifier = Modifier.weight(1f),
                    onClick = { onTypeSelected(MediaType.PHOTO) },
                )
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(160.dp),
            modifier = Modifier
                .height((220 * ((items.size + 1) / 2).coerceAtLeast(1)).dp)
                .testTag(if (state.selectedType == MediaType.VIDEO) "remote_video_list" else "remote_photo_list"),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(items, key = { it.id }) { item ->
                RemoteMediaCard(
                    item = item,
                    thumbnailUrl = thumbnailUrl(item.id),
                    onClick = { onItemSelected(item) },
                )
            }
        }
    }
}

@Composable
private fun RemoteMediaHeader(state: RemoteViewerUiState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF1C2026))
            .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(8.dp))
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("LIVE CONNECTION", color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        Text("Connected to DroidDash V1", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Active hotspot: ${state.status.hotspotSsid.ifBlank { "DroidDash" }}", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer), onClick = {}) {
                Text("BULK DOWNLOAD")
            }
            OutlinedButton(onClick = {}) {
                Text("DELETE")
            }
        }
    }
}

@Composable
private fun MediaTabButton(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Text(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .background(if (selected) Color(0x22FF6B00) else Color.Transparent)
            .padding(vertical = 12.dp),
        text = label,
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.Bold,
    )
}

@Composable
private fun RemoteMediaCard(
    item: RemoteMediaItem,
    thumbnailUrl: String,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("remote_media_item_${item.id}"),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1C2026)),
        shape = RoundedCornerShape(8.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFF31353C), Color(0xFF0A0E14)),
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (item.type == MediaType.VIDEO) "VIDEO" else "PHOTO",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = FontFamily.Monospace,
            )
            item.durationMs?.let { durationMs ->
                Text(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0x99000000))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    text = DashCamFormatters.formatDuration(durationMs),
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            if (item.locked || item.mode == RecordingMode.EVENT) {
                Text(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(MaterialTheme.colorScheme.error)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    text = if (item.locked) "INCIDENT" else item.mode.label.uppercase(),
                    color = MaterialTheme.colorScheme.onError,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = item.path.substringAfterLast('/').ifBlank { "REMOTE_${item.id}" },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontFamily = FontFamily.Monospace,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(remoteDateKey(item.createdAt), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                Text(DashCamFormatters.formatFileSize(item.sizeBytes), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
            }
        }
        Spacer(modifier = Modifier.height(if (thumbnailUrl.isBlank()) 0.dp else 0.dp))
    }
}

@Composable
fun RemoteViewerDetailContent(
    item: RemoteMediaItem,
    streamUrl: String,
    onBackClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier,
    downloadUrl: String = streamUrl,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .background(Color(0xFF10141A))
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                modifier = Modifier.clickable(onClick = onBackClick),
                text = "BACK",
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
            Text("DroidDash", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Black)
                .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(8.dp)),
        ) {
            if (item.type == MediaType.VIDEO) {
                AndroidView(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("remote_video_player"),
                    factory = { context ->
                        VideoView(context).apply {
                            val controller = MediaController(context)
                            controller.setAnchorView(this)
                            setMediaController(controller)
                            setOnErrorListener { _, _, _ -> true }
                        }
                    },
                    update = { view ->
                        if (view.tag != streamUrl) {
                            view.stopPlayback()
                            view.tag = streamUrl
                            view.setVideoPath(streamUrl)
                        }
                    },
                    onRelease = { view ->
                        view.stopPlayback()
                        view.setMediaController(null)
                    },
                )
            } else {
                AndroidView(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("remote_photo_view"),
                    factory = { context ->
                        WebView(context).apply {
                            setBackgroundColor(android.graphics.Color.BLACK)
                            webViewClient = WebViewClient()
                            settings.loadWithOverviewMode = true
                            settings.useWideViewPort = true
                        }
                    },
                    update = { view ->
                        if (view.tag != streamUrl) {
                            view.tag = streamUrl
                            view.loadUrl(streamUrl)
                        }
                    },
                    onRelease = { view ->
                        view.stopLoading()
                        view.destroy()
                    },
                )
            }
            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                StatusPill("REC: ${item.mode.label.uppercase()} MODE")
                Text(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xAA000000))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    text = DashCamFormatters.formatTimestamp(item.createdAt),
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            OutlinedButton(onClick = {}) {
                Text("SHARE")
            }
            Button(
                modifier = Modifier.testTag("remote_delete_media_button"),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                onClick = onDeleteClick,
            ) {
                Text("DELETE")
            }
        }
        DetailCard(
            title = "FILE DETAILS",
            rows = listOf(
                "Path" to item.path,
                "Size" to DashCamFormatters.formatFileSize(item.sizeBytes),
                "Resolution" to listOfNotNull(item.width, item.height).joinToString("x").ifBlank { "--" },
                "Download" to downloadUrl,
            ),
        )
        DetailCard(
            title = "TELEMETRY STATS",
            rows = listOf(
                "Mode" to item.mode.label,
                "Frame rate" to (item.fps?.let { "%.1f fps".format(it) } ?: "--"),
                "Audio" to if (item.hasAudio) "On" else "Off",
            ),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFF1C2026), Color(0xFF0A0E14)),
                    ),
                )
                .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text("GPS LOCKED", color = MaterialTheme.colorScheme.secondary, fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
private fun DetailCard(
    title: String,
    rows: List<Pair<String, String>>,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF181C22))
            .border(1.dp, Color(0x14FFFFFF), RoundedCornerShape(8.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline, fontWeight = FontWeight.Bold)
        rows.forEach { (label, value) ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    text = value,
                    modifier = Modifier.fillMaxWidth(0.62f),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun RemoteSettingsScreen(
    status: RemoteStatus,
    settings: RemoteSettings?,
    message: String,
    onSettingsChanged: (RemoteSettings) -> Unit,
    onSaveClick: () -> Unit,
) {
    val current = settings ?: return
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .testTag("remote_settings_screen")
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ModeSettingsCard("Driving Mode", "ACTIVE", MaterialTheme.colorScheme.secondary, current.drivingResolution, "${current.drivingFps}fps", "${current.segmentDurationMinutes}m")
        ModeSettingsCard("Parking Mode", "STANDBY", MaterialTheme.colorScheme.tertiary, current.parkingResolution, "${current.parkingFps}fps", "Motion")
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF1C2026))
                .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(8.dp))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Hotspot Settings", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            RemoteStatusRow("Hotspot Status", if (status.hotspotEnabled) "On" else "Off")
            RemoteStatusRow("Network SSID", status.hotspotSsid.ifBlank { "DroidDash" })
            RemoteStatusRow("Access Password", "************")
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF1C2026))
                .border(1.dp, MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(8.dp))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Storage", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            OutlinedTextField(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("remote_min_free_space_field"),
                value = current.minFreeSpaceGb.toString(),
                onValueChange = { value ->
                    value.toIntOrNull()?.let { onSettingsChanged(current.copy(minFreeSpaceGb = it)) }
                },
                label = { Text("Deletion Threshold GB") },
                singleLine = true,
            )
            RemoteStatusRow("Auto-delete Oldest", "On")
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF181C22))
                .border(1.dp, Color(0x22FFB693), RoundedCornerShape(8.dp))
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Voice Assistant", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            OutlinedTextField(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("remote_wake_word_field"),
                value = current.wakeWord,
                onValueChange = { onSettingsChanged(current.copy(wakeWord = it)) },
                label = { Text("Keyword") },
                singleLine = true,
            )
            Button(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("remote_save_settings_button"),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                onClick = onSaveClick,
            ) {
                Text("SAVE SETTINGS")
            }
            if (message.isNotBlank()) {
                Text(message, color = if (message == "Settings saved") MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun ModeSettingsCard(
    title: String,
    status: String,
    accent: Color,
    quality: String,
    frameRate: String,
    loop: String,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF1C2026))
            .border(1.dp, accent, RoundedCornerShape(8.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(status, color = accent, fontWeight = FontWeight.Bold)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SettingMetric("QUALITY", quality, Modifier.weight(1f))
            SettingMetric("FRAME RATE", frameRate, Modifier.weight(1f))
            SettingMetric("LOOP", loop, Modifier.weight(1f))
        }
    }
}

@Composable
private fun SettingMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0x5510141A))
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        Text(value, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun RemoteBottomNav(
    selected: RemoteDestination,
    onDestinationSelected: (RemoteDestination) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(88.dp)
            .background(Color(0xEE10141A))
            .border(1.dp, Color(0x1AFFFFFF))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RemoteDestination.entries.forEach { destination ->
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(24.dp))
                    .clickable { onDestinationSelected(destination) }
                    .background(if (selected == destination) Color(0x33FF6B00) else Color.Transparent)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .testTag("remote_nav_${destination.name.lowercase()}"),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(destination.label, color = if (selected == destination) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun BrandHeader(
    trailing: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "DroidDash",
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            modifier = Modifier
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF1C2026))
                .padding(horizontal = 14.dp, vertical = 8.dp),
            text = trailing,
            color = MaterialTheme.colorScheme.secondary,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun ConnectionIllustration() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(8.dp))
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF2A201D), Color(0xFF181C22)),
                ),
            )
            .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(28.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("CAM", color = MaterialTheme.colorScheme.primary, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            Text("...", color = MaterialTheme.colorScheme.primary, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            Text("PHONE", color = MaterialTheme.colorScheme.secondary, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
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
