# Golden Set 数据准备 TODO（User 操作手册）

日期：2026-08-08（修订：主集移除 asr-noise）  
负责人：User（取数 + 粘贴模型） / Agent（代码与 harness，见 `2026-08-08-plan.md`）  
原则：**你不做任何英语对错判断**；只跑 SQL、导出、粘贴给 GPT/Luna、把返回的 JSON 存盘。

相关文档：

- 设计：`2026-08-08-design.md`
- 实现计划：`2026-08-08-plan.md`
- 工作副本：`data/output/`
- 落盘目录（实现后）：`backend/src/test/resources/eval/golden/cases/`

> **已移除 `asr-noise-01`：** 与 Stage2「IGNORE 重复词/uh/ASR 垃圾」冲突，叶子比对噪声大，不适合作主精度黄金集。主集只保留下面 3 个 case。

---

## 你最终要得到什么

3 个文件夹（工作副本：`data/output/`）：

```
clear-error-01/     ← 来自 SQL-A + 批次 0
confusable-point-01/← 来自 SQL-A + 批次 0
legit-spoken-01/    ← 来自多场对话 + 批次 0-NEG
```

每个文件夹内：`meta.json`、`input.json`、`gold.json`（及可选 `prompt-gold.md`）。

---

## 总流程（按顺序勾）

**线 1 — 明确错误 / 易混（只用候选句，不抠对话）**

- [ ] **步骤 1**：跑 SQL-A → 一行一句候选（约 80–120 行）
- [ ] **步骤 2**：Luna **批次 0** → 只产出 `clear-error-01/input.json` + `confusable-point-01/input.json`

**线 2 — 真阴口语（多场对话，专责抠句）**

- [ ] **步骤 3**：跑 SQL-B 列表 → 点选 **多场**对话（`len` 800–5000，按长度选，不看英语）
- [ ] **步骤 4**：导出这几场全文 → Luna **批次 0-NEG** → 只产出 `legit-spoken-01/input.json`

**线 3 — 标注 gold（3 个 case 各一轮）**

- [ ] **步骤 5**：Luna 批次 1–3 → 各 case 的 `meta.json` + `gold.json`
- [ ] **步骤 6**：存盘；交给 harness / 实现计划

> **不要做的事**：自己改 `pointId`、自己判断对错、自己从对话里挑句。  
> JSON 坏了 → 贴回 Luna 重出。

---

## 步骤 1：SQL-A — 候选英文句

### 这份结果干什么用？

只给 **批次 0** 用：从句子池里挑出  
`clear-error` + `confusable_point` 两类的 `input.json`。  
**不**用来补真阴/ASR。

### 去重逻辑

- 同一句（忽略大小写、首尾空格）只留 1 行
- 优先 `user_id <> 1`，否则用 `user_id = 1`
- 不导出线上 `point_id`

### SQL-A

```sql
SELECT original_sentence
FROM (
  SELECT
    TRIM(i.original_sentence) AS original_sentence,
    a.user_id,
    ROW_NUMBER() OVER (
      PARTITION BY LOWER(TRIM(i.original_sentence))
      ORDER BY
        CASE WHEN a.user_id <> 1 THEN 0 ELSE 1 END,
        a.created_at DESC
    ) AS rn
  FROM conversation_analysis_item i
  JOIN conversation_analysis a ON a.analysis_id = i.analysis_id
  WHERE a.status = 'success'
    AND TRIM(i.original_sentence) <> ''
    AND i.original_sentence NOT REGEXP '[一-龥]'
) x
WHERE rn = 1
ORDER BY
  CASE WHEN user_id <> 1 THEN 0 ELSE 1 END,
  CHAR_LENGTH(original_sentence) DESC
LIMIT 120;
```

### 你拿到结果后做什么

1. 导出为纯文本，**一行一句**：

```text
It have a example.
She go yesterday.
```

2. 存成例如 `~/Desktop/golden-candidates.txt`（不要改原文）。
3. 进入步骤 2，整段贴进批次 0 的 `<<<CANDIDATES>>>`。

---

## 步骤 2：Luna 批次 0 — 只做 clear_error + confusable

### 你要做什么

1. 新开对话，粘贴下方提示词 + SQL-A 全文。
2. 得到 JSON 后，只保存 **2** 个文件：

| 存盘 | case id |
|------|---------|
| `clear-error-01/input.json` | `clear-error-01` |
| `confusable-point-01/input.json` | `confusable-point-01` |

只存每个 case 的 `input` 对象（含 `utterances`）。

### 批次 0 提示词

