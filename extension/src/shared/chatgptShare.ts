export interface ExtractedMessage {
  role: 'user' | 'assistant'
  text: string
  createTime: number | null
}

export interface ExtractedShareConversation {
  shareId: string
  title: string | null
  messages: ExtractedMessage[]
}

interface SharePartObject {
  content_type?: string
  text?: string
}

interface ShareMessageContent {
  content_type?: string
  parts?: Array<string | SharePartObject>
  text?: string
}

interface ShareMessage {
  id?: string
  author?: {role?: string}
  create_time?: number
  content?: ShareMessageContent
  metadata?: {
    is_visually_hidden_from_conversation?: boolean
  }
}

interface ShareNode {
  id?: string
  message?: ShareMessage
}

interface ShareApiResponse {
  title?: string
  mapping?: Record<string, ShareNode>
  linear_conversation?: ShareNode[]
}

function extractTextFromContent(content: ShareMessageContent | undefined): string {
  if (!content) {
    return ''
  }
  if (typeof content.text === 'string' && content.text.trim()) {
    return content.text.trim()
  }
  const parts = content.parts
  if (!Array.isArray(parts)) {
    return ''
  }
  const chunks: string[] = []
  for (const part of parts) {
    if (typeof part === 'string') {
      if (part.trim()) {
        chunks.push(part)
      }
      continue
    }
    if (part && typeof part === 'object') {
      if (part.content_type === 'audio_transcription' && typeof part.text === 'string') {
        if (part.text.trim()) {
          chunks.push(part.text)
        }
      } else if (typeof part.text === 'string' && part.text.trim()) {
        chunks.push(part.text)
      }
    }
  }
  return chunks.join('\n').trim()
}

function nodesFromResponse(data: ShareApiResponse): ShareNode[] {
  if (Array.isArray(data.linear_conversation) && data.linear_conversation.length > 0) {
    return data.linear_conversation
  }
  return Object.values(data.mapping ?? {})
}

export function parseShareApiResponse(shareId: string, data: ShareApiResponse): ExtractedShareConversation {
  const messages: ExtractedMessage[] = []
  for (const node of nodesFromResponse(data)) {
    const msg = node.message
    if (!msg) {
      continue
    }
    const role = msg.author?.role
    if (role !== 'user' && role !== 'assistant') {
      continue
    }
    if (msg.metadata?.is_visually_hidden_from_conversation) {
      continue
    }
    const text = extractTextFromContent(msg.content)
    if (!text) {
      continue
    }
    messages.push({
      role,
      text,
      createTime: typeof msg.create_time === 'number' ? msg.create_time : null,
    })
  }

  messages.sort((a, b) => (a.createTime ?? 0) - (b.createTime ?? 0))

  return {
    shareId,
    title: typeof data.title === 'string' && data.title.trim() ? data.title.trim() : null,
    messages,
  }
}

export function formatConversationContent(messages: ExtractedMessage[]): string {
  return messages
      .map((m) => {
        const label = m.role === 'user' ? 'User' : 'Assistant'
        return `**${label}:** ${m.text}`
      })
      .join('\n\n')
}

export async function fetchChatGptShare(shareId: string): Promise<ExtractedShareConversation> {
  const url = `https://chatgpt.com/backend-api/share/${encodeURIComponent(shareId)}`
  const response = await fetch(url, {
    method: 'GET',
    headers: {
      Accept: 'application/json',
    },
    credentials: 'omit',
  })
  if (!response.ok) {
    throw new Error(`拉取分享失败（HTTP ${response.status}）`)
  }
  const data = (await response.json()) as ShareApiResponse
  const parsed = parseShareApiResponse(shareId, data)
  if (parsed.messages.length === 0) {
    throw new Error('分享中未找到可用对话字幕（可能已过期或非公开）')
  }
  return parsed
}
