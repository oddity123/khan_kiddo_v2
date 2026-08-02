# Conversation Analysis Action Cards Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在对话分析详情页上线跨通道「下次最该改的说话习惯」Top1+Top3，并提供无 LLM 的「重说一句」行动。

**Architecture:** Stage2 改为输出 `pointId`；后端字典查表推导 family/channel/ProblemType；纯函数 `HabitCardScorer` 跨 rule/fluency/lexical/chinese 打分；详情 API 下发 `topHabit`/`actionCards`；前端 Hero + 行动卡 + Resay Dialog。中文夹杂主要来自 Stage1.5 的 `chineseExpressions`（多数不进 Stage2），打分时必须单独注入。

**Tech Stack:** Java 21 · Spring Boot 3 · MyBatis-Plus · Vue 3 · TypeScript · Element Plus  
**Spec:** `docs/superpowers/specs/2026-08-01-conversation-analysis-action-cards-design.md`（r3）  
**字典草案:** `scripts/data/v1_dictionary_draft.json`  
**构建:** 后端 `./mvn.sh -q test`（仓库根）；前端 `cd frontend && npm run build`

**本计划范围 = MVP / P0。** 不做：`user_focus_point`、回检条、LLM「练 3 句」。

---

## File map

| 文件 | 职责 |
|---|---|
| `backend/.../resources/knowledge/point-dictionary-v1.json` | 运行时字典（由草案加工：补 `cardKind`/`habitUnit`/`impactWeight`/`topTitleZh`） |
| `.../knowledge/PointDefinition.java` 等 | 字典模型 |
| `.../knowledge/PointDictionary.java` | 加载与查表 |
| `.../knowledge/HabitCardScorer.java` | 跨通道 Top 打分（纯函数） |
| `.../dto/conversation/ActionCardDto.java` 等 | 详情扩展 DTO |
| `GrammarErrorDto` / `AnalysisErrorDto` | 增加 `pointId` |
| `conversation-analysis-schema.json` + Stage2 prompt | enum 改为 pointId |
| `GrammarAnalysisSanitizer` | 非法 pointId → OTHER |
| `ConversationAnalysisItem` + DDL + `schema-test.sql` | 列 `point_id` |
| `ConversationAnalysisServiceImpl` | persist + getDetail 组装卡片 |
| `frontend/.../types/conversation.ts` | 类型 |
| `TopHabitHero.vue` / `ActionCardsPanel.vue` / `ResayPracticeDialog.vue` | UI |
| `AnalysisDetailView.vue` / `ErrorTypePieChart.vue` | 接线与分布改家族 |

---

### Task 1: 运行时字典 JSON + PointDictionary

**Files:**
- Create: `backend/src/main/resources/knowledge/point-dictionary-v1.json`
- Create: `backend/src/main/java/com/khankiddo/learning/knowledge/PointChannel.java`
- Create: `backend/src/main/java/com/khankiddo/learning/knowledge/CardKind.java`
- Create: `backend/src/main/java/com/khankiddo/learning/knowledge/CardPolicy.java`
- Create: `backend/src/main/java/com/khankiddo/learning/knowledge/HabitUnit.java`
- Create: `backend/src/main/java/com/khankiddo/learning/knowledge/PointDefinition.java`
- Create: `backend/src/main/java/com/khankiddo/learning/knowledge/FamilyDefinition.java`
- Create: `backend/src/main/java/com/khankiddo/learning/knowledge/PointDictionary.java`
- Create: `backend/src/main/java/com/khankiddo/learning/knowledge/PointDictionaryConfig.java`
- Test: `backend/src/test/java/com/khankiddo/learning/knowledge/PointDictionaryTest.java`

- [ ] **Step 1: 从草案生成运行时 JSON**

在仓库根执行（一次性脚本，结果提交进 git）：

