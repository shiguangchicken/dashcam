package com.firmmy.dashcam

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.firmmy.dashcam.core.common.DashCamCommand
import com.firmmy.dashcam.core.common.DeviceRole
import com.firmmy.dashcam.core.common.MediaType
import com.firmmy.dashcam.core.common.RecordingMode
import com.firmmy.dashcam.core.database.DashCamDatabaseProvider
import com.firmmy.dashcam.core.database.DashCamSettings
import com.firmmy.dashcam.core.database.MediaFileEntity
import com.firmmy.dashcam.core.database.MediaRepository
import com.firmmy.dashcam.core.media.AndroidThumbnailGenerator
import com.firmmy.dashcam.core.media.DashCamMediaDirectories
import com.firmmy.dashcam.core.media.DashCamMediaRepository
import com.firmmy.dashcam.core.network.AndroidLocalOnlyHotspotStarter
import com.firmmy.dashcam.core.network.EmbeddedHttpServer
import com.firmmy.dashcam.core.network.HotspotController
import com.firmmy.dashcam.core.network.HotspotState
import com.firmmy.dashcam.core.network.RemoteConnectionPayload
import com.firmmy.dashcam.feature.recorder.MediaBrowserItem
import com.firmmy.dashcam.feature.recorder.MediaBrowserScreen
import com.firmmy.dashcam.feature.settings.SettingsInitialSection
import com.firmmy.dashcam.feature.settings.SettingsScreen
import com.firmmy.dashcam.ui.theme.DashCamTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private val roleStore by lazy { RoleStore(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DashCamTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    DashCamApp(
                        initialRole = roleStore.currentRole(),
                        initialSettings = roleStore.currentSettings(),
                        onRoleSelected = roleStore::saveRole,
                        onRequestPermissions = ::requestDashCamPermissions,
                        onSettingsSaved = roleStore::saveSettings,
                    )
                }
            }
        }
    }

    private fun requestDashCamPermissions() {
        val permissions = buildList {
            add(Manifest.permission.CAMERA)
            add(Manifest.permission.RECORD_AUDIO)
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
                add(Manifest.permission.NEARBY_WIFI_DEVICES)
            }
        }
        requestPermissions(permissions.toTypedArray(), REQUEST_DASHCAM_PERMISSIONS)
    }

    companion object {
        private const val REQUEST_DASHCAM_PERMISSIONS = 2001
    }
}

@Composable
fun DashCamApp(
    initialRole: DeviceRole?,
    initialSettings: DashCamSettings = DashCamSettings(deviceRole = initialRole),
    onRoleSelected: (DeviceRole) -> Unit,
    onRequestPermissions: () -> Unit = {},
    onSettingsSaved: (DashCamSettings) -> Unit = {},
) {
    var selectedRole by remember { mutableStateOf(initialRole) }
    var settings by remember { mutableStateOf(initialSettings.copy(deviceRole = initialRole ?: initialSettings.deviceRole)) }
    var permissionsAcknowledged by remember { mutableStateOf(false) }

    when {
        selectedRole == null -> RoleSelectionScreen(
            onRecorderSelected = {
                onRoleSelected(DeviceRole.RECORDER)
                selectedRole = DeviceRole.RECORDER
                settings = settings.copy(deviceRole = DeviceRole.RECORDER)
            },
            onRemoteSelected = {
                onRoleSelected(DeviceRole.REMOTE)
                selectedRole = DeviceRole.REMOTE
                settings = settings.copy(deviceRole = DeviceRole.REMOTE)
                permissionsAcknowledged = true
            },
        )

        selectedRole == DeviceRole.RECORDER && !permissionsAcknowledged -> PermissionGuideScreen(
            role = selectedRole ?: DeviceRole.RECORDER,
            onContinue = {
                onRequestPermissions()
                permissionsAcknowledged = true
            },
        )

        else -> HomeScreen(
            role = selectedRole ?: DeviceRole.RECORDER,
            settings = settings,
            onSettingsSaved = { updatedSettings ->
                onSettingsSaved(updatedSettings)
                settings = updatedSettings
                updatedSettings.deviceRole?.let { selectedRole = it }
            },
        )
    }
}

