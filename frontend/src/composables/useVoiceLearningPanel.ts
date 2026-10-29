import {computed, ref} from 'vue'

import {fetchVoiceLearningPanel} from '@/api/voicePractice'
import {getErrorMessage} from '@/utils/error'
import {
  applyLearningPanelEvent,
  emptyLearningPanel,
  mergeLearningPanel,
  type VoiceLearningPanelEvent,
  type VoiceLearningPanelState,
} from '@/utils/voiceLearningPanel'

/**
 * 右侧「本场学习」面板的状态：实时事件即时叠加，REST hydrate 兜底刷新/回看。
 * 纯合并逻辑在 utils/voiceLearningPanel，这里只编排。
 */
export function useVoiceLearningPanel() {
  const state = ref<VoiceLearningPanelState>(emptyLearningPanel())
  const loading = ref(false)
  const error = ref('')
  /** 防止慢响应覆盖切换后的场次 */
  let hydrateToken = 0

  const focus = computed(() => state.value.focus)
  const attempts = computed(() => state.value.attempts)
  const notes = computed(() => state.value.notes)
  const hasContent = computed(() =>
    Boolean(state.value.focus) || state.value.attempts.length > 0 || state.value.notes.length > 0)
  const itemCount = computed(() => state.value.attempts.length + state.value.notes.length)

  function reset() {
    hydrateToken += 1
    state.value = emptyLearningPanel()
    error.value = ''
    loading.value = false
  }

  function applyEvent(event: VoiceLearningPanelEvent) {
    state.value = applyLearningPanelEvent(state.value, event)
  }

  async function hydrate(practiceSessionId: number) {
    const token = ++hydrateToken
    loading.value = true
    error.value = ''
    try {
      const {data} = await fetchVoiceLearningPanel(practiceSessionId)
      if (token !== hydrateToken) {
        return
      }
      state.value = mergeLearningPanel(state.value, data)
    } catch (e) {
      if (token === hydrateToken) {
        error.value = getErrorMessage(e, '读取本场学习记录失败')
      }
    } finally {
      if (token === hydrateToken) {
        loading.value = false
      }
    }
  }

  return {state, focus, attempts, notes, hasContent, itemCount, loading, error, reset, applyEvent, hydrate}
}
