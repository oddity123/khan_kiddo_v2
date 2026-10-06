/**
 * 合并实时语音流式文本片段。
 * 上游可能发「累计全文快照」或「真增量」；盲目 += 会叠字。
 */
export function mergeStreamingText(current: string, delta: string): string {
  if (!delta) {
    return current
  }
  if (!current) {
    return delta
  }
  // 累计快照：新全文包含已有内容
  if (delta.startsWith(current)) {
    return delta
  }
  // 乱序/回退的较短快照：保持已有
  if (current.startsWith(delta)) {
    return current
  }
  // 真增量片段（如 token 流）
  return current + delta
}
