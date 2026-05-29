package com.firmmy.dashcam.feature.recorder

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.firmmy.dashcam.core.common.MediaType
import com.firmmy.dashcam.core.common.RecordingMode
import com.firmmy.dashcam.core.common.RecordingStatus
import org.junit.Rule
import org.junit.Test

class RecorderScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun recorderScreenShowsRequiredStatusAndActions() {
        setRecorderContent()

        listOf(
            "recorder_start_button",
            "mode_driving_button",
            "mode_parking_button",
            "take_photo_button",
            "audio_toggle_button",
            "hotspot_toggle_button",
            "current_mode_text",
            "recording_status_text",
        ).forEach { tag ->
            composeRule.onNodeWithTag(tag).assertIsDisplayed()
        }
    }

    @Test
    fun fakeRecorderStateSwitchesRecordingModeAndTogglesControls() {
        setRecorderContent()

        composeRule.onNodeWithTag("recording_status_text").assertTextContains("Idle")
        composeRule.onNodeWithTag("recorder_start_button").performClick()
        composeRule.onNodeWithTag("recording_status_text").assertTextContains("Recording driving")

        composeRule.onNodeWithTag("mode_parking_button").performClick()
        composeRule.onNodeWithTag("current_mode_text").assertTextContains("Parking")
        composeRule.onNodeWithTag("recording_status_text").assertTextContains("Recording parking")

        composeRule.onNodeWithTag("audio_toggle_button").performClick()
        composeRule.onNodeWithTag("audio_toggle_button").assertTextContains("Audio")

        composeRule.onNodeWithTag("hotspot_toggle_button").performClick()
        composeRule.onNodeWithTag("hotspot_toggle_button").assertTextContains("Hotspot off")

        composeRule.onNodeWithTag("take_photo_button").performClick()
        composeRule.onNodeWithTag("recorder_start_button").performClick()
        composeRule.onNodeWithTag("recording_status_text").assertTextContains("Idle")
    }

    @Test
    fun mediaBrowserShowsListsAndFiltersMedia() {
        setMediaBrowserContent()

        composeRule.onNodeWithTag("media_video_list").assertIsDisplayed()
        composeRule.onNodeWithTag("media_filter_date").assertIsDisplayed()
        composeRule.onNodeWithTag("media_item_1").assertIsDisplayed()

        composeRule.onNodeWithText("2026-05-28").performClick()
        composeRule.onNodeWithTag("media_item_1").assertIsDisplayed()

        composeRule.onNodeWithText("Photos").performClick()
        composeRule.onNodeWithTag("media_photo_list").assertIsDisplayed()
        composeRule.onNodeWithTag("media_item_3").assertIsDisplayed()
    }

    @Test
    fun mediaBrowserOpensVideoAndRunsFileActions() {
        var deleted = 0L
        var locked = 0L
        setMediaBrowserContent(
            onDeleteClick = { deleted = it.id },
            onLockClick = { locked = it.id },
        )

        composeRule.onNodeWithTag("media_item_1").performClick()
        composeRule.onNodeWithTag("media_video_player").assertIsDisplayed()
        composeRule.onNodeWithTag("media_lock_button").performClick()
        composeRule.runOnIdle { assert(locked == 1L) }

        composeRule.onNodeWithTag("media_item_1").performClick()
        composeRule.onNodeWithTag("media_delete_button").performClick()
        composeRule.runOnIdle { assert(deleted == 1L) }
    }

    private fun setRecorderContent(
        state: RecorderUiState = RecorderUiState(
            mode = RecordingMode.DRIVING,
            recordingStatus = RecordingStatus.IDLE,
        ),
    ) {
        composeRule.setContent {
            MaterialTheme {
                val stateHolder = rememberFakeRecorderState(state)
                RecorderScreen(
                    state = stateHolder.state,
                    onStartStopClick = stateHolder::toggleRecording,
                    onDrivingModeClick = { stateHolder.switchMode(RecordingMode.DRIVING) },
                    onParkingModeClick = { stateHolder.switchMode(RecordingMode.PARKING) },
                    onTakePhotoClick = stateHolder::takePhoto,
                    onAudioToggleClick = stateHolder::toggleAudio,
                    onHotspotToggleClick = stateHolder::toggleHotspot,
                    onViewFilesClick = {},
                    onSettingsClick = {},
                )
            }
        }
    }

    private fun setMediaBrowserContent(
        onDeleteClick: (MediaBrowserItem) -> Unit = {},
        onLockClick: (MediaBrowserItem) -> Unit = {},
    ) {
        composeRule.setContent {
            MaterialTheme {
                MediaBrowserScreen(
                    items = listOf(
                        mediaItem(
                            id = 1L,
                            type = MediaType.VIDEO,
                            mode = RecordingMode.DRIVING,
                            createdAt = 1_779_926_400_000L,
                        ),
                        mediaItem(
                            id = 2L,
                            type = MediaType.VIDEO,
                            mode = RecordingMode.PARKING,
                            createdAt = 1_780_012_800_000L,
                        ),
                        mediaItem(
                            id = 3L,
                            type = MediaType.PHOTO,
                            mode = RecordingMode.MANUAL,
                            createdAt = 1_779_926_400_000L,
                        ),
                    ),
                    onDeleteClick = onDeleteClick,
                    onLockClick = onLockClick,
                )
            }
        }
    }

    private fun mediaItem(
        id: Long,
        type: MediaType,
        mode: RecordingMode,
        createdAt: Long,
    ): MediaBrowserItem =
        MediaBrowserItem(
            id = id,
            type = type,
            mode = mode,
            path = "/tmp/media_$id",
            createdAt = createdAt,
            durationMs = if (type == MediaType.VIDEO) 60_000L else null,
            sizeBytes = 1024L,
        )
}
