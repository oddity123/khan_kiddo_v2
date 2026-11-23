import {loadCredential} from '../shared/credentialStore'
import type {AnalysisTaskStatusResponse, SubmitAnalysisResult} from '../shared/protocol'
import {bindPendingPracticeSession} from './dailyPractice'

const PENDING_TASK_KEY = 'kk.extension.analysisTask.pending'

interface PendingTask {
  taskId: string
  webOrigin: string
  startedAt: string
}

interface TaskConfig {
  enabled: boolean
  pollIntervalMs: number
  pollTimeoutMs: number
}

interface ApiErrorBody {
  message?: string
}

let pollLoop: Promise<void> | null = null

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

function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

async function loadPending(): Promise<PendingTask | null> {
  const stored = await chrome.storage.local.get(PENDING_TASK_KEY)
  const value = stored[PENDING_TASK_KEY]
  if (!value || typeof value !== 'object') {
    return null
  }
  const pending = value as PendingTask
  if (typeof pending.taskId !== 'string' || typeof pending.webOrigin !== 'string') {
    return null
  }
  return pending
}

async function savePending(pending: PendingTask): Promise<void> {
  await chrome.storage.local.set({[PENDING_TASK_KEY]: pending})
}

async function clearPending(): Promise<void> {
  await chrome.storage.local.remove(PENDING_TASK_KEY)
}

async function loadConfig(webOrigin: string, token: string): Promise<TaskConfig> {
  const data = await apiFetch<TaskConfig>(webOrigin, '/api/conversation/analysis-tasks/config', {}, token)
  return {
    enabled: data.enabled !== false,
    pollIntervalMs: data.pollIntervalMs > 0 ? data.pollIntervalMs : 2000,
    pollTimeoutMs: data.pollTimeoutMs > 0 ? data.pollTimeoutMs : 900_000,
  }
}

function ensurePollRunning(): void {
  if (pollLoop) {
    return
  }
  pollLoop = (async () => {
    try {
      const pending = await loadPending()
      if (!pending) {
        return
      }
      const cred = await loadCredential()
      if (!cred) {
        await clearPending()
        return
      }
      const config = await loadConfig(pending.webOrigin, cred.token)
      const deadline = Date.now() + config.pollTimeoutMs
      while (Date.now() < deadline) {
        const task = await apiFetch<{
          taskId: string
          status: string
          analysisId?: string
          errorMessage?: string
        }>(pending.webOrigin, `/api/conversation/analysis-tasks/${encodeURIComponent(pending.taskId)}`, {}, cred.token)
        if (task.status === 'SUCCEEDED' && task.analysisId) {
          await clearPending()
          await chrome.tabs.create({
            url: `${pending.webOrigin}/conversation/analyses/${encodeURIComponent(task.analysisId)}`,
            active: true,
          })
          return
        }
        if (task.status === 'FAILED') {
          await clearPending()
          return
        }
        await sleep(config.pollIntervalMs)
      }
      await clearPending()
    } catch {
      await clearPending()
    } finally {
      pollLoop = null
    }
  })()
}

export async function submitAnalysis(
  webOrigin: string,
  ensureHostPermission: (origin: string) => Promise<void>,
  payload: {
    conversationContent: string
    sourceType: string
    sourceConversationId?: string | null
    tabId?: number
  },
): Promise<SubmitAnalysisResult> {
  try {
    await ensureHostPermission(webOrigin)
    const cred = await loadCredential()
    if (!cred) {
      return {type: 'SUBMIT_ANALYSIS_RESULT', ok: false, error: '请先连接 Khan Kiddo 账号'}
    }
    const config = await loadConfig(webOrigin, cred.token)
    if (!config.enabled) {
      return {type: 'SUBMIT_ANALYSIS_RESULT', ok: false, error: '站点暂未开放一键分析'}
    }
    const content = payload.conversationContent.trim()
    if (!content) {
      return {type: 'SUBMIT_ANALYSIS_RESULT', ok: false, error: '没有可分析的对话内容'}
    }
    if (typeof payload.tabId === 'number' && payload.sourceConversationId?.trim()) {
      await bindPendingPracticeSession(webOrigin, payload.tabId, payload.sourceConversationId)
    }
    const created = await apiFetch<{
      taskId: string
      status: string
      analysisId?: string
    }>(
      webOrigin,
      '/api/conversation/analysis-tasks',
      {
        method: 'POST',
        body: JSON.stringify({
          conversationContent: content,
          sourceType: payload.sourceType,
          sourceConversationId: payload.sourceConversationId || undefined,
        }),
      },
      cred.token,
    )
    if (created.status === 'SUCCEEDED' && created.analysisId) {
      await chrome.tabs.create({
        url: `${webOrigin}/conversation/analyses/${encodeURIComponent(created.analysisId)}`,
        active: true,
      })
      return {
        type: 'SUBMIT_ANALYSIS_RESULT',
        ok: true,
        taskId: created.taskId,
        status: created.status,
        analysisId: created.analysisId,
      }
    }
    await savePending({
      taskId: created.taskId,
      webOrigin,
      startedAt: new Date().toISOString(),
    })
    ensurePollRunning()
    return {
      type: 'SUBMIT_ANALYSIS_RESULT',
      ok: true,
      taskId: created.taskId,
      status: created.status,
    }
  } catch (error) {
    return {
      type: 'SUBMIT_ANALYSIS_RESULT',
      ok: false,
      error: error instanceof Error ? error.message : '提交失败',
    }
  }
}

