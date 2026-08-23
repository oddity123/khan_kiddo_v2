/** 知识点字典管理页：字段说明（悬浮于属性名旁的信息 icon） */

export const KNOWLEDGE_FAMILY_FIELD_HELP: Record<string, string> = {
  familyId: '家族稳定标识，如 FAM_ARTICLE；Stage2 通过 pointId 间接关联。',
  titleZh: '家族中文名称，用于行动卡与分布展示。',
  channel: '所属通道：rule / fluency / lexical / chinese，决定打分与卡片形态。',
  habitUnit: '习惯分组粒度：family 按家族聚合，leaf 按叶子，channel 按通道。',
  fixability: '家族默认可教性（0–1），rule 通道参与 Top 习惯卡打分；叶子未填时继承。',
  impactWeight: '跨通道影响权重，用于比较不同通道习惯的优先级。',
  otherPointId: '该家族的兜底叶子；无法命中细叶子时使用。',
  pointCount: '该家族下叶子节点数量（含兜底叶子）。',
}

export const KNOWLEDGE_POINT_FIELD_HELP: Record<string, string> = {
  pointId: '错误叶子稳定 ID，Stage2 输出枚举值，全站唯一。',
  familyId: '所属家族 ID，必须存在于 families 列表。',
  channel: '叶子所属通道，通常与家族 channel 一致。',
  cardKind: '卡片形态：grammar / fluency_strategy / lexical_upgrade / chinese_bypass。',
  cardPolicy: 'normal 可进 Top3；rare 仅分类不占 Top；channel 为通道级习惯。',
  habitUnit: '本叶子的习惯分组键：family / leaf / channel。',
  impactWeight: '该叶子在习惯打分中的影响权重。',
  fixability: '可教性（0–1）；rule 通道使用，空则继承家族 fixability。',
  errorLevel: '严重度：FATAL / BASIC / NATURAL / STYLE，影响打分权重。',
  scoreProfile: '评分 profile 键，对应 performance-scoring.yml 中的权重配置。',
  titleZh: '一句人话规则标题，用于展示与家族卡副标题。',
  catchAllLeaf: '是否为兜底类叶子（含 *_OTHER、家族 other、WORD_FORM_POS）。',
  globalFallback: '是否为全字典全局 fallback（STRUCTURE_OTHER）。',
  familyOtherLeaf: '是否为该家族的 otherPointId 兜底叶子。',
}

export const KNOWLEDGE_CHANNEL_FIELD_HELP: Record<string, string> = {
  通道: 'PointChannel 枚举值：rule / fluency / lexical / chinese。',
  中文名: '通道在界面上的中文标签。',
  家族数: '该通道下的家族数量。',
  叶子数: '该通道下所有叶子节点总数。',
}

export function knowledgeFieldHelp(
  map: Record<string, string>,
  key: string,
): string {
  return map[key] ?? '暂无说明'
}
