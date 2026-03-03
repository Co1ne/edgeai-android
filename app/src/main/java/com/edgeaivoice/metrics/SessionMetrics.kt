package com.edgeaivoice.metrics

enum class DeviceTier {
    A_MAINSTREAM,
    B_FLAGSHIP
}

data class SessionMetrics(
    val sessionId: Long,
    val source: String,
    val tier: DeviceTier,
    val thermalStatus: Int,
    val asrElapsedMs: Long,
    val llmFirstTokenE2EMs: Long,
    val llmFirstTokenNativeMs: Long,
    val llmTotalMs: Long,
    val llmTokens: Int,
    val llmTokPerSec: Double,
    val javaHeapMb: Long,
    val nativeHeapMb: Long
)
