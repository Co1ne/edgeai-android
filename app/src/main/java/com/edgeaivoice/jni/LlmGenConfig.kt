package com.edgeaivoice.jni

data class LlmGenConfig(
    val temperature: Float = 0.2f,
    val topP: Float = 0.9f,
    val maxNewTokens: Int = 256,
    val repeatPenalty: Float = 1.1f,
    val stopWords: List<String> = listOf("</s>")
)
