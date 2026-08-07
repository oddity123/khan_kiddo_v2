import type {
  ConversationAnalysisDetail,
  ConversationAnalysisResult,
  EducationalSummaryRoot,
} from '@/types/conversation'

/**
 * 将 SSE 完成结果转成详情页可用的内存结构（游客不落库）。
 */
export function resultToEphemeralDetail(
    result: ConversationAnalysisResult,
    conversationContent: string,
): ConversationAnalysisDetail {
  const ar = result.analysisResults
  let educationalSummary = ar?.educationalSummary as EducationalSummaryRoot | undefined
  if (!educationalSummary && result.educationalSummaryJson) {
    try {
      educationalSummary = JSON.parse(result.educationalSummaryJson) as EducationalSummaryRoot
    } catch {
      educationalSummary = undefined
    }
  }

  return {
    analysisId: result.analysisId || 'preview',
    conversationContent,
    status: result.status || 'success',
    processingTimeMs: result.processingTimeMs,
    createdAt: result.analyzedAt,
    llmModelId: result.llmModelId,
    llmModelName: result.llmModelName,
    llmProvider: result.llmProvider,
    educationalSummary,
    items: ar?.items,
    errorTypeDistribution: ar?.errorTypeDistribution,
    chineseExpressions: ar?.chineseExpressions ?? educationalSummary?.chineseExpressions,
    topHabit: ar?.topHabit,
    actionCards: ar?.actionCards ?? [],
    familyDistribution: ar?.familyDistribution,
    habitGrowthMintStatus: 'none',
    growthCards: [],
  }
}
