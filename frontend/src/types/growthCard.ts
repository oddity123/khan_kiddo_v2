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
}
