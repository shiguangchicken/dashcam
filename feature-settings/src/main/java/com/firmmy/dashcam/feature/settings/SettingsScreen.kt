package com.firmmy.dashcam.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.LocalParking
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.firmmy.dashcam.core.common.DeviceRole
import com.firmmy.dashcam.core.database.DashCamSettings
import com.firmmy.dashcam.core.database.SettingsDefaults

private val Surface = Color(0xFF10141A)
private val SurfaceLow = Color(0xFF181C22)
private val SurfaceContainer = Color(0xFF1C2026)
private val SurfaceHigh = Color(0xFF262A31)
private val OnSurface = Color(0xFFDFE2EB)
private val OnSurfaceMuted = Color(0xFFE2BFB0)
private val SafetyOrange = Color(0xFFFF6B00)
private val PrimarySoft = Color(0xFFFFB693)
private val SecondaryBlue = Color(0xFF00A2FD)
private val SecondaryText = Color(0xFF98CBFF)
private val TertiaryGreen = Color(0xFF4AE183)
private val Outline = Color(0x335A4136)

@Composable
fun SettingsScreen(
    settings: DashCamSettings,
    onSave: (DashCamSettings) -> Unit,
    modifier: Modifier = Modifier,
    onBackClick: (() -> Unit)? = null,
) {
    var editableSettings by remember(settings) { mutableStateOf(settings) }

    Column(
        modifier = modifier
            .testTag("settings_screen")
            .fillMaxSize()
            .background(Surface)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SettingsTopBar(onBackClick = onBackClick)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
        ModeDashboardCard(
            title = "Driving Mode",
            status = "ACTIVE",
            icon = Icons.Filled.DirectionsCar,
            accent = SecondaryBlue,
            statusColor = SecondaryText,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetricTile("QUALITY", editableSettings.drivingResolution.shortResolution(), Modifier.weight(1f))
                MetricTile("FRAME\nRATE", "${editableSettings.drivingFps}fps", Modifier.weight(1f))
                MetricTile("LOOP", "${editableSettings.segmentDurationMinutes}m", Modifier.weight(1f))
            }
            StringChoiceRow(
                label = "Resolution",
                tag = "settings_driving_resolution",
                options = SettingsDefaults.allowedResolutions.toList(),
                selected = editableSettings.drivingResolution,
                onSelected = { editableSettings = editableSettings.copy(drivingResolution = it) },
            )
            IntChoiceRow(
                label = "FPS",
                tag = "settings_driving_fps",
                options = SettingsDefaults.allowedDrivingFps.toList(),
                selected = editableSettings.drivingFps,
                suffix = " fps",
                onSelected = { editableSettings = editableSettings.copy(drivingFps = it) },
            )
            IntChoiceRow(
                label = "Bitrate",
                tag = "settings_driving_bitrate",
                options = SettingsDefaults.allowedDrivingBitrates.toList(),
                selected = editableSettings.drivingBitrateKbps,
                suffix = " kbps",
                onSelected = { editableSettings = editableSettings.copy(drivingBitrateKbps = it) },
            )
        }

        ModeDashboardCard(
            title = "Parking Mode",
            status = "STANDBY",
            icon = Icons.Filled.LocalParking,
            accent = TertiaryGreen,
            statusColor = TertiaryGreen,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetricTile("QUALITY", editableSettings.parkingResolution.shortResolution(), Modifier.weight(1f))
                MetricTile("FRAME\nRATE", "${editableSettings.parkingFps}fps", Modifier.weight(1f))
                MetricTile("TRIGGER", "Motion", Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetricTile("DETECTION\nDELAY", "10s", Modifier.weight(1f))
                Spacer(Modifier.weight(2f))
            }
            StringChoiceRow(
                label = "Resolution",
                tag = "settings_parking_resolution",
                options = SettingsDefaults.allowedResolutions.toList(),
                selected = editableSettings.parkingResolution,
                onSelected = { editableSettings = editableSettings.copy(parkingResolution = it) },
            )
            IntChoiceRow(
                label = "FPS",
                tag = "settings_parking_fps",
                options = SettingsDefaults.allowedParkingFps.toList(),
                selected = editableSettings.parkingFps,
                suffix = " fps",
                onSelected = { editableSettings = editableSettings.copy(parkingFps = it) },
            )
            IntChoiceRow(
                label = "Bitrate",
                tag = "settings_parking_bitrate",
                options = SettingsDefaults.allowedParkingBitrates.toList(),
                selected = editableSettings.parkingBitrateKbps,
                suffix = " kbps",
                onSelected = { editableSettings = editableSettings.copy(parkingBitrateKbps = it) },
            )
        }

        DashboardCard {
            SectionTitle(
                title = "Hotspot Settings",
                icon = Icons.Filled.WifiTethering,
                iconTint = OnSurface,
            )
            SwitchRow(
                label = "Hotspot Status",
                tag = null,
                checked = editableSettings.hotspotSsid.isNotBlank(),
                enabled = false,
                onCheckedChange = {},
            )
            CredentialField(
                label = "NETWORK SSID (VISIBLE)",
                value = editableSettings.hotspotSsid.ifBlank { "DroidDash_Cam" },
                tag = "settings_hotspot_ssid_field",
                icon = Icons.Filled.ContentCopy,
            )
            CredentialField(
                label = "ACCESS PASSWORD",
                value = editableSettings.hotspotPassword,
                tag = "settings_wifi_password_field",
                icon = Icons.Filled.Visibility,
                masked = true,
            )
        }

        DashboardCard(accent = SafetyOrange) {
            SectionTitle(
                title = "Storage",
                icon = Icons.Filled.SdStorage,
                iconTint = OnSurface,
            )
            StorageUsage(maxStorageGb = editableSettings.maxStorageGb)
            IntChoiceRow(
                label = "Segment",
                tag = "settings_segment_duration",
                options = SettingsDefaults.allowedSegmentDurations.toList(),
                selected = editableSettings.segmentDurationMinutes,
                suffix = " min",
                onSelected = { editableSettings = editableSettings.copy(segmentDurationMinutes = it) },
            )
            IntChoiceRow(
                label = "Max storage",
                tag = "settings_max_storage",
                options = SettingsDefaults.allowedStorageGb.toList(),
                selected = editableSettings.maxStorageGb,
                suffix = " GB",
                onSelected = { editableSettings = editableSettings.copy(maxStorageGb = it) },
            )
            IntChoiceRow(
                label = "Deletion Threshold",
                tag = "settings_min_free_space",
                options = SettingsDefaults.allowedMinFreeSpaceGb.toList(),
                selected = editableSettings.minFreeSpaceGb,
                suffix = " GB",
                onSelected = { editableSettings = editableSettings.copy(minFreeSpaceGb = it) },
            )
            SwitchRow(
                label = "Auto-delete Oldest",
                tag = null,
                checked = true,
                enabled = false,
                onCheckedChange = {},
            )
            Text(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(4.dp))
                    .border(1.dp, Color(0x66825D4E), RoundedCornerShape(4.dp))
                    .padding(vertical = 4.dp),
                text = "CLEAN CACHE (420 MB)",
                color = OnSurface,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
        }

        VoiceAssistantCard(
            settings = editableSettings,
            onAudioChange = { editableSettings = editableSettings.copy(audioEnabled = it) },
            onVoiceWakeupChange = { editableSettings = editableSettings.copy(voiceWakeupEnabled = it) },
            onWakeWordChange = { editableSettings = editableSettings.copy(wakeWord = it) },
            onSave = { onSave(editableSettings) },
        )

        DeviceHeader(
            role = editableSettings.deviceRole,
            onRoleSelected = { editableSettings = editableSettings.copy(deviceRole = it) },
        )
        }
    }
}