```bash
python3 - <<'PY'
import json
from pathlib import Path

src = json.loads(Path("scripts/data/v1_dictionary_draft.json").read_text())
IMPACT = {"rule": 1.0, "fluency": 1.15, "lexical": 0.95, "chinese": 1.25}
KIND = {
    "rule": "grammar",
    "fluency": "fluency_strategy",
    "lexical": "lexical_upgrade",
    "chinese": "chinese_bypass",
}
HABIT_UNIT = {
    "rule": "family",
    "fluency": "leaf",
    "lexical": "channel",
    "chinese": "channel",
}
TOP = {
    "CHINESE_CODE_SWITCH": "想不到词时容易切回中文",
    "LEXICAL_GAP": "关键概念缺少可出口的表达",
    "FLUENCY_INCOMPLETE": "句子常说到一半就断",
    "FLUENCY_REDUNDANCY": "启动时重复或叠词拖慢表达",
}

families = []
for f in src["families"]:
    ch = f.get("channel") or "rule"
    families.append({
        "familyId": f["familyId"],
        "titleZh": f["titleZh"],
        "channel": ch,
        "fixability": f.get("fixability"),
        "otherPointId": f.get("otherPointId"),
        "impactWeight": IMPACT[ch],
        "habitUnit": HABIT_UNIT[ch],
    })

points = []
for p in src["points"]:
    if not p.get("v1Keep", True) and p["pointId"] not in (
        # v1Keep false 完全体不进 runtime enum；仅 v1Keep true + OTHER
    ):
        # 仍跳过 v1Keep=false
        if p.get("v1Keep") is False:
            continue
    ch = p["channel"]
    points.append({
        "pointId": p["pointId"],
        "familyId": p["familyId"],
        "channel": ch,
        "cardKind": KIND[ch],
        "cardPolicy": p.get("cardPolicy") or "normal",
        "habitUnit": HABIT_UNIT[ch],
        "impactWeight": IMPACT[ch],
        "fixability": p.get("fixability"),
        "errorLevel": p["errorLevel"],
        "problemType": p["problemType"],
        "titleZh": p["titleZh"],
        "whyZh": p["whyZh"],
        "topTitleZh": TOP.get(p["pointId"]) or p["titleZh"],
        "actionHintZh": {
            "grammar": "用纠正句重说一句",
            "fluency_strategy": "按策略把这句再说一遍",
            "lexical_upgrade": "用目标说法重说一句",
            "chinese_bypass": "不用中文，用英文绕行重说一句",
        }[KIND[ch]],
    })

# 为每个规则家族补 OTHER 叶子（若不存在）
existing = {p["pointId"] for p in points}
for f in families:
    oid = f.get("otherPointId")
    if not oid or oid in existing:
        continue
    if f["channel"] != "rule":
        continue
    points.append({
        "pointId": oid,
        "familyId": f["familyId"],
        "channel": "rule",
        "cardKind": "grammar",
        "cardPolicy": "normal",
        "habitUnit": "family",
        "impactWeight": 1.0,
        "fixability": f.get("fixability") or 0.5,
        "errorLevel": "BASIC",
        "problemType": "Structure",
        "titleZh": f["titleZh"] + "（其它）",
        "whyZh": "无法归入更细叶子时的兜底。",
        "topTitleZh": f["titleZh"] + "还有一些零散问题",
        "actionHintZh": "用纠正句重说一句",
    })

out = {
    "version": src["version"] + "-runtime",
    "discriminators": src["discriminators"],
    "families": families,
    "points": points,
}
path = Path("backend/src/main/resources/knowledge/point-dictionary-v1.json")
path.parent.mkdir(parents=True, exist_ok=True)
path.write_text(json.dumps(out, ensure_ascii=False, indent=2), encoding="utf-8")
print("points", len(points), "->", path)
PY
```

Expected: 打印 `points` 数量约 30+（含 OTHER）。

- [ ] **Step 2: 写失败测试**

```java
package com.khankiddo.learning.knowledge;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PointDictionaryTest {

    @Test
    void loadsAndResolvesFeelEdAdj() {
        PointDictionary dict = PointDictionary.loadFromClasspath("knowledge/point-dictionary-v1.json");
        PointDefinition p = dict.require("FEEL_ED_ADJ");
        assertEquals("FAM_WORD_FORM", p.familyId());
        assertEquals(PointChannel.RULE, p.channel());
        assertEquals(CardKind.GRAMMAR, p.cardKind());
        assertEquals("Word Form", p.problemType());
    }

    @Test
    void unknownFallsBackToStructureOther() {
        PointDictionary dict = PointDictionary.loadFromClasspath("knowledge/point-dictionary-v1.json");
        PointDefinition p = dict.resolveOrFallback("NOT_A_REAL_POINT");
        assertEquals("STRUCTURE_OTHER", p.pointId());
    }
}
```

