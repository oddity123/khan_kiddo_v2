# 对话分析详情页：知识点行动卡设计

**日期：** 2026-08-01  
**状态：** 待评审  
**范围：** `khan_kiddo_v2` 对话分析详情页「下一步该练什么」能力（字典 + Top 卡 + 回检 + 练 3 句）  
**字典草案：** `scripts/data/v1_dictionary_draft.json`（同份副本：`docs/superpowers/specs/2026-08-01-v1-dictionary-draft.json`，version `v1-draft-2026-08-01-r2`）

---

## 1. 问题与目标

### 1.1 现状痛点

详情页已能展示句子级检查（原句 / AI 建议 / 错误类型 chip），侧栏有综合得分与类型分布。用户能看见「错了什么」，但难以回答：

> 我下一步到底该练哪一条？

现有「知识卡片」仅覆盖中文表达缺口（词汇闪卡），且复习进度不落库。饼图按旧 `ProblemType` 统计，会把互不相干的一次性换词堆成「用词不当」，把同一母语迁移根因拆进多个类目。

### 1.2 成功标准

一次看完详情页后，用户能在 10 秒内得到：

1. **本场 Top 1–3 个可练焦点**（人话规则 + 自己的原句证据）  
2. **一个即时行动**（练 3 句，约 40 秒）  
3. **从第二次分析起**：上次盯着的点有没有改掉（回检）

非目标（v1 不做）：完整 SRS、跨用户公共语法知识库、文章生成、回填全部历史 `point_id`。

---

## 2. 核心概念与层级

```
家族 Family（归因 / Top 排序）
  └── 叶子 pointId（可练规则 / 一张行动卡）
         └── 单条错误（原句 + error_point 证据）
```

| 层 | 标识 | 职责 |
|---|---|---|
| 家族 | `FAM_*` | 「同一个毛病」；Top 排序单位；饼图着色 |
| 叶子 | `pointId` | Stage2 模型唯一分类目标；卡片标题与练习锚点 |
| 通道 | `rule` / `fluency` / `lexical` / `chinese` | 决定 UI 去向；仅 `rule` 参与行动卡排序 |
| 旧类型 | `ProblemType` | **兼容层**：由 `pointId` 查表得到，写入现有 `problem_types`；不参与模型选择 |

**Stage2 契约变更：** 模型输出从 `type`（ProblemType 枚举）改为 `pointId`（知识点枚举）。`type` / `errorLevel` / `familyId` / `channel` / 卡片文案全部后端查字典推导。

---

## 3. 知识点字典（v1）

### 3.1 来源

由本地 + 线上合并去重语料（2791 条错误）离线挖掘后人工定稿，产物见  
`scripts/data/v1_dictionary_draft.json`。离线挖掘脚本：`scripts/mine_knowledge_points.py`（原始语料与 batch 输出在 `scripts/out/`，已 gitignore）。

### 3.2 家族（12）

**规则家族（9）：**  
`FAM_ARTICLE` · `FAM_NOUN_NUMBER` · `FAM_AGREEMENT` · `FAM_TENSE` · `FAM_VERB_PATTERN` · `FAM_PREPOSITION` · `FAM_WORD_FORM` · `FAM_STRUCTURE` · `FAM_PRONOUN`

**通道家族（3）：**  
`FAM_FLUENCY` · `FAM_LEXICAL` · `FAM_CHINESE`

每家族一个 `{FAMILY}_OTHER` 兜底（词汇/中文通道叶子本身即兜底，不再另加）。

### 3.3 Stage2 枚举规模

约 **28 个 `v1Keep` 叶子 + 10 个家族 OTHER ≈ 38 项** 写入 JSON Schema `enum`。

完全体候补（`v1Keep=false`，不进 Stage2 enum）：如 `PAST_VS_PERFECT`、`COMPARISON_FORM` 等，后续按 OTHER 命中率迭代加入。

### 3.4 关键判据（必须进 Stage2 system prompt）