@Composable
private fun RoleSelectionScreen(
    onRecorderSelected: () -> Unit,
    onRemoteSelected: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Choose device role",
            style = MaterialTheme.typography.headlineMedium,
        )
        Text("Recorder mode runs on the old phone. Remote mode views the recorder over local Wi-Fi.")
        Button(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("role_recorder_button"),
            onClick = onRecorderSelected,
        ) {
            Text("Recorder mode")
        }
        Button(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("role_remote_button"),
            onClick = onRemoteSelected,
        ) {
            Text("Remote viewer mode")
        }
    }
}

@Composable
private fun PermissionGuideScreen(
    role: DeviceRole,
    onContinue: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Permissions and safety",
            style = MaterialTheme.typography.headlineMedium,
        )
        Text("Selected role: ${role.label}")
        Text("Camera, microphone, notifications, battery optimization, and background running must be reviewed before recording.")
        Text("Set up the phone before driving and keep attention on the road.")
        Button(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("permission_continue_button"),
            onClick = onContinue,
        ) {
            Text("Continue")
        }
    }
}

data class RecorderHotspotUiState(
    val enabled: Boolean = false,
    val starting: Boolean = false,
    val ssid: String = "",
    val password: String = "",
    val remoteServerUrl: String = "",
    val remoteQrText: String = "",
    val error: String = "",
)