- [ ] **Step 3: 跑测试确认失败**

```bash
cd /Users/oddity/workspace/khan_kiddo_v2 && ./mvn.sh -q -Dtest=PointDictionaryTest test
```

Expected: FAIL（类不存在）

- [ ] **Step 4: 实现枚举与模型 + PointDictionary**

要点：
- `PointDictionary` 用 Jackson 读 classpath JSON，建 `Map<String, PointDefinition>`。
- `require(id)` 找不到抛 `IllegalArgumentException`。
- `resolveOrFallback(id)`：未知 → `STRUCTURE_OTHER`；若家族 OTHER 存在则优先用该家族 OTHER（可选：根据前缀猜测，MVP 固定 `STRUCTURE_OTHER` 即可）。
- `allPointIds()` 返回 Stage2 enum 列表（所有 points 的 pointId）。
- Spring：`@Configuration` + `@Bean PointDictionary`。

- [ ] **Step 5: 再跑测试**

```bash
./mvn.sh -q -Dtest=PointDictionaryTest test
```

Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/resources/knowledge/point-dictionary-v1.json \
  backend/src/main/java/com/khankiddo/learning/knowledge \
  backend/src/test/java/com/khankiddo/learning/knowledge
git commit -m "feat: add knowledge point dictionary loader"
```

---

### Task 2: HabitCardScorer（跨通道打分）

**Files:**
- Create: `backend/src/main/java/com/khankiddo/learning/knowledge/HabitScoreInput.java`
- Create: `backend/src/main/java/com/khankiddo/learning/knowledge/HabitCardScorer.java`
- Create: `backend/src/main/java/com/khankiddo/learning/dto/conversation/ActionCardDto.java`
- Create: `backend/src/main/java/com/khankiddo/learning/dto/conversation/PracticePromptDto.java`
- Create: `backend/src/main/java/com/khankiddo/learning/dto/conversation/FamilyDistributionDto.java`
- Test: `backend/src/test/java/com/khankiddo/learning/knowledge/HabitCardScorerTest.java`

- [ ] **Step 1: 定义输入与 DTO**

`HabitScoreInput.ErrorHit` 字段：`pointId`, `sentenceId`, `originalSentence`, `errorPoint`, `suggestion`, `errorLevel`（可空，空则查字典）。

另：`List<ChineseExpressionDto> chineseExpressions`（可空）——有则合成 `CHINESE_CODE_SWITCH` 命中（每条表达式计 1，severity=BASIC）。

`ActionCardDto` 字段对齐 spec §7.2：`rank`, `channel`, `cardKind`, `habitKey`, `pointId`, `headlineZh`, `titleZh`, `whyZh`, `errorCount`, `score`, `cardPolicy`, `examples`, `siblingPoints`, `actionHintZh`, `practicePrompt`。

`PracticePromptDto`：`originalSentence`, `targetSentence`, `coachingZh`。

- [ ] **Step 2: 写打分失败测试（先写断言）**

```java
@Test
void chineseCanBeatSparseStyleGrammar() {
    PointDictionary dict = PointDictionary.loadFromClasspath("knowledge/point-dictionary-v1.json");
    HabitCardScorer scorer = new HabitCardScorer(dict);

    List<HabitScoreInput.ErrorHit> hits = List.of(
        hit("ARTICLE_A_AN", "STYLE", "a apple", "an apple", "I eat a apple."),
        // 仅 1 条 rare/弱语法不够形成强习惯；再加少量
        hit("ARTICLE_A_AN", "BASIC", "a hour", "an hour", "Wait a hour.")
    );
    // 3 条中文夹杂
    var chinese = List.of(
        chinese("立法", "legislation"),
        chinese("客商", "client"),
        chinese("敲碗", "tap the bowl")
    );

    var result = scorer.score(new HabitScoreInput(hits, chinese));
    assertEquals("CHINESE_CODE_SWITCH", result.topHabit().getPointId());
    assertTrue(result.topHabit().getHeadlineZh().contains("中文") || result.topHabit().getHeadlineZh().contains("切"));
}

