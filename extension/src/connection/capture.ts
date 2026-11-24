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
    const getParentId = (message: any): string | null => {
      const parent = message?.metadata?.parent_id ?? message?.parent_id ?? null
      return typeof parent === 'string' && parent ? parent : null
    }
    const readMessage = (message: any) => {
      const role = message?.author?.role
      if ((role !== 'user' && role !== 'assistant') || message.metadata?.is_visually_hidden_from_conversation) return []
      const collectText = (part: unknown, depth = 0): string[] => {
        if (depth > 6) return []
        if (typeof part === 'string') return [part]
        if (!part || typeof part !== 'object') return []
        const value = part as Record<string, unknown>
        if (typeof value.text === 'string' && value.text.trim()) return [value.text]
        return Array.isArray(value.parts) ? value.parts.flatMap(child => collectText(child, depth + 1)) : []
      }
      const text = collectText(message.content).join('\n').trim()
      return text ? [{messageId: message.id, parentId: getParentId(message), role, text}] : []
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
        const response = await original.call(window, '/api/auth/session', {
          credentials: 'include',
          cache: 'no-store',
        })
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

    const readDomMessages = (): Array<{messageId: string | null; role: 'user' | 'assistant'; text: string}> => {
      if (typeof document === 'undefined' || typeof document.querySelectorAll !== 'function') return []
      const elements = Array.from(document.querySelectorAll('[data-message-author-role]')) as any[]
      const extracted: Array<{messageId: string | null; role: 'user' | 'assistant'; text: string}> = []
      for (const el of elements) {
        const role = el?.getAttribute?.('data-message-author-role')
        if (role !== 'user' && role !== 'assistant') continue
        const messageId = el?.getAttribute?.('data-message-id') || null
        let text = ''
        if (typeof el.querySelectorAll === 'function') {
          const blocks = Array.from(el.querySelectorAll('.markdown, .whitespace-pre-wrap')) as any[]
          if (blocks.length > 0) {
            text = blocks
              .map(node => String(node?.innerText ?? node?.textContent ?? '').trim())
              .filter(Boolean)
              .join('\n')
              .trim()
          }
        }
        if (!text) {
          text = String(el?.innerText ?? el?.textContent ?? '').trim()
        }
        if (text) {
          extracted.push({messageId, role, text})
        }
      }
      return extracted
    }

    const supplementTailFromDom = (
      apiMessages: Array<{messageId: string; parentId: string | null; role: string; text: string}>,
      visited: Set<string>,
    ) => {
      if (!apiMessages.length) return apiMessages
      const domMessages = readDomMessages()
      if (!domMessages.length) return apiMessages
      const lastApi = apiMessages[apiMessages.length - 1]
      const normalize = (value: string) => value.replace(/\s+/g, ' ').trim()
      let anchorIndex = -1
      if (lastApi.messageId) {
        anchorIndex = domMessages.findIndex(item => item.messageId === lastApi.messageId)
      }
      if (anchorIndex < 0) {
        const targetText = normalize(lastApi.text)
        for (let i = domMessages.length - 1; i >= 0; i--) {
          if (domMessages[i].role === lastApi.role && normalize(domMessages[i].text) === targetText) {
            anchorIndex = i
            break
          }
        }
      }
      if (anchorIndex < 0 || anchorIndex >= domMessages.length - 1) return apiMessages
      const merged = apiMessages.slice()
      for (let i = anchorIndex + 1; i < domMessages.length; i++) {
        const item = domMessages[i]
        if (item.messageId && visited.has(item.messageId)) continue
        merged.push({
          messageId: item.messageId ?? `dom-${i}`,
          parentId: null,
          role: item.role,
          text: item.text,
        })
      }
      return merged
    }

    const readHistory = async (first: any, initialUrl: URL, headers: Headers) => {
      const nodes = new Map<string, any>()
      const cursors = new Set<string>()
      let page = first
      let oldestPageIds: string[] = []
      let pages = 0
      let stopReason = 'page-limit'
      let latestCurrentNode: unknown = first.current_node
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
            cache: 'no-store',
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

        // 若首页之后还有更新页（has_next_page），继续向后翻页补齐最新轮次
        let nextPage = first
        const nextCursors = new Set<string>()
        while (pages < 60 && nextPage?.page_info?.has_next_page === true) {
          if (!stillCurrent()) {
            stopReason = 'capture-ended-or-conversation-changed'
            break
          }
          const endCursor = nextPage.page_info?.end_cursor
          if (typeof endCursor !== 'string' || !endCursor || nextCursors.has(endCursor)) {
            break
          }
          nextCursors.add(endCursor)
          const url = new URL(`${base}/messages`, location.origin)
          url.searchParams.set('after', endCursor)
          url.searchParams.set('num_turns', initialUrl.searchParams.get('num_turns') ?? '10')
          url.searchParams.set('include_has_versions', initialUrl.searchParams.get('include_has_versions') ?? 'true')
          const response = await original.call(window, url.href, {
            method: 'GET',
            headers,
            credentials: 'include',
            cache: 'no-store',
            redirect: 'error',
            signal: AbortSignal.timeout(8000),
          })
          if (!response.ok) break
          const newer = await response.json()
          if (!Array.isArray(newer?.messages) || (newer.conversation_id != null && newer.conversation_id !== target)) {
            break
          }
          for (const message of newer.messages) {
            if (typeof message?.id === 'string' && !nodes.has(message.id)) nodes.set(message.id, message)
          }
          if (typeof newer.current_node === 'string' && newer.current_node) {
            latestCurrentNode = newer.current_node
          }
          pages++
          state.history = {status: 'reading', conversationId: target, pages, rawMessageCount: nodes.size}
          nextPage = newer
        }
      } catch { stopReason = 'request-failed' }

      if (!stillCurrent() && stopReason === 'page-limit') {
        stopReason = 'capture-ended-or-conversation-changed'
      }

      // 若 current_node 滞后且 nodes 中已有其更新子节点，先沿最新子节点推进到叶子节点
      const childrenByParent = new Map<string, any[]>()
      for (const message of nodes.values()) {
        const parentId = getParentId(message)
        if (!parentId) continue
        const list = childrenByParent.get(parentId)
        if (list) list.push(message)
        else childrenByParent.set(parentId, [message])
      }
      let leafId: unknown = latestCurrentNode
      const forwardVisited = new Set<string>()
      while (typeof leafId === 'string' && leafId && !forwardVisited.has(leafId)) {
        forwardVisited.add(leafId)
        const children = (childrenByParent.get(leafId) ?? [])
          .filter(child => typeof child?.id === 'string' && !forwardVisited.has(child.id))
        if (!children.length) break
        let best = children[children.length - 1]
        let bestTime = typeof best?.create_time === 'number' ? best.create_time : -Infinity
        for (const child of children) {
          const t = typeof child?.create_time === 'number' ? child.create_time : -Infinity
          if (t > bestTime) {
            best = child
            bestTime = t
          }
        }
        leafId = best.id
      }

      const chain = []
      const visited = new Set<string>()
      let id: unknown = leafId
      let branchStatus = 'root-reached'
      while (typeof id === 'string' && id) {
        if (visited.has(id)) { branchStatus = 'cycle'; break }
        const message = nodes.get(id)
        if (!message) { branchStatus = 'missing-parent'; break }
        visited.add(id)
        chain.push(message)
        id = getParentId(message)
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
      const messages = supplementTailFromDom(chain.reverse().flatMap(readMessage), visited)
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
          cache: 'no-store',
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
