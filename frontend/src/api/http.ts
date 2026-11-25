import axios from 'axios'

import {AUTH_TOKEN_KEY} from '@/constants/auth'
import {shouldHandleUnauthorizedResponse} from '@/utils/authExpiration'
import {notifySessionExpired} from '@/utils/authSession'

declare module 'axios' {
  export interface AxiosRequestConfig {
    skipAuthExpiredHandler?: boolean
  }
}

const http = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '',
  timeout: 30_000,
  withCredentials: true,
})

http.interceptors.request.use((config) => {
  const token = localStorage.getItem(AUTH_TOKEN_KEY)
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

http.interceptors.response.use(
  (response) => response,
  (error) => {
    if (axios.isAxiosError(error)) {
      const status = error.response?.status
      const url = error.config?.url
      const skip = Boolean(error.config?.skipAuthExpiredHandler)
      if (shouldHandleUnauthorizedResponse({ status, url, skipAuthExpiredHandler: skip })) {
        const data = error.response?.data as { message?: string } | undefined
        if (notifySessionExpired({ message: data?.message })) {
          return new Promise(() => {})
        }
      }
    }
    return Promise.reject(error)
  },
)

export default http