interface TaskBody {
  taskId: string
  status: string
  analysisId?: string
  errorMessage?: string
  sourceConversationId?: string | null
}

function toStatusResponse(task: TaskBody, running: boolean): AnalysisTaskStatusResponse {
  return {
    type: 'ANALYSIS_TASK_STATUS',
    running,
    found: true,
    taskId: task.taskId,
    status: task.status,
    analysisId: task.analysisId,
    error: task.errorMessage,
    sourceConversationId: task.sourceConversationId,
  }
}

async function lookupBySource(
  webOrigin: string,
  token: string,
  sourceType: string,
  sourceConversationId: string,
): Promise<AnalysisTaskStatusResponse> {
  const params = new URLSearchParams({
    sourceType,
    sourceConversationId,
  })
  const lookup = await apiFetch<{found: boolean; task?: TaskBody}>(
    webOrigin,
    `/api/conversation/analysis-tasks/by-source?${params}`,
    {},
    token,
  )
  if (!lookup.found || !lookup.task) {
    return {
      type: 'ANALYSIS_TASK_STATUS',
      running: false,
      found: false,
      sourceConversationId,
    }
  }
  const running = lookup.task.status === 'QUEUED' || lookup.task.status === 'RUNNING'
  return toStatusResponse(lookup.task, running)
}

export async function getAnalysisTaskStatus(
  webOrigin: string,
  opts?: {
    sourceType?: string | null
    sourceConversationId?: string | null
  },
): Promise<AnalysisTaskStatusResponse> {
  const sourceType = opts?.sourceType?.trim() || 'chatgpt_history'
  const sourceConversationId = opts?.sourceConversationId?.trim() || ''

  const pending = await loadPending()
  if (pending) {
    ensurePollRunning()
    try {
      const cred = await loadCredential()
      if (!cred) {
        return {type: 'ANALYSIS_TASK_STATUS', running: false}
      }
      const task = await apiFetch<TaskBody>(
        webOrigin,
        `/api/conversation/analysis-tasks/${encodeURIComponent(pending.taskId)}`,
        {},
        cred.token,
      )
      if (
        sourceConversationId &&
        task.sourceConversationId &&
        task.sourceConversationId !== sourceConversationId
      ) {
        // 本地 pending 属于另一对话：仍继续轮询，但对本对话改查 by-source
        return await lookupBySource(webOrigin, cred.token, sourceType, sourceConversationId)
      }
      if (task.status === 'SUCCEEDED' || task.status === 'FAILED') {
        return toStatusResponse(task, false)
      }
      return toStatusResponse(task, true)
    } catch (error) {
      return {
        type: 'ANALYSIS_TASK_STATUS',
        running: true,
        found: true,
        taskId: pending.taskId,
        error: error instanceof Error ? error.message : '查询失败',
      }
    }
  }

  if (!sourceConversationId) {
    return {type: 'ANALYSIS_TASK_STATUS', running: false, found: false}
  }

  try {
    const cred = await loadCredential()
    if (!cred) {
      return {type: 'ANALYSIS_TASK_STATUS', running: false, found: false, sourceConversationId}
    }
    return await lookupBySource(webOrigin, cred.token, sourceType, sourceConversationId)
  } catch (error) {
    return {
      type: 'ANALYSIS_TASK_STATUS',
      running: false,
      found: false,
      sourceConversationId,
      error: error instanceof Error ? error.message : '查询失败',
    }
  }
}
