package com.edgeaivoice.jni

interface LlmTokenCallback {
    fun onFirstToken(latencyMs: Long)
    fun onToken(token: String)
    fun onComplete(stats: LlmStats)
    fun onError(code: Int, message: String)
}