1. **冠词 vs 单复数：** 数错 → 单复数叶子；数对只错冠词 → 冠词叶子。  
2. **时态 vs 动词句型：** 选错时间 → `FAM_TENSE`；助动词已出现、后面形态错 → `FAM_VERB_PATTERN`。  
3. **词形细叶子优先：** `FEEL_ED_ADJ` / `GERUND_*` 能命中则不用 `WORD_FORM_POS`。  
4. **Structure 溢出：** 说不清规则的整句问题 → `FLUENCY_*` / `LEXICAL_GAP` / `CHINESE_CODE_SWITCH`，禁止硬塞 `STRUCTURE_*`。  
5. **搭配 vs 介词 vs 词汇：** 固定短语 → `COLLOCATION`；单纯 in/on/at → `PREP_FIXED`；一次性换词 → `LEXICAL_GAP`。

### 3.5 字典落地形态

- 运行时：classpath 资源（JSON 或 YAML），启动加载为不可变索引：`pointId → PointDefinition`。  
- 每条至少含：`pointId`、`familyId`、`channel`、`cardPolicy`、`titleZh`、`whyZh`、`fixability`、`errorLevel`、`problemType`。  
- 非法 / 未知 `pointId`：sanitizer 降级到对应家族 `*_OTHER`（若无法解析家族则 `STRUCTURE_OTHER`），并打日志。

---

## 4. Top 行动卡算法

### 4.1 目标函数

> 练哪一个，能一次消掉最多「未来还会再犯」的错——不是哪个标签出现次数最多。

### 4.2 公式（v1）

对每个**规则家族**（仅聚合 `channel=rule` 且 `cardPolicy=normal` 的叶子）：

```
familyScore = Σ severity(error) × family.fixability × recurrenceBoost
```

| 因子 | v1 取值 |
|---|---|
| `severity` | `FATAL=3` · `BASIC=2` · `NATURAL=1.5` · `STYLE=1`（沿用现有 `ErrorLevel`） |
| `fixability` | 字典家族默认值（叶子可覆盖） |
| `recurrenceBoost` | **恒为 1**（历史加权字段预留，本版不启用） |

**准入：** 该家族本场 `cardPolicy=normal` 错误条数 ≥ 2。  
**输出：** 分数最高的最多 **3** 个家族；每张卡正面展示该家族内本场得分最高的叶子规则。  
**`cardPolicy=rare`：** 可累计用于诊断展示，**不进入 Top3 焦点位**（`PREP_FIXED`、`COLLOCATION`、`SENTENCE_LOOSE_AND`）。

### 4.3 卡片内容（零额外 LLM）

全部由字典静态文案 + 本场 `items` 过滤得到：

- 徽章：本次 TOP n · 家族名 · 本场条数  
- `titleZh`（人话规则）  
- 正误对照：取该叶子下一条代表性 `originalSentence` + `suggestion` / `error_point`  
- `whyZh`（母语迁移）  
- 证据列表：最多 5 条，可点击定位句子级检查  
- 同家族其它叶子摘要（「同一个毛病的其它表现」）  
- 操作：练 3 句 · 下次帮我盯着 · 我已经会了  

### 4.4 时间窗

- **排序：仅本场。**  
- `point_id` **现在落库**，用于回检与未来复发加权；不回填 19/74 条旧测试数据。

---

## 5. 行动层（档位 C）

### 5.1 练 3 句

- **触发：** 用户点击按钮后按需调用，**不**并入分析 SSE，不预生成。  
- **模型：** 轻量模型（豆包 Flash / mini 级），超时与失败需友好降级。  
- **输入：** `pointId` + `titleZh` + 本场最多 3 条原句证据。  
- **输出：** 3 道改错或中译英微练习（JSON）；改错题可与标准答案比对，开放题可再调一次轻量评判或先做「自评 / 展示参考答案」。  
- **v1 评判策略：** 优先「改错 + 标准答案比对」；开放造句可先展示参考句，不做强判分（避免拖延上线）。

### 5.2 下次帮我盯着（回检）

新表（示意）：

```sql
user_focus_point (
  id BIGINT PK,
  user_id BIGINT NOT NULL,
  point_id VARCHAR(48) NOT NULL,
  source_analysis_id VARCHAR(64) NOT NULL,
  status VARCHAR(16) NOT NULL,  -- watching | dismissed | mastered
  created_at, updated_at,
  UNIQUE(user_id, point_id)
)
```

- 「下次帮我盯着」→ `watching`  
- 「我已经会了」→ `mastered`（不再出现在回检条；同一 `pointId` 再次「盯着」可覆盖回 `watching`）  
- 「略过 / 不想练」若需要第三按钮 → `dismissed`（v1 UI 可不暴露，状态预留）  
- 详情页加载时：取该用户 `watching` 列表，与**本场**按 `point_id` 计数对比，渲染回检条：  
  - 上次有、本次 0 或明显下降 → 改掉了  
  - 上次有、本次仍 ≥1 → 还在犯  
  - 样本不足 → 灰态  

