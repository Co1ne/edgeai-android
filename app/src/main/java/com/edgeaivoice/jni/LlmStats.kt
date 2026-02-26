package com.edgeaivoice.jni

data class LlmStats(
    val firstTokenLatencyMs: Long,
    val totalElapsedMs: Long,
    val totalTokens: Int
)
