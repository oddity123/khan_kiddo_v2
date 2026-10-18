import http from '@/api/http'

export type FunnelEventName =
  | 'visit_analyze'
  | 'click_sample'
  | 'analyze_start'
  | 'analyze_success'
  | 'analyze_error'
  | 'voice_start'
  | 'voice_end'
  | 'voice_saved'
  | 'voice_to_analyze'

export interface FunnelEventPayload {
  name: FunnelEventName
  durationSeconds?: number
  voiceSessionId?: number
}

/** 计数失败不影响主流程。游客请求不带 token 也能记。 */
export function trackFunnelEvent(payload: FunnelEventPayload): void {
  void http.post('/api/funnel/events', payload).catch(() => undefined)
}
