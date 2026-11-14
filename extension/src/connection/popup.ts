import {requireActiveChatGptTab} from '../shared/chatgptTab.ts'

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
  const preview = section.querySelector('#historyText')!
  const copy = section.querySelector<HTMLButtonElement>('#copyHistory')!
  let transcript = ''
  const read = section.querySelector<HTMLButtonElement>('#readHistory')!
  let timer: number | undefined
  let generation = 0
  let reloadDeadline = 0
  let readingTarget: {tabId: number; conversationId: string} | undefined
  const show = (result: HistoryResult) => {
    read.disabled = result.status === 'waiting' || result.status === 'reading'
    transcript = result.messages?.map(message => `${message.role === 'user' ? 'User' : 'Assistant'}: ${message.text}`).join('\n\n') ?? ''
    preview.textContent = transcript
    copy.disabled = !transcript
    if (result.error) status.textContent = result.error
    else if (result.status === 'waiting') status.textContent = '等待对话加载…'
    else if (result.status === 'reading') status.textContent = `已读取 ${result.pages ?? 0} 页…`
    else if (result.status && result.status !== 'finished') status.textContent = result.status
    else if (result.completeness === 'partial') status.textContent = `读到 ${result.messageCount ?? 0} 条，历史可能不完整，可通过分享链接导入。`
    else status.textContent = `读到 ${result.messageCount ?? 0} 条，请核对对话开头。`
  }
  const activeTab = async () => {
    const {tabId, url} = await requireActiveChatGptTab()
    const conversationId = url.pathname.match(/(?:^|\/)c\/([\w-]+)\/?$/)?.[1]
    if (!conversationId) throw new Error('请先打开已有对话')
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
        reloadDeadline = 0
        throw new Error('当前对话已切换，请重新点击自动读取')
      }
      const [frame] = await chrome.scripting.executeScript({
        target: {tabId: target.tabId}, world: 'MAIN',
        func: () => (window as unknown as {__kkCapture?: {history: HistoryResult}}).__kkCapture?.history,
      })
      if (run !== generation) return
      const result = frame?.result
      if (result?.conversationId === target.conversationId) {
        reloadDeadline = 0
        show(result)
        if (result.status === 'waiting' || result.status === 'reading') scheduleRefresh(run)
      } else if (reloadDeadline) {
        if (Date.now() >= reloadDeadline) throw new Error('未能启动读取，请等待页面加载完成后重试')
        scheduleRefresh(run)
      }
    } catch (error) {
      if (run !== generation) return
      if (reloadDeadline && Date.now() < reloadDeadline) {
        scheduleRefresh(run)
      } else {
        reloadDeadline = 0
        readingTarget = undefined
        show({error: error instanceof Error ? error.message : String(error)})
      }
    }
  }
  void refresh(generation)
  read.addEventListener('click', async () => {
    const run = ++generation
    window.clearTimeout(timer)
    reloadDeadline = 0
    readingTarget = undefined
    show({status: 'waiting'})
    status.textContent = '正在刷新页面，随后自动更新读取进度…'
    try {
      const target = await activeTab()
      if (run !== generation) return
      const [frame] = await chrome.scripting.executeScript({target: {tabId: target.tabId}, world: 'MAIN', func: () => {
        const id = location.pathname.match(/(?:^|\/)c\/([\w-]+)\/?$/)?.[1]
        if (!id) return false
        sessionStorage.setItem('kk.connection.capture', id)
        delete (window as unknown as {__kkCapture?: unknown}).__kkCapture
        return true
      }})
      if (run !== generation) return
      if (!frame?.result) throw new Error('请先打开已有对话')
      readingTarget = target
      // Acknowledge the marker before navigation can destroy the injected context.
      await chrome.tabs.reload(target.tabId)
      if (run !== generation) return
      reloadDeadline = Date.now() + 15000
      scheduleRefresh(run)
    } catch (error) {
      if (run !== generation) return
      readingTarget = undefined
      reloadDeadline = 0
      show({error: error instanceof Error ? error.message : String(error)})
    }
  })
  window.addEventListener('pagehide', () => {
    generation++
    window.clearTimeout(timer)
  }, {once: true})
  copy.onclick = async () => {
    try {
      await navigator.clipboard.writeText(transcript)
      status.textContent = '已复制对话'
    } catch {
      status.textContent = '复制失败，请选中下方文本手动复制'
    }
  }
}
