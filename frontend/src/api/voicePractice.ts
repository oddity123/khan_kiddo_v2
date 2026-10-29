import http from '@/api/http'

import type {VoiceLearningPanelState} from '@/utils/voiceLearningPanel'
import type {VoiceTranscriptLine} from '@/utils/voicePracticeTranscript'

export interface VoicePracticeMaterialSelection {
  cardIds: string[]
  analysisIds: string[]
}

export interface VoicePracticeBeginResult {
  id: number
  grantedSeconds: number
  materials?: VoicePracticeMaterialSelection | null
}

export interface VoicePracticeSession {
  id: number
  startedAt: string
  endedAt?: string | null
  wallDurationSeconds: number
  stoppedByCap: boolean
  transcriptSaved: boolean
  transcript?: string | null
}

export function beginVoicePracticeSession(body?: VoicePracticeMaterialSelection) {
  return http.post<VoicePracticeBeginResult>('/api/voice/practice/sessions', body ?? {})
}

export function abandonVoicePracticeSession(id: number) {
  return http.post(`/api/voice/practice/sessions/${id}/abandon`)
}

export function saveVoicePracticeTranscript(
  id: number,
  body: { lines: VoiceTranscriptLine[]; stoppedByCap: boolean },
) {
  return http.put<VoicePracticeSession>(`/api/voice/practice/sessions/${id}/transcript`, body)
}

export function listVoicePracticeSessions() {
  return http.get<VoicePracticeSession[]>('/api/voice/practice/sessions')
}

export function fetchVoicePracticeSession(id: number) {
  return http.get<VoicePracticeSession>(`/api/voice/practice/sessions/${id}`)
}

/** 本场学习证据（焦点卡 / 尝试 / 备注）；刷新或回看时恢复右侧面板。 */
export function fetchVoiceLearningPanel(id: number) {
  return http.get<VoiceLearningPanelState>(`/api/voice/practice/sessions/${id}/learning`)
}