@Test
void rarePrepositionNeverEntersTop() {
    // 构造大量 PREP_FIXED + 少量 FEEL_ED_ADJ(>=2)
    // assert top 不含 PREP_FIXED，且 FEEL 所在家族可进 Top
}

@Test
void lexicalIsSingleCandidateEvenIfManyHits() {
    // 10 条 LEXICAL_GAP → actionCards 里 channel=lexical 最多 1 张
}

@Test
void requiresAtLeastTwoHits() {
    // 单条 FEEL_ED_ADJ → 不出现该习惯
}
```

实现 `hit(...)` / `chinese(...)` 测试辅助方法。

- [ ] **Step 3: 跑测试确认失败**

```bash
./mvn.sh -q -Dtest=HabitCardScorerTest test
```

- [ ] **Step 4: 实现 HabitCardScorer**

算法（锁死，勿发挥）：

1. 将每个 `ErrorHit` resolve 到 `PointDefinition`（非法 → fallback）。  
2. 跳过 `cardPolicy=rare`。  
3. 按 `habitUnit` 分组：  
   - `family` → key=`familyId`  
   - `leaf` → key=`pointId`  
   - `channel` → key=`channel.name()`  
4. 注入中文：`chineseExpressions` 非空时，向 `CHINESE_CODE_SWITCH` 组追加 N 次 BASIC。  
5. `score = Σ severity × impactWeight × fixability'`，`fixability'`：rule 用叶子/家族 fixability（空当 0.5），非 rule 用 1.0。  
6. severity 映射：FATAL=3, BASIC=2, NATURAL=1.5, STYLE=1。  
7. 过滤 count&lt;2；按 score 降序取前 3。  
8. 每张卡：  
   - `pointId` = 组内出现最多（或分数贡献最大）的叶子  
   - `headlineZh` = `"本次最该改：" + topTitleZh`（仅 rank1 用完整句；rank2/3 可用 topTitleZh）  
   - `examples` ≤5  
   - `practicePrompt`：original=第一条证据原句；target=`suggestion` 若有否则用 errorPoint 箭头右侧；`coachingZh`=actionHintZh  
   - rule 家族填 `siblingPoints`（同家族其它叶子 count）  
9. 同时输出 `familyDistribution`：按 familyId 计数（含 rare，便于饼图）。

返回 record：`HabitScoreResult(ActionCardDto topHabit, List<ActionCardDto> actionCards, List<FamilyDistributionDto> familyDistribution)`，`topHabit` 即 rank1。

- [ ] **Step 5: 测试通过并 commit**

```bash
./mvn.sh -q -Dtest=HabitCardScorerTest,PointDictionaryTest test
git add backend/src/main/java/com/khankiddo/learning/knowledge \
  backend/src/main/java/com/khankiddo/learning/dto/conversation \
  backend/src/test/java/com/khankiddo/learning/knowledge
