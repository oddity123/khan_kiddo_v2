<script setup lang="ts">
import {ElMessage} from 'element-plus'
import {computed, nextTick, ref, watch} from 'vue'
import {FlashCards, FlipCard} from 'vue3-flashcards'

import {gradeGrowthCard} from '@/api/growthCard'
import type {GrowthCard, GrowthGrade} from '@/types/growthCard'
import {getErrorMessage} from '@/utils/error'

const props = withDefaults(
    defineProps<{
      cards: GrowthCard[]
      /** 首页等嵌入场景：顶对齐、略紧凑 */
      compact?: boolean
      /** 独立复习页：卡片更大、垂直居中 */
      page?: boolean
      emptyTodayText?: string
      emptyDoneText?: string
    }>(),
    {
      compact: false,
      page: false,
      emptyTodayText: '今天没有待复习的成长卡',
      emptyDoneText: '本轮卡片已练完',
    },
)

interface DeckExpose {
  reset: (options?: {animate?: boolean; delay?: number}) => void | Promise<void>
  swipeLeft: () => void
  swipeRight: () => void
}

const rootRef = ref<HTMLElement | null>(null)
const deckRef = ref<DeckExpose | null>(null)
const queue = ref<GrowthCard[]>([])
const initialCount = ref(0)
const isFlipped = ref(false)
const grading = ref(false)

watch(
    () => props.cards,
    (cards) => {
      queue.value = [...cards]
      if (cards.length > 0) {
        initialCount.value = cards.length
      }
      isFlipped.value = false
    },
    {immediate: true},
)

const count = computed(() => queue.value.length)

const emptyMessage = computed(() => {
  if (initialCount.value > 0 && queue.value.length === 0) {
    return props.emptyDoneText
  }
  return props.emptyTodayText
})

function asCard(item: Record<string, unknown>): GrowthCard {
  return item as unknown as GrowthCard
}

function cardOrdinal(item: GrowthCard): number {
  const idx = queue.value.findIndex((card) => card.cardId === item.cardId)
  return idx >= 0 ? idx + 1 : 1
}

function typeLabel(type: GrowthCard['type']): string {
  return type === 'habit' ? '习惯' : '词汇'
}

function syncFlipState() {
  window.setTimeout(() => {
    const inner = rootRef.value?.querySelector(
        '.flashcards__card--active .flip-card__inner, [data-active-card="true"] .flip-card__inner',
    ) as HTMLElement | null
    isFlipped.value = inner?.classList.contains('flip-card__inner--flipped') ?? false
  }, 320)
}

function onCardPointerUp() {
  syncFlipState()
}

watch(count, () => {
  isFlipped.value = false
})

function gradeSwipeDirection(grade: GrowthGrade): 'left' | 'right' {
  return grade === 'again' || grade === 'hard' || grade === 'fuzzy' ? 'left' : 'right'
}

function wait(ms: number) {
  return new Promise<void>((resolve) => {
    window.setTimeout(resolve, ms)
  })
}

async function submitGrade(grade: GrowthGrade) {
  const current = queue.value[0]
  if (!current || grading.value) {
    return
  }
  if (!isFlipped.value) {
    ElMessage.info('请先点击卡片翻面')
    return
  }

  grading.value = true
  const direction = gradeSwipeDirection(grade)
  try {
    await gradeGrowthCard(current.cardId, grade)
    if (direction === 'left') {
      deckRef.value?.swipeLeft()
    } else {
      deckRef.value?.swipeRight()
    }
    await wait(380)
    queue.value = queue.value.filter((card) => card.cardId !== current.cardId)
    isFlipped.value = false
    await nextTick()
    await deckRef.value?.reset?.({animate: false})
  } catch (error) {
    ElMessage.error(getErrorMessage(error, '评分失败，请稍后重试'))
  } finally {
    grading.value = false
  }
}
</script>

