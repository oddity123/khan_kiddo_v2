import http from './http'
import type {ShadowingScore, ShadowingStatus} from '@/types/shadowing'

function sentencePath(analysisId: string, sentenceId: string | number) {
  return `/api/shadowing/analyses/${encodeURIComponent(analysisId)}/sentences/${encodeURIComponent(String(sentenceId))}`
}

export function fetchShadowingStatus() {
  return http.get<ShadowingStatus>('/api/shadowing/status')
}

/** `<audio src>` 带不上 JWT，只能取 Blob 再转 object URL */
export function fetchShadowingAudio(analysisId: string, sentenceId: string | number) {
  return http.get<Blob>(`${sentencePath(analysisId, sentenceId)}/audio`, {responseType: 'blob'})
}

export function scoreShadowing(analysisId: string, sentenceId: string | number, wav: Blob) {
  const form = new FormData()
  form.append('audio', wav, 'shadowing.wav')
  return http.post<ShadowingScore>(`${sentencePath(analysisId, sentenceId)}/score`, form, {
    timeout: 60_000,
  })
}
