import {loadCredential} from '../shared/credentialStore.ts'
import type {
  DailyPracticeResponse,
  MarkDailyPracticedResponse,
} from '../shared/protocol.ts'

export const PENDING_PRACTICE_SESSIONS_KEY = 'kk.extension.practiceSession.pendingByTab'
const PENDING_SESSION_TTL_MS = 12 * 60 * 60 * 1000

interface PendingPracticeSessionEntry {
  sessionIds: number[]
  injectedAt: string
}

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

interface MarkPracticedBody {
  sessionIds?: number[]
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

async function loadPendingMap(): Promise<Record<string, PendingPracticeSessionEntry>> {
  const stored = await chrome.storage.local.get(PENDING_PRACTICE_SESSIONS_KEY)
  const raw = stored[PENDING_PRACTICE_SESSIONS_KEY]
  if (!raw || typeof raw !== 'object') {
    return {}
  }
  const now = Date.now()
  const cleaned: Record<string, PendingPracticeSessionEntry> = {}
  for (const [key, entry] of Object.entries(raw as Record<string, unknown>)) {
    if (!entry || typeof entry !== 'object') {
      continue
    }
    const typed = entry as PendingPracticeSessionEntry
    const ts = Date.parse(typed.injectedAt)
    if (
      Array.isArray(typed.sessionIds) &&
      typed.sessionIds.length > 0 &&
      Number.isFinite(ts) &&
      now - ts <= PENDING_SESSION_TTL_MS
    ) {
      cleaned[key] = typed
    }
  }
  return cleaned
}

async function savePendingForTab(tabId: number, sessionIds: number[]): Promise<void> {
  const map = await loadPendingMap()
  map[String(tabId)] = {
    sessionIds,
    injectedAt: new Date().toISOString(),
  }
  await chrome.storage.local.set({[PENDING_PRACTICE_SESSIONS_KEY]: map})
}

export async function clearPendingPracticeSessions(): Promise<void> {
  await chrome.storage.local.remove(PENDING_PRACTICE_SESSIONS_KEY)
}

export async function bindPendingPracticeSession(
  webOrigin: string,
  tabId: number,
  sourceConversationId: string,
): Promise<void> {
  const convId = sourceConversationId.trim()
  if (!convId || !Number.isFinite(tabId)) {
    return
  }
  const map = await loadPendingMap()
  const key = String(tabId)
  const entry = map[key]
  if (!entry || entry.sessionIds.length === 0) {
    return
  }
  const credential = await loadCredential()
  if (!credential?.token) {
    return
  }
  try {
    await apiFetch<unknown>(
      webOrigin,
      '/api/practice/sessions/bind-conversation',
      {
        method: 'POST',
        body: JSON.stringify({
          sessionIds: entry.sessionIds,
          sourceConversationId: convId,
        }),
      },
      credential.token,
    )
    delete map[key]
    await chrome.storage.local.set({[PENDING_PRACTICE_SESSIONS_KEY]: map})
  } catch {
    // 不阻断主分析提交流程
  }
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
  opts: {
    memoryIds: number[]
    sourceConversationId?: string | null
    tabId?: number
    practiceType?: string
  },
): Promise<MarkDailyPracticedResponse> {
  const credential = await loadCredential()
  if (!credential?.token) {
    return {type: 'MARK_DAILY_PRACTICED_RESULT', ok: false, error: '请先连接 Khan Kiddo 账号'}
  }
  const ids = (opts.memoryIds ?? []).filter((id) => typeof id === 'number' && Number.isFinite(id))
  if (ids.length === 0) {
    return {type: 'MARK_DAILY_PRACTICED_RESULT', ok: true, sessionIds: []}
  }
  const convId = opts.sourceConversationId?.trim() || null
  try {
    const data = await apiFetch<MarkPracticedBody | null>(
      webOrigin,
      '/api/practice/daily/mark-practiced',
      {
        method: 'POST',
        body: JSON.stringify({
          memoryIds: ids,
          sourceConversationId: convId || undefined,
          practiceType: opts.practiceType ?? 'SCENARIO',
        }),
      },
      credential.token,
    )
    const sessionIds = Array.isArray(data?.sessionIds)
      ? data.sessionIds.filter((id): id is number => typeof id === 'number' && Number.isFinite(id))
      : []
    if (!convId && typeof opts.tabId === 'number' && Number.isFinite(opts.tabId) && sessionIds.length > 0) {
      await savePendingForTab(opts.tabId, sessionIds)
    }
    return {type: 'MARK_DAILY_PRACTICED_RESULT', ok: true, sessionIds}
  } catch (error) {
    return {
      type: 'MARK_DAILY_PRACTICED_RESULT',
      ok: false,
      error: error instanceof Error ? error.message : '标记练习失败',
    }
  }
}