不需要 SRS 调度；产品每次分析即考试。

---

## 6. 流水线与成本

| 环节 | 变更 | 增量耗时 |
|---|---|---|
| Stage1 分离 | 不变 | 0 |
| 中文表达审查 | 不变；与 `CHINESE_CODE_SWITCH` / 词汇卡对齐展示 | 0 |
| Stage2 语法 | Schema/`type`→`pointId`；prompt 加判据 | ≈0（字段替换，非新增输出） |
| Sanitizer | 校验 pointId，非法降级 OTHER | &lt;5ms |
| Top3 / 卡片组装 | 纯函数，分析完成后 | &lt;5ms |
| Stage3 教育总结 | **不变**；不负责生成行动卡文案 | 0 |
| 回检查询 | 详情 GET 时 | &lt;20ms |
| 练 3 句 | 按需 API | 2–4s（仅点击者） |

分析主路径总时长预期仍与现状同量级（约 1 分钟级，取决于所选模型）。

---

## 7. 数据模型

### 7.1 `conversation_analysis_item`

新增列：

```sql
point_id VARCHAR(48) NULL COMMENT '知识点叶子；旧数据可为 NULL'
```

- 新分析：每条错误一行，写入 `point_id`；`problem_types` 继续写英文 `ProblemType` 名（由字典反查）。  
- 旧行：`point_id IS NULL`；不参与回检分子。

### 7.2 详情 API 扩展

`GET /api/conversation/analyses/{id}` 在现有字段上增加（命名可在实现时微调，语义固定）：

```json
{
  "actionCards": [
    {
      "rank": 1,
      "familyId": "FAM_WORD_FORM",
      "familyTitleZh": "词形与词类",
      "pointId": "FEEL_ED_ADJ",
      "titleZh": "...",
      "whyZh": "...",
      "errorCount": 8,
      "score": 12.75,
      "examples": [{ "sentenceId": 1, "originalSentence": "...", "errorPoint": "...", "suggestion": "..." }],
      "siblingPoints": [{ "pointId": "...", "titleZh": "...", "errorCount": 2 }]
    }
  ],
  "focusRecheck": [
    {
      "pointId": "FEEL_ED_ADJ",
      "titleZh": "...",
      "previousCount": 5,
      "currentCount": 1,
      "status": "improved"
    }
  ],
  "fluencyHints": [{ "pointId": "FLUENCY_INCOMPLETE", "count": 5, "titleZh": "..." }],
  "familyDistribution": [{ "familyId": "...", "titleZh": "...", "count": 12 }]
}
```

`items[].errors[]` 增加 `pointId`（及可选 `familyId`），保留 `type` / `errorLevel` / `point` 以兼容现有句子卡。

