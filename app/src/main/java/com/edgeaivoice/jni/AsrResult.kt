package com.edgeaivoice.jni

data class AsrResult(
    val text: String,
    val elapsedMs: Long,
    val errorCode: Int = NativeErrorCode.OK,
    val errorMessage: String? = null
)
