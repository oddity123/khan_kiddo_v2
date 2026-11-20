import {requireActiveChatGptTab} from '../shared/chatgptTab.ts'
import type {DailyPracticeResponse} from '../shared/protocol.ts'
import {injectComposerText} from './inject.ts'

interface DailyPracticeState {
  summary: string
  prompt: string
  memoryIds: number[]
  cardIds: string[]
}

export function mountDailyPractice() {
  const section = document.getElementById('dailyPracticeSection')
  if (!section) {
    return
  }
  const summaryEl = section.querySelector<HTMLElement>('#dailyPracticeSummary')!
  const statusEl = section.querySelector<HTMLElement>('#dailyPracticeStatus')!
  const injectBtn = section.querySelector<HTMLButtonElement>('#dailyPracticeInject')!
  const refreshBtn = section.querySelector<HTMLButtonElement>('#dailyPracticeRefresh')!

  let current: DailyPracticeState | null = null
  let loading = false

  const setStatus = (text: string, kind: '' | 'ok' | 'error' = '') => {
    statusEl.textContent = text
    statusEl.className = kind ? `status ${kind}` : 'status'
  }

  const applyPayload = (payload: DailyPracticeResponse) => {
    if (!payload.ok) {
      current = null
      summaryEl.textContent = '暂无法加载今日练习'
      setStatus(payload.error ?? '加载失败', 'error')
      injectBtn.disabled = true
      refreshBtn.disabled = false
      return
    }
    const memoryIds = (payload.goals ?? [])
      .map((g) => g.memoryId)
      .filter((id): id is number => typeof id === 'number')
    const cardIds = (payload.vocabulary ?? [])
      .map((v) => v.cardId)
      .filter((id): id is string => typeof id === 'string' && id.length > 0)
    current = {
      summary: payload.summary?.trim() || (payload.empty ? '暂无专属练习' : '今日练习已就绪'),
      prompt: payload.prompt ?? '',
      memoryIds,
      cardIds,
    }
    summaryEl.textContent = current.summary
    injectBtn.disabled = !current.prompt
    refreshBtn.disabled = false
    setStatus(payload.empty ? '可先用通用开场，分析几场后会更贴你的病灶。' : '')
  }

  const load = async (exclude = false) => {
    if (loading) {
      return
    }
    loading = true
    injectBtn.disabled = true
    refreshBtn.disabled = true
    setStatus('正在加载今日练习…')
    try {
      const response = (await chrome.runtime.sendMessage({
        type: 'GET_DAILY_PRACTICE',
        excludeMemoryIds: exclude && current ? current.memoryIds : [],
        excludeCardIds: exclude && current ? current.cardIds : [],
      })) as DailyPracticeResponse
      applyPayload(response)
    } catch (error) {
      current = null
      summaryEl.textContent = '暂无法加载今日练习'
      setStatus(error instanceof Error ? error.message : '加载失败', 'error')
      injectBtn.disabled = true
      refreshBtn.disabled = false
    } finally {
      loading = false
    }
  }

  injectBtn.addEventListener('click', async () => {
    if (!current?.prompt) {
      return
    }
    injectBtn.disabled = true
    refreshBtn.disabled = true
    setStatus('正在填入输入框…')
    try {
      const {tabId} = await requireActiveChatGptTab()
      const [frame] = await chrome.scripting.executeScript({
        target: {tabId},
        func: injectComposerText,
        args: [current.prompt],
      })
      if (!frame?.result?.ok) {
        throw new Error(frame?.result?.error ?? '未能连接页面，请刷新 ChatGPT 后重试')
      }
      if (current.memoryIds.length > 0) {
        await chrome.runtime.sendMessage({
          type: 'MARK_DAILY_PRACTICED',
          memoryIds: current.memoryIds,
        })
      }
      setStatus('已填入 ChatGPT 输入框，请检查后手动发送。', 'ok')
    } catch (error) {
      setStatus(error instanceof Error ? error.message : '注入失败，请重试', 'error')
    } finally {
      injectBtn.disabled = !current?.prompt
      refreshBtn.disabled = false
    }
  })

  refreshBtn.addEventListener('click', () => {
    void load(true)
  })

  return {
    show() {
      section.classList.remove('hidden')
      void load(false)
    },
    hide() {
      section.classList.add('hidden')
      current = null
      summaryEl.textContent = ''
      setStatus('')
    },
  }
}
