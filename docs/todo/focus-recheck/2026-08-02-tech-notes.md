# 回检（focus recheck）技术方案备忘

**日期：** 2026-08-02  
**状态：** backlog（非当前优先）  
**范围：** 习惯盯梢 + 详情页回检条；不含 LLM「练 3 句」  
**前置：** 行动卡 MVP 已归档，见 `docs/achieved/conversation-analysis-action-cards/`

---

## 1. 优先级结论

回检属于**习惯追踪**，不是 Top 级需求。学习闭环（复习队列 / 掌握度）更优先时，本能力可继续挂起。

产品范围若重启，按 **B**：

- `user_focus_point` 盯梢
- 详情页回检条（「上次盯的习惯改掉了吗」）
- Top / 行动卡「下次帮我盯着」
- **不做** LLM 练 3 句

---

## 2. 方案比选（归档时结论）

| | 做法 | 说明 |
|---|---|---|
| **A（推荐）** | 显式 `user_focus_point`；详情组装时对比 watching | 与行动卡规格一致；可 master/dismiss |
| B | 默认盯上一场 Top1，不建表 | 无法 dismiss，心智乱 |
| C | 跨历史复发加权改 Top | 偏分析层，不是回检闭环 |

---

## 3. 产品行为（草稿）

1. 详情 Top1 / Top2–3：**「下次帮我盯着」** → `watching`（同用户同 `pointId` 唯一）。
2. **下次**打开某次分析详情：若有 `watching`，对本场证据匹配后展示回检条：
   - 仍出现 →「本场还在（命中 N 句）」+ 可定位
   - 未出现 → 可标 `mastered` / 继续盯 / `dismissed`
3. 只在**分析详情**展示；列表/首页不做入口。
4. **不自动 watch**（用户必须点一次）。
5. 同时盯梢上限建议 **1**（新 watch 顶替旧 watching）。
6. 回检条位置建议：**优先阶梯正上方**。

---

## 4. 数据

```sql
user_focus_point (
  id BIGINT PK,
  user_id BIGINT NOT NULL,
  point_id VARCHAR(48) NOT NULL,
  habit_key VARCHAR(64) NULL,
  source_analysis_id VARCHAR(...) NOT NULL,
  status VARCHAR(16) NOT NULL,        -- watching | mastered | dismissed
  created_at, updated_at,
  UNIQUE(user_id, point_id)
)
```

旧分析无 `point_id`：跳过对不上的证据，不回填。

---

## 5. 对比口径

- 语法家族卡：本场同 `familyId` 任一叶子命中 ⇒「还在」
- fluency / lexical / chinese：按 `pointId`（或 channel 级 `habitKey`）精确比
- 计数与 HabitCardScorer 证据同源（含 Stage1.5 注入的 chinese）

---

## 6. API

| 方法 | 路径 | 说明 |
|---|---|---|
| `POST` | `/api/conversation/focus-points` | `{ pointId, sourceAnalysisId, action: watch\|master\|dismiss }` |
| 扩展 | `GET .../analyses/{id}` | `focusRecheck: FocusRecheckDto[]` |

```ts
{
  pointId, habitKey, titleZh, status: 'watching',
  appeared: boolean,
  currentCount: number,
  sourceAnalysisId,
  examples?: [...]
}
```

---

## 7. 实现落点（重启时）

- DDL + `UserFocusPoint` model / mapper
- `FocusPointService`（写状态 + 组装 recheck）
- `ConversationAnalysisServiceImpl.getDetail` 填 `focusRecheck`
- 前端：`FocusRecheckBar.vue` + 行动卡「盯着」按钮
- 单测：匹配口径、上限 1 顶替、无 watching 时空数组

---

## 8. 明确不做（本备忘范围）

- LLM 练 3 句 / `POST /practice`
- 历史复发加权改 Top
- 回填旧 `point_id`
- 复习队列 / 掌握度体系（见 `docs/todo/learning-loop-gaps/`）

---

## 9. 验收（重启后人工）

1. 场 A 点「盯着」→ DB `watching`
2. 场 B 同点/同家族仍在 → 回检条「还在」
3. 场 C 没有 → 可掌握，之后不再出回检
4. 再 watch 别的点 → 旧 watching 被顶替
