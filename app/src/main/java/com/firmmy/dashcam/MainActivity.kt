package com.firmmy.dashcam

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.firmmy.dashcam.core.common.DeviceRole
import com.firmmy.dashcam.ui.theme.DashCamTheme

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
                        onRoleSelected = roleStore::saveRole,
                    )
                }
            }
        }
    }
}

@Composable
fun DashCamApp(
    initialRole: DeviceRole?,
    onRoleSelected: (DeviceRole) -> Unit,
) {
    var selectedRole by remember { mutableStateOf(initialRole) }
    var permissionsAcknowledged by remember { mutableStateOf(false) }

    when {
        selectedRole == null -> RoleSelectionScreen(
            onRecorderSelected = {
                onRoleSelected(DeviceRole.RECORDER)
                selectedRole = DeviceRole.RECORDER
            },
            onRemoteSelected = {
                onRoleSelected(DeviceRole.REMOTE)
                selectedRole = DeviceRole.REMOTE
            },
        )

        !permissionsAcknowledged -> PermissionGuideScreen(
            role = selectedRole ?: DeviceRole.RECORDER,
            onContinue = { permissionsAcknowledged = true },
        )

        else -> HomeScreen(role = selectedRole ?: DeviceRole.RECORDER)
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
private fun HomeScreen(role: DeviceRole) {
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
    }
}
