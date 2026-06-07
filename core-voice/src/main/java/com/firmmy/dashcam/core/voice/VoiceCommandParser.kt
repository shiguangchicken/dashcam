package com.firmmy.dashcam.core.voice

import com.firmmy.dashcam.core.common.DashCamCommand

sealed interface VoiceCommandParseResult {
    data class Recognized(
        val command: DashCamCommand,
        val normalizedInput: String,
        val matchedPhrase: String,
        val confidence: Float,
    ) : VoiceCommandParseResult

    data class Unrecognized(
        val reason: VoiceCommandParseFailure,
        val rawInput: String,
        val normalizedInput: String,
    ) : VoiceCommandParseResult
}

enum class VoiceCommandParseFailure {
    EMPTY_INPUT,
    UNKNOWN_COMMAND,
}

class VoiceCommandParser(
    phrasesByCommand: Map<DashCamCommand, Set<String>> = defaultPhrasesByCommand,
    private val eventSink: VoiceEventSink = NoOpVoiceEventSink,
) {
    private val commandByPhrase: Map<String, Pair<DashCamCommand, String>> =
        phrasesByCommand.flatMap { (command, phrases) ->
            phrases.map { phrase -> normalize(phrase) to (command to phrase) }
        }.toMap()

    fun parse(input: String): VoiceCommandParseResult {
        val normalized = normalize(input)
        if (normalized.isBlank()) {
            eventSink.record(VoiceRecognitionEvent.ParseFailed(input, normalized, VoiceCommandParseFailure.EMPTY_INPUT, 0.0f))
            return VoiceCommandParseResult.Unrecognized(
                reason = VoiceCommandParseFailure.EMPTY_INPUT,
                rawInput = input,
                normalizedInput = normalized,
            )
        }

        val match = commandByPhrase[normalized]
        if (match == null) {
            eventSink.record(VoiceRecognitionEvent.ParseFailed(input, normalized, VoiceCommandParseFailure.UNKNOWN_COMMAND, 0.0f))
            return VoiceCommandParseResult.Unrecognized(
                reason = VoiceCommandParseFailure.UNKNOWN_COMMAND,
                rawInput = input,
                normalizedInput = normalized,
            )
        }

        val confidence = 1.0f
        eventSink.record(VoiceRecognitionEvent.CommandRecognized(input, normalized, match.first, confidence))
        return VoiceCommandParseResult.Recognized(
            command = match.first,
            normalizedInput = normalized,
            matchedPhrase = match.second,
            confidence = confidence,
        )
    }

    companion object {
        private val punctuation = Regex("[\\s　，。！？；：、,.!?;:「」『』（）()\\[\\]【】\"'`]+")

        val defaultPhrasesByCommand: Map<DashCamCommand, Set<String>> = mapOf(
            DashCamCommand.TakePhoto to setOf("拍照", "照相", "拍张照", "拍一张照", "拍一张照片", "照一张"),
            DashCamCommand.EnableAudio to setOf("打开录音", "开启录音", "录音打开", "开始录音", "打开声音"),
            DashCamCommand.DisableAudio to setOf("关闭录音", "关掉录音", "录音关闭", "停止录音", "关闭声音", "静音"),
            DashCamCommand.StartHotspot to setOf("打开热点", "开启热点", "热点打开", "启动热点"),
            DashCamCommand.StopHotspot to setOf("关闭热点", "关掉热点", "热点关闭", "停止热点"),
            DashCamCommand.StartParkingMode to setOf("停车模式", "切换停车模式", "进入停车模式", "开始停车模式"),
            DashCamCommand.StartDrivingMode to setOf("驾驶模式", "行车模式", "切换驾驶模式", "进入驾驶模式", "开始驾驶模式"),
            DashCamCommand.LockCurrentClip to setOf("保存这段", "锁定这段", "保存当前视频", "锁定当前视频", "保留这段"),
            DashCamCommand.StopRecording to setOf("停止录像", "停止录制", "停止记录", "结束录像", "结束录制"),
        )

        fun normalize(input: String): String =
            input.trim().replace(punctuation, "")
    }
}