<template>
  <div
      ref="rootRef"
      class="growth-deck"
      :class="{
        'growth-deck--compact': compact,
        'growth-deck--page': page,
      }"
      aria-label="成长卡复习"
  >
    <FlashCards
        v-if="count > 0"
        ref="deckRef"
        class="growth-flashcards"
        :items="queue"
        item-key="cardId"
        :loop="false"
        :stack="2"
        stack-direction="top"
        :stack-offset="14"
        :stack-scale="0.02"
        swipe-direction="horizontal"
        :disable-drag="true"
        :wait-animation-end="true"
        :a11y="{ enabled: true, keyboard: false, manageFocus: false }"
    >
      <template #default="{ item: rawItem }">
        <template v-for="item in [asCard(rawItem)]" :key="item.cardId">
          <FlipCard class="growth-flip" flip-axis="y" @pointerup="onCardPointerUp">
            <template #front>
              <article class="growth-face growth-face--front">
                <header class="growth-head">
                  <span class="growth-badge">{{ typeLabel(item.type) }}</span>
                  <span class="growth-index">{{ cardOrdinal(item) }}/{{ count }}</span>
                </header>
                <section class="growth-pane">
                  <p class="growth-main">{{ item.front }}</p>
                </section>
              </article>
            </template>
            <template #back>
              <article class="growth-face growth-face--back">
                <header class="growth-head">
                  <span class="growth-badge growth-badge--back">答案</span>
                  <span class="growth-index">{{ cardOrdinal(item) }}/{{ count }}</span>
                </header>
                <section class="growth-pane">
                  <p class="growth-main growth-main--back">{{ item.back }}</p>
                </section>
              </article>
            </template>
          </FlipCard>
        </template>
      </template>
      <template #empty>
        <div class="growth-empty" role="status">
          <p>{{ emptyMessage }}</p>
        </div>
      </template>
    </FlashCards>

    <div v-else class="growth-empty" role="status">
      <p>{{ emptyMessage }}</p>
    </div>

    <div v-if="count > 0" class="growth-actions">
      <p class="growth-actions-hint">点击卡片翻面后评分</p>
      <div class="growth-grade-row">
        <button
            type="button"
            class="growth-grade-btn growth-grade-btn--again"
            :disabled="grading || !isFlipped"
            @click="submitGrade('again')"
        >
          <span class="growth-grade-label">Again</span>
          <span class="growth-grade-sub">不会 · 1天</span>
        </button>
        <button
            type="button"
            class="growth-grade-btn growth-grade-btn--hard"
            :disabled="grading || !isFlipped"
            @click="submitGrade('hard')"
        >
          <span class="growth-grade-label">Hard</span>
          <span class="growth-grade-sub">困难 · 2天</span>
        </button>
        <button
            type="button"
            class="growth-grade-btn growth-grade-btn--good"
            :disabled="grading || !isFlipped"
            @click="submitGrade('good')"
        >
          <span class="growth-grade-label">Good</span>
          <span class="growth-grade-sub">良好 · 4天</span>
        </button>
        <button
            type="button"
            class="growth-grade-btn growth-grade-btn--easy"
            :disabled="grading || !isFlipped"
            @click="submitGrade('easy')"
        >
          <span class="growth-grade-label">Easy</span>
          <span class="growth-grade-sub">简单 · 掌握</span>
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.growth-deck {
  flex: 1 1 auto;
  display: flex;
  flex-direction: column;
  justify-content: flex-end;
  min-height: 0;
  gap: 0.55rem;
}

.growth-deck--compact {
  justify-content: flex-start;
  gap: 0.45rem;
}

.growth-deck--compact :deep(.flashcards) {
  padding-top: 1.15rem;
}

.growth-deck--compact :deep(.flip-card),
.growth-deck--compact :deep(.flip-card__inner) {
  height: 10.5rem;
}

.growth-deck--compact .growth-empty {
  min-height: 10.5rem;
  background: transparent;
}

.growth-deck :deep(.flashcards),
.growth-deck :deep(.flashcards__card-wrapper),
.growth-deck :deep(.flash-card),
.growth-deck :deep(.flip-card) {
  outline: none !important;
  box-shadow: none;
}

.growth-deck :deep(.flashcards) {
  width: 100%;
  padding-top: 1.75rem;
  box-sizing: content-box;
}

.growth-deck :deep(.flashcards__card-wrapper[style*='opacity: 0']),
.growth-deck :deep(.flashcards__card-wrapper[style*='opacity:0']) {
  visibility: hidden;
  pointer-events: none;
}

.growth-deck :deep(.flip-card),
.growth-deck :deep(.flip-card__inner) {
  width: 100%;
  height: 9.8rem;
}

.growth-deck :deep(.flip-card__front),
.growth-deck :deep(.flip-card__back) {
  height: 100%;
}

/*
 * page 模式：对齐 ChineseExpressionFan 的 17.25rem，再 +20% → 20.7rem。
 * 必须放在基础高度之后，并用 !important 压过 vue3-flashcards 的 inline height。
 * flashcards 用 content-box，padding-top 叠在卡片高度之外（与 Fan 一致）。
 */
.growth-deck--page {
  flex: 0 0 auto;
  justify-content: flex-start;
  gap: 1rem;
  width: min(100%, 36rem);
  margin: 0 auto;
}

.growth-deck--page :deep(.flashcards) {
  padding-top: 2.75rem !important;
  min-height: 20.7rem !important;
  height: 20.7rem !important;
  max-height: none !important;
  box-sizing: content-box !important;
  overflow: visible;
}

.growth-deck--page :deep(.flashcards__stack),
.growth-deck--page :deep(.flashcards__cards),
.growth-deck--page :deep(.flashcards__card-wrapper) {
  min-height: 20.7rem !important;
  height: 20.7rem !important;
  max-height: none !important;
}

.growth-deck--page :deep(.flip-card),
.growth-deck--page :deep(.flip-card__inner) {
  height: 20.7rem !important;
  max-height: none !important;
}

.growth-deck--page .growth-empty {
  min-height: 20.7rem;
}

