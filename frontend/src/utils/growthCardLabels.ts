import type {GrowthCardType} from '@/types/growthCard'

/** 成长卡 type → 中文标签（列表 / 详情 / 牌组共用）。 */
export function growthCardTypeLabel(type?: string | GrowthCardType): string {
  if (type === 'habit') return '习惯'
  if (type === 'vocab') return '词汇'
  if (type === 'expression') return '表达'
  return type ?? ''
}

/** 成长卡进度 → 中文标签（仅三档，不做百分制）。 */
export function growthCardStatusLabel(status?: string): string {
  if (status === 'unfamiliar') return '不熟'
  if (status === 'fuzzy') return '模糊'
  if (status === 'mastered') return '掌握'
  return status ?? ''
}