@Composable
private fun HomeScreen(
    role: DeviceRole,
    settings: DashCamSettings,
    onSettingsSaved: (DashCamSettings) -> Unit,
) {
    var showSettings by remember { mutableStateOf(false) }
    var settingsInitialSection by remember { mutableStateOf(SettingsInitialSection.Top) }
    var showFiles by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val applicationContext = context.applicationContext
    val hotspotController = remember(applicationContext) {
        HotspotController(AndroidLocalOnlyHotspotStarter(applicationContext))
    }
    var addressesBeforeHotspot by remember { mutableStateOf(emptySet<String>()) }
    val remoteServerController = remember(applicationContext, hotspotController) {
        AppRemoteServerController(applicationContext) { command ->
            when (command) {
                DashCamCommand.StartHotspot -> {
                    addressesBeforeHotspot = HotspotEndpointResolver.privateIpv4Addresses()
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
    var recorderHotspot by remember {
        mutableStateOf(
            RecorderHotspotUiState(
                ssid = settings.hotspotSsid,
                password = settings.hotspotPassword,
            ),
        )
    }

    fun setHotspotEnabled(enabled: Boolean) {
        if (enabled) {
            addressesBeforeHotspot = HotspotEndpointResolver.privateIpv4Addresses()
            hotspotController.start()
        } else {
            hotspotController.stop()
        }
    }

    DisposableEffect(remoteServerController) {
        onDispose {
            remoteServerController.stop()
            hotspotController.stop()
        }
    }

    LaunchedEffect(hotspotState) {
        recorderHotspot = when (val currentHotspotState = hotspotState) {
            HotspotState.Stopped -> {
                remoteServerController.stop()
                RecorderRuntimeState.updateHotspot(enabled = false, ssid = "")
                recorderHotspot.copy(
                    enabled = false,
                    starting = false,
                    remoteServerUrl = "",
                    remoteQrText = "",
                    error = "",
                )
            }

            HotspotState.Starting -> recorderHotspot.copy(
                enabled = false,
                starting = true,
                remoteServerUrl = "",
                remoteQrText = "",
                error = "Starting",
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
                val updatedSettings = settings.copy(
                    hotspotSsid = currentHotspotState.credentials.ssid,
                    hotspotPassword = currentHotspotState.credentials.password,
                )
                onSettingsSaved(updatedSettings)
                RecorderHotspotUiState(
                    enabled = true,
                    starting = false,
                    ssid = currentHotspotState.credentials.ssid,
                    password = currentHotspotState.credentials.password,
                    remoteServerUrl = baseUrl,
                    remoteQrText = qrText,
                    error = "",
                )
            }

            is HotspotState.Failed -> recorderHotspot.copy(
                enabled = false,
                starting = false,
                remoteServerUrl = "",
                remoteQrText = "",
                error = currentHotspotState.message,
            )
        }
    }

    if (role == DeviceRole.REMOTE && !showSettings) {
        RemoteConnectionScreen(context = context)
        return
    }

    if (role == DeviceRole.RECORDER && showFiles) {
        RecorderMediaBrowserScreen(
            context = context,
            onBackClick = { showFiles = false },
        )
        return
    }

    if (role == DeviceRole.RECORDER && !showSettings) {
        CameraBackedRecorderScreen(
            context = context,
            settings = settings,
            hotspot = recorderHotspot,
            onHotspotClick = {
                settingsInitialSection = SettingsInitialSection.Hotspot
                showSettings = true
            },
            onViewFilesClick = { showFiles = true },
            onSettingsClick = {
                settingsInitialSection = SettingsInitialSection.Top
                showSettings = true
            },
        )
        return
    }

    SettingsScreen(
        modifier = Modifier.fillMaxSize(),
        settings = settings.copy(deviceRole = role),
        initialSection = settingsInitialSection,
        hotspotEnabled = recorderHotspot.enabled,
        hotspotStarting = recorderHotspot.starting,
        hotspotSsid = recorderHotspot.ssid.ifBlank { settings.hotspotSsid },
        hotspotPassword = recorderHotspot.password.ifBlank { settings.hotspotPassword },
        remoteServerUrl = recorderHotspot.remoteServerUrl,
        remoteQrText = recorderHotspot.remoteQrText,
        hotspotError = recorderHotspot.error,
        onHotspotToggle = ::setHotspotEnabled,
        onBackClick = {
            settingsInitialSection = SettingsInitialSection.Top
            showSettings = false
        },
        onSave = { updatedSettings ->
            onSettingsSaved(updatedSettings)
            if (updatedSettings.deviceRole == DeviceRole.RECORDER || updatedSettings.deviceRole == DeviceRole.REMOTE) {
                settingsInitialSection = SettingsInitialSection.Top
                showSettings = false
            }
        },
    )
}

@Composable
private fun RecorderMediaBrowserScreen(
    context: android.content.Context,
    onBackClick: () -> Unit,
) {
    val applicationContext = context.applicationContext
    val mediaRepository = remember(applicationContext) {
        val database = DashCamDatabaseProvider.get(applicationContext)
        DashCamMediaRepository(
            mediaRepository = MediaRepository(database.mediaFileDao()),
            directories = DashCamMediaDirectories.fromContext(applicationContext),
            thumbnailGenerator = AndroidThumbnailGenerator(),
        )
    }
    val scope = rememberCoroutineScope()
    val media by mediaRepository.observeMedia().collectAsState(initial = emptyList())

    MediaBrowserScreen(
        items = media.mapNotNull(MediaFileEntity::toBrowserItem),
        onBackClick = onBackClick,
        onDeleteClick = { item ->
            scope.launch {
                withContext(Dispatchers.IO) {
                    mediaRepository.deleteMedia(item.id)
                }
            }
        },
        onLockClick = { item ->
            scope.launch {
                withContext(Dispatchers.IO) {
                    mediaRepository.setMediaLocked(item.id, locked = true)
                }
            }
        },
        onUnlockClick = { item ->
            scope.launch {
                withContext(Dispatchers.IO) {
                    mediaRepository.setMediaLocked(item.id, locked = false)
                }
            }
        },
    )
}

private fun MediaFileEntity.toBrowserItem(): MediaBrowserItem? {
    val type = MediaType.fromStoredValue(type) ?: return null
    val mode = RecordingMode.fromStoredValue(mode) ?: RecordingMode.MANUAL
    return MediaBrowserItem(
        id = id,
        type = type,
        mode = mode,
        path = path,
        thumbnailPath = thumbnailPath,
        createdAt = createdAt,
        durationMs = durationMs,
        sizeBytes = sizeBytes,
        locked = locked,
    )
}
