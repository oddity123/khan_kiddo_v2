# 分析漂移回归度量（Drift Harness）

本目录用于**免标注**地量化「AI 分析漂移」：把同一段对话用当前 pipeline 连跑 N 次，
看结果晃不晃。它**不判断对错**（那是 golden set 的事），只测**稳定性**——因此不需要
任何标准答案，也不涉及「谁是权威」。

## 目录结构

```
eval/drift/
├── README.md                 # 本文件
└── conversations/            # 种子语料，每个 .txt 是一段完整对话字幕
    └── sample-vocab-recap.txt
```

想扩充语料，直接往 `conversations/` 放 `*.txt` 即可（建议 10–20 段，覆盖不同长度与
错误密度）。文件名（去掉 `.txt`）即报告里的对话 ID。

## 怎么跑

运行器是 `com.khankiddo.learning.eval.ConversationDriftHarness`，**opt-in**，默认不随
`mvn test` 执行（避免消耗真实 LLM 额度、拖慢 CI）。需要真实模型，运行前确保 `.env`
里已配置 `DOUBAO_API_KEY`（或其它已启用模型的 Key）。

### 加载环境变量

根目录 `.env` 若含 `&` 等特殊字符，直接 `source .env` 可能在 zsh 下报错
（`parse error near '&'`）。推荐用下面方式加载**不含特殊字符**的键值行：

```bash
cd /Users/oddity/workspace/khan_kiddo_v2/backend
export $(grep -E '^[A-Za-z_][A-Za-z0-9_]*=' ../.env | grep -v '&' | xargs)
```

若 `.env` 无特殊字符，也可：

```bash
cd /Users/oddity/workspace/khan_kiddo_v2
set -a && source .env && set +a
cd backend
```

### 执行命令

漂移 harness 使用 `@SpringBootTest`，`-Dspring.profiles.active=test` 即可（H2 内存库，
**无需启动 MySQL**）：

```bash
./mvn.sh -q test \
    -Dspring.profiles.active=test \
    -Ddrift=true \
    -Ddrift.runs=5 \
    -Dtest=ConversationDriftHarness
```

- `-Ddrift=true`：解锁运行器（否则跳过）。
- `-Ddrift.runs=5`：每段对话跑几次（默认 5）。
- `-Ddrift.modelId=<id>`：指定 Stage2/Stage3 分析模型（可选）；未设置时用
  `app.llm.default-model-id`（当前默认 **`doubao-seed`** → API 模型
  `doubao-seed-1-8-251228`）。Stage1 对话分离始终用 Flash 模型，不受此参数影响。

报告输出到 `backend/target/drift-report/drift-<时间戳>.md`，控制台也会打印。
报告含**模型信息**、**汇总表**与**每次运行的分数/错误数明细**。

### 模型选择

| 参数 | 作用 | 默认 |
|---|---|---|
| `-Ddrift.modelId` | Stage2 语法分析 + Stage3 教育总结 | `doubao-seed` |
| （无参数） | 同左，走 `application.yml` 的 `default-model-id` | `doubao-seed` |
| Stage1 分离 | 固定 Flash，见 `app.conversation-analysis.separation-model-name` | `doubao-seed-1-6-flash-250828` |

`app.llm.models` 中已启用且 Key 已配置的条目均可作为 `drift.modelId`，例如：

- `doubao-seed` — 需 `DOUBAO_API_KEY`
- `qwen-plus` — 需 `QWEN_API_KEY`

Temperature 来自各模型配置（或环境变量 `AI_TEMPERATURE`），与线上一致。

换模型示例：

```bash
./mvn.sh -q test \
    -Dspring.profiles.active=test \
    -Ddrift=true \
    -Ddrift.runs=5 \
    -Ddrift.modelId=qwen-plus \
    -Dtest=ConversationDriftHarness
```

### 一键复制（推荐）

```bash
cd /Users/oddity/workspace/khan_kiddo_v2/backend
export $(grep -E '^[A-Za-z_][A-Za-z0-9_]*=' ../.env | grep -v '&' | xargs)
./mvn.sh -q test \
    -Dspring.profiles.active=test \
    -Ddrift=true \
    -Ddrift.runs=5 \
    -Dtest=ConversationDriftHarness
```

## 指标含义

| 指标 | 含义 | 越稳越接近 |
|---|---|---|
| 分数极差 | N 次综合分的 max−min（用户直接看到的数字） | 0 |
| 分数σ | 综合分标准差 | 0 |
| 句数极差 | Stage1 切分句数的波动 | 0 |
| 错误数极差 | 错误总数的波动 | 0 |
| 类型 Jaccard | 错误类型分布的多重集相似度（两两平均） | 1 |
| 句子翻转率 | 「有时被标、有时不被标」的句子占比 | 0 |

裁决（STABLE / MODERATE / HIGH_DRIFT）阈值见 `DriftStatistics`，可按需调整。

## 定位

- **纯度量内核** `DriftStatistics` 不依赖 LLM/Spring，有独立单测 `DriftStatisticsTest`，
  随 `mvn test` 常规运行。
- **运行器** `ConversationDriftHarness` 只负责调真实模型 N 次并喂给内核，不做断言。

## 下一步（golden set，本期未做）

漂移测稳定性；**准确性**需要独立参照。计划用**换厂商的强模型**（线上用豆包，裁判用
GPT/Claude）逐句提候选、人工只裁决分歧项，冻结成 golden set，再算 precision/recall。
