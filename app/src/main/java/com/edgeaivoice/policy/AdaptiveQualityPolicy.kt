package com.edgeaivoice.policy

import android.util.Log
import com.edgeaivoice.jni.LlmGenConfig
import com.edgeaivoice.metrics.DeviceTier

data class PolicyDecision(
    val config: LlmGenConfig,
    val degradeLevel: Int,
    val reason: String,
    val actionHint: String?
)

data class AdaptivePolicyConfig(
    val ttftSlowThresholdMs: Long,
    val tokPerSecSlowThreshold: Double,
    val slowToDegradeCount: Int,
    val healthyToRecoverCount: Int,
    val thermalSevereThreshold: Int,
    val levelConfigs: List<LlmGenConfig>
)

object AdaptivePolicyProfiles {
    fun forTier(tier: DeviceTier): AdaptivePolicyConfig {
        return when (tier) {
            DeviceTier.B_FLAGSHIP -> AdaptivePolicyConfig(
                ttftSlowThresholdMs = 1800L,
                tokPerSecSlowThreshold = 6.0,
                slowToDegradeCount = 2,
                healthyToRecoverCount = 3,
                thermalSevereThreshold = 4,
                levelConfigs = listOf(
                    LlmGenConfig(temperature = 0.2f, topP = 0.9f, maxNewTokens = 128, repeatPenalty = 1.1f),
                    LlmGenConfig(temperature = 0.2f, topP = 0.88f, maxNewTokens = 96, repeatPenalty = 1.1f),
                    LlmGenConfig(temperature = 0.15f, topP = 0.85f, maxNewTokens = 72, repeatPenalty = 1.1f)
                )
            )
            DeviceTier.A_MAINSTREAM -> AdaptivePolicyConfig(
                ttftSlowThresholdMs = 2000L,
                tokPerSecSlowThreshold = 4.0,
                slowToDegradeCount = 2,
                healthyToRecoverCount = 3,
                thermalSevereThreshold = 4,
                levelConfigs = listOf(
                    LlmGenConfig(temperature = 0.2f, topP = 0.9f, maxNewTokens = 96, repeatPenalty = 1.1f),
                    LlmGenConfig(temperature = 0.2f, topP = 0.88f, maxNewTokens = 64, repeatPenalty = 1.1f),
                    LlmGenConfig(temperature = 0.15f, topP = 0.85f, maxNewTokens = 48, repeatPenalty = 1.1f)
                )
            )
        }
    }
}

class AdaptiveQualityPolicy {
    private var tier: DeviceTier = DeviceTier.A_MAINSTREAM
    private var profile: AdaptivePolicyConfig = AdaptivePolicyProfiles.forTier(DeviceTier.A_MAINSTREAM)
    private var degradeLevel: Int = 0
    private var consecutiveSlow: Int = 0
    private var consecutiveFailure: Int = 0
    private var healthyStreak: Int = 0
    private var lastActionHint: String? = null
    private var lastThermalStatus: Int = 0

    fun setTier(t: DeviceTier) {
        tier = t
        profile = AdaptivePolicyProfiles.forTier(t)
        degradeLevel = degradeLevel.coerceIn(0, profile.levelConfigs.lastIndex)
    }

    fun currentLevel(): Int = degradeLevel
    fun currentThermalStatus(): Int = lastThermalStatus

    fun decide(): PolicyDecision {
        val config = profile.levelConfigs[degradeLevel]
        val reason = when {
            consecutiveFailure > 0 -> "failure x$consecutiveFailure"
            consecutiveSlow > 0 -> "slow x$consecutiveSlow"
            else -> "normal"
        }
        return PolicyDecision(
            config = config,
            degradeLevel = degradeLevel,
            reason = reason,
            actionHint = lastActionHint
        )
    }

    fun onSuccess(ttftE2EMs: Long, tokPerSec: Double) {
        val isSlow = ttftE2EMs > profile.ttftSlowThresholdMs || tokPerSec < profile.tokPerSecSlowThreshold
        if (isSlow) {
            consecutiveSlow += 1
            healthyStreak = 0
        } else {
            healthyStreak += 1
            consecutiveSlow = 0
            consecutiveFailure = 0
        }

        val maxLevel = profile.levelConfigs.lastIndex
        if (consecutiveSlow >= profile.slowToDegradeCount && degradeLevel < maxLevel) {
            degradeLevel += 1
            consecutiveSlow = 0
            lastActionHint = "检测到慢会话，已自动降级到 level=$degradeLevel。"
            Log.w("M3-POLICY", "degrade up -> level=$degradeLevel by slow sessions")
        } else if (healthyStreak >= profile.healthyToRecoverCount && degradeLevel > 0) {
            degradeLevel -= 1
            healthyStreak = 0
            lastActionHint = "连续稳定后已恢复到 level=$degradeLevel。"
            Log.i("M3-POLICY", "degrade down -> level=$degradeLevel by healthy sessions")
        } else if (!isSlow) {
            lastActionHint = null
        }
    }

    fun onFailure(code: Int) {
        consecutiveFailure += 1
        healthyStreak = 0
        val maxLevel = profile.levelConfigs.lastIndex
        if (degradeLevel < maxLevel) {
            degradeLevel += 1
            lastActionHint = "发生推理失败，已自动降级到 level=$degradeLevel。建议重试；若仍失败可先重置会话。"
            Log.w("M3-POLICY", "degrade up -> level=$degradeLevel by failure code=$code")
        } else {
            lastActionHint = "当前已在最低质量档。建议重试或点击重置清空会话。"
            Log.w("M3-POLICY", "failure at lowest level code=$code")
        }
    }

    fun onThermalStatus(status: Int) {
        lastThermalStatus = status
        val maxLevel = profile.levelConfigs.lastIndex
        if (status >= profile.thermalSevereThreshold && degradeLevel < maxLevel) {
            degradeLevel = maxLevel
            lastActionHint = "设备温度较高，已切到最低质量档以降低负载。"
            Log.w("M3-POLICY", "thermal degrade -> level=$degradeLevel thermalStatus=$status")
        }
    }
}
