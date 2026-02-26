# 端侧语音 AI 关键模块设计与待补定义（v0.1）

更新时间：2026-02-26
适用范围：EdgeAiVoice v1（离线优先）

## 1. 目标与边界

本设计用于把当前“可运行骨架”推进到“可实现 PRD v1 核心链路”的工程状态。

v1 in-scope：
- Push-to-Talk 录音
- ASR（Whisper）转写
- Prompt 构建
- LLM（llama）流式输出
- 状态机可观测
- 性能指标采集
- 本地模型加载与校验

v1 out-of-scope：
- TTS
- 自动 VAD
- 多语言
- 复杂工具调用

## 2. 端到端调用链

```text
UI (Compose)
  -> VoiceSessionViewModel
  -> VoicePipelineCoordinator (状态机)
  -> AudioCaptureEngine (PCM16 mono 16k)
  -> AsrEngine (JNI -> whisper)
  -> PromptBuilder
  -> LlmEngine (JNI -> llama, streaming callback)
  -> UI state reducer (token 增量渲染)
  -> MetricsRecorder / EventLogger
```

关键线程模型：
- UI 线程：状态展示、交互事件分发
- IO 线程：模型路径校验、文件读写
- Audio 线程：录音采集（高优先级）
- Inference 线程：ASR/LLM 推理

## 3. 状态机定义（必须实现）

状态：
- `Idle`
- `Recording`
- `Transcribing`
- `Thinking`
- `Streaming`
- `Done`
- `Error`

事件：
- `PressToTalk`
- `ReleaseToStop`
- `AudioReady`
- `AsrSuccess(text)`
- `AsrFailed(error)`
- `LlmFirstToken`
- `LlmToken(token)`
- `LlmDone(stats)`
- `LlmFailed(error)`
- `Reset`

状态迁移：
- `Idle --PressToTalk--> Recording`
- `Recording --ReleaseToStop--> Transcribing`
- `Transcribing --AsrSuccess--> Thinking`
- `Thinking --LlmFirstToken--> Streaming`
- `Streaming --LlmDone--> Done`
- 任意状态 `--*Failed--> Error`
- `Done/Error --Reset--> Idle`

超时建议：
- `Recording`: 最大 60s（可配置）
- `Transcribing`: 默认 20s 超时
- `Thinking`: 首 token 超时 10s

## 4. UI 交互设计（v1 最小可用）

主页面组件：
- 顶部：运行状态（当前状态、模型就绪状态）
- 中部：ASR 文本 + LLM 流式回复区域
- 底部：PTT 按钮（按住说话）
- 辅助：错误提示条、性能信息区

交互规则：
- 按下 PTT：进入 `Recording`，按钮高亮 + 录音计时
- 松开：停止录音，自动进入 `Transcribing`
- Streaming 期间实时追加 token
- 错误可重试（回到 `Idle`）

## 5. 音频模块设计

模块名建议：`AudioCaptureEngine`

输入要求：
- `AudioRecord`
- PCM 16-bit
- Mono
- 16kHz

核心接口：
- `startCapture(sessionId): Result<Unit>`
- `stopCapture(): Result<AudioPcmBuffer>`
- `cancelCapture()`

关键定义：
- 帧长：20ms（320 samples @16k）
- 缓冲策略：双缓冲 + ring buffer
- 输出格式：`ShortArray` 或 `ByteArray(PCM16LE)`

异常处理：
- 权限拒绝 -> `Error.PermissionDenied`
- 设备占用 -> `Error.AudioDeviceBusy`
- 空音频 -> `Error.EmptyAudio`

## 6. 模型管理与路径策略

模型来源优先级：
1. `${externalFilesDir}/models/...`（首选，adb push / 后续下载）
2. `${filesDir}/models/...`（内部兜底）

模型清单（manifest）建议字段：
- `name`
- `relativePath`
- `sha256`
- `sizeBytes`
- `version`
- `required` (bool)

校验策略：
- 启动时校验 `exists + size>0`
- 用户触发或调试模式下校验 `sha256`

