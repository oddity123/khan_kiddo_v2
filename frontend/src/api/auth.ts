import http from './http'
import type {LoginResponse, RegisterPayload, RegisterResponse, UserProfile} from '@/types/auth'

export interface LoginPayload {
  username: string
  password: string
}

export function login(payload: LoginPayload) {
  return http.post<LoginResponse>('/api/auth/login', payload)
}

export function register(payload: RegisterPayload) {
  return http.post<RegisterResponse>('/api/auth/register', payload)
}

export function fetchCurrentUser() {
  return http.get<UserProfile>('/api/auth/me')
}

export function logout() {
  return http.post<{ message: string }>('/api/auth/logout')
}
