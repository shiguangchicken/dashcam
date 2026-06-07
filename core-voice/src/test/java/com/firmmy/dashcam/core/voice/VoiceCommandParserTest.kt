package com.firmmy.dashcam.core.voice

import com.firmmy.dashcam.core.common.DashCamCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceCommandParserTest {
    private val parser = VoiceCommandParser()

    @Test
    fun mapsRequiredChineseCommandsToDashCamCommands() {
        assertCommand("拍照", DashCamCommand.TakePhoto)
        assertCommand("打开录音", DashCamCommand.EnableAudio)
        assertCommand("关闭录音", DashCamCommand.DisableAudio)
        assertCommand("打开热点", DashCamCommand.StartHotspot)
        assertCommand("关闭热点", DashCamCommand.StopHotspot)
        assertCommand("停车模式", DashCamCommand.StartParkingMode)
        assertCommand("驾驶模式", DashCamCommand.StartDrivingMode)
        assertCommand("保存这段", DashCamCommand.LockCurrentClip)
        assertCommand("停止录像", DashCamCommand.StopRecording)
    }

    @Test
    fun supportsSynonymsWhitespaceAndPunctuation() {
        assertCommand("  拍一张照片。", DashCamCommand.TakePhoto)
        assertCommand("开启录音！", DashCamCommand.EnableAudio)
        assertCommand("关掉热点", DashCamCommand.StopHotspot)
        assertCommand("进入停车模式", DashCamCommand.StartParkingMode)
        assertCommand("锁定当前视频", DashCamCommand.LockCurrentClip)
        assertCommand("结束录制", DashCamCommand.StopRecording)
    }

    @Test
    fun returnsClearReasonForEmptyInput() {
        val result = parser.parse(" ，。 ")

        assertTrue(result is VoiceCommandParseResult.Unrecognized)
        assertEquals(
            VoiceCommandParseFailure.EMPTY_INPUT,
            (result as VoiceCommandParseResult.Unrecognized).reason,
        )
    }

    @Test
    fun returnsClearReasonAndLogsForUnknownInput() {
        val sink = RecordingVoiceEventSink()
        val result = VoiceCommandParser(eventSink = sink).parse("播放音乐")

        assertTrue(result is VoiceCommandParseResult.Unrecognized)
        assertEquals(
            VoiceCommandParseFailure.UNKNOWN_COMMAND,
            (result as VoiceCommandParseResult.Unrecognized).reason,
        )
        assertTrue(sink.events.any { it is VoiceRecognitionEvent.ParseFailed })
    }

    private fun assertCommand(
        input: String,
        expected: DashCamCommand,
    ) {
        val result = parser.parse(input)

        assertTrue(result is VoiceCommandParseResult.Recognized)
        val recognized = result as VoiceCommandParseResult.Recognized
        assertEquals(expected, recognized.command)
        assertEquals(1.0f, recognized.confidence, 0.0f)
    }
}
