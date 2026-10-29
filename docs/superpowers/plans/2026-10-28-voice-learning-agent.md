# Voice Learning Agent Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Status（2026-10-29）：** Task 1–3 与口语页去拥挤已完成并合入分支 `feature/voice-learning-agent`。Task 4（语音制卡）按计划后置，本轮关闭跟踪。

**Goal:** 把口语陪练从「纯通话 demo」做成「勾选成长卡/分析材料 → 豆包全双工练习 → 后端工具更新进度/笔记/新卡 → 右侧学习面板实时反映」。

**Architecture:** 沿用现有浏览器 ↔ `VoiceRealtimeProxyHandler` ↔ openspeech 全双工链路。工具在**后端**执行：代理拦截上游 `response.function_call_arguments.done`，委托 `VoiceRealtimeToolGateway`（按 tool name 分发到 Step/Handler），回传 `conversation.item.create`（`type=message`），并向浏览器推送 `proxy.learning.*` 业务事件。材料范围在 `voice_practice_session` 快照里，工具只读写本场允许集合。UI 为 Editorial Calm 双栏（左通话/字幕，右学习旁注），不继续加厚 `VoicePracticeView` / 代理上帝类。

**Tech Stack:** Java 21 + Spring Boot 3 + MyBatis-Plus + Vue 3 + 豆包 Seeduplex FC；设计遵循根目录 `DESIGN.md` / `PRODUCT.md` 与 `frontend/AGENTS.md`。

## Global Constraints

- 架构守门 R1–R7：新职责旁路到 Support/Gateway/composable；代理只委托；一次 PR 只加深一个 seam。
- 空判断用 `CollectionUtils` / `StringUtils` / `ObjectUtils`；登录用户用 `SecurityUtils`。
- 豆包 FC 回传必须含 `items[].type=message`；工具 JSON Schema 保持简单（避免 `additionalProperties:false` 卡住）。
- 首版材料 = 成长卡 + 分析材料；不建独立笔记本；进度保守映射现有 `unfamiliar/fuzzy/mastered`。
- 前端不硬编码 openspeech；非敏感配置走 `/api/voice/realtime/config`。
- 视觉：`--kk-*` token + `.kk-glass*`；禁止换紫 SaaS / Inter。

---

## File map（按职责）

| 单元 | 职责 |
|------|------|
| `voice/VoiceRealtimeFunctionCallSupport` | 解析 FC 下行、拼装 tool result 上行（纯函数） |
| `voice/VoiceRealtimeToolGateway` | 按 name 分发；会话上下文（userId、practiceSessionId、材料快照） |
| `voice/tool/*ToolHandler` | 单个工具（ping → materials → focus → attempt → note/card） |
| `VoiceRealtimeProxyHandler` | 仅：上游帧若为 FC → 委托 Gateway；仍转发原文给客户端（含 FC） |
| `voice_practice_session` + DDL 扩展 | `material_snapshot` JSON、可选练习笔记表 |
| `VoicePracticeSessionService` | begin 接受材料 ID、校验归属、落快照 |
| `frontend/utils/voiceRealtimeFunctionCall.ts` | 保留解析；**去掉**客户端执行业务工具（改由后端） |
| `frontend/composables/useVoiceLearningPanel.ts` | 订阅 `proxy.learning.*`，编排侧栏状态 |
| `frontend/components/practice/VoiceLearningPanel.vue` | 右侧面板 UI |
| `VoicePracticeView.vue` | 薄编排：双栏布局 + 挂载面板；移除 FC 尖峰调试块 |

---

## Task 1: 后端 FC 解析 + ping 工具闭环（第一刀）

**Goal:** 代理侧能拦截 FC、执行 `ping_practice_lab`、回传上游，并推 `proxy.learning.tool` 给前端；前端不再本地执行假工具。

### Steps

- [x] 1.1 写失败测试：`VoiceRealtimeFunctionCallSupportTest` — 解析 `response.function_call_arguments.done`；`buildToolResultPayload(callId, json)` 含 `type=message`
- [x] 1.2 实现 `VoiceRealtimeFunctionCallSupport`
- [x] 1.3 写失败测试：`VoiceRealtimeToolGatewayTest` — `ping_practice_lab` 返回 ok JSON；未知工具返回 error JSON 且 handled
- [x] 1.4 实现 Gateway + `PingPracticeLabToolHandler`
- [x] 1.5 改 `VoiceRealtimeProxyHandler.forwardToClient`：检测 FC → 执行 → `upstream.sendText(toolResult)`；仍把原 FC 帧转给客户端；另发 `proxy.learning.tool`
- [x] 1.6 前端：去掉本地 tool 回传；听 `proxy.learning.tool` 记日志；仍由前端 `session.create` 声明 tools
- [x] 1.7 人工说「测试工具」验收（需重启后端后通话）

**Done when:** 真人通话仍出现 received→replied（来自 `proxy.learning.tool`），且回传不依赖前端 `conversation.item.create`。

---

## Task 2: 本场材料快照

**Goal:** begin 时可提交成长卡 ID + 分析相关 ID；落库；工具可读范围受快照约束。

- [x] 2.1 DDL：`voice_practice_session.material_snapshot` JSON NULL（注释 IF NOT EXISTS 升级说明）
- [x] 2.2 `VoicePracticeBeginRequest` + Service 校验归属（只允许当前用户的卡/分析）
- [x] 2.3 API + 前端选材步进（`VoiceMaterialPicker`）
- [x] 2.4 工具 `get_practice_materials` 只返回快照内材料

**Done when:** 未勾选则空练习或拒绝越界；勾选后工具只能看到所选。

---

## Task 3: 学习工具 + 右侧面板

**Goal:** `focus_practice_card` / `record_practice_attempt` / `append_practice_note`（及保守进度写入）；右侧面板订阅事件。

- [x] 3.1 Handlers + 证据表或 JSON 回合记录（最小可查）
- [x] 3.2 `proxy.learning.focus` / `proxy.learning.attempt` / `proxy.learning.note` 事件契约
- [x] 3.3 `useVoiceLearningPanel` + `VoiceLearningPanel.vue`（DESIGN.md 玻璃面板）
- [x] 3.4 Voice 页桌面双栏 / 移动 Tab 或 sheet
- [x] 3.5 移除尖峰调试 UI；instructions 改为教练+材料驱动（yml Properties）

**Done when:** 通话中右侧能看到焦点卡与本场证据，刷新后仍在。

---

## Task 4: 语音来源制卡（可后置同一 PR 序列）— 本轮关闭

**Goal:** `create_learning_card` 不伪造 analysisId；扩展 sourceRef / 表约束。

- [x] 4.1 source 形态设计 + DDL/UK（后置，未实现）
- [x] 4.2 Handler + 去重（后置，未实现）
- [x] 4.3 面板「新增」区展示（后置，未实现）

**Done when:** 对话中可出现可删、可追溯原话的新卡。（另开任务再做）

---

## Out of scope（本计划不做）

- 独立笔记本实体、发音评测、换语音供应商、LiveKit 流水线、掌握度百分制仪表盘。