git commit -m "feat: add cross-channel habit card scorer"
```

---

### Task 3: Stage2 契约改为 pointId

**Files:**
- Modify: `backend/src/main/resources/schemas/conversation-analysis-schema.json`
- Modify: `backend/src/main/resources/templates/prompts/system-prompt-conversation-analysis.txt`
- Modify: `backend/src/main/java/com/khankiddo/learning/ai/conversation/model/GrammarErrorDto.java`
- Modify: `backend/src/main/java/com/khankiddo/learning/dto/conversation/AnalysisErrorDto.java`
- Modify: 所有把 `error.type` 当 ProblemType 解析的映射点（搜 `getType()` / `toEnglishProblemType` / `ProblemType.fromEnglishName`）
- Test: 扩展或新增 `SchemaLoaderTest` / 小型映射测试

- [ ] **Step 1: 改 schema**

`errors[]` 属性：

```json
"pointId": {
  "type": "string",
  "enum": [ "/* PointDictionary.allPointIds() 生成的完整列表 */" ],
  "description": "知识点叶子 ID；必须从枚举中选择"
},
"point": {
  "type": "string",
  "description": "（保持原有 point 格式说明）"
}
```

**删除** `type` 字段（或保留但不要求——推荐删除以免模型继续填旧类型）。`required` 含 `pointId`, `point`。

用脚本从 `point-dictionary-v1.json` 生成 enum 数组，避免手抄。

- [ ] **Step 2: 更新 system prompt**

在 `system-prompt-conversation-analysis.txt` 增加：
- 输出 `pointId` 而非问题类型名  
- 粘贴字典 `discriminators` 五条中文判据  
- 各 channel 简述：流利度 / 词汇 / 中文夹杂何时用  

保持原有「point 格式」硬性要求。

- [ ] **Step 3: DTO**

```java
// GrammarErrorDto
private String pointId;
private String point;
// 删除 type 或 @Deprecated；映射层不再读 type

// AnalysisErrorDto
private String pointId;
private String type;       // 展示用中文/英文名，由字典反查
private String point;
private String errorLevel; // 由字典反查
private String familyId;   // 可选
private String channel;    // 可选
```

- [ ] **Step 4: 映射层**

凡 `ProblemType.fromEnglishName(error.getType())` 改为：

```java
PointDefinition def = pointDictionary.resolveOrFallback(error.getPointId());
String englishType = def.problemType(); // "Article" 等
ErrorLevel level = ErrorLevel.valueOf(def.errorLevel());
```

展示 `type` 用 `ProblemType.translate(englishType)` 或字典 title——与现前端 `displayTypeLabel` 兼容则继续写中文名。

- [ ] **Step 5: 编译测试**

```bash
./mvn.sh -q -Dtest=SchemaLoaderTest,HabitCardScorerTest,PointDictionaryTest,GrammarAnalysisSanitizerTest test
```

修复所有编译错误后再 commit：

```bash
git commit -am "feat: switch Stage2 schema from ProblemType to pointId"
```

---

### Task 4: Sanitizer 校验 pointId

**Files:**
- Modify: `backend/src/main/java/com/khankiddo/learning/conversation/GrammarAnalysisSanitizer.java`
- Modify: `backend/src/test/java/com/khankiddo/learning/conversation/GrammarAnalysisSanitizerTest.java`

- [ ] **Step 1: 测试**

```java
@Test
void replacesUnknownPointIdWithFallback() {
    // 构造含 pointId=NOT_REAL 的 GrammarAnalysisResult
    // sanitize 后变为 STRUCTURE_OTHER（或字典 fallback）
}
```

- [ ] **Step 2: 注入 PointDictionary，sanitize 时改写非法 pointId**

保持现有 span/自我修正过滤逻辑不变。

- [ ] **Step 3: 测试通过并 commit**

```bash
./mvn.sh -q -Dtest=GrammarAnalysisSanitizerTest test
git commit -am "feat: sanitize unknown pointIds to dictionary fallback"
```

---

### Task 5: 落库 point_id

**Files:**
- Modify: `backend/src/main/resources/sql/DDL.sql`
- Modify: `backend/src/test/resources/schema-test.sql`
- Modify: `backend/src/main/java/com/khankiddo/learning/model/ConversationAnalysisItem.java`
- Modify: `ConversationAnalysisItemMapper.xml`（若列显式列出）
- Modify: `ConversationAnalysisServiceImpl.buildDbItems`

- [ ] **Step 1: DDL**

```sql
-- conversation_analysis_item 表内增加：
`point_id` VARCHAR(48) NULL COMMENT '知识点叶子 pointId' AFTER `problem_types`,
```

测试 schema 同步。本地/线上执行一次 `ALTER`（文档写明）：

```sql
ALTER TABLE conversation_analysis_item
  ADD COLUMN point_id VARCHAR(48) NULL COMMENT '知识点叶子 pointId' AFTER problem_types;
