import {newCodeVerifier, pkceS256Challenge} from '../shared/pkce'
import {
  clearCredential,
  clearPendingConnect,
  loadCredential,
  loadPendingConnect,
  saveCredential,
  savePendingConnect,
  type StoredCredential,
} from '../shared/credentialStore'
import type {ConnectionStatusResponse} from '../shared/protocol'

interface ConnectConfig {
  enabled: boolean
  pollIntervalMs: number
  pollTimeoutMs: number
}

interface SessionResponse {
  sessionId: string
  status: string
  expiresAt: string
}

interface ExchangeResponse {
  status: string
  token?: string
  username?: string
  expiresAt?: string
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

async function loadConfig(webOrigin: string): Promise<ConnectConfig> {
  const data = await apiFetch<ConnectConfig>(webOrigin, '/api/extension/connect/config')
  return {
    enabled: data.enabled !== false,
    pollIntervalMs: data.pollIntervalMs > 0 ? data.pollIntervalMs : 1500,
    pollTimeoutMs: data.pollTimeoutMs > 0 ? data.pollTimeoutMs : 600_000,
  }
}

function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

async function pollExchange(
  webOrigin: string,
  sessionId: string,
  codeVerifier: string,
  config: ConnectConfig,
): Promise<StoredCredential> {
  const deadline = Date.now() + config.pollTimeoutMs
  while (Date.now() < deadline) {
    const result = await apiFetch<ExchangeResponse>(webOrigin, '/api/extension/connect/exchange', {
      method: 'POST',
      body: JSON.stringify({sessionId, codeVerifier}),
    })
    if (result.status === 'PENDING') {
      await sleep(config.pollIntervalMs)
      continue
    }
    if (result.status === 'EXCHANGED' && typeof result.token === 'string') {
      return {
        token: result.token,
        username: result.username || '',
        expiresAt: result.expiresAt || '',
        connectedAt: new Date().toISOString(),
      }
    }
    throw new Error('兑换状态异常，请重试')
  }
  throw new Error('等待授权超时，请重新连接并保持授权页打开')
}

function ensurePollRunning(webOrigin: string): void {
  if (pollLoop) {
    return
  }
  pollLoop = (async () => {
    try {
      const pending = await loadPendingConnect()
      if (!pending) {
        return
      }
      const config = await loadConfig(webOrigin)
      const credential = await pollExchange(
        webOrigin,
        pending.sessionId,
        pending.codeVerifier,
        config,
      )
      await saveCredential(credential)
      await clearPendingConnect()
    } catch {
      await clearPendingConnect()
    } finally {
      pollLoop = null
    }
  })()
}

export async function getConnectionStatus(webOrigin: string): Promise<ConnectionStatusResponse> {
  const pending = await loadPendingConnect()
  if (pending) {
    ensurePollRunning(webOrigin)
    return {
      type: 'CONNECTION_STATUS',
      connected: false,
      connecting: true,
    }
  }
  const cred = await loadCredential()
  if (!cred) {
    return {type: 'CONNECTION_STATUS', connected: false}
  }
  try {
    const me = await apiFetch<{
      connected: boolean
      username?: string
      expiresAt?: string
    }>(webOrigin, '/api/extension/connect/me', {}, cred.token)
    if (!me.connected) {
      await clearCredential()
      return {type: 'CONNECTION_STATUS', connected: false}
    }
    return {
      type: 'CONNECTION_STATUS',
      connected: true,
      username: me.username || cred.username,
      expiresAt: me.expiresAt || cred.expiresAt,
    }
  } catch (error) {
    const message = error instanceof Error ? error.message : '连接已失效'
    if (message.includes('未登录') || message.includes('登录已失效')) {
      await clearCredential()
      return {type: 'CONNECTION_STATUS', connected: false, error: '登录已失效，请重新连接'}
    }
    return {
      type: 'CONNECTION_STATUS',
      connected: true,
      username: cred.username,
      expiresAt: cred.expiresAt,
      error: message,
    }
  }
}

export async function startConnect(
  webOrigin: string,
  ensureHostPermission: (origin: string) => Promise<void>,
): Promise<ConnectionStatusResponse> {
  await ensureHostPermission(webOrigin)
  const config = await loadConfig(webOrigin)
  if (!config.enabled) {
    return {
      type: 'CONNECTION_STATUS',
      connected: false,
      error: '站点暂未开放扩展连接',
    }
  }

  const existing = await loadCredential()
  if (existing) {
    const status = await getConnectionStatus(webOrigin)
    if (status.connected) {
      return status
    }
  }

  if (await loadPendingConnect()) {
    ensurePollRunning(webOrigin)
    return {type: 'CONNECTION_STATUS', connected: false, connecting: true}
  }

  const codeVerifier = newCodeVerifier()
  const codeChallenge = await pkceS256Challenge(codeVerifier)
  const session = await apiFetch<SessionResponse>(webOrigin, '/api/extension/connect/sessions', {
    method: 'POST',
    body: JSON.stringify({codeChallenge}),
  })
  await savePendingConnect({
    sessionId: session.sessionId,
    codeVerifier,
    expiresAt: session.expiresAt,
    startedAt: new Date().toISOString(),
  })

  const connectUrl = `${webOrigin}/extension/connect?session=${encodeURIComponent(session.sessionId)}`
  await chrome.tabs.create({url: connectUrl, active: true})
  ensurePollRunning(webOrigin)
  return {type: 'CONNECTION_STATUS', connected: false, connecting: true}
}

export async function revokeConnection(webOrigin: string): Promise<ConnectionStatusResponse> {
  const cred = await loadCredential()
  if (cred) {
    try {
      await apiFetch<void>(
        webOrigin,
        '/api/extension/connect/revoke',
        {method: 'POST', body: '{}'},
        cred.token,
      )
    } catch {
      // 本地仍清除
    }
  }
  await clearCredential()
  await clearPendingConnect()
  return {type: 'CONNECTION_STATUS', connected: false}
}
