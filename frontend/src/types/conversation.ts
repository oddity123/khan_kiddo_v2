export interface ConversationAnalysisRequest {
  conversationContent: string
    /** Stage2/Stage3 分析模型配置 ID */
    modelId?: string
}

export interface LlmModelOption {
    id: string
    displayName: string
    provider: string
    defaultModel: boolean
}

export interface ConversationAnalysisProgress {
  status: string
  message?: string
  result?: ConversationAnalysisResult
  errorMessage?: string
  analysisId?: string
  messageStats?: MessageStats
    /** Stage2 流式预览：当前句原句（可能以 ... 结尾） */
    streamingOriginal?: string
    streamingSuggestion?: string
    streamingErrorsHint?: string
    /** 上一句已完成，用于追加卡片 */
    streamingCommitOriginal?: string
    streamingCommitSuggestion?: string
    streamingCommitErrorsHint?: string
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
    llmModelId?: string
    llmModelName?: string
    llmProvider?: string
}

export interface AnalysisResultsPayload {
  items?: AnalysisItem[]
  totalSentences?: number
  englishPracticeCount?: number
  totalErrors?: number
  chineseExpressions?: ChineseExpressionItem[]
  chineseExpressionCount?: number
  educationalSummary?: EducationalSummaryRoot
  errorTypeDistribution?: ErrorTypeDistribution[]
}

export interface ChineseExpressionItem {
  originalIndex?: number
  originalSentence: string
    /** 词汇求助时抽出的中文目标词；有则作为知识卡片正面 */
    focusPhrase?: string
  suggestion?: string
}

/** 与后端 EducationalSummaryParser / v1 一致：{ report: { overallStats, overallSummary }, chineseExpressions? } */
export interface EducationalSummaryRoot {
  report?: EducationalSummaryReport
  chineseExpressions?: ChineseExpressionItem[]
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
  chineseExpressionCount?: number
  mainCategory?: string
    /** 后端确定性算法计算的综合口语自然度分（45–98） */
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
    /** 对话原文字符数 */
    contentCharCount?: number
    llmModelId?: string
    llmModelName?: string
    llmProvider?: string
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
    llmModelId?: string
    llmModelName?: string
    llmProvider?: string
  educationalSummary?: EducationalSummaryRoot
  items?: AnalysisItem[]
  errorTypeDistribution?: ErrorTypeDistribution[]
  chineseExpressions?: ChineseExpressionItem[]
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
