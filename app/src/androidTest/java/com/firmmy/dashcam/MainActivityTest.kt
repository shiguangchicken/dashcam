package com.firmmy.dashcam

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.firmmy.dashcam.core.common.DeviceRole
import com.firmmy.dashcam.ui.theme.DashCamTheme
import org.junit.Rule
import org.junit.Test

class MainActivityTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun roleSelectionShowsRequiredActions() {
        setDashCamContent()

        composeRule.onNodeWithTag("role_recorder_button").assertIsDisplayed()
        composeRule.onNodeWithTag("role_remote_button").assertIsDisplayed()
    }

    @Test
    fun selectingRecorderShowsPermissionGuide() {
        setDashCamContent()

        composeRule.onNodeWithTag("role_recorder_button").performClick()
        composeRule.onNodeWithTag("permission_continue_button").assertIsDisplayed()
    }

    private fun setDashCamContent() {
        composeRule.setContent {
            DashCamTheme {
                DashCamApp(
                    initialRole = null,
                    onRoleSelected = { _: DeviceRole -> },
                )
            }
        }
    }
}
