package com.firmmy.dashcam.feature.recorder

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
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
}
