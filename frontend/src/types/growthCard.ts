export type GrowthCardType = 'habit' | 'vocab'
export type GrowthCardStatus = 'unfamiliar' | 'fuzzy' | 'mastered'
/** Anki 式四档；fuzzy 为 hard 的兼容别名 */
export type GrowthGrade = 'again' | 'hard' | 'good' | 'easy' | 'fuzzy'

/** 成长卡证据行（关系表 growth_card_evidence） */
export interface GrowthCardEvidence {
  sentenceId?: string | null
  originalSentence: string
  suggestion?: string | null
  sortOrder?: number
}

export interface GrowthCard {
  cardId: string
  type: GrowthCardType
  status: GrowthCardStatus
  nextDueAt?: string | null
  front: string
  back: string
  sourceAnalysisId?: string | null
  /** 如 habit:{habitKey}，用于判断行动卡是否已铸卡 */
  sourceRef?: string | null
  createdAt?: string | null
  /** 制卡时落库的证据句，用于弹窗展示与按句追踪 */
  evidence?: GrowthCardEvidence[]
}

export interface CollectGrowthCardRequest {
  analysisId: string
  type?: GrowthCardType
  front: string
  back: string
  sourceRef: string
}
