// Functions passed to executeScript must be self-contained (no runtime imports).
export function inspectConversationPage() {
  try {
    const url = new URL(location.href)
    if (url.origin !== 'https://chatgpt.com') throw new Error('仅支持 chatgpt.com')
    const conversationId = url.pathname.match(/(?:^|\/)c\/([\w-]+)\/?$/)?.[1] ?? null
    const legacyNodes = Array.from(document.querySelectorAll<HTMLElement>('[data-message-author-role]'))
    const messageNodes = legacyNodes.length ? legacyNodes : Array.from(document.querySelectorAll<HTMLElement>(
      '[data-chatgpt-conversation-selection-target] [data-user-message-bubble], ' +
      '[data-chatgpt-conversation-selection-target] [data-selected-text-overlay-target][data-markdown-text-style]',
    ))
    const messages = messageNodes
      .filter(el => !el.closest('[hidden], [aria-hidden="true"]') && el.getClientRects().length > 0)
      .flatMap(el => {
        const role = legacyNodes.length ? el.dataset.messageAuthorRole
          : el.hasAttribute('data-user-message-bubble') ? 'user' : 'assistant'
        if (role !== 'user' && role !== 'assistant') return []
        const body = legacyNodes.length ? el.querySelector<HTMLElement>('.markdown, .whitespace-pre-wrap') ?? el : el
        const text = body.innerText.trim()
        return text ? [{role, text, messageId: el.dataset.messageId ??
          el.closest('[data-chatgpt-selection-message-id]')?.getAttribute('data-chatgpt-selection-message-id') ??
          (el.closest('[data-turn-key]')?.getAttribute('data-turn-key')
            ? `${el.closest('[data-turn-key]')!.getAttribute('data-turn-key')}:${role}` : null)}] : []
      })
    return {
      ok: true,
      adapter: legacyNodes.length ? 'legacy-role' : 'conversation-selection',
      diagnostics: {path: url.pathname},
      conversationId,
      messages,
      generating: !!document.querySelector('[data-testid="stop-button"]'),
      completeness: 'unverified' as const,
      reason: 'DOM 无法证明未加载消息、分支和语音字幕完整；仅用于本地检查，请保留分享导入。',
    }
  } catch (error) {
    return {ok: false, error: error instanceof Error ? error.message : String(error), path: location.pathname}
  }
}
