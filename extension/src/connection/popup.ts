import {requireActiveChatGptTab} from '../shared/chatgptTab.ts'
import type {
  AnalysisTaskStatusResponse,
  SettingsResponse,
  SubmitAnalysisResult,
} from '../shared/protocol'

interface HistoryResult {
  status?: string
  conversationId?: string
  pages?: number
  messageCount?: number
  completeness?: string
  stopReason?: string
  error?: string
  messages?: {role: string; text: string}[]
}

export function mountConnectionProbe() {
  const section = document.getElementById('historySection')!
  section.classList.remove('hidden')
  const status = section.querySelector('#historyStatus')!
  const badge = section.querySelector('#historyTaskBadge') as HTMLElement
  const preview = section.querySelector('#historyText')!
  const copy = section.querySelector<HTMLButtonElement>('#copyHistory')!
  const analyze = section.querySelector<HTMLButtonElement>('#analyzeHistory')!
  let transcript = ''
  let sourceConversationId: string | undefined
  let activeTabId: number | undefined
  let reportAnalysisId: string | undefined
  let taskRunning = false
  let timer: number | undefined
  let taskPollTimer: number | undefined
  let generation = 0
  let readingTarget: {tabId: number; conversationId: string} | undefined
  let analyzing = false
  /** 一键分析：读取完成后自动提交 */
  let submitAfterRead = false

  const setBadge = (text: string, kind: '' | 'done' | 'running' | 'failed' = '') => {
    badge.textContent = text
    badge.className = kind ? `task-badge is-${kind}` : 'task-badge'
  }

  const paintGuide = (kind: 'idle' | 'running' | 'done' | 'failed', detail?: string) => {
    if (kind === 'running') {
      status.textContent = '正在分析中，可关闭弹窗；完成后会自动打开报告'
      return
    }
    if (kind === 'done') {
      status.textContent = '本对话已分析完成，点击「查看报告」查看结果'
      return
    }
    if (kind === 'failed') {
      status.textContent = detail
        ? `分析失败：${detail}。可再次点击「一键分析」重试`
        : '分析失败，可再次点击「一键分析」重试'
      return
    }
    status.textContent = '尚未分析，点击「一键分析」读取当前对话并开始分析'
  }

  const syncAnalyzeButton = () => {
    if (reportAnalysisId) {
      analyze.textContent = '查看报告'
      analyze.disabled = analyzing || !!readingTarget
      return
    }
    if (taskRunning || analyzing) {
      analyze.textContent = '分析中'
      analyze.disabled = true
      return
    }
    analyze.textContent = '一键分析'
    analyze.disabled = !!readingTarget
  }

  const stopTaskPoll = () => {
    window.clearTimeout(taskPollTimer)
    taskPollTimer = undefined
  }

  const scheduleTaskPoll = (conversationId: string) => {
    stopTaskPoll()
    taskPollTimer = window.setTimeout(() => {
      void refreshTaskStatus(conversationId)
    }, 2000)
  }

  const applyTaskStatus = (task: AnalysisTaskStatusResponse) => {
    if (task.running) {
      reportAnalysisId = undefined
      taskRunning = true
      setBadge('分析中', 'running')
      paintGuide('running')
      syncAnalyzeButton()
      if (task.sourceConversationId || sourceConversationId) {
        scheduleTaskPoll(task.sourceConversationId || sourceConversationId!)
      }
      return
    }
    stopTaskPoll()
    taskRunning = false
    if (task.status === 'FAILED') {
      reportAnalysisId = undefined
      setBadge('失败', 'failed')
      paintGuide('failed', task.error || undefined)
      syncAnalyzeButton()
      return
    }
    if (task.status === 'SUCCEEDED' && task.analysisId) {
      reportAnalysisId = task.analysisId
      setBadge('已分析', 'done')
      paintGuide('done')
      syncAnalyzeButton()
      return
    }
    reportAnalysisId = undefined
    setBadge('')
    paintGuide('idle')
    syncAnalyzeButton()
  }

  const refreshTaskStatus = async (conversationId?: string) => {
    const id = conversationId || sourceConversationId
    if (!id) return
    try {
      const task = (await chrome.runtime.sendMessage({
        type: 'GET_ANALYSIS_TASK',
        sourceType: 'chatgpt_history',
        sourceConversationId: id,
      })) as AnalysisTaskStatusResponse
      applyTaskStatus(task)
    } catch {
      // ignore status probe failures
    }
  }

  const submitAnalysis = async () => {
    if (!transcript || analyzing || taskRunning) return
    analyzing = true
    taskRunning = true
    syncAnalyzeButton()
    setBadge('分析中', 'running')
    paintGuide('running')
    try {
      const result = (await chrome.runtime.sendMessage({
        type: 'SUBMIT_ANALYSIS',
        conversationContent: transcript,
        sourceType: 'chatgpt_history',
        sourceConversationId: sourceConversationId ?? null,
        tabId: activeTabId,
      })) as SubmitAnalysisResult
      if (!result.ok) {
        taskRunning = false
        setBadge('失败', 'failed')
        paintGuide('failed', result.error || '提交失败')
        return
      }
      if (result.status === 'SUCCEEDED' && result.analysisId) {
        taskRunning = false
        reportAnalysisId = result.analysisId
        setBadge('已分析', 'done')
        paintGuide('done')
        return
      }
      taskRunning = true
      setBadge('分析中', 'running')
      paintGuide('running')
      if (sourceConversationId) {
        scheduleTaskPoll(sourceConversationId)
      }
    } catch (error) {
      taskRunning = false
      setBadge('失败', 'failed')
      paintGuide('failed', error instanceof Error ? error.message : '提交失败')
    } finally {
      analyzing = false
      syncAnalyzeButton()
    }
  }

  const show = (result: HistoryResult) => {
    transcript = result.messages?.map(message => `${message.role === 'user' ? 'User' : 'Assistant'}: ${message.text}`).join('\n\n') ?? ''
    if (result.conversationId) {
      sourceConversationId = result.conversationId
    }
    preview.textContent = transcript
    copy.disabled = !transcript
    const busy = result.status === 'waiting' || result.status === 'reading'
    if (!busy) {
      readingTarget = undefined
    }
    syncAnalyzeButton()
    if (result.error) status.textContent = result.error
    else if (result.status === 'waiting') status.textContent = '正在读取对话…'
    else if (result.status === 'reading') status.textContent = `已读取 ${result.pages ?? 0} 页…`
    else if (result.status && result.status !== 'finished') status.textContent = result.status
    else if ((result.messageCount ?? 0) === 0) {
      const reason = result.stopReason ? `（${result.stopReason}）` : ''
      status.textContent = `未读到消息${reason}。可刷新 ChatGPT 后重试，或用分享链接导入。`
      submitAfterRead = false
    }
    else if (result.status === 'finished') {
      // 终态文案由 applyTaskStatus / submitAnalysis 写入，避免残留「读到 N 条」
      if (!submitAfterRead) {
        if (reportAnalysisId) paintGuide('done')
        else if (taskRunning) paintGuide('running')
        else paintGuide('idle')
      }
    }

    if (result.conversationId && result.status === 'finished' && (result.messageCount ?? 0) > 0) {
      if (submitAfterRead) {
        submitAfterRead = false
        void submitAnalysis()
        return
      }
      void refreshTaskStatus(result.conversationId)
    }
  }

  const activeTab = async () => {
    const {tabId, url} = await requireActiveChatGptTab()
    const conversationId = url.pathname.match(/(?:^|\/)c\/([\w-]+)\/?$/)?.[1]
    if (!conversationId) throw new Error('请先打开已有对话')
    activeTabId = tabId
    return {tabId, conversationId}
  }

  const scheduleRefresh = (run: number) => {
    timer = window.setTimeout(() => { void refresh(run) }, 1000)
  }

  const refresh = async (run: number) => {
    window.clearTimeout(timer)
    try {
      const target = await activeTab()
      if (run !== generation) return
      if (readingTarget && (readingTarget.tabId !== target.tabId || readingTarget.conversationId !== target.conversationId)) {
        throw new Error('当前对话已切换，请重新点击一键分析')
      }
      const [frame] = await chrome.scripting.executeScript({
        target: {tabId: target.tabId}, world: 'MAIN',
        func: () => (window as unknown as {__kkCapture?: {history: HistoryResult}}).__kkCapture?.history,
      })
      if (run !== generation) return
      const result = frame?.result
      if (result?.conversationId === target.conversationId) {
        show(result)
        if (result.status === 'waiting' || result.status === 'reading') scheduleRefresh(run)
      } else if (readingTarget) {
        scheduleRefresh(run)
      }
    } catch (error) {
      if (run !== generation) return
      readingTarget = undefined
      submitAfterRead = false
      show({error: error instanceof Error ? error.message : String(error)})
    }
  }

  const startRead = async () => {
    const run = ++generation
    window.clearTimeout(timer)
    readingTarget = undefined
    show({status: 'waiting'})
    status.textContent = '正在读取对话…'
    try {
      const target = await activeTab()
      if (run !== generation) return
      await chrome.scripting.executeScript({
        target: {tabId: target.tabId},
        world: 'MAIN',
        files: ['connection-capture.js'],
      })
      if (run !== generation) return
      const [started] = await chrome.scripting.executeScript({
        target: {tabId: target.tabId},
        world: 'MAIN',
        func: (conversationId: string) => {
          const start = (window as unknown as {
            __kkStartHistoryCapture?: (id: string) => void
          }).__kkStartHistoryCapture
          if (typeof start !== 'function') return false
          start(conversationId)
          return true
        },
        args: [target.conversationId],
      })
      if (run !== generation) return
      if (!started?.result) throw new Error('未能启动读取，请刷新 ChatGPT 页面后重试')
      readingTarget = target
      sourceConversationId = target.conversationId
      syncAnalyzeButton()
      scheduleRefresh(run)
    } catch (error) {
      if (run !== generation) return
      readingTarget = undefined
      submitAfterRead = false
      show({error: error instanceof Error ? error.message : String(error)})
    }
  }

  void refresh(generation)

  window.addEventListener('pagehide', () => {
    generation++
    window.clearTimeout(timer)
    stopTaskPoll()
  }, {once: true})

  copy.addEventListener('click', async () => {
    try {
      await navigator.clipboard.writeText(transcript)
      status.textContent = '已复制对话'
    } catch {
      status.textContent = '复制失败，请选中下方文本手动复制'
    }
  })

  analyze.addEventListener('click', async () => {
    if (analyzing || readingTarget || taskRunning) return
    if (reportAnalysisId) {
      try {
        const settings = (await chrome.runtime.sendMessage({
          type: 'GET_SETTINGS',
        })) as SettingsResponse
        await chrome.tabs.create({
          url: `${settings.webOrigin}/conversation/analyses/${encodeURIComponent(reportAnalysisId)}`,
          active: true,
        })
      } catch (error) {
        status.textContent = error instanceof Error ? error.message : '打开报告失败'
      }
      return
    }
    submitAfterRead = true
    await startRead()
  })

  void (async () => {
    try {
      const target = await activeTab()
      sourceConversationId = target.conversationId
      await refreshTaskStatus(target.conversationId)
    } catch {
      const task = (await chrome.runtime.sendMessage({
        type: 'GET_ANALYSIS_TASK',
      })) as AnalysisTaskStatusResponse
      applyTaskStatus(task)
    }
  })()
}
