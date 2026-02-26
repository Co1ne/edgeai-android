package com.edgeaivoice.jni

object NativeBridge {
    init {
        System.loadLibrary("native-lib")
    }

    external fun nativeHello(): String

    external fun asrInit(modelPath: String, threads: Int): Int
    external fun asrTranscribePcm16(audio: ByteArray, sampleRate: Int): AsrResult
    external fun asrRelease(): Int

    external fun llmInit(modelPath: String, ctxSize: Int, threads: Int): Int
    external fun llmStart(prompt: String, config: LlmGenConfig, callback: LlmTokenCallback): Int
    external fun llmCancel(): Int
    external fun llmRelease(): Int
}