@Composable
private fun SettingsTopBar(onBackClick: (() -> Unit)?) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBackClick != null) {
                OutlinedButton(
                    modifier = Modifier.testTag("settings_back_button"),
                    shape = CircleShape,
                    border = BorderStroke(1.dp, Color(0x22FFFFFF)),
                    contentPadding = PaddingValues(0.dp),
                    onClick = onBackClick,
                ) {
                    Text("<", color = SafetyOrange)
                }
            }
            Text(
                text = "DroidDash",
                color = SafetyOrange,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
        }
        Text(
            text = "GPS LOCKED",
            color = SecondaryText,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun DeviceHeader(
    role: DeviceRole?,
    onRoleSelected: (DeviceRole) -> Unit,
) {
    DashboardCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(SafetyOrange),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Settings, contentDescription = null, tint = Color(0xFF572000))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = "Dash Cam V1",
                    color = PrimarySoft,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = role?.label ?: "Choose a device role",
                    color = OnSurfaceMuted.copy(alpha = 0.72f),
                )
            }
        }
        SingleChoiceRow(
            label = "Role",
            tag = "settings_role_selector",
            options = DeviceRole.entries,
            selected = role,
            optionLabel = { it.label },
            onSelected = onRoleSelected,
        )
    }
}

@Composable
private fun ModeDashboardCard(
    title: String,
    status: String,
    icon: ImageVector,
    accent: Color,
    statusColor: Color,
    content: @Composable ColumnScope.() -> Unit,
) {
    DashboardCard(accent = accent) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(icon, contentDescription = null, tint = accent)
                Text(
                    text = title,
                    color = OnSurface,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
            StatusChip(status, statusColor)
        }
        content()
    }
}