## 7. JNI API 契约（重点补充）

### 7.1 ASR JNI 契约

Kotlin：
- `fun asrInit(modelPath: String, threads: Int): Int`
- `fun asrTranscribePcm16(audio: ByteArray, sampleRate: Int): AsrResult`
- `fun asrRelease(): Int`

返回码约定：
- `0` 成功
- `-1` 参数错误
- `-2` 模型加载失败
- `-3` 推理失败
- `-4` OOM

`AsrResult`：
- `text: String`
- `elapsedMs: Long`
- `errorCode: Int`
- `errorMessage: String?`

### 7.2 LLM JNI 契约

Kotlin：
- `fun llmInit(modelPath: String, ctxSize: Int, threads: Int): Int`
- `fun llmStart(prompt: String, config: LlmGenConfig, callback: LlmTokenCallback): Int`
- `fun llmCancel(): Int`
- `fun llmRelease(): Int`

回调接口：
- `onToken(token: String)`
- `onFirstToken(latencyMs: Long)`
- `onComplete(stats: LlmStats)`
- `onError(code: Int, message: String)`

线程约束：
- JNI 回调不直接触 UI
- 通过 coroutine channel/flow 切回主线程渲染

### 7.3 Native 生命周期约束

- 同一时刻仅一个活跃 session
- `init` 必须幂等（重复调用可安全返回）
- `release` 必须可重入
- App 退后台时不强制释放模型（由策略控制）

## 8. LLM 封装模块设计

模块名建议：`LlmEngine`

职责：
- 初始化模型
- 执行流式推理
- 输出 token 事件流
- 提供取消与超时控制
- 汇总统计（首 token/总耗时/token 数）

配置对象：`LlmGenConfig`
- `temperature`
- `topP`
- `maxNewTokens`
- `repeatPenalty`
- `stopWords`

## 9. PromptBuilder 设计

v1 模板：
- system（固定中文助手约束）
- history（默认关闭，多轮时保留 N 轮）
- user（ASR 文本）

必须定义：
- 上下文截断策略（按 token）
- 空输入策略（ASR 空文本直接回 Idle + 提示）

## 10. 观测与日志规范

每次会话指标：
- `sessionId`
- `audioDurationMs`
- `asrElapsedMs`
- `llmFirstTokenMs`
- `llmTotalMs`
- `llmTokens`
- `peakRssMb`（可选）
- `avgCpuUsage`（可选）

日志分级：
- `INFO` 状态迁移与关键耗时
- `WARN` 可恢复错误
- `ERROR` 推理失败/模型缺失

隐私要求：
- 默认不落盘原始音频
- 文本日志可配置开关

## 11. 错误与降级策略

错误域：
- `ModelMissing`
- `ModelInvalid`
- `PermissionDenied`
- `AudioReadFailed`
- `AsrRuntimeError`
- `LlmRuntimeError`
- `OutOfMemory`
- `Timeout`

降级顺序：
1. 降低线程数
2. 降低 `maxNewTokens`
3. 降低上下文长度
4. 引导用户关闭后台应用

## 12. 当前工程到目标的差距

已完成（截至 2026-02-26）：
- Android 工程骨架
- so 装载顺序与检查
- 模型外置路径策略（runtime/models + push脚本）
- 录音权限请求流程
- `AudioCaptureEngine`（AudioRecord 16k/mono/PCM16）
- PTT UI 交互与状态机联动
- ASR JNI 调用链打通（stub返回）

未完成：
- 真实 whisper ASR 推理实现
- LLM streaming 与回调接入
- 会话级 metrics 完整采集

## 13. 开发前必须确认的待补定义（建议你确认）

1. ASR 输出格式：纯文本 vs 分段时间戳
2. LLM 回调粒度：token / 子词 / 字符
3. 默认生成参数：`temperature/topP/maxNewTokens`
4. 超时阈值：ASR/首 token/总时长
5. 多轮默认策略：关闭还是开启 N=3
6. 指标持久化：仅 logcat 还是本地 db
7. 错误码规范是否统一跨 JNI
8. `filesDir` fallback 是否保留