### 7.3 新 API

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/conversation/focus-points` | body: `{ pointId, analysisId, action: watch\|master\|dismiss }` |
| POST | `/api/conversation/practice` | body: `{ pointId, analysisId }` → 3 道题 JSON |

均需登录；`pointId` / `analysisId` 校验归属当前用户。

---

## 8. 详情页信息架构

桌面端保持主栏 + 右侧概要；主栏顺序：

1. 顶栏（返回 / 标题 / 删除）— 保留  
2. **本次结论条**（一句：句数 · 可优化点数 · 主要矛盾家族）— 改造  
3. **该练什么 · Top 3 行动卡** — **新增，页面重心**  
4. **回检条**（第二次分析起有 watching 时显示）— 新增  
5. **词汇卡**（现有中文表达闪卡 + 本场 `LEXICAL_GAP` 可后续合并；v1 至少保留现有中文表达卡，并展示 `CHINESE_CODE_SWITCH` 入口或合并说明）— 改造  
6. **流利度提示**（`FLUENCY_*`）— 新增  
7. **句子级检查** — **降级为证据层**：默认折叠或仅展开含 FATAL；顶部品类 chip 按 `pointId`/家族筛选；从行动卡「查看原句」深链定位  
8. 原始对话 — 保留折叠  

侧栏：

- 综合自然度 / 分项得分 — 保留  
- 类型分布 — **改为按家族** `familyDistribution` 着色（避免与行动卡口径打架）  
- AI 总结 / 元信息 — 保留  

移动端：行动卡单列；侧栏概要置于行动卡之后或手风琴。

---

## 9. 前端要点

- 新组件建议：`ActionCardsPanel.vue`、`FocusRecheckBar.vue`、`FluencyHints.vue`；练习可用 Dialog/Drawer。  
- 类型扩展：`frontend/src/types/conversation.ts`。  
- API：`frontend/src/api/conversationAnalysis.ts`。  
- 排序/标签工具可扩展 `analysisDisplay.ts`；**Top 分由后端算好下发**，前端不重算家族分。  
- 样式：沿用 `--kk-*` 与现有详情页玻璃面板，不引入新设计体系。

---

## 10. 后端要点

| 模块 | 职责 |
|---|---|
| 字典加载器 | 读资源文件，建索引，校验 OTHER 齐全 |
| Schema / Prompt | `conversation-analysis-schema.json` enum；system prompt 嵌入判据与精简叶子说明 |
| `GrammarAnalysisSanitizer` | pointId 合法性与降级 |
| `ActionCardScorer`（纯函数） | 本场 items → `actionCards` |
| Focus 仓储 | `user_focus_point` CRUD |
| Practice 服务 | 按需 LLM，独立超时 |
| 详情组装 | 填充 `actionCards` / `focusRecheck` / `fluencyHints` / `familyDistribution` |

持久化仍走现有 `analyzeAndPersist`；`point_id` 写入 `buildDbItems()`。

---

## 11. 测试计划（验收）

1. **字典单元测试：** 每个 `v1Keep` pointId 可解析出 family/problemType/errorLevel；未知 id 降级 OTHER。  
2. **打分单元测试：** 用固定 fixtures（含 rare 叶子、单条不足准入、FATAL 加权）断言 Top 顺序与张数。  
3. **Sanitizer：** 非法 pointId → OTHER；细叶子优先判据用样例 prompt 抽检（可人工 + 少量集成）。  
4. **API：** 关注点写入后，同用户下一次详情 `focusRecheck` 状态正确；越权 analysisId 拒绝。  
5. **回归：** 无 `point_id` 的旧详情页不报错；句子卡仍可读 `type`/`errorLevel`。  
6. **手动：** 用分析 `045d6688` 同类语料重跑，确认 Top 卡不是「用词不当」而是可练叶子，且饼图家族口径与卡一致。

---

## 12. 风险与缓解

| 风险 | 缓解 |
|---|---|
| enum 从 ~19→~38，分类准确率下降 | 判据进 prompt；OTHER 兜底；sanitizer；上线后看 OTHER 命中率再拆叶子 |
| 冠词/单复数易混 | 硬性 discriminator #1 |
| rare 叶子占满 Top | 打分器强制过滤 `cardPolicy=rare` |
| 练 3 句延迟/失败 | 按需调用 + 超时文案 + 可重试；v1 不做强判开放题 |
| 字典文案质量 | 人工维护 JSON；改文案不改代码 |

---

## 13. 明确不在 v1

- 历史复发加权进排序（列已有，逻辑关闭）  
- 回填旧分析 `point_id`  
- 完整 SRS / 错题本产品化  
- Stage3 生成卡片文案  
- 分析时预生成练习题  
- 比较级、past vs perfect 等完全体叶子进 enum  

---

## 14. 决策记录（已确认）

| 决策 | 结论 |
|---|---|
| 卡片粒度 | 知识点叶子（方案 B） |
| 时间窗 | 本场排序；point_id 预留历史 |
| 行动档位 | C：讲清 + 练 3 句 + 回检 |
| 卡片是否进 Stage3 | 否，字典静态 + Stage2 聚合 |
| 字典来源 | 全历史语料挖掘 + 人工定稿（r2） |
| 旧 ProblemType | 查表兼容，非模型输出 |

---

## 15. 实现分期建议

**P0（可独立上线）：** 字典 + Stage2 pointId + 落库 + 详情 `actionCards` / `familyDistribution` + 句子卡降级与深链 + 侧栏饼图改家族。  
**P1：** `user_focus_point` + 回检条。  
**P2：** 练 3 句 API + UI；词汇卡与 `LEXICAL_GAP` 更深合并。

P0 即可解决「看不见下一步」的主痛点；P1/P2 闭合学习闭环。
