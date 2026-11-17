import http from './http'

export type ConnectSessionStatus =
  | 'PENDING'
  | 'AUTHORIZED'
  | 'EXCHANGED'
  | 'DENIED'
  | 'EXPIRED'

export interface ConnectSessionResponse {
  sessionId: string
  status: ConnectSessionStatus
  expiresAt: string
}

export function fetchConnectSession(sessionId: string) {
  return http.get<ConnectSessionResponse>(`/api/extension/connect/sessions/${encodeURIComponent(sessionId)}`)
}

export function authorizeConnectSession(sessionId: string) {
  return http.post<ConnectSessionResponse>(
    `/api/extension/connect/sessions/${encodeURIComponent(sessionId)}/authorize`,
  )
}

export function denyConnectSession(sessionId: string) {
  return http.post<ConnectSessionResponse>(
    `/api/extension/connect/sessions/${encodeURIComponent(sessionId)}/deny`,
  )
}
