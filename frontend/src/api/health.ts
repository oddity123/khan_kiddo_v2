import http from './http'

export interface HealthResponse {
  status: string
  application: string
}

export function fetchHealth() {
  return http.get<HealthResponse>('/api/health')
}
