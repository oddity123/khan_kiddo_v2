export interface RecentSentenceView {
  originalSentence: string
  suggestion?: string
  problemTypeTags: string[]
  createdAt?: string
}

export interface AnalysisDashboardStats {
  analyzedSentenceCount: number
  seriousIssueCount: number
  mostCommonErrorType: string
  recent7DaysSentenceCount: number
  recentSentences: RecentSentenceView[]
}

export interface HomePageResponse {
  title: string
  description: string
  authenticated: boolean
  analysisStats: AnalysisDashboardStats | null
}
