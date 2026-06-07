package com.firmmy.dashcam.core.voice

import com.firmmy.dashcam.core.common.DashCamCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceWakeServiceTest {
    @Test
    fun wakeWordOpensShortCommandWindowAndDispatchesRecognizedCommand() {
        val dispatched = mutableListOf<DashCamCommand>()
        val sink = RecordingVoiceEventSink()
        val service = VoiceWakeService(
            dispatcher = VoiceCommandDispatcher {
                dispatched += it
                true
            },
            eventSink = sink,
        )

        assertTrue(service.acceptWakeText("小行车"))
        assertEquals(VoiceListeningStatus.COMMAND_WINDOW, service.status)

        assertTrue(service.acceptCommandText("拍照"))

        assertEquals(listOf(DashCamCommand.TakePhoto), dispatched)
        assertEquals(VoiceListeningStatus.LISTENING, service.status)
        assertTrue(sink.events.any { it is VoiceRecognitionEvent.CommandDispatched })
    }

    @Test
    fun unknownCommandStaysInCommandWindowForRetry() {
        val service = VoiceWakeService(dispatcher = VoiceCommandDispatcher { true })

        service.acceptWakeText("记录仪")
        assertFalse(service.acceptCommandText("播放音乐"))

        assertEquals(VoiceListeningStatus.COMMAND_WINDOW, service.status)
    }

    @Test
    fun commandWindowTimesOutBackToListening() {
        var now = 1_000L
        val service = VoiceWakeService(
            dispatcher = VoiceCommandDispatcher { true },
            clock = { now },
            config = VoiceWakeConfig(commandWindowMillis = 4_000L),
        )

        service.acceptWakeText("小行车")
        now = 5_000L
        service.tick()

        assertEquals(VoiceListeningStatus.LISTENING, service.status)
    }

    @Test
    fun pausePreventsWakeAndResumeRestoresListening() {
        val service = VoiceWakeService(dispatcher = VoiceCommandDispatcher { true })

        service.pause(VoicePauseReason.AUDIO_RECORDING_ACTIVE)
        assertEquals(VoiceListeningStatus.PAUSED, service.status)
        assertFalse(service.acceptWakeText("小行车"))

        service.resume()

        assertEquals(VoiceListeningStatus.LISTENING, service.status)
        assertTrue(service.acceptWakeText("小行车"))
    }
}
