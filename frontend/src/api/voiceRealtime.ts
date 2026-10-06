import http from '@/api/http'

export interface VoiceRealtimeConfig {
  configured: boolean
  wsPath: string
  model: string
  voice: string
  defaultInstructions: string
  inputSampleRate: number
  outputSampleRate: number
  chunkMs: number
  message?: string | null
}

export function fetchVoiceRealtimeConfig() {
  return http.get<VoiceRealtimeConfig>('/api/voice/realtime/config')
}

/** 将相对 wsPath 转为当前站点的 ws(s) URL，并附带 JWT。 */
export function buildVoiceRealtimeWsUrl(wsPath: string, accessToken: string): string {
  const proto = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  const host = window.location.host
  const path = wsPath.startsWith('/') ? wsPath : `/${wsPath}`
  return `${proto}//${host}${path}?access_token=${encodeURIComponent(accessToken)}`
}
