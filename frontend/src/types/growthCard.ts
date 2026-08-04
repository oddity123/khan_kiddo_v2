export type GrowthCardType = 'habit' | 'vocab'
export type GrowthCardStatus = 'unfamiliar' | 'fuzzy' | 'mastered'
export type GrowthGrade = 'again' | 'fuzzy' | 'good'

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
}

export interface CollectGrowthCardRequest {
  analysisId: string
  type?: GrowthCardType
  front: string
  back: string
  sourceRef: string
}
