export interface RecentSentenceView {
  originalSentence: string
  suggestion?: string
  problemTypeTags: string[]
  createdAt?: string
}

export interface ProblemTypeStat {
  label: string
  count: number
}

export interface DailyPracticeStat {
  date: string
  label: string
  count: number
}

export interface GrowthCardStatusStat {
  key: string
  label: string
  count: number
}

export interface WeeklyDeltaStat {
  current: number
  previous: number
  delta: number
  percent: number
}

export interface AnalysisDashboardStats {
  analyzedSentenceCount: number
  seriousIssueCount: number
  mostCommonErrorType: string
  recent7DaysSentenceCount: number
  analysisCount: number
  dueGrowthCardCount: number
  recent30DaysTopProblemTypes: ProblemTypeStat[]
  recent7DaysProblemTypeDistribution: ProblemTypeStat[]
  dailyPracticeTrend: DailyPracticeStat[]
  dailyIssueHeatmap: DailyPracticeStat[]
  growthCardStatusCounts: GrowthCardStatusStat[]
  weeklySentenceDelta: WeeklyDeltaStat
  recentSentences: RecentSentenceView[]
}

export interface HomePageResponse {
  title: string
  description: string
  authenticated: boolean
  analysisStats: AnalysisDashboardStats | null
}
