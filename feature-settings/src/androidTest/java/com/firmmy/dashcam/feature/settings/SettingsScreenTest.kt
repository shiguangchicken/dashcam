package com.firmmy.dashcam.feature.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.firmmy.dashcam.core.common.DeviceRole
import com.firmmy.dashcam.core.database.DashCamSettings
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SettingsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun settingsScreenShowsRequiredControls() {
        setSettingsContent()

        composeRule.onNodeWithTag("settings_screen").assertExists()

        listOf(
            "settings_role_selector",
            "settings_driving_resolution",
            "settings_driving_fps",
            "settings_driving_bitrate",
            "settings_parking_resolution",
            "settings_parking_fps",
            "settings_parking_bitrate",
            "settings_segment_duration",
            "settings_max_storage",
            "settings_min_free_space",
            "settings_audio_enabled",
            "settings_voice_wakeup_enabled",
            "settings_wake_word_field",
            "settings_hotspot_ssid_field",
            "settings_wifi_password_field",
            "settings_pairing_token_field",
            "settings_pairing_code_field",
            "settings_refresh_pairing_button",
            "settings_copy_pairing_button",
            "settings_save_button",
        ).forEach { tag ->
            composeRule.onNodeWithTag(tag).assertExists()
        }
    }

    @Test
    fun saveReturnsEditedSettings() {
        var savedSettings: DashCamSettings? = null
        setSettingsContent(onSave = { savedSettings = it })

        composeRule.onNodeWithTag("settings_role_selector").performScrollTo()
        composeRule.onNodeWithText("Remote viewer").performClick()
        composeRule.onNodeWithTag("settings_audio_enabled").performScrollTo().performClick()
        composeRule.onNodeWithTag("settings_save_button").performScrollTo().performClick()

        assertEquals(DeviceRole.REMOTE, savedSettings?.deviceRole)
        assertEquals(false, savedSettings?.audioEnabled)
    }

    private fun setSettingsContent(
        settings: DashCamSettings = DashCamSettings(deviceRole = DeviceRole.RECORDER),
        onSave: (DashCamSettings) -> Unit = {},
        onRefreshPairing: (DashCamSettings) -> DashCamSettings = { it },
    ) {
        composeRule.setContent {
            MaterialTheme {
                SettingsScreen(
                    settings = settings,
                    onSave = onSave,
                    onRefreshPairing = onRefreshPairing,
                )
            }
        }
    }
}