.growth-deck--page .growth-face {
  padding: 0.75rem 0.9rem 0.8rem;
  box-shadow: var(--kk-shadow-card);
}

.growth-deck--page .growth-main {
  font-size: clamp(1.1rem, 2.4vw, 1.45rem);
}

.growth-deck--page .growth-main--back {
  font-size: clamp(0.98rem, 2vw, 1.2rem);
}

.growth-flip {
  width: 100%;
}

.growth-face {
  box-sizing: border-box;
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  padding: 0.6rem 0.72rem 0.65rem;
  border-radius: var(--kk-radius-md);
  border: 1px solid var(--kk-color-border);
  background: var(--kk-color-surface-solid);
  box-shadow: 0 6px 14px rgba(11, 26, 125, 0.07);
  cursor: pointer;
  user-select: none;
}

.growth-face--back {
  border-color: color-mix(in srgb, var(--kk-color-accent) 36%, var(--kk-color-border));
  box-shadow:
      var(--kk-shadow-card),
      inset 0 0 0 1px color-mix(in srgb, var(--kk-color-accent) 12%, transparent);
}

.growth-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.35rem;
  margin-bottom: 0.35rem;
  flex-shrink: 0;
}

.growth-badge {
  display: inline-flex;
  align-items: center;
  padding: 0.08rem 0.45rem;
  border-radius: var(--kk-radius-pill);
  font-size: 0.68rem;
  font-weight: 600;
  color: var(--kk-color-text-muted);
  background: var(--kk-color-surface-muted);
  border: 1px solid var(--kk-color-border-subtle);
}

.growth-badge--back {
  color: var(--kk-color-accent-text);
  background: var(--kk-color-accent-bg);
  border-color: color-mix(in srgb, var(--kk-color-accent) 28%, transparent);
}

.growth-index {
  font-family: var(--kk-font-mono);
  font-size: 0.68rem;
  font-variant-numeric: tabular-nums;
  color: var(--kk-color-text-subtle);
}

.growth-pane {
  flex: 1;
  min-height: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  text-align: center;
}

.growth-main {
  margin: 0;
  font-family: var(--kk-font-display);
  font-size: clamp(0.88rem, 1vw, 0.98rem);
  font-weight: 700;
  line-height: 1.45;
  color: var(--kk-color-primary);
}

.growth-main--back {
  font-family: var(--kk-font-mono);
  font-size: 0.88rem;
  font-weight: 600;
}

.growth-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 9.8rem;
  padding: 0.75rem;
  border-radius: var(--kk-radius-md);
  border: 1px dashed var(--kk-color-border);
  background: var(--kk-color-surface-muted);
}

.growth-empty p {
  margin: 0;
  font-size: 0.82rem;
  line-height: 1.55;
  color: var(--kk-color-text-muted);
  text-align: center;
}

.growth-actions {
  display: flex;
  flex-direction: column;
  gap: 0.4rem;
}

.growth-actions-hint {
  margin: 0;
  font-size: 0.68rem;
  color: var(--kk-color-text-subtle);
  text-align: center;
}

.growth-grade-row {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 0.4rem;
}

.growth-grade-btn {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 0.12rem;
  min-height: 2.85rem;
  padding: 0.42rem 0.3rem;
  border-radius: var(--kk-radius-sm);
  border: 1px solid var(--kk-color-border);
  background: var(--kk-color-surface-solid);
  color: var(--kk-color-text-secondary);
  font-family: var(--kk-font-body);
  cursor: pointer;
  transition:
      background var(--kk-duration-normal) var(--kk-ease-out),
      border-color var(--kk-duration-normal) var(--kk-ease-out),
      color var(--kk-duration-normal) var(--kk-ease-out),
      transform var(--kk-duration-normal) var(--kk-ease-out);
}

.growth-grade-label {
  font-size: 0.82rem;
  font-weight: 800;
  letter-spacing: 0.01em;
}

.growth-grade-sub {
  font-size: 0.62rem;
  font-weight: 600;
  opacity: 0.88;
}

.growth-grade-btn:hover:not(:disabled) {
  transform: translateY(-1px);
}

.growth-grade-btn:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.growth-grade-btn--again {
  color: var(--kk-color-danger);
  background: var(--kk-color-danger-bg);
  border-color: color-mix(in srgb, var(--kk-color-danger) 18%, transparent);
}

.growth-grade-btn--hard {
  color: var(--kk-color-warn);
  background: var(--kk-color-warn-bg);
  border-color: color-mix(in srgb, var(--kk-color-warn) 18%, transparent);
}

.growth-grade-btn--good {
  color: var(--kk-color-link);
  background: color-mix(in srgb, var(--kk-color-link) 10%, #ffffff);
  border-color: color-mix(in srgb, var(--kk-color-link) 18%, transparent);
}

.growth-grade-btn--easy {
  color: var(--kk-color-success);
  background: var(--kk-color-success-bg);
  border-color: color-mix(in srgb, var(--kk-color-success) 18%, transparent);
}

@media (max-width: 560px) {
  .growth-grade-row {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
