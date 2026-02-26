package com.edgeaivoice.jni

data class RuntimeConfig(
    val asrThreads: Int = 4,
    val llmThreads: Int = 4,
    val llmContextSize: Int = 2048,
    val recordingTimeoutMs: Long = 60_000L,
    val asrTimeoutMs: Long = 20_000L,
    val llmFirstTokenTimeoutMs: Long = 10_000L,
    val llmTotalTimeoutMs: Long = 120_000L,
    val multiTurnEnabled: Boolean = false,
    val historyTurns: Int = 0,
    val maxPromptTokens: Int = 1024
)
