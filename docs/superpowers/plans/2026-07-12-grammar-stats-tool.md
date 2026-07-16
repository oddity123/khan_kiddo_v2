# Grammar Stats Tool Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 用 LangChain4j `@Tool` + 最小 `@AiService` 验通「查询当前用户语法错误类型统计」的 Tool Calling 闭环。

**Architecture:** `GrammarErrorStatsTools` 适配现有 `GrammarErrorStatsService`；`GrammarStatsAssistant` 通过 Spring `@AiService(tools=...)` 绑定工具；验通 API 需登录。统计服务与 Qdrant 条件解耦。

**Tech Stack:** Java 21、Spring Boot 3、LangChain4j 1.15.1（`@Tool` / `@AiService`）、MyBatis、JUnit 5。

**Spec:** `docs/superpowers/specs/2026-07-12-grammar-stats-tool-design.md`

---

## File map

| 文件 | 职责 |
|------|------|
| `.../ai/grammar/GrammarErrorStatsTools.java` | `@Tool` 适配层 |
| `.../ai/grammar/GrammarStatsAssistant.java` | 最小 AiService |
| `.../controller/GrammarStatsAssistantController.java` | 验通 HTTP |
| `.../rag/grammar/GrammarErrorStatsService.java` | 去掉 RAG condition |
| `.../ai/grammar/GrammarErrorStatsToolsTest.java` | Tool 单测 |

---

### Task 1: StatsService 与 Qdrant 解耦

**Files:**
- Modify: `backend/src/main/java/com/khankiddo/learning/rag/grammar/GrammarErrorStatsService.java`

- [ ] **Step 1:** 移除 `@Conditional(OnGrammarErrorRagCondition.class)` 及相关 import。
- [ ] **Step 2:** `./mvn.sh -q -Dtest=GrammarErrorHybridRankerTest,GrammarErrorQueryIntentAnalyzerTest test`（烟雾，确认无关破坏；若有 Stats 相关测试一并跑）。

---

### Task 2: GrammarErrorStatsTools + 单测（TDD）

**Files:**
- Create: `backend/src/test/java/com/khankiddo/learning/ai/grammar/GrammarErrorStatsToolsTest.java`
- Create: `backend/src/main/java/com/khankiddo/learning/ai/grammar/GrammarErrorStatsTools.java`

- [ ] **Step 1:** 写失败单测：mock `GrammarErrorStatsService`；用 mock `SecurityContext` 注入 `AuthenticatedUser`；断言 Tool 调用 `buildStatsSummary(userId, filterList)`；未登录抛错；逗号分隔 types 解析。
- [ ] **Step 2:** 实现 `GrammarErrorStatsTools`（`@Component("grammarErrorStatsTools")` + `@Tool`）。
- [ ] **Step 3:** `./mvn.sh -q -Dtest=GrammarErrorStatsToolsTest test`

---

### Task 3: GrammarStatsAssistant + 验通 Controller

**Files:**
- Create: `backend/src/main/java/com/khankiddo/learning/ai/grammar/GrammarStatsAssistant.java`
- Create: `backend/src/main/java/com/khankiddo/learning/controller/GrammarStatsAssistantController.java`

- [ ] **Step 1:** 定义 `@AiService(..., chatModel="openAiChatModel", tools={"grammarErrorStatsTools"})` 接口与 system 提示。
- [ ] **Step 2:** 增加 `GET /api/ai/grammar-stats/chat`（对齐 `AiChatController` 风格，校验 API Key，需登录）。
- [ ] **Step 3:** `./mvn.sh -q compile` 确认装配无编译错误。

---

### Task 4: 回归与收尾

- [ ] **Step 1:** `./mvn.sh -q test`（或至少 Tool + 现有 rag/grammar 单测）。
- [ ] **Step 2:** 若用户要求再 commit；默认不自动提交。

---

## 手工验通（可选）

1. 登录拿到 JWT；库中该用户有分析错句数据。
2. `GET /api/ai/grammar-stats/chat?message=我最常犯什么语法错误`（带 Authorization）。
3. 日志中应出现 tool call；回复应引用统计而非编造。
