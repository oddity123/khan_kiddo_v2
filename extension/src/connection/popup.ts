import {CONNECTION_PROBE_STATUS} from './background'

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
  const section = document.createElement('section')
  section.innerHTML = `<hr><h2>读取 ChatGPT 历史对话</h2>
    <p>自动读取会刷新当前对话，请先保存草稿。结果仅在浏览器内查看和复制。</p>
    <button id="readHistory">自动读取</button>
    <button id="scrollHistory">滚动读取</button>
    <button id="copyHistory" disabled>复制对话</button>
    <p id="historyStatus" role="status"></p>
    <pre id="historyText" style="white-space:pre-wrap;max-height:300px;overflow:auto;user-select:text"></pre>`
  document.body.append(section)
  const status = section.querySelector('#historyStatus')!
  const preview = section.querySelector('#historyText')!
  const copy = section.querySelector<HTMLButtonElement>('#copyHistory')!
  let transcript = ''
  let timer: number | undefined
  const show = (result: HistoryResult) => {
    transcript = result.messages?.map(message => `${message.role === 'user' ? 'User' : 'Assistant'}: ${message.text}`).join('\n\n') ?? ''
    preview.textContent = transcript
    copy.disabled = !transcript
    if (result.error) status.textContent = result.error
    else if (result.status === 'waiting') status.textContent = '等待对话加载…'
    else if (result.status === 'reading') status.textContent = `已读取 ${result.pages ?? 0} 页…`
    else if (result.status && result.status !== 'finished') status.textContent = result.status
    else if (result.completeness === 'partial') status.textContent = `读到 ${result.messageCount ?? 0} 条，历史可能不完整。可尝试滚动读取。`
    else status.textContent = `读到 ${result.messageCount ?? 0} 条，请核对对话开头。`
  }
  const activeTab = async () => {
    const [tab] = await chrome.tabs.query({active: true, currentWindow: true})
    if (tab?.id == null || !tab.url?.startsWith('https://chatgpt.com/')) throw new Error('请打开 ChatGPT 历史对话')
    const conversationId = new URL(tab.url).pathname.match(/(?:^|\/)c\/([\w-]+)\/?$/)?.[1]
    if (!conversationId) throw new Error('请先打开已有对话')
    return {tabId: tab.id, conversationId}
  }
  const refresh = async () => {
    window.clearTimeout(timer)
    try {
      const {tabId, conversationId} = await activeTab()
      const key = CONNECTION_PROBE_STATUS + tabId
      const stored = (await chrome.storage.session.get(key))[key] as HistoryResult | undefined
      if (stored?.conversationId === conversationId) { show(stored); return }
      const [frame] = await chrome.scripting.executeScript({
        target: {tabId}, world: 'MAIN',
        func: () => (window as unknown as {__kkCapture?: {history: HistoryResult}}).__kkCapture?.history,
      })
      const result = frame?.result
      if (result?.conversationId === conversationId) {
        show(result)
        if (result.status === 'waiting' || result.status === 'reading') timer = window.setTimeout(refresh, 1000)
      }
    } catch (error) { show({error: String(error)}) }
  }
  void refresh()
  chrome.storage.onChanged.addListener((changes, area) => {
    if (area === 'session' && Object.keys(changes).some(key => key.startsWith(CONNECTION_PROBE_STATUS))) void refresh()
  })
  section.querySelector('#readHistory')!.addEventListener('click', async () => {
    try {
      const {tabId} = await activeTab()
      await chrome.storage.session.remove(CONNECTION_PROBE_STATUS + tabId)
      const [frame] = await chrome.scripting.executeScript({target: {tabId}, world: 'MAIN', func: () => {
        const id = location.pathname.match(/(?:^|\/)c\/([\w-]+)\/?$/)?.[1]
        if (!id) return false
        sessionStorage.setItem('kk.connection.capture', id)
        location.reload()
        return true
      }})
      if (!frame?.result) throw new Error('请先打开已有对话')
      show({status: '页面刷新后重新打开插件，查看读取进度'})
    } catch (error) { show({error: String(error)}) }
  })
  section.querySelector('#scrollHistory')!.addEventListener('click', async () => {
    try {
      window.clearTimeout(timer)
      const {tabId, conversationId} = await activeTab()
      const result = await chrome.runtime.sendMessage({type: 'CONNECTION_PROBE_HISTORY', tabId, conversationId})
      if (result?.error) show(result)
      else await refresh()
    } catch (error) { show({error: String(error)}) }
  })
  copy.onclick = async () => {
    try {
      await navigator.clipboard.writeText(transcript)
      status.textContent = '已复制对话'
    } catch {
      status.textContent = '复制失败，请选中下方文本手动复制'
    }
  }
}
