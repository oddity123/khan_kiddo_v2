import {ElMessage} from 'element-plus'
import type {Router} from 'vue-router'

import {AUTH_TOKEN_KEY, SESSION_EXPIRED_MESSAGE} from '@/constants/auth'
import {useAuthStore} from '@/stores/auth'
import {
  buildLoginRedirectLocation,
  resolveUnauthorizedMessage,
} from '@/utils/authExpiration'

export interface SessionExpiredOptions {
  message?: string
}

type SessionExpiredHandler = (options?: SessionExpiredOptions) => boolean

const DEDUP_WINDOW_MS = 3_000

let registeredHandler: SessionExpiredHandler | null = null

export function registerSessionExpiredHandler(handler: SessionExpiredHandler): void {
  registeredHandler = handler
}

/**
 * 统一处理登录态失效（401 或服务端明确返回未认证）：
 * 清理本地会话并跳转到登录页。返回 true 表示已接管并触发跳转（或正处于同批次防抖窗口内）。
 */
export function notifySessionExpired(options?: SessionExpiredOptions): boolean {
  return registeredHandler ? registeredHandler(options) : false
}

export function setupAuthSession(router: Router): void {
  let redirectInFlight = false
  let lastHandledAt = 0

  registerSessionExpiredHandler((options) => {
    const auth = useAuthStore()
    const hadSession = Boolean(auth.token || auth.user)
    auth.clearSession()

    const now = Date.now()
    if (redirectInFlight || now - lastHandledAt < DEDUP_WINDOW_MS) {
      return true
    }

    const currentRoute = router.currentRoute.value
    const target = buildLoginRedirectLocation(currentRoute.path, currentRoute.fullPath)
    if (!target) {
      return false
    }

    redirectInFlight = true
    lastHandledAt = now
    ElMessage.warning(resolveUnauthorizedMessage(options?.message, hadSession))
    void router.replace(target).finally(() => {
      redirectInFlight = false
    })
    return true
  })

  if (typeof window !== 'undefined') {
    window.addEventListener('storage', (event) => {
      if (event.key !== null && event.key !== AUTH_TOKEN_KEY) {
        return
      }
      if (localStorage.getItem(AUTH_TOKEN_KEY)) {
        return
      }
      const auth = useAuthStore()
      if (!auth.token && !auth.user) {
        return
      }
      auth.clearSession()
      const currentRoute = router.currentRoute.value
      const requiresAuth = currentRoute.matched.some((record) => record.meta.requiresAuth)
      if (!requiresAuth) {
        return
      }
      const target = buildLoginRedirectLocation(currentRoute.path, currentRoute.fullPath)
      if (target) {
        ElMessage.warning(SESSION_EXPIRED_MESSAGE)
        void router.replace(target)
      }
    })
  }
}
