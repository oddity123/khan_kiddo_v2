/** Float32 [-1,1] → 16-bit PCM（小端 Int16）。 */
export function floatTo16BitPcm(input: Float32Array): Int16Array {
  const out = new Int16Array(input.length)
  for (let i = 0; i < input.length; i++) {
    const s = Math.max(-1, Math.min(1, input[i]!))
    out[i] = s < 0 ? s * 0x8000 : s * 0x7fff
  }
  return out
}

/**
 * 将任意采样率的 Float32 单声道降/升采样到目标速率（默认 16k，openspeech 输入约定）。
 * 使用简单抽点，MVP 足够；不引入额外依赖。
 */
export function downsampleTo16k(
  input: Float32Array,
  inputRate: number,
  targetRate = 16_000,
): Float32Array {
  if (inputRate === targetRate) {
    return input
  }
  const ratio = inputRate / targetRate
  const newLen = Math.floor(input.length / ratio)
  const result = new Float32Array(newLen)
  for (let i = 0; i < newLen; i++) {
    const start = Math.floor(i * ratio)
    result[i] = input[start] ?? 0
  }
  return result
}

export function bytesToBase64(bytes: Uint8Array): string {
  let binary = ''
  const chunk = 0x8000
  for (let i = 0; i < bytes.length; i += chunk) {
    binary += String.fromCharCode(...bytes.subarray(i, i + chunk))
  }
  return btoa(binary)
}

/** Base64 → Int16 PCM（小端）。 */
export function base64ToInt16(base64: string): Int16Array {
  const binary = atob(base64)
  const len = binary.length
  const bytes = new Uint8Array(len)
  for (let i = 0; i < len; i++) {
    bytes[i] = binary.charCodeAt(i)
  }
  return new Int16Array(bytes.buffer, bytes.byteOffset, Math.floor(bytes.byteLength / 2))
}
