package com.firmmy.dashcam

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.firmmy.dashcam.core.network.RemoteConnectionPayload
import com.firmmy.dashcam.feature.remote.RemoteViewerScreen
import kotlinx.coroutines.launch

@Composable
fun RemoteConnectionScreen(
    context: Context,
    modifier: Modifier = Modifier,
) {
    val applicationContext = context.applicationContext
    val wifiConnector = remember(applicationContext) { RecorderWifiConnector(applicationContext) }
    val viewerClient = remember(applicationContext) { AppRemoteViewerClient(applicationContext) }
    val scope = rememberCoroutineScope()
    var scanning by remember { mutableStateOf(false) }
    var payload by remember { mutableStateOf<RemoteConnectionPayload?>(null) }
    var connectingWifi by remember { mutableStateOf(false) }
    var wifiConnected by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }

    DisposableEffect(wifiConnector) {
        onDispose { wifiConnector.release() }
    }

    val currentPayload = payload
    if (wifiConnected && currentPayload != null) {
        RemoteViewerScreen(
            client = viewerClient,
            modifier = modifier,
            initialManualHost = currentPayload.baseUrl,
            autoConnect = true,
        )
        return
    }

    if (scanning) {
        RemoteQrScanner(
            modifier = modifier,
            onPayloadScanned = {
                payload = it
                scanning = false
                message = ""
            },
        )
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Remote", style = MaterialTheme.typography.headlineMedium)
        Text("Scan the QR code shown on the recorder phone.")

        Button(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("remote_scan_qr_button"),
            onClick = { scanning = true },
        ) {
            Text("Scan recorder QR")
        }

        currentPayload?.let {
            Text(
                modifier = Modifier.testTag("remote_scanned_ssid_text"),
                text = "SSID: ${it.ssid}",
            )
            Text(
                modifier = Modifier.testTag("remote_scanned_url_text"),
                text = "Server: ${it.baseUrl}",
            )
            Button(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("remote_connect_wifi_button"),
                enabled = !connectingWifi,
                onClick = {
                    scope.launch {
                        connectingWifi = true
                        message = ""
                        val connected = wifiConnector.connect(it)
                        connectingWifi = false
                        wifiConnected = connected
                        message = if (connected) "" else "Wi-Fi connection failed. Connect in system Wi-Fi settings, then continue."
                    }
                },
            ) {
                Text(if (connectingWifi) "Connecting Wi-Fi" else "Connect to recorder Wi-Fi")
            }
            OutlinedButton(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("remote_open_wifi_settings_button"),
                onClick = {
                    context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
                },
            ) {
                Text("Open Wi-Fi settings")
            }
            OutlinedButton(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("remote_continue_connected_button"),
                onClick = { wifiConnected = true },
            ) {
                Text("Continue")
            }
        }

        if (message.isNotBlank()) {
            Text(
                modifier = Modifier.testTag("remote_connection_message_text"),
                text = message,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}
