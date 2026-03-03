# Milestone 3 Validation (In Progress)

更新时间：2026-03-02（晚间实机回归已更新）

## 1. 验证范围

本文件用于记录 Milestone 3 第一阶段（可观测 + 策略降级 V1）的真机验收证据。

当前覆盖：
- 会话级指标采集（TTFT/tok/s/heap/tier）
- 调试日志结构化输出（`M3-METRICS`、`M3-POLICY`）
- 自动降级策略 V1（按慢会话/失败调整 level）
- 热状态信号接入策略（thermal status）
- 交付 UI 与调试 UI 分离（同一业务内核）

## 2. 验证环境

- 设备：MediaTek Dimensity 8200，12GB RAM
- 序列号：`5XA6USSGWWPRJFQW`
- 应用包名：`com.edgeaivoice`
- 构建命令：`./gradlew :app:assembleDebug`
- 安装命令：`adb -s 5XA6USSGWWPRJFQW install -r app/build/outputs/apk/debug/app-debug.apk`

## 3. 关键日志证据

会话链路（状态机）：
- `event=AsrSuccess(...) => state=Thinking`
- `event=LlmFirstToken => state=Streaming`
- `event=LlmDone(totalTokens=96) => state=Done`

指标日志（结构化）：
- `I/M3-METRICS: session=1 source=m2_demo tier=A_MAINSTREAM asrMs=-1 ttftE2EMs=19029 ttftNativeMs=19024 llmTotalMs=49199 tokens=96 tokPerSec=1.95 javaHeapMb=4 nativeHeapMb=1687`

策略日志（结构化）：
- `I/M3-POLICY: session=1 source=m2_demo tier=A_MAINSTREAM level=0 reason=normal maxNew=96 topP=0.9 temp=0.2`

稳定性（本轮）：
- 未见 `FATAL EXCEPTION`
- 未见 `ClassNotFoundException`

30 轮回归摘要（`bash scripts/m3_regression.sh 30 com.edgeaivoice 5XA6USSGWWPRJFQW`）：
- `success=21 failed=9 loops=30`
- `metrics_count=21 avg_ttft_e2e_ms=22633 avg_tok_per_sec=1.53`
- 运行期间 `dumpsys thermalservice` 为 `Thermal Status: 2 (MODERATE)`

## 4. 当前判定

已完成（M3 第一阶段）：
- 指标埋点与导出到 logcat
- A/B 档策略配置化（policy profile）
- 自动降级逻辑接入推理入口
- 失败恢复动作提示（策略 action hint）
- 调试 UI 能展示策略状态与动作提示
- 自动化回归脚本（可跑 N 次并汇总）
- 实机 30 轮长跑已可执行并产出统计

待完成（M3 后续）：
- 30 次连续回归稳定性达标（当前 21/30，需定位 9 次失败是超时还是链路中断）
- 统一错误码到可对外错误域映射
- 降级策略与模型路由联动（7B -> 3B）
- CPU 频率信号接入降级触发
- UI 就绪判定增强（降低坐标点击和固定等待对回归结果的影响）

## 5. 回归脚本

脚本位置：
- `scripts/m3_regression.sh`

示例命令：
- `bash scripts/m3_regression.sh 30 com.edgeaivoice 5XA6USSGWWPRJFQW`

输出摘要包含：
- `success/failed/loops`
- `failed_timeout/failed_llm`
- `avg_ttft_e2e_ms`
- `avg_tok_per_sec`
