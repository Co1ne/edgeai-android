package com.edgeaivoice.jni

object NativeErrorCode {
    const val OK = 0
    const val INVALID_ARGUMENT = -1
    const val MODEL_LOAD_FAILED = -2
    const val INFERENCE_FAILED = -3
    const val OUT_OF_MEMORY = -4
    const val TIMEOUT = -5
    const val CANCELLED = -6
    const val PERMISSION_DENIED = -7
    const val AUDIO_IO_FAILED = -8
    const val NOT_INITIALIZED = -9
}
