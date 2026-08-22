import type {UserRole} from '@/types/auth'
import type {AnalysisSummaryRow, ConversationAnalysisListResponse} from '@/types/conversation'

export interface AdminUserRow {
  id: number
  username: string
  email?: string | null
  role: UserRole
  enabled?: boolean
  createdAt?: string
  analysisCount?: number
}

export interface AdminUserListResponse {
  total: number
  records: AdminUserRow[]
}

export type AdminAnalysisListResponse = ConversationAnalysisListResponse
export type AdminAnalysisSummaryRow = AnalysisSummaryRow

export interface AdminAnalysisRow extends AnalysisSummaryRow {
  userId: number
  username: string
}

export interface AdminGlobalAnalysisListResponse {
  total: number
  records: AdminAnalysisRow[]
}
