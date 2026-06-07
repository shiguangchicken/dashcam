package com.firmmy.dashcam.core.voice

import com.firmmy.dashcam.core.common.DashCamCommand

fun interface VoiceCommandDispatcher {
    fun dispatch(command: DashCamCommand): Boolean
}

enum class VoiceListeningStatus {
    DISABLED,
    LISTENING,
    COMMAND_WINDOW,
    PAUSED,
}

enum class VoicePauseReason {
    AUDIO_RECORDING_ACTIVE,
    LOW_BATTERY,
    HIGH_TEMPERATURE,
    USER_DISABLED,
}

data class VoiceWakeConfig(
    val enabled: Boolean = true,
    val wakeWords: Set<String> = setOf("小行车", "记录仪"),
    val commandWindowMillis: Long = 4_000L,
)

class VoiceWakeService(
    private val parser: VoiceCommandParser = VoiceCommandParser(),
    private val dispatcher: VoiceCommandDispatcher,
    private val eventSink: VoiceEventSink = NoOpVoiceEventSink,
    private val clock: () -> Long = { System.currentTimeMillis() },
    config: VoiceWakeConfig = VoiceWakeConfig(),
) {
    private val normalizedWakeWords = config.wakeWords.map { VoiceCommandParser.normalize(it) }.toSet()
    private val commandWindowMillis = config.commandWindowMillis
    private var commandWindowStartedAt: Long? = null
    private var pauseReason: VoicePauseReason? = null

    var status: VoiceListeningStatus = if (config.enabled) VoiceListeningStatus.LISTENING else VoiceListeningStatus.DISABLED
        private set

    fun setEnabled(enabled: Boolean) {
        commandWindowStartedAt = null
        pauseReason = if (enabled) null else VoicePauseReason.USER_DISABLED
        status = if (enabled) VoiceListeningStatus.LISTENING else VoiceListeningStatus.DISABLED
        eventSink.record(VoiceRecognitionEvent.StatusChanged(status, pauseReason))
    }

    fun pause(reason: VoicePauseReason) {
        commandWindowStartedAt = null
        pauseReason = reason
        status = VoiceListeningStatus.PAUSED
        eventSink.record(VoiceRecognitionEvent.StatusChanged(status, reason))
    }

    fun resume() {
        commandWindowStartedAt = null
        pauseReason = null
        status = VoiceListeningStatus.LISTENING
        eventSink.record(VoiceRecognitionEvent.StatusChanged(status, null))
    }

    fun acceptWakeText(text: String): Boolean {
        if (status != VoiceListeningStatus.LISTENING) return false
        val normalized = VoiceCommandParser.normalize(text)
        val woke = normalizedWakeWords.any { normalized.contains(it) }
        if (!woke) {
            eventSink.record(VoiceRecognitionEvent.WakeRejected(text))
            return false
        }

        commandWindowStartedAt = clock()
        status = VoiceListeningStatus.COMMAND_WINDOW
        eventSink.record(VoiceRecognitionEvent.WakeAccepted(text, commandWindowMillis))
        return true
    }

    fun acceptCommandText(text: String): Boolean {
        if (status != VoiceListeningStatus.COMMAND_WINDOW) return false
        if (hasTimedOut()) {
            timeout()
            return false
        }

        return when (val parsed = parser.parse(text)) {
            is VoiceCommandParseResult.Recognized -> {
                val dispatched = dispatcher.dispatch(parsed.command)
                eventSink.record(VoiceRecognitionEvent.CommandDispatched(parsed.command, dispatched))
                commandWindowStartedAt = null
                status = VoiceListeningStatus.LISTENING
                dispatched
            }

            is VoiceCommandParseResult.Unrecognized -> {
                eventSink.record(VoiceRecognitionEvent.CommandRejected(parsed.reason))
                false
            }
        }
    }

    fun tick() {
        if (status == VoiceListeningStatus.COMMAND_WINDOW && hasTimedOut()) {
            timeout()
        }
    }

    private fun hasTimedOut(): Boolean {
        val startedAt = commandWindowStartedAt ?: return false
        return clock() - startedAt >= commandWindowMillis
    }

    private fun timeout() {
        commandWindowStartedAt = null
        status = VoiceListeningStatus.LISTENING
        eventSink.record(VoiceRecognitionEvent.CommandWindowTimedOut)
    }
}
