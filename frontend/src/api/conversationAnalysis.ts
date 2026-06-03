import http from './http'
import { AUTH_TOKEN_KEY } from '@/constants/auth'
import type {
  ConversationAnalysisDetail,
  ConversationAnalysisListResponse,
  ConversationAnalysisProgress,
  ConversationAnalysisRequest,
  ConversationAnalysisResult,
  ConversationAnalysisSaveRequest,
} from '@/types/conversation'
import { PROGRESS_STATUS } from '@/types/conversation'

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

function parseSseChunk(buffer: string): { remainder: string; events: ConversationAnalysisProgress[] } {
  const events: ConversationAnalysisProgress[] = []
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
      events.push(JSON.parse(dataLine) as ConversationAnalysisProgress)
    } catch {
      // ignore malformed chunks
    }
  }
  return { remainder, events }
}

/**
 * POST /api/conversation/analyze/stream — SSE progress events, resolves on COMPLETED.
 */
export async function analyzeConversationStream(
  payload: ConversationAnalysisRequest,
  onProgress: (progress: ConversationAnalysisProgress) => void,
  signal?: AbortSignal,
): Promise<ConversationAnalysisResult> {
  const response = await fetch(`${API_BASE}/api/conversation/analyze/stream`, {
    method: 'POST',
    headers: authHeaders(),
    body: JSON.stringify(payload),
    signal,
  })

  if (!response.ok) {
    let message = `分析请求失败（${response.status}）`
    try {
      const body = (await response.json()) as { message?: string }
      if (body?.message) {
        message = body.message
      }
    } catch {
      // ignore
    }
    throw new Error(message)
  }

  if (!response.body) {
    throw new Error('响应体为空')
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  let lastResult: ConversationAnalysisResult | undefined

  const consume = async (): Promise<ConversationAnalysisResult> => {
    const { done, value } = await reader.read()
    if (done) {
      if (buffer.trim()) {
        const tail = parseSseChunk(`${buffer}\n\n`)
        for (const event of tail.events) {
          onProgress(event)
          if (event.status === PROGRESS_STATUS.COMPLETED && event.result) {
            lastResult = event.result
          }
          if (event.status === PROGRESS_STATUS.ERROR) {
            const err = new Error(event.errorMessage || event.message || '分析失败') as Error & {
              analysisId?: string
            }
            if (event.analysisId) {
              err.analysisId = event.analysisId
            }
            throw err
          }
        }
      }
      if (lastResult) {
        return lastResult
      }
      throw new Error('分析未完成')
    }

    buffer += decoder.decode(value, { stream: true })
    const parsed = parseSseChunk(buffer)
    buffer = parsed.remainder
    for (const event of parsed.events) {
      onProgress(event)
      if (event.status === PROGRESS_STATUS.COMPLETED && event.result) {
        lastResult = event.result
      }
      if (event.status === PROGRESS_STATUS.ERROR) {
        const err = new Error(event.errorMessage || event.message || '分析失败') as Error & {
          analysisId?: string
        }
        if (event.analysisId) {
          err.analysisId = event.analysisId
        }
        throw err
      }
    }
    return consume()
  }

  return consume()
}

export function saveConversationAnalysis(payload: ConversationAnalysisSaveRequest) {
  return http.post<ConversationAnalysisResult>('/api/conversation/analyses', payload)
}

export function listConversationAnalyses(params?: {
  page?: number
  size?: number
  keyword?: string
}) {
  return http.get<ConversationAnalysisListResponse>('/api/conversation/analyses', { params })
}

export function getConversationAnalysisDetail(analysisId: string) {
  return http.get<ConversationAnalysisDetail>(`/api/conversation/analyses/${analysisId}`)
}

export function deleteConversationAnalysis(analysisId: string) {
  return http.delete(`/api/conversation/analyses/${analysisId}`)
}
