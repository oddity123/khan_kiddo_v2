// Serialized as a standalone MAIN-world document_start script in development only.
// Exposes window.__kkStartHistoryCapture(conversationId) for on-demand reads without reload.
export function installResponseCapture() {
  const startHistoryCapture = (target: string) => {
    if (!target) return
    const state: {active: boolean; history: unknown; generation: number} = {
      active: true,
      generation: ((window as unknown as {__kkCapture?: {generation?: number}}).__kkCapture?.generation ?? 0) + 1,
      history: {status: 'waiting', conversationId: target, pages: 0, rawMessageCount: 0},
    }
    ;(window as unknown as Record<string, unknown>).__kkCapture = state
    const generation = state.generation
    const original = window.fetch
    const base = `/backend-api/conversations/${encodeURIComponent(target)}`
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
    const stillCurrent = () =>
      state.active &&
      (window as unknown as {__kkCapture?: {generation?: number}}).__kkCapture?.generation === generation &&
      location.pathname.match(/(?:^|\/)c\/([\w-]+)\/?$/)?.[1] === target

    const finishEmpty = (stopReason: string) => {
      state.history = {
        status: 'finished',
        conversationId: target,
        pages: 0,
        rawMessageCount: 0,
        messageCount: 0,
        stopReason,
        completeness: 'partial',
        messages: [],
      }
      state.active = false
    }

    const resolveAccessToken = async (): Promise<string | null> => {
      try {
        const response = await original.call(window, '/api/auth/session', {credentials: 'include'})
        if (!response.ok) return null
        const data = await response.json()
        return typeof data?.accessToken === 'string' && data.accessToken ? data.accessToken : null
      } catch {
        return null
      }
    }

    const buildHeaders = (accessToken: string | null): Headers => {
      const headers = new Headers({Accept: 'application/json'})
      if (accessToken) {
        headers.set('Authorization', `Bearer ${accessToken}`)
      }
      return headers
    }

    const readHistory = async (first: any, initialUrl: URL, headers: Headers) => {
      const nodes = new Map<string, any>()
      const cursors = new Set<string>()
      let page = first
      let oldestPageIds: string[] = []
      let pages = 0
      let stopReason = 'page-limit'
      state.history = {status: 'reading', conversationId: target, pages, rawMessageCount: 0}
      try {
        while (pages < 60) {
          if (!stillCurrent()) {
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
            method: 'GET',
            headers,
            credentials: 'include',
            redirect: 'error',
            signal: AbortSignal.timeout(8000),
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

      if (!stillCurrent() && stopReason === 'page-limit') {
        stopReason = 'capture-ended-or-conversation-changed'
      }

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
      state.active = false
    }

    void (async () => {
      try {
        const accessToken = await resolveAccessToken()
        if (!stillCurrent()) return
        if (!accessToken) {
          finishEmpty('missing-access-token')
          return
        }
        const headers = buildHeaders(accessToken)
        const url = new URL(base, location.origin)
        url.searchParams.set('num_turns', '10')
        url.searchParams.set('include_has_versions', 'true')
        const response = await original.call(window, url.href, {
          method: 'GET',
          headers,
          credentials: 'include',
          redirect: 'error',
          signal: AbortSignal.timeout(15000),
        })
        if (!stillCurrent()) return
        if (!response.ok) {
          finishEmpty(`http-${response.status}`)
          return
        }
        const data = await response.json()
        if (!stillCurrent()) return
        if (!Array.isArray(data?.messages) || typeof data.current_node !== 'string') {
          finishEmpty('unexpected-page-shape')
          return
        }
        if (data.conversation_id != null && data.conversation_id !== target) {
          finishEmpty('conversation-mismatch')
          return
        }
        await readHistory(data, url, headers)
      } catch {
        if (!stillCurrent()) return
        finishEmpty('request-failed')
      }
    })()
  }

  ;(window as unknown as Record<string, unknown>).__kkStartHistoryCapture = startHistoryCapture
}
