package com.edgeaivoice.metrics

import android.app.ActivityManager
import android.content.Context
import android.os.Debug
import android.util.Log
import kotlin.math.roundToInt

object SessionMetricsRecorder {
    private const val TAG = "M3-METRICS"

    fun detectTier(context: Context): DeviceTier {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            ?: return DeviceTier.A_MAINSTREAM
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        val totalGb = info.totalMem.toDouble() / (1024.0 * 1024.0 * 1024.0)
        return if (totalGb >= 11.5) DeviceTier.B_FLAGSHIP else DeviceTier.A_MAINSTREAM
    }

    fun sampleHeapsMb(): Pair<Long, Long> {
        val javaBytes = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()
        val nativeBytes = Debug.getNativeHeapAllocatedSize()
        return Pair(javaBytes / (1024 * 1024), nativeBytes / (1024 * 1024))
    }

    fun logSuccess(m: SessionMetrics) {
        Log.i(
            TAG,
            "session=${m.sessionId} source=${m.source} tier=${m.tier} " +
                "thermal=${m.thermalStatus} " +
                "asrMs=${m.asrElapsedMs} ttftE2EMs=${m.llmFirstTokenE2EMs} ttftNativeMs=${m.llmFirstTokenNativeMs} " +
                "llmTotalMs=${m.llmTotalMs} tokens=${m.llmTokens} tokPerSec=${m.llmTokPerSec} " +
                "javaHeapMb=${m.javaHeapMb} nativeHeapMb=${m.nativeHeapMb}"
        )
    }

    fun logFailure(
        sessionId: Long,
        source: String,
        tier: DeviceTier,
        code: Int,
        message: String
    ) {
        val (javaHeapMb, nativeHeapMb) = sampleHeapsMb()
        Log.w(
            TAG,
            "session=$sessionId source=$source tier=$tier failCode=$code failMsg=${message.replace('\n', ' ')} " +
                "javaHeapMb=$javaHeapMb nativeHeapMb=$nativeHeapMb"
        )
    }

    fun calcTokPerSec(tokens: Int, totalMs: Long): Double {
        if (tokens <= 0 || totalMs <= 0L) return 0.0
        val raw = tokens.toDouble() * 1000.0 / totalMs.toDouble()
        return (raw * 100.0).roundToInt() / 100.0
    }
}
