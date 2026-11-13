// Serialized as a standalone MAIN-world document_start script in development only.
export function installResponseCapture() {
  const key = 'kk.connection.capture'
  const target = sessionStorage.getItem(key)
  const current = location.pathname.match(/(?:^|\/)c\/([\w-]+)\/?$/)?.[1]
  if (!target || current !== target) return
  sessionStorage.removeItem(key)
  const state: {active: boolean; history: unknown} = {
    active: true, history: {status: 'waiting', conversationId: target, pages: 0, rawMessageCount: 0},
  }
  ;(window as unknown as Record<string, unknown>).__kkCapture = state
  const original = window.fetch
  const base = `/backend-api/conversations/${encodeURIComponent(target)}`
  let started = false
  const readMessage = (message: any) => {
    const role = message?.author?.role
    if ((role !== 'user' && role !== 'assistant') || message.metadata?.is_visually_hidden_from_conversation) return []
    const collectText = (part: unknown, depth = 0): string[] => {
      if (depth > 6) return []
      if (typeof part === 'string') return [part]
      if (!part || typeof part !== 'object') return []
      const value = part as Record<string, unknown>
      if (typeof value.text === 'string') return [value.text]
      return Array.isArray(value.parts) ? value.parts.flatMap(child => collectText(child, depth + 1)) : []
    }
    const text = collectText(message.content).join('\n').trim()
    return text ? [{messageId: message.id, parentId: message.metadata?.parent_id ?? null, role, text}] : []
  }
  const readHistory = async (first: any, initialUrl: URL, request: Request) => {
    const nodes = new Map<string, any>()
    const cursors = new Set<string>()
    let page = first
    let oldestPageIds: string[] = []
    let pages = 0
    let stopReason = 'page-limit'
    state.history = {status: 'reading', conversationId: target, pages, rawMessageCount: 0}
    try {
      while (pages < 60) {
        if (!state.active || location.pathname.match(/(?:^|\/)c\/([\w-]+)\/?$/)?.[1] !== target) {
          stopReason = 'capture-ended-or-conversation-changed'
          break
        }
        for (const message of page.messages) {
          if (typeof message?.id === 'string' && !nodes.has(message.id)) nodes.set(message.id, message)
        }
        if (page.messages.length) {
          oldestPageIds = page.messages.flatMap((message: any) => typeof message?.id === 'string' ? [message.id] : [])
        }
        pages++
        state.history = {status: 'reading', conversationId: target, pages, rawMessageCount: nodes.size}
        if (page.page_info?.has_previous_page === false) { stopReason = 'no-previous-page'; break }
        const cursor = page.page_info?.start_cursor
        if (page.page_info?.has_previous_page !== true || typeof cursor !== 'string' || !cursor || cursors.has(cursor)) {
          stopReason = 'invalid-or-repeated-cursor'
          break
        }
        if (pages === 60) break
        cursors.add(cursor)
        const url = new URL(`${base}/messages`, location.origin)
        url.searchParams.set('before', cursor)
        url.searchParams.set('num_turns', initialUrl.searchParams.get('num_turns') ?? '10')
        url.searchParams.set('include_has_versions', initialUrl.searchParams.get('include_has_versions') ?? 'true')
        const response = await original.call(window, url.href, {
          headers: request.headers, credentials: request.credentials, redirect: 'error', signal: AbortSignal.timeout(8000),
        })
        if (!response.ok) { stopReason = `http-${response.status}`; break }
        const older = await response.json()
        if (!Array.isArray(older?.messages) || (older.conversation_id != null && older.conversation_id !== target)) {
          stopReason = 'unexpected-page-shape'
          break
        }
        page = older
      }
    } catch { stopReason = 'request-failed' }

    const chain = []
    const visited = new Set<string>()
    let id: unknown = first.current_node
    let branchStatus = 'root-reached'
    while (typeof id === 'string' && id) {
      if (visited.has(id)) { branchStatus = 'cycle'; break }
      const message = nodes.get(id)
      if (!message) { branchStatus = 'missing-parent'; break }
      visited.add(id)
      chain.push(message)
      id = message.metadata?.parent_id ?? null
    }
    if (!chain.length) branchStatus = 'missing-current-node'
    const oldestNodeId = chain.at(-1)?.id
    const oldestPageIndex = oldestPageIds.indexOf(oldestNodeId)
    let readableOutsideBranch = 0
    for (const [messageId, message] of nodes) {
      if (!visited.has(messageId)) readableOutsideBranch += readMessage(message).length
    }
    // An external parent at the first record of the oldest page is distinct from
    // a broken link inside the returned history. Keep the boundary unverified.
    if (branchStatus === 'missing-parent' && stopReason === 'no-previous-page' &&
        oldestPageIndex === 0 && readableOutsideBranch === 0) {
      branchStatus = 'oldest-page-boundary'
    }
    const messages = chain.reverse().flatMap(readMessage)
    const reachedRoot = stopReason === 'no-previous-page' && branchStatus === 'root-reached'
    state.history = {status: 'finished', conversationId: target, pages, rawMessageCount: nodes.size,
      messageCount: messages.length, stopReason, branchStatus,
      oldestPageIndex: oldestPageIndex < 0 ? null : oldestPageIndex, readableOutsideBranch,
      completeness: reachedRoot || branchStatus === 'oldest-page-boundary' ? 'unverified' : 'partial', messages}
  }
  const wrapped: typeof fetch = async function (this: Window, ...args) {
    const response = await original.apply(this, args)
    if (started || !state.active || !response.ok || !response.headers.get('content-type')?.includes('application/json')) return response
    try {
      const input = args[0]
      const url = new URL(input instanceof Request ? input.url : String(input), location.href)
      if (url.origin !== 'https://chatgpt.com' || url.pathname !== base || url.searchParams.has('before')) return response
      if (location.pathname.match(/(?:^|\/)c\/([\w-]+)\/?$/)?.[1] !== target) return response
      const context = new Request(input instanceof Request ? input : url, args[1])
      if (context.method !== 'GET') return response
      void response.clone().json().then(data => {
        if (started || !state.active || !Array.isArray(data?.messages) || typeof data.current_node !== 'string') return
        if (data.conversation_id != null && data.conversation_id !== target) return
        if (location.pathname.match(/(?:^|\/)c\/([\w-]+)\/?$/)?.[1] !== target) return
        started = true
        return readHistory(data, url, context)
      }).catch(() => undefined)
    } catch { /* Observation must never break the page's request. */ }
    return response
  }
  window.fetch = wrapped
  setTimeout(() => {
    state.active = false
    if (window.fetch === wrapped) window.fetch = original
    if (!started) state.history = {status: 'finished', conversationId: target, pages: 0, rawMessageCount: 0,
      messageCount: 0, stopReason: 'no-initial-page', completeness: 'partial'}
  }, 120000)
}
