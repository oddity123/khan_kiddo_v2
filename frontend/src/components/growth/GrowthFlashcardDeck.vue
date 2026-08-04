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
    }>(),
    {compact: false},
)

interface DeckExpose {
  reset: (options?: {animate?: boolean; delay?: number}) => void | Promise<void>
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
    return '今日成长卡已练完'
  }
  return '今天没有待复习的成长卡'
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
  try {
    await gradeGrowthCard(current.cardId, grade)
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
      :class="{'growth-deck--compact': compact}"
      aria-label="今日成长卡"
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
      <p class="growth-actions-hint">点击卡片翻面后，选择掌握程度</p>
      <div class="growth-grade-row">
        <button
            type="button"
            class="growth-grade-btn growth-grade-btn--again"
            :disabled="grading || !isFlipped"
            @click="submitGrade('again')"
        >
          不会
        </button>
        <button
            type="button"
            class="growth-grade-btn growth-grade-btn--fuzzy"
            :disabled="grading || !isFlipped"
            @click="submitGrade('fuzzy')"
        >
          模糊
        </button>
        <button
            type="button"
            class="growth-grade-btn growth-grade-btn--good"
            :disabled="grading || !isFlipped"
            @click="submitGrade('good')"
        >
          会了
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
  grid-template-columns: repeat(3, 1fr);
  gap: 0.35rem;
}

.growth-grade-btn {
  padding: 0.38rem 0.35rem;
  border-radius: var(--kk-radius-sm);
  border: 1px solid var(--kk-color-border);
  background: var(--kk-color-surface-solid);
  color: var(--kk-color-text-secondary);
  font-family: var(--kk-font-body);
  font-size: 0.72rem;
  font-weight: 600;
  cursor: pointer;
  transition:
      background var(--kk-duration-normal) var(--kk-ease-out),
      border-color var(--kk-duration-normal) var(--kk-ease-out),
      color var(--kk-duration-normal) var(--kk-ease-out);
}

.growth-grade-btn:hover:not(:disabled) {
  border-color: color-mix(in srgb, var(--kk-color-primary) 24%, var(--kk-color-border));
  color: var(--kk-color-primary);
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

.growth-grade-btn--fuzzy {
  color: var(--kk-color-warn);
  background: var(--kk-color-warn-bg);
  border-color: color-mix(in srgb, var(--kk-color-warn) 18%, transparent);
}

.growth-grade-btn--good {
  color: var(--kk-color-success);
  background: var(--kk-color-success-bg);
  border-color: color-mix(in srgb, var(--kk-color-success) 18%, transparent);
}
</style>
