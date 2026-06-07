package com.firmmy.dashcam.core.voice

data class VoiceRuntimeConditions(
    val voiceWakeupEnabled: Boolean,
    val audioRecordingActive: Boolean,
    val batteryPercent: Int,
    val temperatureCelsius: Float,
)

object VoiceSafetyPolicy {
    private const val LOW_BATTERY_PERCENT = 15
    private const val HIGH_TEMPERATURE_CELSIUS = 45.0f

    fun pauseReasonFor(conditions: VoiceRuntimeConditions): VoicePauseReason? =
        when {
            !conditions.voiceWakeupEnabled -> VoicePauseReason.USER_DISABLED
            conditions.audioRecordingActive -> VoicePauseReason.AUDIO_RECORDING_ACTIVE
            conditions.batteryPercent <= LOW_BATTERY_PERCENT -> VoicePauseReason.LOW_BATTERY
            conditions.temperatureCelsius >= HIGH_TEMPERATURE_CELSIUS -> VoicePauseReason.HIGH_TEMPERATURE
            else -> null
        }
}
