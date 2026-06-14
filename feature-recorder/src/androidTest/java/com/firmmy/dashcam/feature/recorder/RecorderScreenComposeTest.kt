package com.firmmy.dashcam.feature.recorder

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.firmmy.dashcam.core.common.MediaType
import com.firmmy.dashcam.core.common.RecordingMode
import com.firmmy.dashcam.core.common.RecordingStatus
import com.firmmy.dashcam.core.network.RemoteViewerClientInfo
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class RecorderScreenComposeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun redesignedRecorderDashboardExposesPrimaryActions() {
        composeRule.setContent {
            MaterialTheme {
                FakeRecorderScreen()
            }
        }

        listOf(
            "recorder_dashboard",
            "recorder_start_button",
            "take_photo_button",
            "audio_toggle_button",
            "hotspot_toggle_button",
            "view_files_button",
            "events_nav_button",
            "recorder_settings_button",
            "recording_status_text",
            "current_mode_text",
            "recorder_speed_hud",
        ).forEach { tag ->
            composeRule.onNodeWithTag(tag).assertExists()
        }
        composeRule.onAllNodesWithTag("hotspot_qr_code").assertCountEquals(0)
    }

    @Test
    fun recorderDashboardShowsSpeedFromState() {
        composeRule.setContent {
            MaterialTheme {
                RecorderScreen(
                    state = RecorderUiState(speedKmh = 42),
                    onStartStopClick = {},
                    onDrivingModeClick = {},
                    onParkingModeClick = {},
                    onTakePhotoClick = {},
                    onAudioToggleClick = {},
                    onHotspotToggleClick = {},
                    onViewFilesClick = {},
                    onSettingsClick = {},
                )
            }
        }

        composeRule.onNodeWithTag("recorder_speed_value").assertTextEquals("42")
        composeRule.onNodeWithTag("recorder_speed_unit").assertTextEquals("KM/H")
    }

    @Test
    fun recorderDashboardUsesIdleAndPreviewBackgrounds() {
        var state by mutableStateOf(RecorderUiState(recordingStatus = RecordingStatus.IDLE))
        composeRule.setContent {
            MaterialTheme {
                RecorderScreen(
                    state = state,
                    recordingBackground = { Box(modifier = androidx.compose.ui.Modifier.testTag("test_recording_preview")) },
                    onStartStopClick = {},
                    onDrivingModeClick = {},
                    onParkingModeClick = {},
                    onTakePhotoClick = {},
                    onAudioToggleClick = {},
                    onHotspotToggleClick = {},
                    onViewFilesClick = {},
                    onSettingsClick = {},
                )
            }
        }
        composeRule.onNodeWithTag("recorder_idle_background").assertExists()

        state = RecorderUiState(
            recordingStatus = RecordingStatus.RECORDING_DRIVING,
        )
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("test_recording_preview").assertExists()
    }

    @Test
    fun recorderDashboardShowsActiveRemoteViewersWhenPresent() {
        composeRule.setContent {
            MaterialTheme {
                RecorderScreen(
                    state = RecorderUiState(
                        remoteViewers = listOf(
                            RemoteViewerClientInfo("127.0.0.1", "Pixel 7", 1_780_000_000_000L),
                        ),
                    ),
                    onStartStopClick = {},
                    onDrivingModeClick = {},
                    onParkingModeClick = {},
                    onTakePhotoClick = {},
                    onAudioToggleClick = {},
                    onHotspotToggleClick = {},
                    onViewFilesClick = {},
                    onSettingsClick = {},
                )
            }
        }

        composeRule.onNodeWithTag("active_remote_viewers_panel").assertExists()
        composeRule.onNodeWithTag("active_remote_viewer_count").assertExists()
        composeRule.onNodeWithTag("active_remote_viewer_0").assertExists()
    }

    @Test
    fun recorderBottomNavInvokesExistingDestinations() {
        var openedFiles by mutableStateOf(false)
        var openedSettings by mutableStateOf(false)
        composeRule.setContent {
            MaterialTheme {
                FakeRecorderScreen(
                    onViewFilesClick = { openedFiles = true },
                    onSettingsClick = { openedSettings = true },
                )
            }
        }

        composeRule.onNodeWithTag("view_files_button").performClick()
        composeRule.runOnIdle {
            assertTrue(openedFiles)
        }

        composeRule.onNodeWithTag("recorder_settings_button").performClick()
        composeRule.runOnIdle {
            assertTrue(openedSettings)
        }
    }

    @Test
    fun recorderWifiButtonInvokesSettingsDestination() {
        var openedSettings = false
        composeRule.setContent {
            MaterialTheme {
                FakeRecorderScreen(
                    onSettingsClick = { openedSettings = true },
                )
            }
        }

        composeRule.onNodeWithTag("hotspot_toggle_button").performClick()
        composeRule.runOnIdle {
            assertTrue(openedSettings)
        }
    }

    @Test
    fun mediaBrowserOpensVideoPlayerFromSelectedItem() {
        composeRule.setContent {
            MaterialTheme {
                MediaBrowserScreen(items = testMediaItems())
            }
        }

        composeRule.onNodeWithTag("media_video_list").assertExists()
        composeRule.onNodeWithTag("media_item_1").performClick()
        composeRule.onNodeWithTag("media_video_player").assertExists()
        composeRule.onNodeWithTag("media_delete_button").assertExists()
        composeRule.onNodeWithTag("media_lock_button").assertExists()
    }

    private fun testMediaItems(): List<MediaBrowserItem> =
        listOf(
            MediaBrowserItem(
                id = 1L,
                type = MediaType.VIDEO,
                mode = RecordingMode.DRIVING,
                path = "/tmp/server_clip_1.mp4",
                createdAt = 1_779_926_400_000L,
                durationMs = 60_000L,
                sizeBytes = 1024L,
            ),
            MediaBrowserItem(
                id = 2L,
                type = MediaType.PHOTO,
                mode = RecordingMode.MANUAL,
                path = "/tmp/server_photo_2.jpg",
                createdAt = 1_779_926_500_000L,
                sizeBytes = 512L,
            ),
        )
}
