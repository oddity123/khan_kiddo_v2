# Stage2 黄金集准确性评测（Golden Set）

本目录存放**准确性**参照：冻结的英文 utterance + gold 标签。与 `eval/drift`（稳定性，免标注）分工明确。

设计/计划/取数手册已归档：`docs/achieved/golden-set/`（含本期模型对比结论）。

## 目录

```
eval/golden/
├── README.md
└── cases/
    ├── clear-error-01/{meta,input,gold}.json
    ├── confusable-point-01/{meta,input,gold}.json
    └── legit-spoken-01/{meta,input,gold}.json
```

主集不含 `asr_noise`（与 Stage2 IGNORE 填料/ASR 规则冲突）。

## 怎么跑

运行器：`com.khankiddo.learning.eval.ConversationGoldenHarness`（opt-in，需真实 LLM）。

```bash
cd /Users/oddity/workspace/khan_kiddo_v2/backend
export $(grep -E '^[A-Za-z_][A-Za-z0-9_]*=' ../.env | grep -v '&' | xargs)
./mvn.sh -q test \
  -Dspring.profiles.active=test \
  -Dgolden=true \
  -Dtest=ConversationGoldenHarness
```

- `-Dgolden=true`：解锁（否则跳过）
- `-Dgolden.modelId=<id>`：可选，指定 Stage2 模型（默认 `app.llm.default-model-id`，当前为 **`doubao-seed`**）

报告：`backend/target/golden-report/golden-<时间戳>.md`  
由 harness 的 `renderMarkdown(...)` **程序生成**（不是手写）：读 case → 调 Stage2 → `GoldenStatistics.evaluate` → 拼 Markdown 落盘。

## 报告「汇总」表指标说明

报告由 `ConversationGoldenHarness.renderMarkdown` **程序生成**（读 case → 调 Stage2 → `GoldenStatistics.evaluate` → 写 Markdown），表头与下表一致。

### 各指标含义（逐项）

- **case**：本条评测对应哪个黄金样本目录（一般等于 `meta.id`，如 `clear-error-01`）。
- **category**：样本类别（`meta.category`）。主集为 `clear_error`（明确错误）、`confusable_point`（易混 pointId）、`legit_spoken`（合法口语 / 真阴为主）。
- **P（precision，句级精确率）**：模型标成「有错」的句子里，有多少真的该标。  
  公式：`TP / (TP + FP)`。越高说明误报越少。分母为 0 时记 0。
- **R（recall，句级召回率）**：gold 里该标错的句子，模型抓到了多少。  
  公式：`TP / (TP + FN)`。越高说明漏报越少。分母为 0 时记 0。
- **F1（句级 F1）**：精确率与召回率的调和平均，综合看「有没有错」判得稳不稳。  
  公式：`2 · P · R / (P + R)`。P+R=0 时记 0。
- **leafAcc（叶子准确率）**：在「双方都认为有错」的句子上，模型给出的 `pointId` 有多少命中 gold 允许集合。  
  公式：`leafHits / goldErrorCount`。匹配方式：对每条 gold error，若存在尚未占用的 actual `pointId ∈ acceptablePointIds`，计一次叶子命中（贪心一对一）。gold 没有任何 error 时记 0。  
  **看这个**：分类到具体知识点准不准。
- **familyAcc（家族准确率）**：与 leafAcc 相同流程，但先把 `pointId` 经 `point-dictionary` 映射成 `familyId` 再匹配。  
  公式：`familyHits / goldErrorCount`。  
  **看这个**：粗类（冠词/时态/流利度等）对不对；叶子抖但家族对时，familyAcc 会高于 leafAcc。
- **TP（真阳性）**：gold 期望有错（`expectError=true`），且 Stage2 对该句产出了带 `errors` 的 item。
- **FP（假阳性 / 误报）**：gold 期望无错，但 Stage2 仍标了错。明细里的 FP 列表是对应 `utterance.id`。
- **FN（假阴性 / 漏报）**：gold 期望有错，但 Stage2 没标。明细里的 FN 列表是对应 `utterance.id`。
- **TN（真阴性）**：gold 期望无错，且 Stage2 也没标。
- **surplus（多余检出）**：叶子匹配占用之后，模型还多出来的、对不上任何 gold error 的 `pointId` 个数。  
  **含义**：句级「有错」可能对了，但细类标多了/标飞了。不进入 P/R/F1。
- **unaligned（未对齐）**：Stage2 返回的 `originalSentence`（规范化后）对不上本 case 任何 `input` 句子的条数。  
  **含义**：模型改写/拆句导致对不齐，这些条不进句级 P/R。理想为 0。
- **ms**：该 case 跑 Stage2 的耗时（毫秒），与准确性无关。

### 句级判定矩阵（TP / FP / FN / TN）

以 `utterance.id` 为键（原文先 `trim`，再把连续空白压成单空格后对齐）：

| | Stage2 **未**产出有错 item | Stage2 **产出**了有错 item |
|--|---------------------------|----------------------------|
| gold **无错**（`expectError=false`） | **TN** | **FP** |
| gold **有错**（`expectError=true`） | **FN** | **TP**（再算 leafAcc / familyAcc） |

### 「按 category」小节

同 `category` 下各 case 的 **F1**、**leafAcc** 做简单算术平均，写作 `avg F1` / `avg leafAcc`。

### 「明细」小节

每个 case 除 FP / FN / unaligned 外，还包含：

- **gold 期望 pointId**：各 `utterance.id` 的 `expectError` 与 `primaryPointId` / `acceptablePointIds`
- **Stage2 actual**：模型检出句的对齐 id、原文、整句 `suggestion`、以及每条 error 的 `pointId` / `familyId` / `point`（解释文案）

便于对照 leafAcc 偏低时模型实际打了哪些叶子、怎么解释、给了什么改写。

### 不算硬指标（报告里有参考字段，但不进上表）

gold 侧的 `suggestionHint`、`span`、`note`；actual 侧的 `point` / `suggestion` 仅作明细对照，不做措辞相似度打分。

## 契约要点

- `input.json`：`utterances[{id,text}]`，同一 case 内原文勿重复
- `gold.json`：错误级；`acceptablePointIds` 须含 `primaryPointId`
- 比对主键：规范化原文对齐到 `utterance.id`
- 调用路径：仅 Stage2（跳过 Stage1），结果经 `GrammarAnalysisSanitizer`

度量内核 `GoldenStatistics` 随 `mvn test` 常规运行；真实 harness 不默认进 CI。
