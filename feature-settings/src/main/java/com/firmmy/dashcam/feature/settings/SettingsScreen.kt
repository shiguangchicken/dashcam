package com.firmmy.dashcam.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.firmmy.dashcam.core.common.DeviceRole
import com.firmmy.dashcam.core.database.DashCamSettings
import com.firmmy.dashcam.core.database.SettingsDefaults

@Composable
fun SettingsScreen(
    settings: DashCamSettings,
    onSave: (DashCamSettings) -> Unit,
    modifier: Modifier = Modifier,
) {
    var editableSettings by remember(settings) { mutableStateOf(settings) }

    Column(
        modifier = modifier
            .testTag("settings_screen")
            .verticalScroll(rememberScrollState())
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineSmall,
        )

        SettingSection("Device") {
            SingleChoiceRow(
                label = "Role",
                tag = "settings_role_selector",
                options = DeviceRole.entries,
                selected = editableSettings.deviceRole,
                optionLabel = { it.label },
                onSelected = { editableSettings = editableSettings.copy(deviceRole = it) },
            )
        }

        SettingSection("Driving mode") {
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

        SettingSection("Parking mode") {
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

        SettingSection("Storage") {
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
                label = "Min free space",
                tag = "settings_min_free_space",
                options = SettingsDefaults.allowedMinFreeSpaceGb.toList(),
                selected = editableSettings.minFreeSpaceGb,
                suffix = " GB",
                onSelected = { editableSettings = editableSettings.copy(minFreeSpaceGb = it) },
            )
        }

        SettingSection("Audio and voice") {
            SwitchRow(
                label = "Audio",
                tag = "settings_audio_enabled",
                checked = editableSettings.audioEnabled,
                onCheckedChange = { editableSettings = editableSettings.copy(audioEnabled = it) },
            )
            SwitchRow(
                label = "Voice wakeup",
                tag = "settings_voice_wakeup_enabled",
                checked = editableSettings.voiceWakeupEnabled,
                onCheckedChange = { editableSettings = editableSettings.copy(voiceWakeupEnabled = it) },
            )
            OutlinedTextField(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("settings_wake_word_field"),
                value = editableSettings.wakeWord,
                onValueChange = { editableSettings = editableSettings.copy(wakeWord = it) },
                label = { Text("Wake word") },
                singleLine = true,
            )
        }

        SettingSection("Remote access") {
            Text("LocalOnlyHotspot uses system-generated SSID and password. The recorder shows a QR code when the hotspot is running.")
            OutlinedTextField(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("settings_hotspot_ssid_field"),
                value = editableSettings.hotspotSsid,
                onValueChange = {},
                label = { Text("System hotspot SSID") },
                readOnly = true,
                singleLine = true,
            )
            OutlinedTextField(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("settings_wifi_password_field"),
                value = editableSettings.hotspotPassword,
                onValueChange = {},
                label = { Text("System Wi-Fi password") },
                readOnly = true,
                singleLine = true,
            )
        }

        Button(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("settings_save_button"),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            onClick = { onSave(editableSettings) },
        ) {
            Text("Save")
        }
    }
}

@Composable
private fun SettingSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF181C22))
            .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(8.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold,
        )
        content()
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
        Text(label)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            options.forEach { option ->
                FilterChip(
                    selected = option == selected,
                    onClick = { onSelected(option) },
                    label = { Text(optionLabel(option)) },
                )
            }
        }
    }
}

@Composable
private fun SwitchRow(
    label: String,
    tag: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .testTag(tag),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}
