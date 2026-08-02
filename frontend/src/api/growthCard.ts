import http from './http'
import type {GrowthCard, GrowthGrade} from '@/types/growthCard'

export function fetchTodayGrowthCards() {
  return http.get<GrowthCard[]>('/api/growth-cards/today')
}

export function gradeGrowthCard(cardId: string, grade: GrowthGrade) {
  return http.post<GrowthCard>(`/api/growth-cards/${cardId}/grade`, {grade})
}
