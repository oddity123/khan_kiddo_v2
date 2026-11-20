import {loadCredential} from '../shared/credentialStore.ts'
import type {
  DailyPracticeResponse,
  MarkDailyPracticedResponse,
} from '../shared/protocol.ts'

interface ApiErrorBody {
  message?: string
}

interface RecommendBody {
  empty?: boolean
  summary?: string
  prompt?: string
  goals?: DailyPracticeResponse['goals']
  vocabulary?: DailyPracticeResponse['vocabulary']
}

async function apiFetch<T>(
  webOrigin: string,
  path: string,
  init: RequestInit = {},
  token?: string,
): Promise<T> {
  const headers = new Headers(init.headers)
  if (!headers.has('Content-Type') && init.body) {
    headers.set('Content-Type', 'application/json')
  }
  if (token) {
    headers.set('Authorization', `Bearer ${token}`)
  }
  const response = await fetch(`${webOrigin}${path}`, {...init, headers})
  const text = await response.text()
  let body: unknown = null
  if (text) {
    try {
      body = JSON.parse(text)
    } catch {
      body = null
    }
  }
  if (!response.ok) {
    const message =
      body && typeof body === 'object' && typeof (body as ApiErrorBody).message === 'string'
        ? (body as ApiErrorBody).message!
        : `请求失败 (${response.status})`
    throw new Error(message)
  }
  return body as T
}

export async function fetchDailyPractice(
  webOrigin: string,
  opts?: {excludeMemoryIds?: number[]; excludeCardIds?: string[]},
): Promise<DailyPracticeResponse> {
  const credential = await loadCredential()
  if (!credential?.token) {
    return {type: 'DAILY_PRACTICE', ok: false, error: '请先连接 Khan Kiddo 账号'}
  }
  try {
    const data = await apiFetch<RecommendBody>(
      webOrigin,
      '/api/practice/daily/recommend',
      {
        method: 'POST',
        body: JSON.stringify({
          excludeMemoryIds: opts?.excludeMemoryIds ?? [],
          excludeCardIds: opts?.excludeCardIds ?? [],
        }),
      },
      credential.token,
    )
    return {
      type: 'DAILY_PRACTICE',
      ok: true,
      empty: Boolean(data.empty),
      summary: data.summary ?? '',
      prompt: data.prompt ?? '',
      goals: data.goals ?? [],
      vocabulary: data.vocabulary ?? [],
    }
  } catch (error) {
    return {
      type: 'DAILY_PRACTICE',
      ok: false,
      error: error instanceof Error ? error.message : '加载今日练习失败',
    }
  }
}

export async function markDailyPracticed(
  webOrigin: string,
  memoryIds: number[],
): Promise<MarkDailyPracticedResponse> {
  const credential = await loadCredential()
  if (!credential?.token) {
    return {type: 'MARK_DAILY_PRACTICED_RESULT', ok: false, error: '请先连接 Khan Kiddo 账号'}
  }
  const ids = (memoryIds ?? []).filter((id) => typeof id === 'number' && Number.isFinite(id))
  if (ids.length === 0) {
    return {type: 'MARK_DAILY_PRACTICED_RESULT', ok: true}
  }
  try {
    await apiFetch<unknown>(
      webOrigin,
      '/api/practice/daily/mark-practiced',
      {
        method: 'POST',
        body: JSON.stringify({memoryIds: ids}),
      },
      credential.token,
    )
    return {type: 'MARK_DAILY_PRACTICED_RESULT', ok: true}
  } catch (error) {
    return {
      type: 'MARK_DAILY_PRACTICED_RESULT',
      ok: false,
      error: error instanceof Error ? error.message : '标记练习失败',
    }
  }
}
