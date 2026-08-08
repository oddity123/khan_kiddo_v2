# Stage2 黄金集（Golden Set）— 归档

**日期：** 2026-08-08（归档 2026-08-09）  
**状态：** 本期告一段落；运行时资产保留，后续可作**模型对比方法论**复用。  
**范围：** Stage2 抽取层准确性评测（跳过 Stage1）

## 落地物（保留、可继续跑）

| 路径 | 作用 |
|------|------|
| `backend/src/test/resources/eval/golden/` | 冻结主集 3 case + 使用说明 |
| `GoldenStatistics` / `GoldenStatisticsTest` | 比对内核（随 `mvn test`） |
| `ConversationGoldenHarness` | `-Dgolden=true` 真实 LLM 报告 |
| `ConversationAnalysisPipeline.analyzeEnglishUtterances` | Stage2-only 入口 |

设计 / 计划 / 取数手册见本目录：

- [`2026-08-08-design.md`](./2026-08-08-design.md)
- [`2026-08-08-plan.md`](./2026-08-08-plan.md)
- [`2026-08-08-data-prep-todo.md`](./2026-08-08-data-prep-todo.md)

## 本期结论（模型对比）

同一契约、主集 3 case、多轮复跑：

- **综合最好：`qwen3.7-plus`**（句级稳、clear 叶子较稳；相对 `glm-5.2` / `qwen3.8-max` 更均衡）
- **换模型对效果的提升 > 继续抠叶子 pointId / 再瘦 prompt**
- 叶子近邻漂移（`PREP_FIXED`↔`PREPOSITION_OTHER` 等）仍存在，更适合后续用状态分层 / 根因聚合消化，不宜当主 KPI
- `STRUCTURE_OTHER` 在本迷你集上几乎不出现，**不足以**验收 prompt-slim 的 STRUCTURE 过滥目标

## 已知局限（复用时注意）

1. **原文精确对齐**：Stage2 拆句/截断 `originalSentence` → unaligned，会把真命中记成 FN、把过标藏出 FP。
2. **主集很小**：适合相对比较模型/回归冒烟，不适合单独证明线上分布。
3. **leafAcc 观察用**：产品决策优先看句级 P/R（误报漏报）与 familyAcc。

## 怎么再跑

见 `backend/src/test/resources/eval/golden/README.md`。
