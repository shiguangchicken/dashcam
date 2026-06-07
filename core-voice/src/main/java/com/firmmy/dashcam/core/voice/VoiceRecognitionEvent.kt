package com.firmmy.dashcam.core.voice

import com.firmmy.dashcam.core.common.DashCamCommand

sealed interface VoiceRecognitionEvent {
    data class CommandRecognized(
        val rawInput: String,
        val normalizedInput: String,
        val command: DashCamCommand,
        val confidence: Float,
    ) : VoiceRecognitionEvent

    data class ParseFailed(
        val rawInput: String,
        val normalizedInput: String,
        val reason: VoiceCommandParseFailure,
        val confidence: Float,
    ) : VoiceRecognitionEvent

    data class WakeAccepted(
        val rawInput: String,
        val commandWindowMillis: Long,
    ) : VoiceRecognitionEvent

    data class WakeRejected(
        val rawInput: String,
    ) : VoiceRecognitionEvent

    data class CommandRejected(
        val reason: VoiceCommandParseFailure,
    ) : VoiceRecognitionEvent

    data class CommandDispatched(
        val command: DashCamCommand,
        val ok: Boolean,
    ) : VoiceRecognitionEvent

    data class StatusChanged(
        val status: VoiceListeningStatus,
        val pauseReason: VoicePauseReason?,
    ) : VoiceRecognitionEvent

    data object CommandWindowTimedOut : VoiceRecognitionEvent
}

fun interface VoiceEventSink {
    fun record(event: VoiceRecognitionEvent)
}

object NoOpVoiceEventSink : VoiceEventSink {
    override fun record(event: VoiceRecognitionEvent) = Unit
}

class RecordingVoiceEventSink : VoiceEventSink {
    private val mutableEvents = mutableListOf<VoiceRecognitionEvent>()

    val events: List<VoiceRecognitionEvent>
        get() = mutableEvents.toList()

    override fun record(event: VoiceRecognitionEvent) {
        mutableEvents += event
    }
}
