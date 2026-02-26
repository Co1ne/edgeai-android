package com.edgeaivoice.jni

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object AssetsModelInstaller {
    private const val TAG = "AssetsModelInstaller"

    private val requiredModels = listOf(
        "models/asr/ggml-base.bin",
        "models/llm/model.gguf",
        "models/_manifest/models.json"
    )

    data class ModelFile(val path: String, val sizeBytes: Long, val note: String)
    data class InstallReport(val success: Boolean, val elapsedMs: Long, val modelFiles: List<ModelFile>)

    suspend fun installIfNeeded(context: Context): InstallReport = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        val outFiles = mutableListOf<ModelFile>()
        var ok = true

        val externalBase = context.getExternalFilesDir(null)

        requiredModels.forEach { relPath ->
            val externalFile = externalBase?.let { File(it, relPath) }
            val internalFile = File(context.filesDir, relPath)

            when {
                externalFile != null && externalFile.exists() && externalFile.length() > 0L -> {
                    outFiles += ModelFile(externalFile.absolutePath, externalFile.length(), "external (preferred)")
                }
                internalFile.exists() && internalFile.length() > 0L -> {
                    outFiles += ModelFile(internalFile.absolutePath, internalFile.length(), "internal fallback")
                }
                else -> {
                    ok = false
                    val expected = externalFile?.absolutePath
                        ?: "<externalFilesDir unavailable>/$relPath"
                    outFiles += ModelFile(expected, 0L, "missing (adb push required)")
                    Log.e(TAG, "missing model: $expected")
                }
            }
        }

        val elapsed = System.currentTimeMillis() - start
        Log.i(TAG, "model locate finished, success=$ok, elapsedMs=$elapsed")
        InstallReport(ok, elapsed, outFiles)
    }
}
