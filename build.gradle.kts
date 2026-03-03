import java.io.File

plugins {
    id("com.android.application") version "8.5.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.24" apply false
}

tasks.register("importRuntime") {
    group = "runtime"
    description = "Import runtime libs/models from ../edge-ai-android-runtime-v1.tgz"

    doLast {
        val runtimeOverride = providers.gradleProperty("runtimeTgzPath").orNull
            ?: System.getenv("RUNTIME_TGZ")
        val edgeAiRoot = System.getenv("EDGE_AI_ROOT")

        val candidates = listOfNotNull(
            runtimeOverride?.let { rootProject.file(it) },
            rootProject.file("../edge-ai-android-runtime-v1.tgz"),
            edgeAiRoot?.let { File(it, "edge-ai-android-runtime-v1.tgz") },
            File(System.getProperty("user.home"), "edge-ai/edge-ai-android-runtime-v1.tgz")
        )

        val tgz = candidates.firstOrNull { it.exists() && it.isFile }
            ?: throw GradleException(
                "Runtime package not found. Tried:\n${candidates.joinToString("\n") { "- ${it.absolutePath}" }}"
            )

        val tmpDir = layout.buildDirectory.dir("tmp/runtimeImport").get().asFile
        if (tmpDir.exists()) {
            tmpDir.deleteRecursively()
        }
        tmpDir.mkdirs()

        exec {
            commandLine("tar", "-xzf", tgz.absolutePath, "-C", tmpDir.absolutePath)
        }

        val mappings = listOf(
            "dist/android/arm64-v8a" to "app/src/main/jniLibs/arm64-v8a",
            "models/asr" to "runtime/models/asr",
            "models/llm" to "runtime/models/llm",
            "models/_manifest/models.json" to "runtime/models/_manifest/models.json",
            "RELEASE_NOTES.txt" to "runtime/RELEASE_NOTES.txt"
        )

        val copied = mutableListOf<File>()

        mappings.forEach { (fromPath, toPath) ->
            val from = File(tmpDir, fromPath)
            val to = rootProject.file(toPath)

            if (!from.exists()) {
                throw GradleException("Expected file/folder missing in archive: $fromPath")
            }

            if (from.isDirectory) {
                to.mkdirs()
                from.listFiles()?.filter { it.isFile }?.forEach { src ->
                    val dst = File(to, src.name)
                    src.copyTo(dst, overwrite = true)
                    copied += dst
                }
            } else {
                to.parentFile.mkdirs()
                from.copyTo(to, overwrite = true)
                copied += to
            }
        }

        println("Copied files:")
        copied.forEach { file ->
            println("- ${file.relativeTo(rootProject.projectDir).path} (${file.length()} bytes)")
        }
    }
}
