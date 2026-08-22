import http from './http'
import type {AdminGlobalAnalysisListResponse, AdminUserListResponse} from '@/types/admin'
import type {AdminPointDictionaryResponse} from '@/types/adminKnowledge'
import type {ConversationAnalysisDetail, ConversationAnalysisListResponse} from '@/types/conversation'

export function listAdminUsers(params?: {
  page?: number
  size?: number
  keyword?: string
  minAnalysisCount?: number
  maxAnalysisCount?: number
}) {
  return http.get<AdminUserListResponse>('/api/admin/users', {params})
}

export function listAdminAnalyses(params?: {
  page?: number
  size?: number
  keyword?: string
  username?: string
}) {
  return http.get<AdminGlobalAnalysisListResponse>('/api/admin/analyses', {params})
}

export function listAdminUserAnalyses(
  userId: number,
  params?: { page?: number; size?: number; keyword?: string },
) {
  return http.get<ConversationAnalysisListResponse>(`/api/admin/users/${userId}/analyses`, {params})
}

export function getAdminAnalysisDetail(analysisId: string) {
  return http.get<ConversationAnalysisDetail>(`/api/admin/analyses/${analysisId}`)
}

export function getAdminPointDictionary() {
  return http.get<AdminPointDictionaryResponse>('/api/admin/knowledge/point-dictionary')
}
