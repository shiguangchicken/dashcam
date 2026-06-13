package com.firmmy.dashcam

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.firmmy.dashcam.core.network.RemoteConnectionPayload
import com.firmmy.dashcam.feature.remote.RemoteViewerScreen
import kotlinx.coroutines.launch

typealias RemoteQrScannerContent = @Composable (
    modifier: Modifier,
    onPayloadScanned: (RemoteConnectionPayload) -> Unit,
) -> Unit

@Composable
fun RemoteConnectionScreen(
    context: Context,
    modifier: Modifier = Modifier,
    scannerContent: RemoteQrScannerContent = { scannerModifier, onPayloadScanned ->
        RemoteQrScanner(
            modifier = scannerModifier,
            onPayloadScanned = onPayloadScanned,
        )
    },
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
        RemoteQrScannerScreen(
            scannerContent = scannerContent,
            onBackClick = { scanning = false },
            onPayloadScanned = {
                payload = it
                scanning = false
                message = ""
            },
            modifier = modifier,
        )
        return
    }

    if (currentPayload != null) {
        RemoteScanSuccessScreen(
            payload = currentPayload,
            connectingWifi = connectingWifi,
            message = message,
            onConnectClick = {
                scope.launch {
                    connectingWifi = true
                    message = ""
                    val connected = wifiConnector.connect(currentPayload)
                    connectingWifi = false
                    wifiConnected = connected
                    message = if (connected) {
                        ""
                    } else {
                        "Wi-Fi connection failed. Connect in system Wi-Fi settings, then continue."
                    }
                }
            },
            onOpenWifiSettingsClick = {
                context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
            },
            onContinueClick = { wifiConnected = true },
            modifier = modifier,
        )
        return
    }

    RemoteConnectionSetupScreen(
        onScanClick = { scanning = true },
        modifier = modifier,
    )
}

@Composable
private fun RemoteQrScannerScreen(
    scannerContent: RemoteQrScannerContent,
    onBackClick: () -> Unit,
    onPayloadScanned: (RemoteConnectionPayload) -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBackClick)

    Box(modifier = modifier.fillMaxSize()) {
        scannerContent(
            Modifier.fillMaxSize(),
            onPayloadScanned,
        )
        IconButton(
            modifier = Modifier
                .padding(16.dp)
                .clip(CircleShape)
                .background(Color(0xCC10141A))
                .testTag("remote_scanner_back_button"),
            onClick = onBackClick,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = Color.White,
            )
        }
    }
}

@Composable
private fun RemoteConnectionSetupScreen(
    onScanClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF10141A)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(26.dp),
        ) {
            ConnectionTopBar()
            ConnectionHero()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Dot()
                Dot()
                Dot()
                Text(
                    modifier = Modifier.padding(start = 18.dp),
                    text = "Searching for DroidDash...",
                    color = MaterialTheme.colorScheme.primary,
                    fontStyle = FontStyle.Italic,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF181C22))
                    .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = "Join Network",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Scan the QR code displayed on your dashcam screen to establish a secure connection automatically.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Button(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .testTag("remote_scan_qr_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    onClick = onScanClick,
                ) {
                    Text("SCAN TO CONNECT", fontWeight = FontWeight.Bold)
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF181C22))
                    .border(1.dp, Color(0x14FFFFFF), RoundedCornerShape(8.dp))
                    .padding(18.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text("INFO", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                Text(
                    modifier = Modifier.weight(1f),
                    text = "If you can't see the network, try turning off and on the dashcam power or resetting the Wi-Fi module.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            ConnectionBottomNav()
        }
    }
}

@Composable
private fun RemoteScanSuccessScreen(
    payload: RemoteConnectionPayload,
    connectingWifi: Boolean,
    message: String,
    onConnectClick: () -> Unit,
    onOpenWifiSettingsClick: () -> Unit,
    onContinueClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF10141A))
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        ConnectionTopBar()
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(8.dp))
                .background(
                    Brush.radialGradient(
                        listOf(Color(0x5538E882), Color(0xFF181C22)),
                    ),
                )
                .border(1.dp, MaterialTheme.colorScheme.tertiary, RoundedCornerShape(8.dp))
                .testTag("remote_scan_success_screen"),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("QR SCAN SUCCESS", color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.Bold)
                Text("Recorder payload verified", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
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
                text = "NETWORK SSID\n${payload.ssid}",
                fontFamily = FontFamily.Monospace,
            )
            Text(
                modifier = Modifier.testTag("remote_scanned_url_text"),
                text = "SERVER\n${payload.baseUrl}",
                fontFamily = FontFamily.Monospace,
            )
            Button(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("remote_connect_wifi_button"),
                enabled = !connectingWifi,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                onClick = onConnectClick,
            ) {
                Text(if (connectingWifi) "CONNECTING WI-FI" else "CONNECT")
            }
            OutlinedButton(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("remote_open_wifi_settings_button"),
                onClick = onOpenWifiSettingsClick,
            ) {
                Text("OPEN WI-FI SETTINGS")
            }
            OutlinedButton(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("remote_continue_connected_button"),
                onClick = onContinueClick,
            ) {
                Text("CONTINUE")
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

@Composable
private fun ConnectionTopBar() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "DroidDash",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
        )
        Text(
            modifier = Modifier
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF1C2026))
                .padding(horizontal = 16.dp, vertical = 8.dp),
            text = "GPS: OK",
            color = MaterialTheme.colorScheme.secondary,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun ConnectionHero() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(8.dp))
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF31221D), Color(0xFF181C22)),
                ),
            )
            .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(34.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DeviceTile("CAM", MaterialTheme.colorScheme.primary)
            Text("...", color = MaterialTheme.colorScheme.primary, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            DeviceTile("PHONE", MaterialTheme.colorScheme.secondary)
        }
    }
}

@Composable
private fun DeviceTile(
    label: String,
    color: Color,
) {
    Box(
        modifier = Modifier
            .size(88.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF31353C))
            .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = color, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun Dot() {
    Box(
        modifier = Modifier
            .padding(horizontal = 3.dp)
            .size(10.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary),
    )
}

@Composable
private fun ConnectionBottomNav(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(88.dp)
            .background(Color(0xEE10141A))
            .border(1.dp, Color(0x14FFFFFF))
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        listOf("Live", "Media", "Events", "Settings").forEachIndexed { index, label ->
            Text(
                modifier = Modifier
                    .clip(RoundedCornerShape(24.dp))
                    .background(if (index == 0) Color(0x33FF6B00) else Color.Transparent)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                text = label,
                color = if (index == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
