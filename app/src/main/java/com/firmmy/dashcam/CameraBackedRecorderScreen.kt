package com.firmmy.dashcam

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.LifecycleOwner
import com.firmmy.dashcam.core.common.DashCamResult
import com.firmmy.dashcam.core.common.RecordingMode
import com.firmmy.dashcam.core.common.RecordingStatus
import com.firmmy.dashcam.core.database.DashCamDatabaseProvider
import com.firmmy.dashcam.core.database.MediaRepository
import com.firmmy.dashcam.core.media.AndroidThumbnailGenerator
import com.firmmy.dashcam.core.media.CameraRecorderManager
import com.firmmy.dashcam.core.media.CameraXCameraFacade
import com.firmmy.dashcam.core.media.DashCamMediaDirectories
import com.firmmy.dashcam.core.media.DashCamMediaRepository
import com.firmmy.dashcam.feature.recorder.RecorderScreen
import com.firmmy.dashcam.feature.recorder.RecorderUiState
import kotlinx.coroutines.launch

@Composable
fun CameraBackedRecorderScreen(
    context: Context,
    modifier: Modifier = Modifier,
    onViewFilesClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
) {
    val lifecycleOwner = remember(context) { context.findActivity() as LifecycleOwner }
    val manager = remember(context, lifecycleOwner) {
        val database = DashCamDatabaseProvider.get(context)
        val directories = DashCamMediaDirectories.fromContext(context)
        CameraRecorderManager(
            directories = directories,
            mediaRepository = DashCamMediaRepository(
                mediaRepository = MediaRepository(database.mediaFileDao()),
                directories = directories,
                thumbnailGenerator = AndroidThumbnailGenerator(),
            ),
            cameraFacade = CameraXCameraFacade(
                context = context.applicationContext,
                lifecycleOwner = lifecycleOwner,
            ),
        )
    }
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf(RecorderUiState()) }

    RecorderScreen(
        modifier = modifier,
        state = state,
        onStartStopClick = {
            scope.launch {
                state = if (state.recordingStatus == RecordingStatus.IDLE) {
                    when (state.mode) {
                        RecordingMode.PARKING -> manager.startParkingRecording(audioEnabled = state.audioEnabled)
                        else -> manager.startDrivingRecording(audioEnabled = state.audioEnabled)
                    }.toRecordingState(state)
                } else {
                    when (manager.stopRecording()) {
                        is DashCamResult.Success -> state.copy(
                            recordingStatus = RecordingStatus.IDLE,
                            currentSegmentMillis = 0L,
                        )

                        is DashCamResult.Failure -> state.copy(recordingStatus = RecordingStatus.IDLE)
                    }
                }
            }
        },
        onDrivingModeClick = {
            state = state.copy(mode = RecordingMode.DRIVING)
        },
        onParkingModeClick = {
            state = state.copy(mode = RecordingMode.PARKING)
        },
        onTakePhotoClick = {
            scope.launch {
                state = when (manager.takePhoto()) {
                    is DashCamResult.Success -> state.copy(photoCount = state.photoCount + 1)
                    is DashCamResult.Failure -> state
                }
            }
        },
        onAudioToggleClick = {
            state = state.copy(audioEnabled = !state.audioEnabled)
        },
        onHotspotToggleClick = {
            state = state.copy(hotspotEnabled = !state.hotspotEnabled)
        },
        onViewFilesClick = onViewFilesClick,
        onSettingsClick = onSettingsClick,
    )
}

private fun DashCamResult<*>.toRecordingState(previous: RecorderUiState): RecorderUiState =
    when (this) {
        is DashCamResult.Success -> previous.copy(
            recordingStatus = when (previous.mode) {
                RecordingMode.PARKING -> RecordingStatus.RECORDING_PARKING
                else -> RecordingStatus.RECORDING_DRIVING
            },
            currentSegmentMillis = 1_000L,
        )

        is DashCamResult.Failure -> previous.copy(recordingStatus = RecordingStatus.IDLE)
    }

private tailrec fun Context.findActivity(): Activity =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> error("Recorder screen requires an Activity context")
    }
