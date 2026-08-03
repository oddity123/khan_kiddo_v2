import http from './http'
import type {CollectGrowthCardRequest, GrowthCard, GrowthGrade} from '@/types/growthCard'

export function fetchTodayGrowthCards() {
  return http.get<GrowthCard[]>('/api/growth-cards/today')
}

export function gradeGrowthCard(cardId: string, grade: GrowthGrade) {
  return http.post<GrowthCard>(`/api/growth-cards/${cardId}/grade`, {grade})
}

export function collectGrowthCard(body: CollectGrowthCardRequest) {
  return http.post<GrowthCard>('/api/growth-cards/collect', body)
}

export function retryMintGrowthCards(analysisId: string) {
  return http.post<void>(`/api/growth-cards/mint/${analysisId}`)
}

/** 对本场指定习惯（Top2/3 等）走 LLM 生成并落库 */
export function mintHabitGrowthCard(analysisId: string, habitKey: string) {
  return http.post<GrowthCard>(`/api/growth-cards/mint/${analysisId}/habit`, {habitKey})
}
