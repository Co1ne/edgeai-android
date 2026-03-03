# EdgeAiVoice v1 执行计划（Implementation Plan）

更新时间：2026-03-02  
目标：在 Android 端交付可商业化推进的离线语音助手 v1（主打 8GB 机型可用，12GB+ 增强）。

## 0. 当前状态（实际）

- Milestone 0：已完成（契约/状态机/模型清单基线）
- Milestone 1：已完成（PTT + ASR 真链路）
- Milestone 2：已完成（LLM streaming 真链路 + 真机验收）
- 当前阶段：Milestone 3 收敛中（稳定性、可观测、降级策略）

## 1. 商业级硬约束（v1 方案门槛）

设备覆盖分层：
- A 档（主力覆盖）：8GB RAM，骁龙 7+ / 8+ Gen1，天玑 8200–8400
- B 档（旗舰增强）：12–16GB RAM，骁龙 8 Gen2/Gen3+，天玑 9300/9400

体验 KPI（验收以 A 档优先）：
- 冷启动（含模型加载到可生成）：<= 2.5s（目标）
- 首 token（TTFT）：A 档 <= 1.2s，B 档 <= 1.0s（目标）
- 续写速度：A 档 >= 6 tok/s，B 档 >= 10 tok/s（目标）
- 峰值内存：A 档 <= 3.5GB（硬约束）
- 连续对话 3–5 分钟：无明显降频导致体验崩塌（硬约束）

说明：
- 若目标与设备现实能力冲突，优先保证稳定与可恢复，再做速度优化。

## 2. 里程碑定义（重构）

### Milestone 0（已完成）— 契约与基线冻结（P0）

交付：
- JNI API 与错误码基线
- 状态机与事件流定义
- 模型 manifest 基线

验收：
- 文档评审通过，接口签名稳定

### Milestone 1（已完成）— 语音输入与 ASR 主链路（P0）

交付：
- PTT 录音链路（AudioRecord）
- JNI ASR init/transcribe/release
- UI 状态流转可见

验收：
- `Idle -> Recording -> Transcribing -> Thinking` 稳定
- 真机可用、可重复

### Milestone 2（已完成）— LLM 流式输出主链路（P0）

交付：
- JNI LLM init/start/cancel/release
- Token streaming 回调 UI
- PromptBuilder v1（单轮）

验收：
- `Thinking -> Streaming -> Done` 完整
- 首 token 与总耗时可观测
- 已通过真机验收（2026-03-02，Dimensity 8200 12GB）

### Milestone 3（进行中）— 稳定性与可观测（P1）

目标：
- 把“能跑”升级为“可维护、可诊断、可降级”

交付：
- 统一会话指标：TTFT/tok/s/峰值内存/错误码/机型档位
- 崩溃与 OOM 归因日志（native + Kotlin）
- 降级策略 V1：线程、context、maxNewTokens、模型路由
- A/B 档配置文件（默认参数分层）

验收：
- 30 次连续回归无崩溃
- 所有失败场景可恢复回 `Idle`
- 关键指标可导出并可复盘

当前状态（2026-03-02）：
- 已完成 30 轮实机长跑，结果 `success=21 failed=9`
- 已完成指标导出（TTFT/tok/s/heap/tier/thermal）
- 未达“稳定通过”目标，需先收敛失败分类与超时策略

### Milestone 4（待开始）— 性能达标与分档策略收敛（P1）

目标：
- 达到 A 档“可卖”的体验下限，并给 B 档增强路径

交付：
- 模型策略：A 档默认 3B Q4；B 档可选 7B Q4
- 路由策略：设备能力 + 实时负载（温度/内存/速度）触发切换
- 性能压测脚本与报表模板

验收：
- A 档满足主要 KPI（TTFT/tok/s/内存）
- B 档在可控发热下提供更高质量输出

### Milestone 5（待开始）— 发布候选与商业化准备（P2）

交付：
- 运行脚本/runbook/故障手册
- 关键工具能力（3–5 个）接入编排层
- 线上/离线策略开关（默认离线）

验收：
- 新设备按 runbook 可部署
- 关键故障可定位、可回退、可恢复

## 3. 模型与机型策略（v1 冻结）

LLM：
- A 档默认：Qwen2.5-3B-Instruct（GGUF，Q4_K_M）
- B 档增强：Qwen2.5-7B-Instruct（GGUF，Q4_K_M，可开关）

ASR：
- A 档：whisper-base 或 whisper-small（二选一，按延迟定）
- B 档：whisper-small 优先

约束：
- 不做“三档全兼容”并行维护
- V1 不把 Vulkan 作为主路径，仅保留实验开关

## 4. 风险与对策（聚焦商业化）

1. 8GB 机型内存/发热超限  
- 对策：默认 3B、限制 context/max tokens、动态降级

2. JNI/Native 崩溃影响口碑  
- 对策：错误码统一、Crash 归因、最小复现脚本

3. 设备碎片化导致体验波动  
- 对策：A/B 分档配置 + 路由 + 降级策略

4. 指标不可观测导致优化盲飞  
- 对策：会话级 metrics 标准化落盘（先 logcat + 导出）

## 5. DoD（Definition of Done）

功能 DoD：
- PTT 录音稳定
- ASR 可用
- LLM 流式输出可用
- 状态机全链路可见

质量 DoD：
- 30 次连续回归稳定
- 错误可恢复
- 关键指标可导出

商业 DoD（v1）：
- A 档可用体验达标（TTFT/tok/s/峰值内存）
- B 档增强可控（不以牺牲稳定性为代价）

## 6. 下一步（从今天开始）

1. 收敛 M3 长跑失败原因：基于 `failed_timeout/failed_llm` 分类修正回归参数与触发链路  
2. 补齐 M3 验收闭环：达成 30 次连续回归稳定通过并沉淀报告模板  
3. 对齐错误域：将 native/Kotlin 错误码映射为统一对外错误码（便于排障与产品提示）  
4. 进入 M4 性能优化：围绕 A 档 KPI（TTFT/tok/s/内存）做参数和模型策略收敛
