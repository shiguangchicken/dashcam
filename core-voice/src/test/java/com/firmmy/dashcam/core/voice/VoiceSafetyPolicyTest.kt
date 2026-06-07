package com.firmmy.dashcam.core.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VoiceSafetyPolicyTest {
    @Test
    fun pausesWhenVoiceWakeupIsDisabled() {
        assertEquals(
            VoicePauseReason.USER_DISABLED,
            VoiceSafetyPolicy.pauseReasonFor(conditions(voiceWakeupEnabled = false)),
        )
    }

    @Test
    fun pausesWhenAudioRecordingUsesMicrophone() {
        assertEquals(
            VoicePauseReason.AUDIO_RECORDING_ACTIVE,
            VoiceSafetyPolicy.pauseReasonFor(conditions(audioRecordingActive = true)),
        )
    }

    @Test
    fun pausesOnLowBatteryOrHighTemperature() {
        assertEquals(
            VoicePauseReason.LOW_BATTERY,
            VoiceSafetyPolicy.pauseReasonFor(conditions(batteryPercent = 10)),
        )
        assertEquals(
            VoicePauseReason.HIGH_TEMPERATURE,
            VoiceSafetyPolicy.pauseReasonFor(conditions(temperatureCelsius = 46.0f)),
        )
    }

    @Test
    fun keepsListeningWhenConditionsAreSafe() {
        assertNull(VoiceSafetyPolicy.pauseReasonFor(conditions()))
    }

    private fun conditions(
        voiceWakeupEnabled: Boolean = true,
        audioRecordingActive: Boolean = false,
        batteryPercent: Int = 80,
        temperatureCelsius: Float = 36.5f,
    ): VoiceRuntimeConditions =
        VoiceRuntimeConditions(
            voiceWakeupEnabled = voiceWakeupEnabled,
            audioRecordingActive = audioRecordingActive,
            batteryPercent = batteryPercent,
            temperatureCelsius = temperatureCelsius,
        )
}
