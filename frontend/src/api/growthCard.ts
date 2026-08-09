import http from './http'
import type {CollectGrowthCardRequest, GrowthCard, GrowthGrade} from '@/types/growthCard'

export function fetchTodayGrowthCards() {
  return http.get<GrowthCard[]>('/api/growth-cards/today')
}

export function fetchRandomGrowthCards(limit = 5) {
  return http.get<GrowthCard[]>('/api/growth-cards/random', {params: {limit}})
}

export function gradeGrowthCard(cardId: string, grade: GrowthGrade) {
  return http.post<GrowthCard>(`/api/growth-cards/${cardId}/grade`, {grade})
}

export function deleteGrowthCard(cardId: string) {
  return http.delete<void>(`/api/growth-cards/${cardId}`)
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

/** 按句追踪：某场分析某句关联到的成长卡 */
export function fetchGrowthCardsBySentence(analysisId: string, sentenceId: string) {
  return http.get<GrowthCard[]>('/api/growth-cards/by-sentence', {
    params: {analysisId, sentenceId},
  })
}
