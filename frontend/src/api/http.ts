import axios from 'axios'

import {AUTH_TOKEN_KEY} from '@/constants/auth'

const http = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '',
  timeout: 30_000,
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
  (error) => Promise.reject(error),
)

export default http
