import {LOGIN_REQUIRED_MESSAGE, SESSION_EXPIRED_MESSAGE} from '../constants/auth.ts'

const CREDENTIAL_AUTH_PATHS = new Set(['/api/auth/login', '/api/auth/register'])

function normalizeRequestPath(url?: string): string {
  if (!url) {
    return ''
  }
  const trimmed = url.trim()
  if (!trimmed) {
    return ''
  }
  try {
    const parsed = new URL(trimmed, 'http://localhost')
    return parsed.pathname
  } catch {
    return trimmed.split(/[?#]/, 1)[0] ?? ''
  }
}

export function isCredentialAuthEndpoint(url?: string): boolean {
  return CREDENTIAL_AUTH_PATHS.has(normalizeRequestPath(url))
}

export interface UnauthorizedResponseOptions {
  status?: number
  url?: string
  skipAuthExpiredHandler?: boolean
}

export function shouldHandleUnauthorizedResponse(options: UnauthorizedResponseOptions): boolean {
  if (options.status !== 401 || options.skipAuthExpiredHandler) {
    return false
  }
  return !isCredentialAuthEndpoint(options.url)
}

export function resolveUnauthorizedMessage(backendMessage: string | undefined, hadSession: boolean): string {
  const trimmed = backendMessage?.trim()
  if (trimmed && trimmed !== '未登录') {
    return trimmed
  }
  return hadSession ? SESSION_EXPIRED_MESSAGE : LOGIN_REQUIRED_MESSAGE
}

export interface LoginRedirectLocation {
  path: '/login'
  query: {
    redirect: string
  }
}

export function buildLoginRedirectLocation(
  currentPath: string,
  currentFullPath: string,
): LoginRedirectLocation | null {
  if (currentPath === '/login' || currentPath === '/register') {
    return null
  }
  const redirect = currentFullPath.startsWith('/') && !currentFullPath.startsWith('//')
    ? currentFullPath
    : '/'
  return {
    path: '/login',
    query: { redirect },
  }
}

/**
 * 解析 JWT payload 中的 exp 字段，返回毫秒级过期时间戳；格式非法或无 exp 返回 null。
 */
export function parseJwtExpMs(token: string | null | undefined): number | null {
  if (!token) {
    return null
  }
  const parts = token.split('.')
  if (parts.length !== 3 || !parts[1]) {
    return null
  }
  try {
    const base64 = parts[1].replace(/-/g, '+').replace(/_/g, '/')
    const padLength = (4 - (base64.length % 4)) % 4
    const padded = base64 + '='.repeat(padLength)
    const binary = atob(padded)
    const bytes = Uint8Array.from(binary, (char) => char.charCodeAt(0))
    const payload = JSON.parse(new TextDecoder().decode(bytes)) as { exp?: unknown }
    if (typeof payload.exp !== 'number' || !Number.isFinite(payload.exp) || payload.exp <= 0) {
      return null
    }
    return payload.exp * 1000
  } catch {
    return null
  }
}

export interface ClockOffsetOptions {
  expMs: number | null
  verifiedAtMs: number
  serverDateHeader?: string | null
}

/**
 * 计算本地时钟与服务端时钟偏差（serverMs - localMs）。
 * 优先使用服务端 Date 响应头；若无响应头且本地时钟已经超过刚验证通过的 exp，说明本地时钟偏快，
 * 返回 null 禁用本地提前判过期，改为完全以服务端 401 为准。
 */
export function computeServerClockOffsetMs(options: ClockOffsetOptions): number | null {
  if (options.serverDateHeader) {
    const serverDateMs = Date.parse(options.serverDateHeader)
    if (Number.isFinite(serverDateMs)) {
      return serverDateMs - options.verifiedAtMs
    }
  }
  if (options.expMs !== null && options.expMs > options.verifiedAtMs) {
    return 0
  }
  return null
}

export function isTokenExpired(
  token: string | null | undefined,
  nowMs: number,
  clockOffsetMs: number | null,
): boolean {
  if (clockOffsetMs === null) {
    return false
  }
  const expMs = parseJwtExpMs(token)
  if (expMs === null) {
    return false
  }
  return nowMs + clockOffsetMs >= expMs
}
