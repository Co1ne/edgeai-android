package com.edgeaivoice.pipeline

sealed interface VoiceEvent {
    data object PressToTalk : VoiceEvent
    data object ReleaseToStop : VoiceEvent
    data class AudioReady(val bytes: Int) : VoiceEvent
    data class AsrSuccess(val text: String) : VoiceEvent
    data class AsrFailed(val code: Int, val message: String) : VoiceEvent
    data object LlmFirstToken : VoiceEvent
    data class LlmToken(val token: String) : VoiceEvent
    data class LlmDone(val totalTokens: Int) : VoiceEvent
    data class LlmFailed(val code: Int, val message: String) : VoiceEvent
    data object Reset : VoiceEvent
}