```text
你是评测集构建助手。操作者不是英语老师。请你只做一件事：从候选句里选出两个 case 的 input。

目标：恰好 2 个 case，合计约 12–16 句。
- clear-error-01 / clear_error：明确语法或用词错误（可含 1–2 句看似没错的作对照）
- confusable-point-01 / confusable_point：pointId 易混边界（冠词vs复数、时态vs动词句型、STRUCTURE vs FLUENCY/LEXICAL 等）

规则：
1. 原文必须来自 CANDIDATES；不要改写（最多 trim）。
2. 同一 case 内不得重复；两 case 之间也尽量不重复。
3. 不要产出 legit_spoken（另有 0-NEG 流程）；不要产出 asr_noise（已移出主集）。
4. 只输出一个 JSON，不要解释，不要 markdown 围栏。

输出格式：
{
  "cases": [
    {
      "id": "clear-error-01",
      "category": "clear_error",
      "title": "短标题",
      "input": {
        "utterances": [
          { "id": "u1", "text": "..." }
        ]
      }
    },
    {
      "id": "confusable-point-01",
      "category": "confusable_point",
      "title": "短标题",
      "input": {
        "utterances": [
          { "id": "u1", "text": "..." }
        ]
      }
    }
  ]
}

每个 case 内 utterance id 从 u1 连续编号。

<<<CANDIDATES>>>
（粘贴 SQL-A：一行一句）
<<<END_CANDIDATES>>>
```

### 验收（只查格式）

- [ ] 正好 2 个 case，id 正确
- [ ] 每个 `utterances` 非空，id 为 u1、u2…
- [ ] 不检查英语对不对

---

## 步骤 3：SQL-B — 选出多场对话（只按长度点选）

### 这份结果干什么用？

给真阴/ASR 专用流程（**批次 0-NEG，单独会话**）准备原材料。  
本步 **只看长度和 preview 是否像口语练习**，不判断句子对错。

- 长度范围：**800–5000** 字
- 场数：**可多场**（建议先选 **3–6 场**；材料不够再加，不必一次贴十几场）
- 优先 `user_id <> 1`；没有就用 `user_id = 1`
- 批次 0-NEG 是独立对话，上下文可以适度大一些，但仍建议总量别一次塞爆（够抠出两类句子即可）

### SQL-B（列表，先看再选）

```sql
SELECT
  analysis_id,
  user_id,
  created_at,
  CHAR_LENGTH(conversation_content) AS len,
  LEFT(conversation_content, 160) AS preview
FROM (
  SELECT
    a.*,
    ROW_NUMBER() OVER (
      PARTITION BY MD5(a.conversation_content)
      ORDER BY
        CASE WHEN a.user_id <> 1 THEN 0 ELSE 1 END,
        a.created_at DESC
    ) AS rn
  FROM conversation_analysis a
  WHERE a.status = 'success'
    AND a.conversation_content IS NOT NULL
    AND CHAR_LENGTH(a.conversation_content) BETWEEN 800 AND 5000
) t
WHERE rn = 1
ORDER BY
  CASE WHEN user_id <> 1 THEN 0 ELSE 1 END,
  created_at DESC
LIMIT 20;
```

### 你拿到结果后做什么

1. 从列表里 **抄下 3–6 个** `analysis_id`（`len` 在 800–5000、preview 像口语即可）。
2. 进入步骤 4，用这些 id **一次性导出**全文。  
   若范围内没有行：把下限改成 `>= 400` 再跑，仍可多选。

---

## 步骤 4：导出多场全文 + Luna 批次 0-NEG

### 4.1 导出全文（SQL-B-MULTI）

把 `IN (...)` 里的 id 换成步骤 3 抄下的多个 `analysis_id`：

```sql
SELECT analysis_id, user_id, conversation_content
FROM conversation_analysis
WHERE status = 'success'
  AND analysis_id IN (
    'id1',
    'id2',
    'id3'
  );
```

### 你拿到全文后做什么

1. 存成例如 `~/Desktop/golden-dialogs.txt`，建议按场分隔：

```text
===== analysis_id=xxx user_id=2 =====
（该场 conversation_content）

===== analysis_id=yyy user_id=1 =====
（该场 conversation_content）
```

2. **新开**一轮 Luna 对话（批次 0-NEG；不要和批次 0 的候选句混在同一上下文）。
3. 粘贴下方「批次 0-NEG」提示词 + 上述多场全文。
4. 得到 JSON 后保存 **1** 个文件：`legit-spoken-01/input.json`（只取 `cases` 里该 case 的 `input`，或按下方单 case 输出格式整份作为 input）。

### 批次 0-NEG 提示词（只抠真阴口语）

