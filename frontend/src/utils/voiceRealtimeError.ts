/**
 * 解析上游/代理 error 帧。常见形状：
 * - `{ type, message, code? }`（本站代理）
 * - `{ type, error: { message, code? }, code? }`（openspeech 嵌套）
 */
export function formatRealtimeError(event: Record<string, unknown>): string {
  const nested =
    event.error && typeof event.error === 'object'
      ? (event.error as Record<string, unknown>)
      : null
  const messageCandidates = [event.message, nested?.message]
  const message = messageCandidates.find((v): v is string => typeof v === 'string' && v.trim().length > 0)
  const codeCandidates = [event.code, nested?.code]
  const code = codeCandidates.find((v) => v !== undefined && v !== null && String(v).length > 0)
  if (message && code !== undefined) {
    return `${message}（code=${String(code)}）`
  }
  if (message) {
    return message
  }
  if (code !== undefined) {
    return `实时语音错误 code=${String(code)}`
  }
  try {
    const snippet = JSON.stringify(event)
    return `实时语音服务返回错误：${snippet.length > 240 ? `${snippet.slice(0, 240)}…` : snippet}`
  } catch {
    return '实时语音服务返回错误'
  }
}
