export interface RecentSentenceView {
  originalSentence: string
  suggestion?: string
  familyTags: string[]
  createdAt?: string
}

export interface KnowledgeFamilyStat {
  familyId: string
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
  mostCommonFamilyLabel: string
  recent7DaysSentenceCount: number
  analysisCount: number
  dueGrowthCardCount: number
  recent7DaysFamilyDistribution: KnowledgeFamilyStat[]
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