@Composable
private fun DashboardCard(
    modifier: Modifier = Modifier,
    accent: Color? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(8.dp))
            .background(SurfaceContainer)
            .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(8.dp)),
    ) {
        if (accent != null) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(accent),
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 20.dp, top = 20.dp, end = 24.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            content()
        }
    }
}

@Composable
private fun SectionTitle(
    title: String,
    icon: ImageVector,
    iconTint: Color,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = iconTint)
        Text(
            text = title,
            color = OnSurface,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun MetricTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .height(84.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(Color(0x6610141A))
            .padding(horizontal = 6.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            color = Color(0xFFFFD7C7),
            fontSize = 12.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Text(
            text = value,
            color = OnSurface,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Serif,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun StatusChip(
    text: String,
    color: Color,
) {
    Text(
        modifier = Modifier
            .clip(RoundedCornerShape(24.dp))
            .background(color.copy(alpha = 0.16f))
            .padding(horizontal = 14.dp, vertical = 5.dp),
        text = text,
        color = color,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
    )
}

@Composable
private fun CredentialField(
    label: String,
    value: String,
    tag: String,
    icon: ImageVector,
    masked: Boolean = false,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = label,
            color = Color(0xFFFFD7C7),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .background(SurfaceLow)
                .border(1.dp, Outline, RoundedCornerShape(4.dp))
                .testTag(tag)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = when {
                    masked && value.isNotBlank() -> "************"
                    masked -> ""
                    else -> value
                },
                color = OnSurface,
                fontFamily = FontFamily.Serif,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Icon(icon, contentDescription = null, tint = OnSurface.copy(alpha = 0.55f), modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun StorageUsage(maxStorageGb: Int) {
    val usedGb = (maxStorageGb * 0.88f).coerceAtLeast(1f)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                text = "${"%.1f".format(usedGb)} GB used",
                color = OnSurface,
                fontSize = 14.sp,
                fontFamily = FontFamily.Serif,
            )
            Text(
                text = "$maxStorageGb GB TOTAL",
                color = OnSurface.copy(alpha = 0.62f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(SurfaceHigh),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.88f)
                    .height(8.dp)
                    .background(SafetyOrange),
            )
        }
    }
}

@Composable
private fun VoiceAssistantCard(
    settings: DashCamSettings,
    onAudioChange: (Boolean) -> Unit,
    onVoiceWakeupChange: (Boolean) -> Unit,
    onWakeWordChange: (String) -> Unit,
    onSave: () -> Unit,
) {
    DashboardCard {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(82.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF57423C)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Mic,
                    contentDescription = null,
                    tint = PrimarySoft,
                    modifier = Modifier.size(40.dp),
                )
            }
            Text(
                text = "Voice Assistant",
                color = PrimarySoft,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Control your recording environment hands-free. DroidDash listens for safety keywords to lock clips or take snapshots while you drive.",
                color = Color(0xFFFFD7C7),
                textAlign = TextAlign.Center,
                lineHeight = 24.sp,
            )
        }
        SwitchRow(
            label = "Audio",
            tag = "settings_audio_enabled",
            checked = settings.audioEnabled,
            onCheckedChange = onAudioChange,
        )
        SwitchRow(
            label = "Voice wakeup",
            tag = "settings_voice_wakeup_enabled",
            checked = settings.voiceWakeupEnabled,
            onCheckedChange = onVoiceWakeupChange,
        )
        OutlinedTextField(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("settings_wake_word_field"),
            value = settings.wakeWord,
            onValueChange = onWakeWordChange,
            label = { Text("Keyword") },
            singleLine = true,
        )
        Button(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("settings_save_button"),
            colors = ButtonDefaults.buttonColors(
                containerColor = SafetyOrange,
                contentColor = Color(0xFF351000),
            ),
            onClick = onSave,
        ) {
            Text("SAVE SETTINGS", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun StringChoiceRow(
    label: String,
    tag: String,
    options: List<String>,
    selected: String,
    onSelected: (String) -> Unit,
) {
    SingleChoiceRow(
        label = label,
        tag = tag,
        options = options,
        selected = selected,
        optionLabel = { it },
        onSelected = onSelected,
    )
}

@Composable
private fun IntChoiceRow(
    label: String,
    tag: String,
    options: List<Int>,
    selected: Int,
    suffix: String,
    onSelected: (Int) -> Unit,
) {
    SingleChoiceRow(
        label = label,
        tag = tag,
        options = options,
        selected = selected,
        optionLabel = { "$it$suffix" },
        onSelected = onSelected,
    )
}

@Composable
private fun <T> SingleChoiceRow(
    label: String,
    tag: String,
    options: List<T>,
    selected: T?,
    optionLabel: (T) -> String,
    onSelected: (T) -> Unit,
) {
    Column(
        modifier = Modifier.testTag(tag),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = label,
            color = OnSurface,
            fontWeight = FontWeight.Medium,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            options.forEach { option ->
                val isSelected = option == selected
                FilterChip(
                    selected = isSelected,
                    onClick = { onSelected(option) },
                    label = { Text(optionLabel(option), maxLines = 1) },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = SurfaceLow,
                        labelColor = OnSurface,
                        selectedContainerColor = SafetyOrange,
                        selectedLabelColor = Color(0xFF351000),
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = isSelected,
                        borderColor = Outline,
                        selectedBorderColor = SafetyOrange,
                    ),
                )
            }
        }
    }
}

@Composable
private fun SwitchRow(
    label: String,
    tag: String?,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (enabled) Modifier.clickable { onCheckedChange(!checked) } else Modifier)
            .then(if (tag != null) Modifier.testTag(tag) else Modifier),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = OnSurface, fontSize = 16.sp)
        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color(0xFF572000),
                checkedTrackColor = SafetyOrange,
                disabledCheckedThumbColor = Color(0xFF572000),
                disabledCheckedTrackColor = SafetyOrange,
            ),
        )
    }
}

private fun String.shortResolution(): String =
    substringAfter("x", this)
        .takeIf { it.all(Char::isDigit) }
        ?.let { "${it}p" }
        ?: this
