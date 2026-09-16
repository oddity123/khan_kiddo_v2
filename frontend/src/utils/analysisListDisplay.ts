/** 分析历史 / 管理端列表共用的展示文案与格式化。 */

export function formatListTime(value?: string): string {
  if (!value) {
    return '—'
  }
  return value.replace('T', ' ').slice(0, 19)
}

export function analysisStatusLabel(status: string): string {
  if (status === 'success') {
    return '已完成'
  }
  if (status === 'failed') {
    return '失败'
  }
  return status
}

export function analysisStatusClass(status: string): string {
  if (status === 'failed') {
    return 'status-tag--failed'
  }
  return 'status-tag--success'
}

/** 列表行耗时（短格式：ms / s），与详情页 formatProcessingTime 不同。 */
export function formatListDuration(ms?: number): string {
  if (ms == null) {
    return '—'
  }
  if (ms < 1000) {
    return `${ms} ms`
  }
  return `${(ms / 1000).toFixed(1)} s`
}

export function formatCharCount(count?: number): string {
  if (count == null) {
    return '—'
  }
  return `${count} 字`
}
