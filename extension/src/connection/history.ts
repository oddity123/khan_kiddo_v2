import {inspectConversationPage} from './pageAdapter'

// Self-contained injected operation. Identify the scroll ancestor from actual messages.
export function scrollHistory(operation: 'bottom' | 'up' | 'restore', expectedPath: string, position = 0) {
  if (location.pathname !== expectedPath) throw new Error('对话已切换，停止读取')
  let node = document.querySelector<HTMLElement>('[data-chatgpt-conversation-selection-target], [data-message-author-role]')
  while (node && !(node.scrollHeight > node.clientHeight && /auto|scroll/.test(getComputedStyle(node).overflowY))) node = node.parentElement
  if (!node) throw new Error('未找到对话滚动容器')
  const before = node.scrollTop
  const height = node.scrollHeight
  if (operation === 'bottom') node.scrollTop = height
  if (operation === 'up') node.scrollTop = Math.max(0, before - Math.max(100, node.clientHeight * 0.6))
  if (operation === 'restore') node.scrollTop = position
  return {before, top: node.scrollTop, height}
}

export async function collectHistory(tabId: number, progress: (value: object) => Promise<void>) {
  const target = {tabId}
  const read = async () => {
    const [frame] = await chrome.scripting.executeScript({target, func: inspectConversationPage})
    const result = frame?.result
    if (!result?.ok || !result.conversationId) throw new Error('请打开已加载的历史对话')
    if (result.generating) throw new Error('请等待回复结束后读取')
    return result
  }
  const initial = await read()
  const path = initial.diagnostics!.path
  const scroll = async (operation: 'bottom' | 'up' | 'restore', position = 0) => {
    const [frame] = await chrome.scripting.executeScript({target, func: scrollHistory, args: [operation, path, position]})
    if (!frame?.result) throw new Error('滚动失败或页面已切换')
    return frame.result
  }
  const start = await scroll('bottom')
  const messages = new Map<string, NonNullable<typeof initial.messages>[number]>()
  let order: string[] = []
  let stable = 0
  let previous = ''
  let lastHeight = start.height
  let stopReason = 'limit-reached'
  try {
    for (let step = 0; step < 120; step++) {
      await new Promise(resolve => setTimeout(resolve, 800))
      const snapshot = await read()
      if (snapshot.diagnostics!.path !== path) throw new Error('对话已切换，停止读取')
      const keys: string[] = []
      for (const message of snapshot.messages!) {
        if (!message.messageId) throw new Error('消息缺少稳定标识，无法安全合并历史')
        keys.push(message.messageId)
        messages.set(message.messageId, message)
      }
      // Each upward viewport overlaps the previous one. Never deduplicate by text:
      // repeated voice acknowledgements are separate messages.
      const newKeys = keys.filter(key => !order.includes(key))
      order = [...newKeys, ...order]
      const state = await scroll('up')
      lastHeight = state.height
      const signature = JSON.stringify([keys, state.height])
      stable = state.before === 0 && state.top === 0 && signature === previous ? stable + 1 : 0
      previous = signature
      await progress({status: '向上加载历史，请勿切换对话或手动滚动', messageCount: messages.size, step: step + 1})
      if (stable >= 5) { stopReason = 'top-stable-unverified'; break }
    }
    return {conversationId: initial.conversationId, completeness: 'unverified', stopReason,
      messageCount: order.length, messages: order.map(key => messages.get(key)),
      reason: '已向上滚动收集；顶部稳定不等于服务端全文已加载，请核对最早一句。'}
  } finally {
    // Older content prepended above changes absolute scroll offsets.
    await scroll('restore', start.before + Math.max(0, lastHeight - start.height)).catch(() => undefined)
  }
}
