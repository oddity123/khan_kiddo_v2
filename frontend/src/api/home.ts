import http from './http'
import type { HomePageResponse } from '@/types/home'

export function fetchHomePage() {
  return http.get<HomePageResponse>('/api/home')
}
