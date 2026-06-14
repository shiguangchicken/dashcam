package com.firmmy.dashcam.feature.remote

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import com.firmmy.dashcam.core.common.DashCamCommand
import com.firmmy.dashcam.core.common.MediaType
import com.firmmy.dashcam.core.common.RecordingMode
import com.firmmy.dashcam.core.common.RecordingStatus
import com.firmmy.dashcam.core.network.RemoteMediaItem
import com.firmmy.dashcam.core.network.RemoteSettings
import com.firmmy.dashcam.core.network.RemoteStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class RemoteViewerScreenInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun remoteViewerScreenConnectsAndLoadsMedia() {
        val client = FakeRemoteViewerClient()

        composeRule.setContent {
            MaterialTheme {
                RemoteViewerScreen(client = client)
            }
        }

        composeRule.onNodeWithTag("remote_manual_ip_field").performTextInput("192.168.62.74")
        composeRule.onNodeWithTag("remote_connect_button").performClick()

        composeRule.waitUntil(timeoutMillis = 5_000L) {
            client.connectedHosts.contains("192.168.62.74") && client.mediaCalls >= 2
        }
        composeRule.onNodeWithTag("remote_status_screen").assertExists()
        composeRule.onNodeWithTag("remote_home_screen").assertExists()
        composeRule.onNodeWithTag("remote_nav_media").performClick()
        composeRule.onNodeWithTag("remote_video_list").assertExists()
    }

    @Test
    fun remoteViewerDetailContentShowsVideoPlayer() {
        composeRule.setContent {
            MaterialTheme {
                RemoteViewerDetailContent(
                    item = media(1L, MediaType.VIDEO),
                    streamUrl = "http://127.0.0.1:8080/api/media/1/stream",
                    onBackClick = {},
                    onDeleteClick = {},
                )
            }
        }

        composeRule.onNodeWithTag("remote_video_player", useUnmergedTree = true).assertExists()
    }

    @Test
    fun remoteViewerScreenSendsCommandThroughFakeClient() {
        val client = FakeRemoteViewerClient()

        composeRule.setContent {
            MaterialTheme {
                RemoteViewerScreen(client = client)
            }
        }

        composeRule.onNodeWithTag("remote_manual_ip_field").performTextInput("192.168.62.74")
        composeRule.onNodeWithTag("remote_connect_button").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            client.connectedHosts.contains("192.168.62.74") && client.mediaCalls >= 2
        }
        composeRule.onNodeWithTag("remote_take_photo_button").performClick()

        composeRule.waitUntil(timeoutMillis = 5_000L) {
            client.commands.contains(DashCamCommand.TakePhoto)
        }
    }

    @Test
    fun remoteViewerContentShowsPhotosAndSavesSettings() {
        var state by mutableStateOf(
            RemoteViewerUiState(
                connected = true,
                status = RemoteStatus(
                    recordingStatus = RecordingStatus.RECORDING_DRIVING,
                    mode = RecordingMode.DRIVING,
                    hotspotEnabled = true,
                    hotspotSsid = "DashCam",
                    freeSpaceBytes = 1024L,
                ),
                videos = listOf(media(1L, MediaType.VIDEO)),
                photos = listOf(media(2L, MediaType.PHOTO)),
                destination = RemoteDestination.Settings,
                settings = settings(wakeWord = "old"),
            ),
        )
        var savedSettings: RemoteSettings? = null

        composeRule.setContent {
            MaterialTheme {
                RemoteViewerContent(
                    state = state,
                    onTypeSelected = { state = state.copy(selectedType = it) },
                    onSettingsChanged = { state = state.copy(settings = it) },
                    onSaveSettingsClick = { savedSettings = state.settings },
                )
            }
        }

        state = state.copy(destination = RemoteDestination.Media)
        composeRule.waitForIdle()
        composeRule.onNodeWithText("PHOTOS (1)").performClick()
        composeRule.onNodeWithTag("remote_photo_list").assertExists()
        state = state.copy(destination = RemoteDestination.Settings)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("remote_wake_word_field").performScrollTo()
        composeRule.onNodeWithTag("remote_wake_word_field").performTextClearance()
        composeRule.onNodeWithTag("remote_wake_word_field").performTextInput("new wake")
        composeRule.onNodeWithTag("remote_save_settings_button").performScrollTo().performClick()

        assertEquals("new wake", savedSettings?.wakeWord)
    }

    @Test
    fun remoteLiveDashboardSwitchesBetweenIdleImageAndStreamBackground() {
        var state by mutableStateOf(
            RemoteViewerUiState(
                connected = true,
                status = RemoteStatus(recordingStatus = RecordingStatus.IDLE),
                destination = RemoteDestination.Live,
            ),
        )

        composeRule.setContent {
            MaterialTheme {
                RemoteViewerContent(
                    state = state,
                    liveStreamUrl = { "http://127.0.0.1:8080/api/live.mjpeg" },
                )
            }
        }

        composeRule.onNodeWithTag("remote_idle_background").assertExists()

        state = state.copy(
            status = state.status.copy(
                recordingStatus = RecordingStatus.RECORDING_DRIVING,
                liveStreamAvailable = true,
            ),
        )
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("remote_live_stream_background").assertExists()
    }

    private class FakeRemoteViewerClient : RemoteViewerClient {
        val connectedHosts = mutableListOf<String>()
        val commands = mutableListOf<DashCamCommand>()
        var mediaCalls = 0

        override suspend fun connect(manualHost: String): Boolean {
            connectedHosts += manualHost
            return true
        }

        override suspend fun status(): RemoteStatus =
            RemoteStatus(
                recordingStatus = RecordingStatus.RECORDING_DRIVING,
                mode = RecordingMode.DRIVING,
                audioEnabled = true,
                hotspotEnabled = true,
                hotspotSsid = "DashCam",
                freeSpaceBytes = 1024L,
            )

        override suspend fun media(type: MediaType?): List<RemoteMediaItem> {
            mediaCalls += 1
            return when (type) {
                MediaType.VIDEO -> listOf(media(1L, MediaType.VIDEO))
                MediaType.PHOTO -> listOf(media(2L, MediaType.PHOTO))
                null -> listOf(media(1L, MediaType.VIDEO), media(2L, MediaType.PHOTO))
            }
        }

        override suspend fun send(command: DashCamCommand): Boolean {
            commands += command
            return true
        }

        override suspend fun deleteMedia(id: Long): Boolean = true

        override suspend fun settings(): RemoteSettings = settings()

        override suspend fun saveSettings(settings: RemoteSettings): Boolean {
            assertTrue(settings.wakeWord.isNotBlank())
            return true
        }

        override fun streamUrl(id: Long): String = "http://127.0.0.1:8080/api/media/$id/stream"

        override fun thumbnailUrl(id: Long): String = "http://127.0.0.1:8080/api/media/$id/thumbnail"

        override fun downloadUrl(id: Long): String = "http://127.0.0.1:8080/api/media/$id/download"

        override fun liveStreamUrl(): String = "http://127.0.0.1:8080/api/live.mjpeg"

        override fun liveH264WebSocketUrl(): String = "ws://127.0.0.1:8080/ws/live/h264"
    }

    companion object {
        private fun media(
            id: Long,
            type: MediaType,
        ): RemoteMediaItem =
            RemoteMediaItem(
                id = id,
                type = type,
                mode = RecordingMode.DRIVING,
                path = "/remote/$id",
                createdAt = 1_780_012_800_000L,
                durationMs = 322_000L,
                sizeBytes = 1024L,
            )

        private fun settings(wakeWord: String = "dashcam"): RemoteSettings =
            RemoteSettings(
                drivingResolution = "1920x1080",
                drivingFps = 30,
                drivingBitrateKbps = 12000,
                parkingResolution = "1280x720",
                parkingFps = 2,
                parkingBitrateKbps = 1000,
                segmentDurationMinutes = 3,
                maxStorageGb = 32,
                minFreeSpaceGb = 2,
                audioEnabled = true,
                voiceWakeupEnabled = false,
                wakeWord = wakeWord,
            )
    }
}
