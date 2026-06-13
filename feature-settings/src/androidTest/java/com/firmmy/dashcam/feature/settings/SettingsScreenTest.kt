package com.firmmy.dashcam.feature.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
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
            "settings_hotspot_toggle",
            "settings_hotspot_ssid_field",
            "settings_wifi_password_field",
            "settings_save_button",
        ).forEach { tag ->
            composeRule.onNodeWithTag("settings_list").performScrollToNode(hasTestTag(tag))
            composeRule.onNodeWithTag(tag).assertExists()
        }
    }

    @Test
    fun saveReturnsEditedSettings() {
        var savedSettings: DashCamSettings? = null
        setSettingsContent(onSave = { savedSettings = it })

        composeRule.onNodeWithTag("settings_list").performScrollToNode(hasTestTag("settings_role_selector"))
        composeRule.onNodeWithText("Remote viewer").performClick()
        composeRule.onNodeWithTag("settings_list").performScrollToNode(hasTestTag("settings_audio_enabled"))
        composeRule.onNodeWithTag("settings_audio_enabled").performClick()
        composeRule.onNodeWithTag("settings_list").performScrollToNode(hasTestTag("settings_save_button"))
        composeRule.onNodeWithTag("settings_save_button").performClick()

        assertEquals(DeviceRole.REMOTE, savedSettings?.deviceRole)
        assertEquals(false, savedSettings?.audioEnabled)
    }

    @Test
    fun savePreservesStoredHotspotCredentials() {
        var savedSettings: DashCamSettings? = null
        setSettingsContent(
            settings = DashCamSettings(
                deviceRole = DeviceRole.RECORDER,
                hotspotSsid = "DIRECT-test",
                hotspotPassword = "password123",
            ),
            onSave = { savedSettings = it },
        )

        composeRule.onNodeWithTag("settings_list").performScrollToNode(hasTestTag("settings_save_button"))
        composeRule.onNodeWithTag("settings_save_button").performClick()

        assertEquals("DIRECT-test", savedSettings?.hotspotSsid)
        assertEquals("password123", savedSettings?.hotspotPassword)
    }

    @Test
    fun hotspotToggleReturnsRequestedState() {
        var requestedState: Boolean? = null
        setSettingsContent(
            onHotspotToggle = { requestedState = it },
        )

        composeRule.onNodeWithTag("settings_list").performScrollToNode(hasTestTag("settings_hotspot_toggle"))
        composeRule.onNodeWithTag("settings_hotspot_toggle").performClick()

        assertEquals(true, requestedState)
    }

    @Test
    fun hotspotSectionShowsQrWhenPayloadExists() {
        setSettingsContent(
            hotspotEnabled = true,
            hotspotSsid = "AndroidShare_1234",
            hotspotPassword = "password123",
            remoteQrText = "dashcam://connect?ssid=AndroidShare_1234",
        )

        composeRule.onNodeWithTag("settings_list").performScrollToNode(hasTestTag("hotspot_qr_code"))
        composeRule.onNodeWithTag("hotspot_qr_code").assertExists()
    }

    private fun setSettingsContent(
        settings: DashCamSettings = DashCamSettings(deviceRole = DeviceRole.RECORDER),
        hotspotEnabled: Boolean = settings.hotspotSsid.isNotBlank(),
        hotspotSsid: String = settings.hotspotSsid,
        hotspotPassword: String = settings.hotspotPassword,
        remoteQrText: String = "",
        onHotspotToggle: (Boolean) -> Unit = {},
        onSave: (DashCamSettings) -> Unit = {},
    ) {
        composeRule.setContent {
            MaterialTheme {
                SettingsScreen(
                    settings = settings,
                    hotspotEnabled = hotspotEnabled,
                    hotspotSsid = hotspotSsid,
                    hotspotPassword = hotspotPassword,
                    remoteQrText = remoteQrText,
                    onHotspotToggle = onHotspotToggle,
                    onSave = onSave,
                )
            }
        }
    }
}