```

- [ ] **Step 2: 实体字段 `pointId` + buildDbItems 写入**

```java
.pointId(def.pointId())
.problemTypes(def.problemType())
```

- [ ] **Step 3: 跑相关持久化测试（若有）+ commit**

```bash
./mvn.sh -q test
git commit -am "feat: persist point_id on conversation_analysis_item"
```

---

### Task 6: 详情 API 组装 topHabit / actionCards

**Files:**
- Modify: `ConversationAnalysisDetailDto.java`
- Modify: `ConversationAnalysisServiceImpl.getDetail`
- Test: `ConversationAnalysisServiceImpl` 相关测试或新建 `HabitCardDetailAssemblyTest`

- [ ] **Step 1: DetailDto 增加字段**

```java
private ActionCardDto topHabit;
private List<ActionCardDto> actionCards;
private List<FamilyDistributionDto> familyDistribution;
```

- [ ] **Step 2: getDetail 中**

从 items/errors 抽出 `ErrorHit`（无 pointId 的旧数据 → `actionCards=empty`，`familyDistribution` 可回退旧 `errorTypeDistribution`）。  
调用 `habitCardScorer.score(...)`，填入 detail。  
`chineseExpressions` 传入 scorer。

- [ ] **Step 3: 手动或集成测试确认 JSON 形状**

```bash
./mvn.sh -q test
git commit -am "feat: expose topHabit and actionCards on analysis detail"
```

---

### Task 7: 前端类型与 API（无行为变化）

**Files:**
- Modify: `frontend/src/types/conversation.ts`
- Modify: `frontend/src/api/conversationAnalysis.ts`（若需；通常不用改 URL）

- [ ] **Step 1: 增加类型**

```ts
export type PointChannel = 'rule' | 'fluency' | 'lexical' | 'chinese'
export type CardKind = 'grammar' | 'fluency_strategy' | 'lexical_upgrade' | 'chinese_bypass'

export interface PracticePrompt {
  originalSentence?: string
  targetSentence?: string
  coachingZh?: string
}

export interface ActionCard {
  rank: number
  channel: PointChannel
  cardKind: CardKind
  habitKey: string
  pointId: string
  headlineZh?: string
  titleZh: string
  whyZh?: string
  errorCount: number
  score?: number
  examples?: Array<{
    sentenceId?: number
    originalSentence: string
    errorPoint?: string
    suggestion?: string
  }>
  siblingPoints?: Array<{ pointId: string; titleZh: string; errorCount: number }>
  actionHintZh?: string
  practicePrompt?: PracticePrompt
}

export interface FamilyDistributionItem {
  familyId: string
  titleZh: string
  channel?: PointChannel
  count: number
}
```

在 `ConversationAnalysisDetail` 上增加：`topHabit?`, `actionCards?`, `familyDistribution?`。  
`AnalysisError` 增加可选 `pointId?`, `familyId?`, `channel?`。

- [ ] **Step 2: `npm run build` 类型检查通过（允许页面尚未使用新字段）**

```bash
cd frontend && npm run build
```

- [ ] **Step 3: Commit**

```bash
git add frontend/src/types/conversation.ts
git commit -m "feat: add action card types on analysis detail"
```

---

### Task 8: ResayPracticeDialog + TopHabitHero + ActionCardsPanel

**Files:**
- Create: `frontend/src/components/conversation/ResayPracticeDialog.vue`
- Create: `frontend/src/components/conversation/TopHabitHero.vue`
- Create: `frontend/src/components/conversation/ActionCardsPanel.vue`

- [ ] **Step 1: ResayPracticeDialog**

Props: `modelValue: boolean`, `prompt: PracticePrompt | null`, `actionHint?: string`  
Emit: `update:modelValue`, `done`  

内容：展示 original / target / coaching；主按钮「我说过了」→ emit done 并关闭。无 API。

- [ ] **Step 2: TopHabitHero**

Props: `card: ActionCard`  
大标题用 `card.headlineZh || ('本次最该改：' + card.titleZh)`  
副文 `whyZh` 一两行  
按钮：`card.actionHintZh || '重说一句'` → emit `practice`

- [ ] **Step 3: ActionCardsPanel**

Props: `cards: ActionCard[]`（通常 rank2–3，或含 rank1 的折叠态）  
每张：title、count、channel 标签、证据 1–2 条、「查看原句」emit `locate(sentenceId)`、「重说」emit `practice(card)`  
rank≥2 默认折叠标题行，点击展开。

样式：只用 `--kk-*` / `.kk-glass`，禁止硬编码品牌色。

- [ ] **Step 4: Commit**

```bash
git add frontend/src/components/conversation/ResayPracticeDialog.vue \
  frontend/src/components/conversation/TopHabitHero.vue \
  frontend/src/components/conversation/ActionCardsPanel.vue
