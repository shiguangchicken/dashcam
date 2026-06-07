package com.firmmy.dashcam

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
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
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF10141A), Color(0xFF0A0E14)),
                ),
            )
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Text(
            "▣ DroidDash",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF181C22))
                .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(8.dp))
                .padding(28.dp),
        ) {
            Text(
                "Recorder phone  · ·  Remote viewer",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = FontFamily.Monospace,
            )
        }

        Button(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("remote_scan_qr_button"),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            onClick = { scanning = true },
        ) {
            Text("Scan recorder QR")
        }

        currentPayload?.let {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF181C22))
                    .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Join Network", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                Text(
                    modifier = Modifier.testTag("remote_scanned_ssid_text"),
                    text = "NETWORK SSID\n${it.ssid}",
                    fontFamily = FontFamily.Monospace,
                )
                Text(
                    modifier = Modifier.testTag("remote_scanned_url_text"),
                    text = "SERVER\n${it.baseUrl}",
                    fontFamily = FontFamily.Monospace,
                )
                Button(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("remote_connect_wifi_button"),
                    enabled = !connectingWifi,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
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
                    Text(if (connectingWifi) "Connecting Wi-Fi" else "Connect")
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
