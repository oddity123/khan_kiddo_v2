export interface ConversationAnalysisRequest {
  conversationContent: string
}

export interface ConversationAnalysisProgress {
  status: string
  message?: string
  result?: ConversationAnalysisResult
  errorMessage?: string
  analysisId?: string
  messageStats?: MessageStats
}

export interface MessageStats {
  totalMessages?: number
  userMessages?: number
  aiMessages?: number
}

export interface ConversationAnalysisResult {
  analysisId: string
  analyzedAt?: string
  processingTimeMs?: number
  status: string
  analysisResults?: AnalysisResultsPayload
  educationalSummaryJson?: string
}

export interface AnalysisResultsPayload {
  items?: AnalysisItem[]
  totalSentences?: number
  totalErrors?: number
  educationalSummary?: EducationalSummaryRoot
  errorTypeDistribution?: ErrorTypeDistribution[]
}

/** 与后端 EducationalSummaryParser / v1 一致：{ report: { overallStats, overallSummary } } */
export interface EducationalSummaryRoot {
  report?: EducationalSummaryReport
}

export interface EducationalSummaryReport {
  overallStats?: EducationalSummaryStats
  overallSummary?: EducationalSummaryOverall
}

export interface PerformanceDimensionScores {
    naturalness?: number
    accuracy?: number
    fluency?: number
    lexical?: number
}

export interface EducationalSummaryStats {
  totalIssues?: number
  totalSentences?: number
  mainCategory?: string
    /** 后端确定性算法计算的综合口语自然度分（60–98） */
    performanceScore?: number
    dimensionScores?: PerformanceDimensionScores
}

export interface EducationalSummaryOverall {
  levelSummary?: string
}

export interface AnalysisItem {
  sentenceId?: number
  originalSentence: string
  suggestion?: string
  errors?: AnalysisError[]
}

export interface AnalysisError {
  type: string
  point?: string
  errorLevel?: string
}

export interface ErrorTypeDistribution {
  type: string
  count: number
}

export interface ConversationAnalysisSaveRequest {
  conversationContent: string
  items: SaveAnalysisItem[]
  analysisId?: string
  analyzedAt?: string
  processingTimeMs?: number
  educationalSummary?: string
}

export interface SaveAnalysisItem {
  originalSentence: string
  suggestion?: string
  errors?: SaveAnalysisError[]
}

export interface SaveAnalysisError {
  type: string
  point?: string
}

export interface ConversationAnalysisListResponse {
  total: number
  records: AnalysisSummaryRow[]
}

export interface AnalysisSummaryRow {
  analysisId: string
  status: string
  processingTimeMs?: number
  createdAt?: string
  preview?: string
  performanceScore?: number
  dimensionScores?: PerformanceDimensionScores
}

export interface ConversationAnalysisDetail {
  analysisId: string
  conversationContent?: string
  status: string
  errorMessage?: string
  processingTimeMs?: number
  createdAt?: string
  educationalSummary?: EducationalSummaryRoot
  items?: AnalysisItem[]
  errorTypeDistribution?: ErrorTypeDistribution[]
}

export const PROGRESS_STATUS = {
  START: 'START',
  VALIDATING: 'VALIDATING',
  SEPARATING: 'SEPARATING',
  ANALYZING: 'ANALYZING',
  PARSING: 'PARSING',
  SUMMARIZING: 'SUMMARIZING',
  COMPLETED: 'COMPLETED',
  ERROR: 'ERROR',
} as const
