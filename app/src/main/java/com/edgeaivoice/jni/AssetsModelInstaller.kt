package com.edgeaivoice.jni

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

object AssetsModelInstaller {
    private const val TAG = "AssetsModelInstaller"
    private const val MANIFEST_REL_PATH = "models/_manifest/models.json"

    private val fallbackRequiredModels = listOf(
        "models/asr/ggml-small.bin",
        "models/llm/model.gguf",
        MANIFEST_REL_PATH
    )

    data class ModelFile(val path: String, val sizeBytes: Long, val note: String)
    data class InstallReport(val success: Boolean, val elapsedMs: Long, val modelFiles: List<ModelFile>)

    suspend fun installIfNeeded(context: Context): InstallReport = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        val outFiles = mutableListOf<ModelFile>()
        var ok = true

        val externalBase = context.getExternalFilesDir(null)
        val internalBase = context.filesDir
        val manifestFile = locateManifest(externalBase, internalBase)
        val requiredModels = manifestFile?.let { parseRequiredModelsFromManifest(it) } ?: fallbackRequiredModels
        if (manifestFile == null) {
            Log.w(TAG, "manifest missing, fallback required model list will be used")
        } else {
            Log.i(TAG, "manifest loaded from ${manifestFile.absolutePath}, requiredModels=${requiredModels.size}")
        }

        requiredModels.forEach { relPath ->
            val externalFile = externalBase?.let { File(it, relPath) }
            val internalFile = File(internalBase, relPath)

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

    private fun locateManifest(externalBase: File?, internalBase: File): File? {
        val externalManifest = externalBase?.let { File(it, MANIFEST_REL_PATH) }
        if (externalManifest != null && externalManifest.exists() && externalManifest.length() > 0L) {
            return externalManifest
        }

        val internalManifest = File(internalBase, MANIFEST_REL_PATH)
        if (internalManifest.exists() && internalManifest.length() > 0L) {
            return internalManifest
        }

        return null
    }

    private fun parseRequiredModelsFromManifest(manifestFile: File): List<String> {
        return runCatching {
            val json = JSONObject(manifestFile.readText(Charsets.UTF_8))
            val arr = json.optJSONArray("models")
            val resolved = mutableListOf(MANIFEST_REL_PATH)

            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val item = arr.optJSONObject(i) ?: continue
                    val required = item.optBoolean("required", true)
                    if (!required) continue

                    val rel = item.optString("relativePath", "").trim()
                    if (rel.isEmpty()) continue
                    resolved += "models/$rel"
                }
            }

            resolved.distinct()
        }.getOrElse { err ->
            Log.e(TAG, "failed to parse manifest: ${manifestFile.absolutePath}, err=${err.message}")
            fallbackRequiredModels
        }
    }
}
