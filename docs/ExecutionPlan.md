# EdgeAiVoice v1 执行计划（Implementation Plan）

更新时间：2026-02-26
目标：完成 PRD v1 的离线语音对话可验收版本。

## 0. 里程碑状态（实际）

- Milestone 0：已完成（2026-02-26）
- Milestone 1：已完成（2026-02-26，ASR为JNI stub打通链路）
- Milestone 2：待开始
## 1. 总体节奏

建议周期：6 周（1 人主开发，含联调与性能调优）

- P0：主链路可跑通（必须）
- P1：稳定性与观测（必须）
- P2：体验优化与可维护性（建议）

## 2. 优先级与里程碑

### Milestone 0（第 1 周）— 基础契约冻结（P0）

交付：
- 冻结 JNI API 契约（ASR/LLM）
- 冻结状态机事件与错误码
- 冻结模型 manifest schema

验收标准：
- 文档评审通过
- Kotlin/Native 接口签名不再频繁变更

风险：
- 契约反复导致后续返工

### Milestone 1（第 2 周）— 录音 + ASR 链路（P0）

交付：
- `AudioCaptureEngine` 实现（PTT start/stop）
- JNI ASR init/transcribe/release 打通
- UI 显示录音与转写结果

验收标准：
- 5s 普通话录音可稳定转写
- `Idle -> Recording -> Transcribing -> Thinking` 状态正确

风险：
- 录音线程阻塞
- 权限流程遗漏

当前结果（2026-02-26）：
- 已实现 `AudioCaptureEngine`（AudioRecord 16k/mono/PCM16）
- 已实现 PTT 交互与录音权限请求流程
- 已打通 Kotlin -> JNI -> Kotlin 的 ASR 调用链（stub）
- UI 已显示状态流转与 ASR 文本

### Milestone 2（第 3 周）— LLM 流式输出（P0）

交付：
- JNI LLM init/start/cancel/release
- token streaming 回调到 UI
- PromptBuilder v1（单轮）

验收标准：
- `Thinking -> Streaming -> Done` 完整
- 首 token 有可观测指标

风险：
- JNI 回调线程与 UI 同步问题
- token 回调频率过高造成卡顿

### Milestone 3（第 4 周）— 端到端稳定性（P1）

交付：
- 错误码与降级策略落地
- 会话级 metrics 采集
- 30 次连续回归脚本（半自动）

验收标准：
- 30 次无崩溃
- 错误可恢复（可回到 Idle）

风险：
- OOM 与设备差异

### Milestone 4（第 5 周）— 性能达标（P1）

交付：
- 线程数/上下文长度调优
- 首 token / 总时延优化
- 指标报表模板

验收标准（旗舰机）：
- ASR ≤ 2s（5秒语音）
- 首 token ≤ 1s
- 总体短答 ≤ 5s（目标）

风险：
- 模型大小与设备热降频

### Milestone 5（第 6 周）— 发布候选（P2）

交付：
- UX 细化（加载提示、错误引导）
- 脚本完善（push/verify/runbook）
- 文档收敛（部署、调试、常见问题）

验收标准：
- 新设备按 runbook 可部署运行
- 关键故障可定位

## 3. 工作分解（WBS）

P0（必须）：
- 状态机实现
- 录音模块实现
- ASR JNI 实现
- LLM JNI + streaming 实现
- PromptBuilder v1
- 关键路径 UI

P1（必须）：
- 指标采集
- 错误码与降级
- 回归测试脚本
- 模型校验（size + sha256）

P2（建议）：
- 多轮上下文开关
- 历史清理
- 性能可视化面板

## 4. 依赖与前置条件

- NDK/CMake 环境稳定
- 目标设备（至少 1 台 Snapdragon 8 Gen1/Gen2）
- 模型文件完整可用（本地校验通过）
- JNI 动态库版本固定

## 5. 风险清单与应对

1. 大模型导致时延不可接受
- 应对：降上下文/降 token/降线程

2. JNI 崩溃难定位
- 应对：错误码 + native 日志 + 最小复现

3. 设备差异导致性能波动
- 应对：分档配置（high/medium/low）

4. 音频链路不稳定
- 应对：统一采样率 + 缓冲策略 + 录音健康检查

## 6. 验收清单（DoD）

功能 DoD：
- PTT 可录音
- ASR 可转写
- LLM 可流式输出
- 状态机全链路可见

质量 DoD：
- 30 次稳定运行
- 指标可导出
- 错误可恢复

工程 DoD：
- README/runbook 完整
- 脚本可一键 push/verify
- 关键模块有单元或集成测试（至少 smoke）

## 7. 建议的下一步执行顺序（立即可开工）

1. 冻结 `JNI API + 错误码 + 状态机事件`（半天）
2. 实现 `AudioCaptureEngine + 权限流`（2-3 天）
3. 打通 `ASR JNI`（2-3 天）
4. 打通 `LLM JNI streaming`（3-4 天）
5. 接入 metrics 与回归脚本（2 天）

## 8. 当前版本判定

当前工程可判定为：
- `Ready for implementation`（可进入编码阶段）
- 非 `Ready for acceptance`（距离 PRD 验收仍需实现核心功能）

