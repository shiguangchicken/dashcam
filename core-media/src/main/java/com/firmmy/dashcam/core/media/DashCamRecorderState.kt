package com.firmmy.dashcam.core.media

import com.firmmy.dashcam.core.common.RecordingMode
import com.firmmy.dashcam.core.common.RecordingStatus

data class DashCamRecorderState(
    val status: RecordingStatus = RecordingStatus.IDLE,
    val mode: RecordingMode? = null,
    val audioEnabled: Boolean = true,
    val hotspotEnabled: Boolean = false,
    val currentClipLocked: Boolean = false,
    val photoCount: Int = 0,
    val errorMessage: String? = null,
)
