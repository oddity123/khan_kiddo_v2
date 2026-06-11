import {AUTH_TOKEN_KEY} from '@/constants/auth'
import type {Langchain4jStreamEvent} from '@/types/langchain4j'
import {LANGCHAIN4J_STREAM_STATUS} from '@/types/langchain4j'

const API_BASE = import.meta.env.VITE_API_BASE_URL || ''

function authHeaders(): HeadersInit {
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    Accept: 'text/event-stream',
  }
  const token = localStorage.getItem(AUTH_TOKEN_KEY)
  if (token) {
    headers.Authorization = `Bearer ${token}`
  }
  return headers
}

function parseSseChunk(buffer: string): { remainder: string; events: Langchain4jStreamEvent[] } {
  const events: Langchain4jStreamEvent[] = []
  const parts = buffer.split('\n\n')
  const remainder = parts.pop() ?? ''
  for (const raw of parts) {
    if (!raw.trim()) {
      continue
    }
    let dataLine = ''
    for (const line of raw.split('\n')) {
      const trimmed = line.trim()
      if (trimmed.startsWith('data:')) {
        const payload = trimmed.slice(5).trim()
        dataLine = dataLine ? `${dataLine}\n${payload}` : payload
      }
    }
    if (!dataLine) {
      continue
    }
    try {
      events.push(JSON.parse(dataLine) as Langchain4jStreamEvent)
    } catch {
      // ignore malformed chunks
    }
  }
  return {remainder, events}
}

/**
 * POST /api/langchain4j-learning/chat/stream — LangChain for Java Easy RAG（个人文档）+ 千问流式回答
 */
export async function chatLangchain4jLearningStream(
    message: string,
    onEvent: (event: Langchain4jStreamEvent) => void,
    signal?: AbortSignal,
): Promise<void> {
  const response = await fetch(`${API_BASE}/api/langchain4j-learning/chat/stream`, {
    method: 'POST',
    headers: authHeaders(),
    body: JSON.stringify({message}),
    signal,
  })

  if (!response.ok) {
    let detail = `请求失败 (${response.status})`
    try {
      const body = await response.json()
      if (body?.message) {
        detail = body.message
      }
    } catch {
      // ignore
    }
    throw new Error(detail)
  }

  const reader = response.body?.getReader()
  if (!reader) {
    throw new Error('浏览器不支持流式响应')
  }

  const decoder = new TextDecoder()
  let buffer = ''

  while (true) {
    const {done, value} = await reader.read()
    if (done) {
      const tail = parseSseChunk(`${buffer}\n\n`)
      for (const event of tail.events) {
        onEvent(event)
      }
      break
    }
    buffer += decoder.decode(value, {stream: true})
    const parsed = parseSseChunk(buffer)
    buffer = parsed.remainder
    for (const event of parsed.events) {
      onEvent(event)
      if (event.status === LANGCHAIN4J_STREAM_STATUS.ERROR) {
        throw new Error(event.message || '流式回答失败')
      }
    }
  }
}