git commit -m "feat: add habit hero, action cards, and resay dialog"
```

---

### Task 9: 接线 AnalysisDetailView + 家族分布

**Files:**
- Modify: `frontend/src/views/conversation/AnalysisDetailView.vue`
- Modify: `frontend/src/components/conversation/ErrorTypePieChart.vue`（或新建 `FamilyDistributionChart.vue` 包装同一图表）

- [ ] **Step 1: 主栏顺序**

在「知识卡片 / 句子级检查」之前插入：

1. `TopHabitHero`（`detail.topHabit` 有则显示）  
2. `ActionCardsPanel`（`actionCards` 过滤 rank&gt;1，或传全部由组件处理）  
3. `ResayPracticeDialog`

- [ ] **Step 2: 句子级检查降级**

- 默认 `<details>` 折叠，summary 显示「句子级检查（证据）· N 句」  
- 提供按 `pointId`/`familyId` 的简易 filter chip（从 items 收集）  
- `locate(sentenceId)`：展开 details + `scrollIntoView` 到对应 `SentenceAnalysisCard`

- [ ] **Step 3: 侧栏分布**

若有 `familyDistribution`，饼图改吃 `{ type: titleZh, count }`；否则回退 `errorTypeDistribution`。

- [ ] **Step 4: build + 目视**

```bash
cd frontend && npm run build
```

用本地已有分析详情页确认：无 topHabit 的旧数据不报错；有数据时（需新跑分析）显示 Hero。

- [ ] **Step 5: Commit**

```bash
git commit -am "feat: wire habit action cards into analysis detail view"
```

---

### Task 10: 端到端冒烟（人工）

- [ ] **Step 1: 本地 DB 执行 ALTER**（若尚未）

- [ ] **Step 2: 启动后端 + 前端，提交一段含中文夹杂或明显 uh 重复的对话做分析**

- [ ] **Step 3: 验收清单**

1. 详情首屏是 Top1 习惯句，不是长列表句子卡  
2. Top 可以是 chinese / fluency / lexical / rule 之一  
3. 点「重说一句」弹出原句+目标句，无网络请求（DevTools）  
4. 句子卡在折叠证据区，可从卡定位  
5. 侧栏分布与家族口径一致（非「用词不当 25%」独霸且与 Top 打架）  
6. `./mvn.sh -q test` 与 `frontend npm run build` 全绿  

- [ ] **Step 4: 修冒烟问题并最终 commit（如有）**

---

## Out of scope（下一计划）

- `user_focus_point` + 回检条  
- `POST /practice` LLM 练 3 句  
- 历史复发加权  
- 回填旧 `point_id`

---

## Spec coverage check

| Spec 要求 | Task |
|---|---|
| 字典运行时 + 查表 | T1 |
| 跨通道 Top 公式 / rare 排除 / lexical 单候选 | T2 |
| Stage2 pointId + 判据 prompt | T3 |
| Sanitizer | T4 |
| point_id 落库 | T5 |
| 详情 topHabit/actionCards/familyDistribution | T6 |
| 前端类型 | T7 |
| 四种卡形态 + 重说一句 | T8（形态用 cardKind 分支，MVP 可同壳异文案） |
| Hero + 证据层 + 分布 | T9 |
| 冒烟 | T10 |
| 回检 / LLM 练 3 句 | 明确不做 |

---

## 执行方式

计划写好后由你选择：

1. **Subagent-Driven（推荐）** — 每任务新开子代理，任务间复查  
2. **Inline Execution** — 本会话按 executing-plans 连续做  

选哪种就按对应 skill 开工。
