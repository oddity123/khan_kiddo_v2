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
  chineseExpressionCount?: number
  educationalSummary?: EducationalSummaryRoot
  errorTypeDistribution?: ErrorTypeDistribution[]
  /** 流水线内 HabitCardScorer 结果，游客预览直接复用 */
  actionCards?: ActionCard[]
  familyDistribution?: FamilyDistributionItem[]
}

export interface ChineseExpressionItem {
    originalIndex?: number
    originalSentence: string
    /** 词汇求助时抽出的中文目标词；有则作为知识卡片正面 */
    focusPhrase?: string
    suggestion?: string
    /** 可选角标（成长卡：习惯 / 词汇） */
    kindLabel?: string
    /** 稳定键（成长卡 cardId 等），供闪卡列表复用 */
    cardKey?: string
    /** 成长卡证据句数（侧栏「证据」入口） */
    evidenceCount?: number
}

/** 与后端 EducationalSummaryParser / v1 一致：{ report: { overallStats, overallSummary }, chineseExpressions? } */
export interface EducationalSummaryRoot {
  report?: EducationalSummaryReport
  chineseExpressions?: ChineseExpressionItem[]
  actionCardDiagnoses?: ActionCardDiagnosis[]
}

export interface ActionCardDiagnosis {
  rank?: number
  habitKey?: string
  pointId?: string
  diagnosisZh?: string
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
  /** ERRANT 空白分词，与 edits 下标对齐 */
  originalTokens?: string[]
  correctedTokens?: string[]
  /** R/M/U 操作编辑；无批注时不渲染高亮 */
  edits?: SentenceEdit[]
}

/** ERRANT 操作编辑（仅操作前缀，不含错误细类） */
export interface SentenceEdit {
  op: 'R' | 'M' | 'U'
  oStart: number
  oEnd: number
  oStr?: string
  cStart: number
  cEnd: number
  cStr?: string
}

export interface AnalysisError {
  type: string
  point?: string
  errorLevel?: string
  pointId?: string
  familyId?: string
  /** 语法家族中文名（筛选标签） */
  familyTitleZh?: string
  channel?: PointChannel
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

import type {GrowthCard} from '@/types/growthCard'

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
    /** 跨通道习惯行动卡 Top 1-3（rank1 即本场最该改），无足够证据时为空数组 */
    actionCards?: ActionCard[]
    /** 语法家族分布（饼图用），旧数据回退 errorTypeDistribution */
    familyDistribution?: FamilyDistributionItem[]
    /** 习惯成长卡状态：ready=已有习惯卡，none=尚未制卡 */
    habitGrowthMintStatus?: 'ready' | 'none'
    /** 本场已生成的全部成长卡（habit + vocab） */
    growthCards?: GrowthCard[]
}

/** 与后端 knowledge.PointChannel 一致，JSON 小写 */
export type PointChannel = 'rule' | 'fluency' | 'lexical' | 'chinese'

/** 与后端 knowledge.CardKind 一致，JSON 小写 */
export type CardKind = 'grammar' | 'fluency_strategy' | 'lexical_upgrade' | 'chinese_bypass'

/** 与后端 knowledge.CardPolicy 一致，JSON 小写 */
export type CardPolicy = 'normal' | 'rare' | 'channel'

export interface PracticePrompt {
    /** 本场第一条证据的原句 */
    originalSentence?: string
    /** 建议改说的目标句 / 目标表达 */
    targetSentence?: string
    /** 中文教练提示，取自字典 actionHintZh */
    coachingZh?: string
}

/** 定向复练提示词：提交已选 Top 项与词汇，由后端套模板组装 */
export interface PracticePromptRequest {
    goals: PracticePromptGoal[]
    vocabulary: PracticeVocabulary[]
}

export interface PracticePromptGoal {
    rank: number
    title: string
    diagnosis?: string
    coaching?: string
    originalSentence?: string
    targetSentence?: string
}

export interface PracticeVocabulary {
    front: string
    back: string
    originalSentence?: string
}

export interface PracticePromptResponse {
    prompt: string
}

/** 跨通道习惯行动卡（Top 1-3），与后端 ActionCardDto 一致 */
export interface ActionCard {
    /** 1-based 排名，1 即本场最该改的习惯 */
    rank: number
    channel: PointChannel
    cardKind: CardKind
    cardPolicy?: CardPolicy
    /** 排序分组键：family→familyId，leaf→pointId，channel→PointChannel 大写名 */
    habitKey: string
    /** 组内代表叶子（出现最多/分数贡献最大） */
    pointId: string
    /** rank=1 为完整结论句，rank=2/3 为 topTitleZh 本身 */
    headlineZh?: string
    titleZh: string
    whyZh?: string
    /** Stage 3 生成的本场专属诊断，不包含练习建议 */
    diagnosisZh?: string
    /** 本场计入该习惯的证据条数 */
    errorCount: number
    score?: number
    /** ≤5 条证据 */
    examples?: ActionCardExample[]
    /** 仅 rule 家族填充：同家族其它叶子（不含代表叶子） */
    siblingPoints?: SiblingPoint[]
    actionHintZh?: string
    practicePrompt?: PracticePrompt
}

export interface ActionCardExample {
    /** 后端 sentenceId 为 String */
    sentenceId?: string | number
    originalSentence: string
    errorPoint?: string
    suggestion?: string
}

export interface SiblingPoint {
    pointId: string
    titleZh: string
    errorCount: number
}

/** 侧栏家族分布（饼图用），与后端 FamilyDistributionDto 一致 */
export interface FamilyDistributionItem {
    familyId: string
    titleZh: string
    channel?: PointChannel
    count: number
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
