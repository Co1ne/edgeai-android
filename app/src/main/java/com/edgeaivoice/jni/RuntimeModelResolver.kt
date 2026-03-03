package com.edgeaivoice.jni

import android.content.Context
import org.json.JSONObject
import java.io.File

object RuntimeModelResolver {
    private const val MANIFEST_REL_PATH = "models/_manifest/models.json"

    data class ResolveReport(
        val success: Boolean,
        val message: String,
        val manifestPath: String?,
        val asrModelPath: String?,
        val llmModelPath: String?,
        val asrThreads: Int,
        val llmThreads: Int,
        val llmContextSize: Int
    )

    fun resolve(context: Context): ResolveReport {
        val fallback = RuntimeConfig()
        val manifest = locateManifest(context)
            ?: return ResolveReport(
                success = false,
                message = "manifest missing: $MANIFEST_REL_PATH",
                manifestPath = null,
                asrModelPath = null,
                llmModelPath = null,
                asrThreads = fallback.asrThreads,
                llmThreads = fallback.llmThreads,
                llmContextSize = fallback.llmContextSize
            )

        return runCatching {
            val root = JSONObject(manifest.readText(Charsets.UTF_8))
            val defaults = root.optJSONObject("defaults")
            val asrThreads = defaults?.optInt("asrThreads", fallback.asrThreads) ?: fallback.asrThreads
            val llmThreads = defaults?.optInt("llmThreads", fallback.llmThreads) ?: fallback.llmThreads
            val llmContextSize = defaults?.optInt("llmContextSize", fallback.llmContextSize) ?: fallback.llmContextSize

            val models = root.optJSONArray("models")
            var asrModelPath: String? = null
            var llmModelPath: String? = null

            if (models != null) {
                for (i in 0 until models.length()) {
                    val item = models.optJSONObject(i) ?: continue
                    val required = item.optBoolean("required", true)
                    if (!required) continue

                    val relative = item.optString("relativePath", "").trim()
                    if (relative.isEmpty()) continue
                    val resolvedFile = resolveModelFile(context, "models/$relative")
                    if (resolvedFile == null) continue

                    when {
                        asrModelPath == null && relative.startsWith("asr/") -> {
                            asrModelPath = resolvedFile.absolutePath
                        }

                        llmModelPath == null && relative.startsWith("llm/") -> {
                            llmModelPath = resolvedFile.absolutePath
                        }
                    }
                }
            }

            if (asrModelPath == null || llmModelPath == null) {
                ResolveReport(
                    success = false,
                    message = "required model missing (asr=$asrModelPath, llm=$llmModelPath)",
                    manifestPath = manifest.absolutePath,
                    asrModelPath = asrModelPath,
                    llmModelPath = llmModelPath,
                    asrThreads = asrThreads,
                    llmThreads = llmThreads,
                    llmContextSize = llmContextSize
                )
            } else {
                ResolveReport(
                    success = true,
                    message = "models resolved",
                    manifestPath = manifest.absolutePath,
                    asrModelPath = asrModelPath,
                    llmModelPath = llmModelPath,
                    asrThreads = asrThreads,
                    llmThreads = llmThreads,
                    llmContextSize = llmContextSize
                )
            }
        }.getOrElse { err ->
            ResolveReport(
                success = false,
                message = "manifest parse failed: ${err.message}",
                manifestPath = manifest.absolutePath,
                asrModelPath = null,
                llmModelPath = null,
                asrThreads = fallback.asrThreads,
                llmThreads = fallback.llmThreads,
                llmContextSize = fallback.llmContextSize
            )
        }
    }

    private fun locateManifest(context: Context): File? {
        val external = context.getExternalFilesDir(null)?.let { File(it, MANIFEST_REL_PATH) }
        if (external != null && external.exists() && external.length() > 0) {
            return external
        }

        val internal = File(context.filesDir, MANIFEST_REL_PATH)
        if (internal.exists() && internal.length() > 0) {
            return internal
        }

        return null
    }

    private fun resolveModelFile(context: Context, relativePath: String): File? {
        val external = context.getExternalFilesDir(null)?.let { File(it, relativePath) }
        if (external != null && external.exists() && external.length() > 0) {
            return external
        }

        val internal = File(context.filesDir, relativePath)
        if (internal.exists() && internal.length() > 0) {
            return internal
        }

        return null
    }
}
