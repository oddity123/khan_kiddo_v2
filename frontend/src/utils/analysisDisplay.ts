import type {
    AnalysisError,
    AnalysisItem,
    EducationalSummaryStats,
    PerformanceDimensionScores,
} from '@/types/conversation'

/** 与后端 performance-scoring 配置的分数上下限一致 */
export const PERFORMANCE_SCORE_MIN = 60
export const PERFORMANCE_SCORE_MAX = 98

export interface PerformanceDimensionItem {
    key: keyof PerformanceDimensionScores
    label: string
    value: number
    emphasis?: boolean
}

export const PERFORMANCE_DIMENSION_META: ReadonlyArray<{
    key: keyof PerformanceDimensionScores
    label: string
    emphasis?: boolean
}> = [
    {key: 'naturalness', label: '表达自然', emphasis: true},
    {key: 'accuracy', label: '语法准确'},
    {key: 'fluency', label: '文本流畅'},
    {key: 'lexical', label: '词汇表达'},
]

export function displayTypeLabel(type?: string) {
  if (!type) {
    return '待优化'
  }
  const s = String(type)
  if (s === '问题') {
    return '待优化'
  }
  return s.replace(/错误/g, '优化')
}

export function getErrorLevelOrder(level?: string) {
  if (!level) {
    return 999
  }
  switch (String(level).toUpperCase()) {
    case 'FATAL':
      return 1
    case 'BASIC':
      return 2
    case 'NATURAL':
      return 3
    case 'STYLE':
      return 4
    default:
      return 999
  }
}

function calculateSentencePriority(item: AnalysisItem) {
  const errors = item.errors ?? []
  let fatal = 0
  let basic = 0
  let natural = 0
  let style = 0
  for (const err of errors) {
    switch (String(err.errorLevel ?? '').toUpperCase()) {
      case 'FATAL':
        fatal++
        break
      case 'BASIC':
        basic++
        break
      case 'NATURAL':
        natural++
        break
      case 'STYLE':
        style++
        break
    }
  }
  const score = fatal * 1000 + basic * 100 + natural * 10 + style
  return {fatal, basic, natural, style, score}
}

export function sortItemsByPriority(items: AnalysisItem[]) {
  return [...items].sort((a, b) => {
    const pa = calculateSentencePriority(a)
    const pb = calculateSentencePriority(b)
    if (pb.score !== pa.score) {
      return pb.score - pa.score
    }
    if (pb.fatal !== pa.fatal) {
      return pb.fatal - pa.fatal
    }
    if (pb.basic !== pa.basic) {
      return pb.basic - pa.basic
    }
    if (pb.natural !== pa.natural) {
      return pb.natural - pa.natural
    }
    return pb.style - pa.style
  })
}

export function sortErrors(errors: AnalysisError[]) {
  return [...errors].sort(
      (a, b) => getErrorLevelOrder(a.errorLevel) - getErrorLevelOrder(b.errorLevel),
  )
}

export function errorPointText(err: AnalysisError) {
  const raw = err.point?.trim()
  return raw?.length ? raw : '（模型未返回具体说明）'
}

export function formatProcessingTime(ms?: number) {
  if (ms == null) {
    return '—'
  }
  if (ms >= 60_000) {
    const minutes = Math.floor(ms / 60_000)
    const seconds = Math.floor((ms % 60_000) / 1000)
    return seconds > 0 ? `${minutes} 分 ${seconds} 秒` : `${minutes} 分钟`
  }
  if (ms >= 1000) {
    return `${(ms / 1000).toFixed(1)} 秒`
  }
  return `${ms} 毫秒`
}

export function scoreBarPercent(score: number): number {
    const clamped = Math.max(
        PERFORMANCE_SCORE_MIN,
        Math.min(PERFORMANCE_SCORE_MAX, score),
    )
    return Math.round(
        ((clamped - PERFORMANCE_SCORE_MIN) / (PERFORMANCE_SCORE_MAX - PERFORMANCE_SCORE_MIN)) * 100,
    )
}

export function listPerformanceDimensions(
    scores?: PerformanceDimensionScores,
): PerformanceDimensionItem[] {
    if (!scores) {
        return []
    }
    return PERFORMANCE_DIMENSION_META.flatMap((meta) => {
        const value = scores[meta.key]
        if (value == null) {
            return []
        }
        return [{...meta, value}]
    })
}

/** 优先使用后端写入的 performanceScore，旧数据回退到简易估算 */
export function resolvePerformanceScore(
    stats: EducationalSummaryStats | undefined,
    fallbackIssues: number,
    fallbackSentences: number,
): number {
    if (stats?.performanceScore != null) {
        return stats.performanceScore
    }
    return estimatePerformanceScoreLegacy(fallbackIssues, fallbackSentences)
}

/** @deprecated 仅用于无 performanceScore 的历史记录 */
export function estimatePerformanceScore(
    totalIssues: number,
    totalSentences: number,
): number {
    return estimatePerformanceScoreLegacy(totalIssues, totalSentences)
}

function estimatePerformanceScoreLegacy(totalIssues: number, totalSentences: number): number {
  const issues = totalIssues || 0
  const sentences = Math.max(1, totalSentences || 1)
  return Math.round(Math.max(55, Math.min(95, 100 - Math.min(40, Math.round((issues / sentences) * 8)))))
}
