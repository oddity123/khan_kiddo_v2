export function parseChatGptShareId(shareUrl: string): string | null {
  try {
    const url = new URL(shareUrl.trim())
    const host = url.hostname.toLowerCase()
    if (host !== 'chatgpt.com' && host !== 'chat.openai.com' && host !== 'www.chatgpt.com') {
      return null
    }
    const parts = url.pathname.split('/').filter(Boolean)
    const shareIdx = parts.indexOf('share')
    if (shareIdx < 0 || shareIdx + 1 >= parts.length) {
      return null
    }
    const id = parts[shareIdx + 1]
    return id && id.length > 8 ? id : null
  } catch {
    return null
  }
}

export function isChatGptShareUrl(shareUrl: string): boolean {
  return parseChatGptShareId(shareUrl) != null
}
