import http from '@/api/http'

import type {VoiceTranscriptLine} from '@/utils/voicePracticeTranscript'

export interface VoicePracticeBeginResult {
  id: number
  grantedSeconds: number
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

export function beginVoicePracticeSession() {
  return http.post<VoicePracticeBeginResult>('/api/voice/practice/sessions')
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
