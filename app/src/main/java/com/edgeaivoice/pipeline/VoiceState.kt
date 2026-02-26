package com.edgeaivoice.pipeline

enum class VoiceState {
    Idle,
    Recording,
    Transcribing,
    Thinking,
    Streaming,
    Done,
    Error
}
