import type {
  ActionCard,
  ChineseExpressionItem,
  PracticePromptGoal,
  PracticeVocabulary,
} from '@/types/conversation'
import type {GrowthCard} from '@/types/growthCard'

export const MAX_PRACTICE_VOCAB = 3

export interface PracticeVocabCandidate extends PracticeVocabulary {
  key: string
}

function optionalText(value?: string | null): string | undefined {
  const trimmed = value?.trim()
  return trimmed ? trimmed : undefined
}

function vocabKey(front: string, back: string): string {
  return `${front}\n${back}`
}

export function mapActionCardsToGoals(cards: ActionCard[]): PracticePromptGoal[] {
  return [...cards]
      .filter((card) => card.rank >= 1 && card.rank <= 3 && Boolean(card.titleZh?.trim()))
      .sort((a, b) => a.rank - b.rank)
      .slice(0, MAX_PRACTICE_VOCAB)
      .map((card) => ({
        rank: card.rank,
        title: card.titleZh.trim(),
        diagnosis: optionalText(card.diagnosisZh),
        coaching: optionalText(card.practicePrompt?.coachingZh) || optionalText(card.actionHintZh),
        originalSentence:
            optionalText(card.practicePrompt?.originalSentence)
            || optionalText(card.examples?.[0]?.originalSentence),
        targetSentence:
            optionalText(card.practicePrompt?.targetSentence)
            || optionalText(card.examples?.[0]?.suggestion),
      }))
}

export function dedupeVocabulary(items: PracticeVocabulary[]): PracticeVocabCandidate[] {
  const seen = new Set<string>()
  const result: PracticeVocabCandidate[] = []
  for (const item of items) {
    const front = item.front?.trim()
    const back = item.back?.trim()
    if (!front || !back) {
      continue
    }
    const key = vocabKey(front, back)
    if (seen.has(key)) {
      continue
    }
    seen.add(key)
    result.push({
      key,
      front,
      back,
      originalSentence: optionalText(item.originalSentence),
    })
  }
  return result
}

export function vocabFromGrowthCards(cards: GrowthCard[]): PracticeVocabCandidate[] {
  return dedupeVocabulary(
      cards
          .filter((card) => card.type === 'vocab')
          .map((card) => ({
            front: card.front,
            back: card.back,
            originalSentence: card.evidence?.[0]?.originalSentence,
          })),
  )
}

export function vocabFromChineseExpressions(
    items: ChineseExpressionItem[],
): PracticeVocabCandidate[] {
  return dedupeVocabulary(
      items.map((item) => ({
        front: item.focusPhrase?.trim() || item.originalSentence,
        back: item.suggestion ?? '',
        originalSentence: item.originalSentence,
      })),
  )
}