```text
你是评测集构建助手。操作者不是英语老师。本轮处理「多场」原始对话字幕（独立任务，与候选句分类无关）。

任务：从这些对话中抽出学习者侧的英文句子，组成 legit-spoken case 的 input。
不要做语法对错的最终标注（那是后续 gold 步骤）；这里只负责选句。

目标：恰好 1 个 case，约 6–10 句。
- legit-spoken-01 / legit_spoken：听起来像合法/可接受口语、完整表达（默认后面多数应 expectError=false）
不要产出 asr_noise（已移出主黄金集）。

规则：
1. 只使用我提供的对话原文；不要改写（最多 trim）；不要发明句子。
2. 尽量只抽学习者/用户侧英文句；跳过明显是 AI/助教的长回复。
3. 跳过纯中文句；中英夹杂且以英文为主的可以保留。
4. 同一 case 内不得重复；可跨多场对话取材。
5. 优先选完整、可理解的口语句；跳过严重 ASR 乱码/碎裂句。
6. 至少 6 句；材料不够就从已给对话里尽力凑，不要从外部编造。
7. 只输出一个 JSON，不要解释，不要 markdown 围栏。

输出格式：
{
  "source_analysis_ids": ["id1", "id2"],
  "cases": [
    {
      "id": "legit-spoken-01",
      "category": "legit_spoken",
      "title": "短标题",
      "input": {
        "utterances": [
          { "id": "u1", "text": "..." }
        ]
      }
    }
  ]
}

utterance id 从 u1 连续编号。
source_analysis_ids 填入本批实际用到的 analysis_id。

<<<DIALOGS>>>
（粘贴多场全文，保留 ===== analysis_id=... ===== 分隔）
<<<END_DIALOGS>>>
```

### 验收（只查格式）

- [ ] 恰好 `legit-spoken-01`，`utterances` 至少 6 句（实在不够则以模型输出为准，不要自己补编）
- [ ] 不检查英语对不对

若凑不齐：回到步骤 3 **再加几场** id，重跑步骤 4（覆盖 input 即可）。

---

## 步骤 5：Luna 批次 1–3 — 每个 case 生成 meta + gold

对 **三个** `input.json` 各开一轮（建议每 case 新开对话）。

**提示词已按 case 放好**：

| case | 提示词文件 |
|------|------------|
| clear-error-01 | `data/output/clear-error-01/prompt-gold.md` |
| confusable-point-01 | `data/output/confusable-point-01/prompt-gold.md` |
| legit-spoken-01 | `data/output/legit-spoken-01/prompt-gold.md` |

操作：

1. 打开该 case 的 `prompt-gold.md`，复制「整段发给模型」代码块内容。
2. 把同目录已填好的 `input.json` 贴进文末 `<<<INPUT_JSON>>>`。
3. 发送；把返回拆成：`meta` → `meta.json`，`gold` → `gold.json`。

### 验收（只查格式）

- [ ] `meta.id` 与文件夹名一致
- [ ] `gold` 的 id 集合与 `input` 一致
- [ ] `expectError=false` ⇒ `errors: []`

---

## 步骤 6：存盘清单

- [ ] `clear-error-01/{meta,input,gold}.json`
- [ ] `confusable-point-01/{meta,input,gold}.json`
- [ ] `legit-spoken-01/{meta,input,gold}.json`

实现 harness 后拷到：`backend/src/test/resources/eval/golden/cases/<id>/`  
（User 侧工作副本曾位于 `docs/todo/golden-set/data/output/<id>/`；文档已归档至 `docs/achieved/golden-set/`）

---

## 常见问题

**Q：为什么去掉 asr-noise？**  
与 Stage2「IGNORE 填料/重复/ASR 垃圾」冲突，长乱码句叶子比对不稳，不适合作主精度集。

**Q：批次 0 还要贴完整对话吗？**  
不要。批次 0 只吃 SQL-A。真阴用批次 0-NEG + **多场**对话（独立会话）。

**Q：为什么列表查询还要看 preview？**  
只为按「像不像口语练习、长度是否合适」点选若干场；不是判断语法。

**Q：多场还凑不齐怎么办？**  
再加几场 id 重跑步骤 4；不要和批次 0 混在同一聊天里。

**Q：自测 user_id=1 很多？**  
列表已优先真实用户；没有就用 1。

**Q：同一段分析很多次？**  
列表用 `MD5(conversation_content)` 去重；SQL-A 用规范化句子去重。

**Q：要不要人工抽查 gold？**  
按约定：不抽查语言学内容，只修 JSON 格式。

---

## 与代码实现的边界

| 谁 | 做什么 |
|----|--------|
| User（本 TODO） | SQL → 批次 0 / 0-NEG → 批次 1–3 → 三个 case JSON |
| Agent（`2026-08-08-plan.md`） | `GoldenStatistics`、harness、目录与文档 |

本 TODO **不阻塞** 代码侧先实现 harness。
