package com.edgeaivoice.pipeline

class VoicePipelineCoordinator {
    private var state: VoiceState = VoiceState.Idle

    fun currentState(): VoiceState = state

    fun onEvent(event: VoiceEvent): VoiceState {
        state = reduce(state, event)
        return state
    }

    private fun reduce(current: VoiceState, event: VoiceEvent): VoiceState {
        return when (current) {
            VoiceState.Idle -> when (event) {
                VoiceEvent.PressToTalk -> VoiceState.Recording
                else -> current
            }

            VoiceState.Recording -> when (event) {
                VoiceEvent.ReleaseToStop -> VoiceState.Transcribing
                is VoiceEvent.AsrFailed, is VoiceEvent.LlmFailed -> VoiceState.Error
                else -> current
            }

            VoiceState.Transcribing -> when (event) {
                is VoiceEvent.AsrSuccess -> VoiceState.Thinking
                is VoiceEvent.AsrFailed -> VoiceState.Error
                else -> current
            }

            VoiceState.Thinking -> when (event) {
                VoiceEvent.LlmFirstToken -> VoiceState.Streaming
                is VoiceEvent.LlmFailed -> VoiceState.Error
                else -> current
            }

            VoiceState.Streaming -> when (event) {
                is VoiceEvent.LlmDone -> VoiceState.Done
                is VoiceEvent.LlmFailed -> VoiceState.Error
                else -> current
            }

            VoiceState.Done, VoiceState.Error -> when (event) {
                VoiceEvent.Reset -> VoiceState.Idle
                else -> current
            }
        }
    }
}
