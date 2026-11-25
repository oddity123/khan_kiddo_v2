import { defineStore } from 'pinia'

import { fetchCurrentUser, login as loginApi, logout as logoutApi } from '@/api/auth'
import { AUTH_TOKEN_KEY } from '@/constants/auth'
import type { UserProfile } from '@/types/auth'
import {
  computeServerClockOffsetMs,
  isTokenExpired,
  parseJwtExpMs,
} from '@/utils/authExpiration'

function extractHeaderDate(headers: unknown): string | null {
  if (!headers || typeof headers !== 'object') {
    return null
  }
  const value = (headers as Record<string, unknown>).date
  return typeof value === 'string' ? value : null
}

export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: localStorage.getItem(AUTH_TOKEN_KEY) as string | null,
    user: null as UserProfile | null,
    clockOffsetMs: null as number | null,
    initialized: false,
    loading: false,
  }),

  getters: {
    isAuthenticated: (state) => Boolean(state.token && state.user),
    displayName: (state) => state.user?.username ?? '',
    isAdmin: (state) => state.user?.role === 'ADMIN',
  },

  actions: {
    setToken(token: string | null) {
      this.token = token
      if (token) {
        localStorage.setItem(AUTH_TOKEN_KEY, token)
      } else {
        localStorage.removeItem(AUTH_TOKEN_KEY)
      }
    },

    calibrateSessionClock(serverDateHeader?: string | null) {
      this.clockOffsetMs = computeServerClockOffsetMs({
        expMs: parseJwtExpMs(this.token),
        verifiedAtMs: Date.now(),
        serverDateHeader,
      })
    },

    expireIfNeeded(): boolean {
      if (!this.token && !this.user) {
        return false
      }
      const storedToken = localStorage.getItem(AUTH_TOKEN_KEY)
      if (!storedToken || isTokenExpired(storedToken, Date.now(), this.clockOffsetMs)) {
        this.clearSession()
        return true
      }
      return false
    },

    async initialize() {
      if (this.initialized) {
        return
      }
      if (!this.token) {
        this.initialized = true
        return
      }
      try {
        await this.loadMe({ skipAuthExpiredHandler: true })
      } catch {
        this.clearSession()
      } finally {
        this.initialized = true
      }
    },

    async login(username: string, password: string) {
      this.loading = true
      try {
        const response = await loginApi({ username, password })
        const { data } = response
        this.setToken(data.token)
        this.user = data.user
        this.calibrateSessionClock(extractHeaderDate(response.headers))
        return data
      } finally {
        this.loading = false
      }
    },

    async loadMe(options?: { skipAuthExpiredHandler?: boolean }) {
      const response = await fetchCurrentUser(
        options?.skipAuthExpiredHandler ? { skipAuthExpiredHandler: true } : undefined,
      )
      this.user = response.data
      this.calibrateSessionClock(extractHeaderDate(response.headers))
      return response.data
    },

    async logout() {
      try {
        await logoutApi()
      } catch {
        // 无状态 JWT：服务端登出为占位，忽略网络错误
      } finally {
        this.clearSession()
      }
    },

    clearSession() {
      this.setToken(null)
      this.user = null
      this.clockOffsetMs = null
    },
  },
})
