package com.edgeaivoice.jni

import android.util.Log

object NativeRuntimeLoader {
    private const val TAG = "NativeRuntimeLoader"

    private val runtimeLibs = listOf(
        "ggml-base",
        "ggml-cpu",
        "ggml",
        "whisper",
        "llama"
    )

    data class LoadReport(val success: Boolean, val message: String)

    fun loadRuntimeLibraries(): LoadReport {
        val detail = mutableListOf<String>()
        var ok = true

        runtimeLibs.forEach { name ->
            try {
                System.loadLibrary(name)
                Log.i(TAG, "load success: $name")
                detail += "OK:$name"
            } catch (e: Throwable) {
                ok = false
                Log.e(TAG, "load failure: $name", e)
                detail += "FAIL:$name(${e.javaClass.simpleName})"
            }
        }

        return LoadReport(ok, detail.joinToString(", "))
    }
}
