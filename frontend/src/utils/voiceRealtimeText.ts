/**
 * 合并 assistant 等「真增量」文本流。
 * ASR 预览请用 {@link pickAsrStreamingPreview}，不要对本函数喂累计快照。
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

/**
 * 从 ASR `…transcription.delta` 事件取出应展示的预览全文。
 *
 * 豆包全双工侧用户反馈与诊断表明：`delta` / `text` 常为**累计全文快照**（非纯增量）。
 * 前缀启发式合并（{@link mergeStreamingText}）在 ASR 改写、大小写/标点变化时会落到 `+=`，出现 `andand this`。
 * 最稳策略：调用方对返回值**覆盖赋值**（`userPartial = latest`），不做 append。
 *
 * 字段优先级：显式定稿/累计字段 `transcript` → `text` → `delta`。
 */
export function pickAsrStreamingPreview(event: Record<string, unknown>): string {
  for (const key of ['transcript', 'text', 'delta'] as const) {
    const value = event[key]
    if (typeof value === 'string' && value.length > 0) {
      return value
    }
  }
  return ''
}

/**
 * 将连续 ASR 预览帧折叠为最终预览串（覆盖赋值语义，供单测与调用方复用）。
 */
export function foldAsrStreamingPreviews(frames: ReadonlyArray<Record<string, unknown>>): string {
  let preview = ''
  for (const frame of frames) {
    const latest = pickAsrStreamingPreview(frame)
    if (latest) {
      preview = latest
    }
  }
  return preview
}
