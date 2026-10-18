import {ANALYSIS_MIN_CONTENT_LENGTH} from '../constants/analysisInput.ts'
import {VOICE_HANDOFF_STORAGE_KEY} from '../constants/voicePractice.ts'

export interface VoiceTranscriptLine {
  role: 'user' | 'assistant'
  text: string
}

export type CaptionSaveState = 'idle' | 'saving' | 'saved' | 'failed'

export function completedCaptionLines(
  lines: Array<{ role: string; text: string }>,
): VoiceTranscriptLine[] {
  const completed: VoiceTranscriptLine[] = []
  for (const line of lines) {
    if (line.role !== 'user' && line.role !== 'assistant') {
      continue
    }
    const text = line.text.trim()
    if (!text) {
      continue
    }
    completed.push({role: line.role, text})
  }
  return completed
}

export function parseVoiceTranscript(transcript: string | null | undefined): VoiceTranscriptLine[] {
  if (!transcript) {
    return []
  }
  const lines: VoiceTranscriptLine[] = []
  for (const raw of transcript.split('\n')) {
    if (raw.startsWith('User:')) {
      const text = raw.slice('User:'.length).trim()
      if (text) {
        lines.push({role: 'user', text})
      }
    } else if (raw.startsWith('Assistant:')) {
      const text = raw.slice('Assistant:'.length).trim()
      if (text) {
        lines.push({role: 'assistant', text})
      }
    }
  }
  return lines
}

/** 不能交接时返回原因；可以交接时返回空字符串。 */
export function handoffBlockReason(transcript: string | null | undefined, saved: boolean): string {
  if (!saved) {
    return '先保存这场字幕，才能送去分析'
  }
  const text = transcript?.trim() ?? ''
  if (!text) {
    return '这场还没有完成的字幕，不能送去分析'
  }
  if (text.length < ANALYSIS_MIN_CONTENT_LENGTH) {
    return '这场字幕太短，还不能分析'
  }
  return ''
}

export function captionSavePresentation(state: CaptionSaveState): { text: string; success: boolean } {
  if (state === 'saved') {
    return {text: '这场字幕已保存', success: true}
  }
  if (state === 'failed') {
    return {text: '没存上，字幕还在', success: false}
  }
  if (state === 'saving') {
    return {text: '正在保存…', success: false}
  }
  return {text: '', success: false}
}

export function formatWallClock(seconds: number): string {
  const safe = Number.isFinite(seconds) ? Math.max(0, Math.floor(seconds)) : 0
  const minutes = Math.floor(safe / 60)
  const rest = safe % 60
  return `${minutes} 分 ${rest} 秒`
}

export interface VoiceHandoffPayload {
  voiceSessionId: number
  transcript: string
}

export function writeVoiceHandoff(payload: VoiceHandoffPayload): void {
  sessionStorage.setItem(VOICE_HANDOFF_STORAGE_KEY, JSON.stringify(payload))
}

export function readVoiceHandoff(sessionId: number): VoiceHandoffPayload | null {
  try {
    const raw = sessionStorage.getItem(VOICE_HANDOFF_STORAGE_KEY)
    if (!raw) {
      return null
    }
    const parsed = JSON.parse(raw) as Partial<VoiceHandoffPayload>
    if (parsed.voiceSessionId !== sessionId || typeof parsed.transcript !== 'string') {
      return null
    }
    return {voiceSessionId: sessionId, transcript: parsed.transcript}
  } catch {
    return null
  }
}
