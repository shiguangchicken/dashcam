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
import com.firmmy.dashcam.core.common.DeviceRole
import com.firmmy.dashcam.core.common.MediaType
import com.firmmy.dashcam.core.common.RecordingMode
import com.firmmy.dashcam.core.database.DashCamSettings
import com.firmmy.dashcam.core.database.DashCamDatabaseProvider
import com.firmmy.dashcam.core.database.MediaFileEntity
import com.firmmy.dashcam.core.database.MediaRepository
import com.firmmy.dashcam.core.media.AndroidThumbnailGenerator
import com.firmmy.dashcam.core.media.DashCamMediaDirectories
import com.firmmy.dashcam.core.media.DashCamMediaRepository
import com.firmmy.dashcam.feature.recorder.MediaBrowserItem
import com.firmmy.dashcam.feature.recorder.MediaBrowserScreen
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
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
            },
        )

        !permissionsAcknowledged -> PermissionGuideScreen(
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

@Composable
private fun HomeScreen(
    role: DeviceRole,
    settings: DashCamSettings,
    onSettingsSaved: (DashCamSettings) -> Unit,
) {
    var showSettings by remember { mutableStateOf(role != DeviceRole.RECORDER) }
    var showFiles by remember { mutableStateOf(false) }

    if (role == DeviceRole.RECORDER && showFiles) {
        RecorderMediaBrowserScreen(
            context = LocalContext.current,
            onBackClick = { showFiles = false },
        )
        return
    }

    if (role == DeviceRole.RECORDER && !showSettings) {
        CameraBackedRecorderScreen(
            context = LocalContext.current,
            onViewFilesClick = { showFiles = true },
            onSettingsClick = { showSettings = true },
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "DashCam",
            style = MaterialTheme.typography.headlineMedium,
        )
        Text("Current role: ${role.label}")
        SettingsScreen(
            modifier = Modifier.weight(1f),
            settings = settings.copy(deviceRole = role),
            onSave = { updatedSettings ->
                onSettingsSaved(updatedSettings)
                if (updatedSettings.deviceRole == DeviceRole.RECORDER) {
                    showSettings = false
                }
            },
        )
    }
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