## 14. 待补定义决策表（v1 建议）

| 待确认项 | 方案选项 | 优点 | 缺点 | v1建议 |
|---|---|---|---|---|
| ASR 输出格式 | 仅纯文本 | 实现最简单；链路最稳 | 无法做词级高亮/时间轴 | 先用纯文本（推荐） |
| ASR 输出格式 | 分段+时间戳 | 便于后续字幕/回放对齐 | JNI/数据结构更复杂 | v1.1再加 |
| LLM 回调粒度 | token级 | 与 llama 天然匹配；性能可控 | 中文观感可能“跳字” | token级（推荐） |
| LLM 回调粒度 | 字符级 | UI观感细腻 | 需要二次切分，CPU开销更高 | 不建议v1 |
| 默认生成参数 | 保守参数（temp低、maxNewTokens中等） | 稳定、时延可控、可复现 | 回答创造性弱 | 保守参数（推荐） |
| 默认生成参数 | 激进参数（temp高、tokens大） | 回答更灵活 | 时延和波动大 | 仅调试档 |
| 超时阈值 | 严格阈值（ASR/首token/总时长） | 体验可预测；易降级 | 容易误判慢机失败 | 严格+可配置（推荐） |
| 超时阈值 | 宽松阈值 | 慢机更包容 | 用户等待感强 | 不建议默认 |
| 多轮默认策略 | 默认关闭 | 逻辑简单；内存与时延稳 | 对话连贯性弱 | 默认关闭（推荐） |
| 多轮默认策略 | 默认开启N轮 | 体验更自然 | prompt膨胀、首token变慢 | 后续可开关 |
| 指标持久化 | 仅logcat | 开发成本最低 | 不利于长期分析 | v1先logcat（推荐） |
| 指标持久化 | 本地DB | 可统计趋势与回归 | 增加存储与隐私治理成本 | v1.1 |
| 错误码规范 | 统一跨JNI错误码表 | 排障与埋点统一 | 前期设计成本高 | 必须统一（推荐） |
| 错误码规范 | ASR/LLM各自定义 | 初期实现快 | 维护困难，UI处理分裂 | 不建议 |
| filesDir fallback | 保留fallback | 兼容性强，迁移平滑 | 路径分叉增加维护复杂度 | v1保留（推荐） |
| filesDir fallback | 仅externalFilesDir | 语义清晰，部署一致 | 某些设备外部目录异常时风险 | v2可收敛 |

## 15. v1 推荐冻结参数（可直接实现）

### 15.1 ASR 参数（Whisper）

- `sampleRate`: `16000`
- `channels`: `1`（mono）
- `pcmFormat`: `PCM16`
- `threads`: `4`（默认）
- `maxAudioSeconds`: `60`
- `language`: `zh`

### 15.2 LLM 参数（llama）

- `temperature`: `0.2`
- `topP`: `0.9`
- `maxNewTokens`: `256`
- `repeatPenalty`: `1.1`
- `contextSize`: `2048`（默认档）
- `threads`: `4`（默认）
- `stopWords`: `["</s>"]`（后续按模板补充）

### 15.3 超时参数

- `recordingTimeoutMs`: `60000`
- `asrTimeoutMs`: `20000`
- `llmFirstTokenTimeoutMs`: `10000`
- `llmTotalTimeoutMs`: `120000`

### 15.4 多轮与上下文

- `multiTurnEnabled`: `false`（默认关闭）
- `historyTurns`: `0`（开启后建议默认 `3`）
- `maxPromptTokens`: `1024`

## 16. 执行基线（按文档开发）

自本节起，开发默认按以下基线推进，除非显式变更：

1. ASR 纯文本输出
2. LLM token 级回调
3. 保守生成参数
4. 严格且可配置超时
5. 多轮默认关闭
6. 指标先写 logcat
7. JNI 统一错误码
8. 保留 `filesDir` fallback

