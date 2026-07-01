import http from './http'
import { AUTH_TOKEN_KEY } from '@/constants/auth'
import type { GrammarErrorSearchRequest, GrammarErrorSearchResponse, RagStreamEvent } from '@/types/grammarRag'
import { RAG_STREAM_STATUS } from '@/types/grammarRag'

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

function parseSseChunk(buffer: string): { remainder: string; events: RagStreamEvent[] } {
  const events: RagStreamEvent[] = []
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
      events.push(JSON.parse(dataLine) as RagStreamEvent)
    } catch {
      // ignore malformed chunks
    }
  }
  return { remainder, events }
}

export function searchGrammarErrors(payload: GrammarErrorSearchRequest) {
  return http.post<GrammarErrorSearchResponse>('/api/conversation/grammar-rag/search', payload)
}

export async function chatGrammarRagStream(
  message: string,
  onEvent: (event: RagStreamEvent) => void,
  signal?: AbortSignal,
): Promise<void> {
  const response = await fetch(`${API_BASE}/api/conversation/grammar-rag/chat/stream`, {
    method: 'POST',
    headers: authHeaders(),
    body: JSON.stringify({ message }),
    signal,
  })

  if (!response.ok) {
    let errorMessage = `问答请求失败（${response.status}）`
    try {
      const body = (await response.json()) as { message?: string }
      if (body?.message) {
        errorMessage = body.message
      }
    } catch {
      // ignore
    }
    throw new Error(errorMessage)
  }

  if (!response.body) {
    throw new Error('响应体为空')
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''

  const consume = async (): Promise<void> => {
    const { done, value } = await reader.read()
    if (done) {
      if (buffer.trim()) {
        const tail = parseSseChunk(`${buffer}\n\n`)
        for (const event of tail.events) {
          onEvent(event)
          if (event.status === RAG_STREAM_STATUS.ERROR) {
            throw new Error(event.message || '问答失败')
          }
        }
      }
      return
    }

    buffer += decoder.decode(value, { stream: true })
    const parsed = parseSseChunk(buffer)
    buffer = parsed.remainder
    for (const event of parsed.events) {
      onEvent(event)
      if (event.status === RAG_STREAM_STATUS.ERROR) {
        throw new Error(event.message || '问答失败')
      }
    }
    return consume()
  }

  await consume()
}
