export interface ConversationAnalysisRequest {
  conversationContent: string
}

export interface ConversationAnalysisProgress {
  status: string
  message?: string
  result?: ConversationAnalysisResult
  errorMessage?: string
  messageStats?: MessageStats
  streamingOriginal?: string | null
  streamingSuggestion?: string | null
  streamingErrorsHint?: string | null
  streamingCommitOriginal?: string | null
  streamingCommitSuggestion?: string | null
  streamingCommitErrorsHint?: string | null
}

export interface StreamingPreviewCard {
  original: string
  suggestion?: string
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

export interface EducationalSummaryStats {
  totalIssues?: number
  totalSentences?: number
  mainCategory?: string
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
}

export interface ConversationAnalysisDetail {
  analysisId: string
  conversationContent?: string
  status: string
  processingTimeMs?: number
  createdAt?: string
  educationalSummary?: { report?: EducationalSummaryReport }
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
