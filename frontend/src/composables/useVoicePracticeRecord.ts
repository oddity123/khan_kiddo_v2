import axios from 'axios'
import {computed, onBeforeUnmount, ref} from 'vue'

import {
  abandonVoicePracticeSession,
  beginVoicePracticeSession,
  listVoicePracticeSessions,
  saveVoicePracticeTranscript,
  type VoicePracticeSession,
} from '@/api/voicePractice'
import {trackFunnelEvent} from '@/api/funnel'
import {VOICE_QUOTA_EXHAUSTED_MESSAGE} from '@/constants/voicePractice'
import {getErrorMessage} from '@/utils/error'
import {
  completedCaptionLines,
  type CaptionSaveState,
  type VoiceTranscriptLine,
} from '@/utils/voicePracticeTranscript'

/**
 * 练习记录的开始、保存、最近列表。字幕文本留在调用方，保存失败时这里不宣称成功。
 */
export function useVoicePracticeRecord() {
  const sessions = ref<VoicePracticeSession[]>([])
  const activeId = ref<number | null>(null)
  const grantedSeconds = ref(0)
  const saveState = ref<CaptionSaveState>('idle')
  const saveError = ref('')
  const review = ref<VoicePracticeSession | null>(null)
  const quotaHit = ref(false)
  const quotaBlocked = ref(false)
  const beginError = ref('')
  const wentLive = ref(false)

  let startedAtMs = 0
  let completion: Promise<void> | null = null
  let capTimer: ReturnType<typeof setTimeout> | null = null

  const quotaMessage = computed(() =>
    quotaHit.value || quotaBlocked.value ? VOICE_QUOTA_EXHAUSTED_MESSAGE : '')

  const activeSaved = computed(() =>
    sessions.value.find((session) => session.id === activeId.value) ?? null)

  async function refresh() {
    try {
      const {data} = await listVoicePracticeSessions()
      sessions.value = data ?? []
    } catch {
      sessions.value = []
    }
  }

  function clearCapTimer() {
    if (capTimer) {
      clearTimeout(capTimer)
      capTimer = null
    }
  }

  function resetActive() {
    clearCapTimer()
    activeId.value = null
    grantedSeconds.value = 0
    saveState.value = 'idle'
    saveError.value = ''
    wentLive.value = false
    startedAtMs = 0
  }

  async function begin(): Promise<boolean> {
    beginError.value = ''
    quotaHit.value = false
    review.value = null
    completion = null
    resetActive()
    try {
      const {data} = await beginVoicePracticeSession()
      activeId.value = data.id
      grantedSeconds.value = data.grantedSeconds
      startedAtMs = Date.now()
      quotaBlocked.value = false
      return true
    } catch (error) {
      if (axios.isAxiosError(error) && error.response?.status === 409) {
        quotaBlocked.value = true
        beginError.value = ''
      } else {
        beginError.value = getErrorMessage(error, '现在开始不了练习')
      }
      return false
    }
  }

  function markLive() {
    if (wentLive.value || activeId.value == null) {
      return
    }
    wentLive.value = true
    trackFunnelEvent({name: 'voice_start', voiceSessionId: activeId.value})
  }

  function armClientCap(onFire: () => void) {
    clearCapTimer()
    const leftMs = grantedSeconds.value * 1000 - (Date.now() - startedAtMs)
    if (leftMs <= 0) {
      onFire()
      return
    }
    capTimer = setTimeout(onFire, leftMs)
  }

  async function abandonIfUnused() {
    const id = activeId.value
    clearCapTimer()
    if (id == null || wentLive.value) {
      return
    }
    try {
      await abandonVoicePracticeSession(id)
    } catch {
      // 服务端断开时仍会按墙钟收口
    }
    resetActive()
  }

  async function saveLines(lines: VoiceTranscriptLine[], stoppedByCap: boolean): Promise<boolean> {
    const id = activeId.value
    if (id == null) {
      saveState.value = 'failed'
      saveError.value = '没有可保存的练习会话'
      return false
    }
    saveState.value = 'saving'
    saveError.value = ''
    try {
      const {data} = await saveVoicePracticeTranscript(id, {lines, stoppedByCap})
      saveState.value = 'saved'
      quotaHit.value = quotaHit.value || data.stoppedByCap
      if (data.stoppedByCap) {
        quotaBlocked.value = true
      }
      trackFunnelEvent({name: 'voice_saved', voiceSessionId: data.id})
      await refresh()
      return true
    } catch (error) {
      saveState.value = 'failed'
      saveError.value = getErrorMessage(error, '保存失败')
      return false
    }
  }

  function noteQuota() {
    quotaHit.value = true
  }

  function complete(
    readLines: () => Array<{ role: string; text: string }>,
    stoppedByCap: boolean,
    stopMedia: () => Promise<void>,
  ): Promise<void> {
    if (completion) {
      return completion
    }
    if (stoppedByCap) {
      quotaHit.value = true
    }
    completion = doComplete(readLines, stoppedByCap, stopMedia)
    return completion
  }

  async function doComplete(
    readLines: () => Array<{ role: string; text: string }>,
    stoppedByCap: boolean,
    stopMedia: () => Promise<void>,
  ) {
    clearCapTimer()
    const lines = completedCaptionLines(readLines())
    const seconds = startedAtMs === 0
      ? 0
      : Math.max(0, Math.round((Date.now() - startedAtMs) / 1000))
    const hadLive = wentLive.value
    try {
      await stopMedia()
    } catch {
      // 媒体停失败也要尝试保存字幕
    }
    if (!hadLive && lines.length === 0 && !stoppedByCap) {
      await abandonIfUnused()
      return
    }
    if (activeId.value != null) {
      trackFunnelEvent({
        name: 'voice_end',
        durationSeconds: seconds,
        voiceSessionId: activeId.value,
      })
    }
    await saveLines(lines, stoppedByCap || quotaHit.value)
  }

  function openReview(session: VoicePracticeSession) {
    if (saveState.value === 'failed') {
      return
    }
    review.value = session
  }

  function closeReview() {
    review.value = null
  }

  onBeforeUnmount(() => {
    clearCapTimer()
  })

  return {
    sessions,
    activeId,
    grantedSeconds,
    saveState,
    saveError,
    review,
    quotaHit,
    quotaBlocked,
    quotaMessage,
    beginError,
    wentLive,
    activeSaved,
    refresh,
    begin,
    markLive,
    armClientCap,
    abandonIfUnused,
    saveLines,
    noteQuota,
    complete,
    openReview,
    closeReview,
  }
}
