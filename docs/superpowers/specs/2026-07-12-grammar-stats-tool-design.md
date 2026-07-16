# 语法复盘 Tools + RAG 设计（进行中）

日期：2026-07-12

## 分工

| 能力 | 形态 |
|------|------|
| 统计 / 样例 / 概览 | `@Tool`（`grammarLearningDbTools`） |
| 相似错句语义检索 | `DefaultRetrievalAugmentor`（`grammarErrorRetrievalAugmentor`） |

已移除：`search_similar_grammar_errors` Tool（与 Augmentor 重复）。

## DB Tools

| Tool | 用途 | 参数 |
|------|------|------|
| `get_grammar_error_stats` | 错误类型分布 | `problemTypes?`, `days?` |
| `list_grammar_error_examples` | 错句样例 | `problemTypes?`, `days?`, `limit?` |
| `get_grammar_practice_overview` | 练习概览 | `days?` |

## 语义 RAG

- Bean：`grammarErrorSemanticContentRetriever`（需 Qdrant + QWEN）
- Bean：`grammarErrorRetrievalAugmentor`（始终存在；无 Qdrant 时透传）
- AiService：`GrammarStatsAssistant` 挂 `retrievalAugmentor` + `@MemoryId` 传 userId
- 多轮记忆：`grammarStatsChatMemoryProvider`，基于 `MessageWindowChatMemory`，按 `@MemoryId userId` 隔离，窗口 20 条消息（进程内内存）

## 验通

`GET /api/ai/grammar-stats/chat`（需登录）

## 后续

1. Typed ContentRetriever / QueryRouter（减少纯统计问题的无效向量检索）
2. QueryTransformer、ContentAggregator
3. 主 Agent 复用同一套 Tools + Augmentor
