import http from './http'

export interface SubmitFeedbackPayload {
  title: string
  content: string
  email?: string
}

export interface SubmitFeedbackResponse {
  id: number
  message: string
}

export function submitFeedback(payload: SubmitFeedbackPayload) {
  return http.post<SubmitFeedbackResponse>('/api/feedback', payload)
}
