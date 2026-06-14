package com.firmmy.dashcam.core.media

data class CameraFpsRange(
    val lower: Int,
    val upper: Int,
) {
    init {
        require(lower > 0) { "lower must be positive" }
        require(upper >= lower) { "upper must be greater than or equal to lower" }
    }

    fun includes(fps: Int): Boolean = fps in lower..upper
}

object CameraFpsSelector {
    fun select(
        availableRanges: List<CameraFpsRange>,
        preferredFps: Int = PREFERRED_FPS,
    ): CameraFpsRange {
        require(availableRanges.isNotEmpty()) { "availableRanges must not be empty" }
        return availableRanges
            .filter { it.includes(preferredFps) }
            .maxWithOrNull(compareBy<CameraFpsRange> { it.lower }.thenBy { it.upper })
            ?: availableRanges.maxWith(compareBy<CameraFpsRange> { it.upper }.thenBy { it.lower })
    }

    const val PREFERRED_FPS = 60
}
