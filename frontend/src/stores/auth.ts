import { defineStore } from 'pinia'

import { fetchCurrentUser, login as loginApi, logout as logoutApi } from '@/api/auth'
import { AUTH_TOKEN_KEY } from '@/constants/auth'
import type { UserProfile } from '@/types/auth'

export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: localStorage.getItem(AUTH_TOKEN_KEY) as string | null,
    user: null as UserProfile | null,
    initialized: false,
    loading: false,
  }),

  getters: {
    isAuthenticated: (state) => Boolean(state.token && state.user),
    displayName: (state) => state.user?.username ?? '',
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

    async initialize() {
      if (this.initialized) {
        return
      }
      if (!this.token) {
        this.initialized = true
        return
      }
      try {
        await this.loadMe()
      } catch {
        this.clearSession()
      } finally {
        this.initialized = true
      }
    },

    async login(username: string, password: string) {
      this.loading = true
      try {
        const { data } = await loginApi({ username, password })
        this.setToken(data.token)
        this.user = data.user
        return data
      } finally {
        this.loading = false
      }
    },

    async loadMe() {
      const { data } = await fetchCurrentUser()
      this.user = data
      return data
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
    },
  },
})
