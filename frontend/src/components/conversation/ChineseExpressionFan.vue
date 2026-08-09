<script setup lang="ts">
import {ChatLineSquare, Check, CloseBold, Delete, Refresh, RefreshLeft,} from '@element-plus/icons-vue'
import confetti from 'canvas-confetti'
import {ElMessage, ElMessageBox} from 'element-plus'
import {computed, nextTick, onBeforeUnmount, onMounted, ref} from 'vue'
import {FlashCards, FlipCard} from 'vue3-flashcards'

import type {ChineseExpressionItem} from '@/types/conversation'
import type {GrowthGrade} from '@/types/growthCard'
import {getErrorMessage} from '@/utils/error'

const props = withDefaults(
    defineProps<{
      items: ChineseExpressionItem[]
      layout?: 'main' | 'aside'
      /** 标题文案；成长卡侧栏可传「成长卡」 */
      heading?: string
      /** expression=中文表达知识卡；growth=成长卡（标签改为提示/答案） */
      variant?: 'expression' | 'growth'
      /** 首页复盘区使用的紧凑牌组 */
      compact?: boolean
      showHint?: boolean
      /** 成长卡可删除：对勾右侧显示垃圾桶 */
      deletable?: boolean
      /** 确认后真正删除；抛错则保留卡片 */
      removeCard?: (cardId: string) => Promise<void>
      /**
       * 首页今日复习：滑出时自评落库。
       * ← again / → good；开启后禁用撤回与「再复习一遍」（避免已评分卡回炉）。
       */
      reviewCard?: (cardId: string, grade: Extract<GrowthGrade, 'again' | 'good'>) => Promise<void>
    }>(),
    {
      layout: 'main',
      heading: '知识卡片',
      variant: 'expression',
      compact: false,
      showHint: undefined,
      deletable: false,
    },
)

interface FlashCardItem {
  id: string
  originalIndex?: number
  originalSentence: string
  focusPhrase?: string
  suggestion?: string
  kindLabel?: string
  cardKey?: string
  evidenceCount?: number
  [key: string]: unknown
}

const resolvedHint = computed(() =>
    props.showHint ?? props.layout !== 'aside',
)

/** aside 只要三张叠层样式（库的 stack=n 会露出 n+1 层，故用 2） */
const stackDepth = computed(() => (props.layout === 'aside' ? (props.compact ? 1 : 2) : 3))
const stackOffset = computed(() => (props.layout === 'aside' ? (props.compact ? 8 : 12) : 16))
const stackScale = computed(() => (props.layout === 'aside' ? (props.compact ? 0.018 : 0.025) : 0.02))

type SwipeDir = 'left' | 'right'

interface DeckExpose {
  swipeLeft: () => void
  swipeRight: () => void
  restore: () => void
  reset: (options?: {animate?: boolean; delay?: number}) => void | Promise<void>
  canRestore: {value: boolean} | boolean
  isStart: {value: boolean} | boolean
  isEnd: {value: boolean} | boolean
}

const rootRef = ref<HTMLElement | null>(null)
const deckRef = ref<DeckExpose | null>(null)
const resetting = ref(false)
const swiping = ref(false)
const dragging = ref(false)
const deleting = ref(false)
const deleteAnimatingId = ref<string | null>(null)
const reviewed = ref(0)
const celebrated = ref(false)
/** 按钮/键盘滑出时叠在卡片上的对勾/叉（不 peek，避免抽动） */
const feedbackDir = ref<SwipeDir | null>(null)
const feedbackCardId = ref<string | null>(null)
/** 悬停本区时启用快捷键，避免抢走页面其它区域的方向键 */
const hotkeysArmed = ref(false)
/** 驱动 canRestore / isStart 在脚本侧刷新 */
const uiTick = ref(0)
let liftHoldTimer: number | null = null

const deckLifted = computed(() => swiping.value || dragging.value || deleting.value)

function clearLiftHold() {
  if (liftHoldTimer) {
    clearTimeout(liftHoldTimer)
    liftHoldTimer = null
  }
}

/** 飞出动画期间保持抬层，避免毛玻璃圆角裁切 */
function holdLift(ms = 480) {
  clearLiftHold()
  swiping.value = true
  liftHoldTimer = window.setTimeout(() => {
    swiping.value = false
    liftHoldTimer = null
  }, ms)
}

function onDragStart() {
  dragging.value = true
}

function onDragEnd() {
  dragging.value = false
}

function onSwipe() {
  reviewed.value = Math.min(count.value, reviewed.value + 1)
  dragging.value = false
  holdLift(480)
  refreshUi()
  if (!suppressCelebrate.value && reviewed.value >= count.value) {
    nextTick(() => celebrate())
  }
}

const reviewMode = computed(() => Boolean(props.reviewCard))

function onSwipeLeft() {
  void settleSwipe('left')
}

function onSwipeRight() {
  void settleSwipe('right')
}

async function settleSwipe(direction: SwipeDir) {
  const card = deckItems.value[reviewed.value]
  onSwipe()
  if (!props.reviewCard || !card?.cardKey) {
    return
  }
  try {
    await props.reviewCard(card.cardKey, direction === 'right' ? 'good' : 'again')
  } catch (error) {
    ElMessage.error(getErrorMessage(error, '评分失败，请稍后重试'))
  }
}

const deckItems = computed((): FlashCardItem[] =>
    props.items.map((item, index) => ({
      id: item.cardKey ? `cn-expr-${item.cardKey}` : `cn-expr-${item.originalIndex ?? index}`,
      originalIndex: item.originalIndex,
      originalSentence: item.originalSentence,
      focusPhrase: item.focusPhrase,
      suggestion: item.suggestion,
      kindLabel: item.kindLabel,
      cardKey: item.cardKey,
      evidenceCount: item.evidenceCount ?? 0,
    })),
)

function frontPaneTag(item: FlashCardItem): string {
  if (props.variant === 'growth') {
    return isVocabFocus(item) ? '中文' : '提示'
  }
  return isVocabFocus(item) ? '目标词' : '原句'
}

function backPaneTag(item: FlashCardItem): string {
  if (props.variant === 'growth') {
    return isVocabFocus(item) ? '英文' : '答案'
  }
  return isVocabFocus(item) ? '英文' : '英文建议'
}

const count = computed(() => deckItems.value.length)

function cardFrontText(item: FlashCardItem): string {
  if (typeof item.focusPhrase === 'string' && item.focusPhrase.trim()) {
    return item.focusPhrase.trim()
  }
  return item.originalSentence
}

function isVocabFocus(item: FlashCardItem): boolean {
  return typeof item.focusPhrase === 'string' && item.focusPhrase.trim().length > 0
}

const emit = defineEmits<{
  openEvidence: [cardId: string]
}>()

function openEvidence(item: FlashCardItem, event: Event) {
  event.stopPropagation()
  event.preventDefault()
  if (!item.cardKey || !(item.evidenceCount && item.evidenceCount > 0)) {
    return
  }
  emit('openEvidence', item.cardKey)
}

const isComplete = computed(() => count.value > 0 && reviewed.value >= count.value)

function asCard(item: Record<string, unknown>): FlashCardItem {
  return item as FlashCardItem
}

function cardOrdinal(item: FlashCardItem): number {
  const idx = deckItems.value.findIndex((card) => card.id === item.id)
  return idx >= 0 ? idx + 1 : 1
}

function unwrapFlag(flag: {value: boolean} | boolean | undefined): boolean {
  if (typeof flag === 'boolean') {
    return flag
  }
  return flag?.value ?? false
}

function refreshUi() {
  uiTick.value += 1
}

const canRestore = computed(() => {
  void uiTick.value
  return unwrapFlag(deckRef.value?.canRestore)
})

const isStart = computed(() => {
  void uiTick.value
  return unwrapFlag(deckRef.value?.isStart)
})

function flipActive() {
  if (isComplete.value || resetting.value || swiping.value) {
    return
  }
  const card = rootRef.value?.querySelector(
      '.flashcards__card--active .flip-card, [data-active-card="true"] .flip-card',
  ) as HTMLElement | null
  card?.dispatchEvent(new PointerEvent('pointerup', {bubbles: true}))
}

function celebrate() {
  if (celebrated.value) {
    return
  }
  celebrated.value = true
  if (typeof window !== 'undefined' && window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
    return
  }
  const colors = ['#c9a227', '#3d4a9e', '#e8b86d', '#5b7cfa', '#f0d78c']
  confetti({
    particleCount: 90,
    spread: 72,
    startVelocity: 38,
    origin: {y: 0.62},
    colors,
  })
  window.setTimeout(() => {
    confetti({
      particleCount: 45,
      angle: 60,
      spread: 55,
      origin: {x: 0.12, y: 0.7},
      colors,
    })
    confetti({
      particleCount: 45,
      angle: 120,
      spread: 55,
      origin: {x: 0.88, y: 0.7},
      colors,
    })
  }, 220)
}

/** 插入动画期间抑制滑出庆祝 */
const suppressCelebrate = ref(false)

/**
 * 用「撤回上一张」同款 restore 动画表现新卡插入：
 * 先轻推走当前顶卡再 restore 飞回（不 reset 整副牌）。
 */
async function playInsertRestore() {
  if (resetting.value || swiping.value || deleting.value || count.value === 0) {
    return
  }
  await nextTick()
  const deck = deckRef.value
  if (!deck) {
    return
  }
  const keepReviewed = reviewed.value
  suppressCelebrate.value = true
  celebrated.value = false
  feedbackDir.value = null
  feedbackCardId.value = null
  holdLift(520)
  swiping.value = true
  try {
    deck.swipeLeft()
    await new Promise<void>((resolve) => {
      window.setTimeout(resolve, 360)
    })
    deck.restore()
    await new Promise<void>((resolve) => {
      window.setTimeout(resolve, 80)
    })
  } finally {
    swiping.value = false
    suppressCelebrate.value = false
    reviewed.value = keepReviewed
    refreshUi()
  }
}

defineExpose({
  playInsertRestore,
  getActiveIndex: () => reviewed.value,
})

function onRestore() {
  reviewed.value = Math.max(0, reviewed.value - 1)
  celebrated.value = false
  feedbackDir.value = null
  feedbackCardId.value = null
  swiping.value = false
  refreshUi()
}

/** 叠层显示对勾/叉，直接滑出（不再 peek，避免先位移再飞出的抽动） */
async function swipeWithFeedback(direction: SwipeDir) {
  if (isComplete.value || resetting.value || swiping.value || deleting.value || !deckRef.value) {
    return
  }
  const card = deckItems.value[reviewed.value]
  if (!card) {
    return
  }
  swiping.value = true
  feedbackCardId.value = card.id
  feedbackDir.value = direction
  try {
    await nextTick()
    if (direction === 'left') {
      deckRef.value.swipeLeft()
    } else {
      deckRef.value.swipeRight()
    }
  } finally {
    window.setTimeout(() => {
      feedbackDir.value = null
      feedbackCardId.value = null
      // onSwipe 会 holdLift；若未触发则兜底落下
      if (!liftHoldTimer) {
        swiping.value = false
      }
    }, 420)
  }
}

function showProgFeedback(itemId: string, direction: SwipeDir): boolean {
  return feedbackCardId.value === itemId && feedbackDir.value === direction
}

function swipeLeft() {
  void swipeWithFeedback('left')
}

function swipeRight() {
  void swipeWithFeedback('right')
}

function restore() {
  if (resetting.value || deleting.value) {
    return
  }
  deckRef.value?.restore()
}

async function deleteCurrent() {
  if (!props.deletable || !props.removeCard || isComplete.value || resetting.value || swiping.value || deleting.value) {
    return
  }
  const card = deckItems.value[reviewed.value]
  if (!card?.cardKey) {
    return
  }

  try {
    await ElMessageBox.confirm('确定删除这张成长卡？删除后不可恢复。', '删除确认', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning',
    })
  } catch {
    return
  }

  deleting.value = true
  deleteAnimatingId.value = card.id
  const keepIndex = reviewed.value
  try {
    await new Promise<void>((resolve) => {
      window.setTimeout(resolve, 320)
    })
    await props.removeCard(card.cardKey)
    celebrated.value = false
    // 删的是当前张：列表收缩后同一 index 即下一张，勿 reset 回第一张
    reviewed.value = Math.min(keepIndex, Math.max(0, props.items.length))
    feedbackDir.value = null
    feedbackCardId.value = null
    await nextTick()
    refreshUi()
  } catch (error) {
    ElMessage.error(getErrorMessage(error, '删除失败，请稍后重试'))
  } finally {
    deleteAnimatingId.value = null
    deleting.value = false
  }
}

async function resetToFirst(resetFn?: DeckExpose['reset']) {
  if (resetting.value || deleting.value) {
    return
  }
  if (isStart.value && !isComplete.value) {
    return
  }
  resetting.value = true
  celebrated.value = false
  reviewed.value = 0
  feedbackDir.value = null
  feedbackCardId.value = null
  swiping.value = false
  try {
    const reset = resetFn ?? deckRef.value?.reset.bind(deckRef.value)
    await reset?.({animate: true, delay: 70})
  } finally {
    resetting.value = false
    refreshUi()
  }
}

function armHotkeys() {
  hotkeysArmed.value = true
}

function disarmHotkeys() {
  hotkeysArmed.value = false
}

function onKeydown(event: KeyboardEvent) {
  if (!hotkeysArmed.value) {
    return
  }
  const target = event.target as HTMLElement | null
  const tag = target?.tagName
  if (tag === 'INPUT' || tag === 'TEXTAREA' || target?.isContentEditable) {
    return
  }

  if (event.key === ' ' || event.key === 'Spacebar') {
    event.preventDefault()
    flipActive()
    return
  }
  if (event.key === 'ArrowLeft') {
    event.preventDefault()
    swipeLeft()
    return
  }
  if (event.key === 'ArrowRight') {
    event.preventDefault()
    swipeRight()
  }
}

onMounted(() => {
  window.addEventListener('keydown', onKeydown)
})

onBeforeUnmount(() => {
  window.removeEventListener('keydown', onKeydown)
  clearLiftHold()
  confetti.reset()
})
</script>

<template>
  <section
      ref="rootRef"
      class="cn-fan kk-glass kk-glass--panel"
      :class="[
        `cn-fan--${layout}`,
        `cn-fan--${variant}`,
        {
          'cn-fan--compact': compact,
          'cn-fan--lift': deckLifted,
        },
      ]"
      :aria-label="heading"
      @mouseenter="armHotkeys"
      @mouseleave="disarmHotkeys()"
  >
    <header class="cn-fan-head">
      <span class="cn-fan-title">
        <el-icon aria-hidden="true"><ChatLineSquare/></el-icon>
        {{ heading }}
      </span>
      <span class="cn-fan-count">总计 {{ count }} 张卡片</span>
    </header>
    <p v-if="resolvedHint" class="cn-fan-hint">
      鼠标移入本区后：
      <kbd>空格</kbd> 翻面 ·
      <template v-if="reviewMode">
        <kbd>←</kbd> 不会 ·
        <kbd>→</kbd> 会了 ·
      </template>
      <template v-else>
        <kbd>←</kbd> 略过 ·
        <kbd>→</kbd> 掌握 ·
      </template>
      也可滑动或点击卡片
      <template v-if="variant === 'expression'"> · 不计入语法错误</template>
    </p>

    <div class="cn-fan-stage">
      <div class="cn-fan-deck">
        <FlashCards
            ref="deckRef"
            class="cn-flashcards"
            :items="deckItems"
            item-key="id"
            :loop="false"
            :stack="stackDepth"
            stack-direction="top"
            :stack-offset="stackOffset"
            :stack-scale="stackScale"
            swipe-direction="horizontal"
            :wait-animation-end="true"
            :a11y="{ enabled: true, keyboard: false, manageFocus: false }"
            @swipe-left="onSwipeLeft"
            @swipe-right="onSwipeRight"
            @restore="onRestore"
            @dragstart="onDragStart"
            @dragend="onDragEnd"
        >
          <template #default="{ item: rawItem }">
            <template v-for="item in [asCard(rawItem)]" :key="item.id">
              <div
                  class="cn-flip-wrap"
                  :class="{ 'cn-flip-wrap--deleting': deleteAnimatingId === item.id }"
              >
                <FlipCard class="cn-flip" flip-axis="y">
                  <template #front>
                    <article class="cn-card cn-card--front">
                      <header class="cn-card-head">
                        <span class="cn-badge">{{ item.kindLabel || '正面' }}</span>
                        <span class="cn-card-index">{{ cardOrdinal(item) }}/{{ count }}</span>
                      </header>
                      <section class="cn-pane cn-pane--back">
                        <template v-if="isVocabFocus(item) || variant === 'growth'">
                          <div class="cn-suggest-block cn-suggest-block--solo">
                            <span class="pane-tag">{{ frontPaneTag(item) }}</span>
                            <p
                                class="pane-improved pane-improved--center"
                                :class="{ 'pane-improved--term': isVocabFocus(item) }"
                                :title="cardFrontText(item)"
                            >{{ cardFrontText(item) }}</p>
                          </div>
                          <p
                              v-if="variant === 'expression' && isVocabFocus(item)"
                              class="cn-orig-mini"
                              :title="item.originalSentence"
                          >
                            原句：{{ item.originalSentence }}
                          </p>
                        </template>
                        <template v-else>
                          <div class="cn-suggest-block cn-suggest-block--solo">
                            <span class="pane-tag">{{ frontPaneTag(item) }}</span>
                            <p
                                class="pane-improved pane-improved--center"
                                :title="item.originalSentence"
                            >{{ item.originalSentence }}</p>
                          </div>
                        </template>
                        <button
                            v-if="variant === 'growth' && (item.evidenceCount ?? 0) > 0"
                            type="button"
                            class="cn-evidence-btn"
                            @click.stop="openEvidence(item, $event)"
                            @pointerdown.stop
                            @pointerup.stop
                        >
                          查看证据
                        </button>
                      </section>
                    </article>
                  </template>

                  <template #back>
                    <article class="cn-card cn-card--back">
                      <header class="cn-card-head">
                        <span class="cn-badge cn-badge--back">{{ item.kindLabel || '反面' }}</span>
                        <span class="cn-card-index">{{ cardOrdinal(item) }}/{{ count }}</span>
                      </header>
                      <section class="cn-pane cn-pane--back">
                        <template v-if="isVocabFocus(item) || variant === 'growth'">
                          <div class="cn-suggest-block cn-suggest-block--solo">
                            <span class="pane-tag">{{ backPaneTag(item) }}</span>
                            <p
                                v-if="item.suggestion"
                                class="pane-improved pane-improved--center"
                                :title="item.suggestion"
                            >{{ item.suggestion }}</p>
                            <p v-else class="cn-empty-hint">暂未生成对应内容</p>
                          </div>
                          <p
                              v-if="variant === 'expression' && isVocabFocus(item)"
                              class="cn-orig-mini"
                              :title="item.originalSentence"
                          >
                            原句：{{ item.originalSentence }}
                          </p>
                        </template>
                        <template v-else>
                          <div class="cn-orig-block">
                            <span class="pane-tag">原句</span>
                            <p class="pane-orig" :title="item.originalSentence">{{ item.originalSentence }}</p>
                          </div>
                          <div class="cn-suggest-block">
                            <span class="pane-tag">{{ backPaneTag(item) }}</span>
                            <p
                                v-if="item.suggestion"
                                class="pane-improved"
                                :title="item.suggestion"
                            >{{ item.suggestion }}</p>
                            <p v-else class="cn-empty-hint">暂未生成英文建议</p>
                          </div>
                        </template>
                        <button
                            v-if="variant === 'growth' && (item.evidenceCount ?? 0) > 0"
                            type="button"
                            class="cn-evidence-btn"
                            @click.stop="openEvidence(item, $event)"
                            @pointerdown.stop
                            @pointerup.stop
                        >
                          查看证据
                        </button>
                      </section>
                    </article>
                  </template>
                </FlipCard>

                <div
                    v-if="showProgFeedback(item.id, 'right')"
                    class="cn-prog-indicator cn-prog-indicator--yes"
                    aria-hidden="true"
                >
                  <svg width="80" height="80" viewBox="0 0 28 28" fill="none" xmlns="http://www.w3.org/2000/svg">
                    <path d="M6.65263 14.0304C6.29251 13.6703 6.29251 13.0864 6.65263 12.7263C7.01276 12.3662 7.59663 12.3662 7.95676 12.7263L11.6602 16.4297L19.438 8.65183C19.7981 8.29171 20.382 8.29171 20.7421 8.65183C21.1023 9.01195 21.1023 9.59583 20.7421 9.95596L12.3667 18.3314C11.9762 18.7219 11.343 18.7219 10.9525 18.3314L6.65263 14.0304Z" fill="currentColor"/>
                    <path clip-rule="evenodd" d="M14 1C6.8203 1 1 6.8203 1 14C1 21.1797 6.8203 27 14 27C21.1797 27 27 21.1797 27 14C27 6.8203 21.1797 1 14 1ZM3 14C3 7.92487 7.92487 3 14 3C20.0751 3 25 7.92487 25 14C25 20.0751 20.0751 25 14 25C7.92487 25 3 20.0751 3 14Z" fill="currentColor" fill-rule="evenodd"/>
                  </svg>
                </div>
                <div
                    v-else-if="showProgFeedback(item.id, 'left')"
                    class="cn-prog-indicator cn-prog-indicator--no"
                    aria-hidden="true"
                >
                  <svg width="80" height="80" viewBox="0 0 64 64" xmlns="http://www.w3.org/2000/svg">
                    <path d="M32.266,7.951c13.246,0 24,10.754 24,24c0,13.246 -10.754,24 -24,24c-13.246,0 -24,-10.754 -24,-24c0,-13.246 10.754,-24 24,-24Zm-15.616,11.465c-2.759,3.433 -4.411,7.792 -4.411,12.535c0,11.053 8.974,20.027 20.027,20.027c4.743,0 9.102,-1.652 12.534,-4.411l-28.15,-28.151Zm31.048,25.295c2.87,-3.466 4.596,-7.913 4.596,-12.76c0,-11.054 -8.974,-20.028 -20.028,-20.028c-4.847,0 -9.294,1.726 -12.76,4.596l28.192,28.192Z" fill="currentColor"/>
                  </svg>
                </div>
              </div>
            </template>
          </template>

          <template #empty="{ reset }">
            <div class="cn-done" role="status">
              <p class="cn-done-title">复习完成</p>
              <p class="cn-done-desc">{{ reviewMode ? '今日成长卡已练完' : '已复习完' }}</p>
              <button
                  v-if="!reviewMode"
                  type="button"
                  class="cn-done-btn"
                  @click="resetToFirst(reset)"
              >
                再复习一遍
              </button>
            </div>
          </template>
        </FlashCards>
      </div>

      <div class="cn-actions" v-if="!isComplete">
        <button
            type="button"
            class="cn-action-btn"
            data-tip="重置到第一张"
            :disabled="reviewMode || isStart || resetting || deleting"
            :class="{ 'cn-action-btn--spin': resetting }"
            aria-label="重置到第一张"
            @click="resetToFirst()"
        >
          <el-icon><Refresh/></el-icon>
        </button>
        <button
            type="button"
            class="cn-action-btn"
            data-tip="撤回上一张"
            :disabled="reviewMode || !canRestore || resetting || deleting"
            aria-label="撤回上一张"
            @click="restore"
        >
          <el-icon><RefreshLeft/></el-icon>
        </button>
        <button
            type="button"
            class="cn-action-btn cn-action-btn--no"
            :data-tip="reviewMode ? '不会 ←' : '略过 ←'"
            :disabled="resetting || swiping || deleting"
            :aria-label="reviewMode ? '不会' : '略过'"
            @click="swipeLeft"
        >
          <el-icon><CloseBold/></el-icon>
        </button>
        <button
            type="button"
            class="cn-action-btn cn-action-btn--yes"
            :data-tip="reviewMode ? '会了 →' : '掌握 →'"
            :disabled="resetting || swiping || deleting"
            :aria-label="reviewMode ? '会了' : '掌握'"
            @click="swipeRight"
        >
          <el-icon><Check/></el-icon>
        </button>
        <button
            v-if="deletable"
            type="button"
            class="cn-action-btn cn-action-btn--delete"
            data-tip="删除"
            :disabled="resetting || swiping || deleting || !deckItems[reviewed]?.cardKey"
            aria-label="删除成长卡"
            @click="deleteCurrent"
        >
          <el-icon><Delete/></el-icon>
        </button>
      </div>
    </div>
  </section>
</template>

<style scoped>
.cn-fan {
  padding: 0.7rem 0.9rem 0.95rem;
  margin-bottom: 1.1rem;
  outline: none;
}

/* 滑走/拖拽/删除时抬到最前，并关掉毛玻璃裁切（backdrop-filter+圆角会裁子元素） */
.cn-fan--lift {
  position: relative;
  z-index: 80;
  overflow: visible;
  backdrop-filter: none;
  -webkit-backdrop-filter: none;
  background: var(--kk-color-surface-solid);
}

/* 去掉牌堆/卡片在聚焦时的浏览器默认黑框 */
.cn-fan-deck :deep(.flashcards),
.cn-fan-deck :deep(.flashcards__card-wrapper),
.cn-fan-deck :deep(.flash-card),
.cn-fan-deck :deep(.flip-card) {
  outline: none !important;
  box-shadow: none;
}

.cn-fan-deck :deep(.flashcards__card-wrapper:focus),
.cn-fan-deck :deep(.flashcards__card-wrapper:focus-visible),
.cn-fan-deck :deep(.flashcards:focus),
.cn-fan-deck :deep(.flashcards:focus-visible) {
  outline: none !important;
}

.cn-fan--aside {
  padding: 0.75rem 0.8rem 0.9rem;
}

.cn-fan--aside .cn-fan-stage {
  width: 100%;
  margin-top: 0.55rem;
}

.cn-fan--aside .cn-fan-deck {
  min-height: calc(14.5rem + 1.75rem);
}

.cn-fan--aside .cn-fan-deck :deep(.flip-card),
.cn-fan--aside .cn-fan-deck :deep(.flip-card__inner) {
  height: 14.5rem;
}

.cn-fan--aside .cn-fan-deck :deep(.flashcards) {
  padding-top: 1.5rem;
}

.cn-fan--aside .cn-actions {
  margin-top: 0.75rem;
}

.cn-fan--compact.cn-fan--aside {
  padding: 0.35rem 0.65rem 0.55rem;
}

.cn-fan--compact.cn-fan--aside .cn-fan-stage {
  width: min(100%, 24rem);
  margin-top: 0;
}

.cn-fan--compact.cn-fan--aside .cn-fan-deck {
  min-height: 11.3rem;
  max-height: 11.3rem;
  margin-bottom: 0;
}

.cn-fan--compact.cn-fan--aside .cn-fan-deck :deep(.flashcards) {
  min-height: 10.35rem !important;
  height: 10.35rem !important;
  max-height: 10.35rem !important;
  padding-top: 0.75rem;
  box-sizing: border-box;
}

.cn-fan--compact.cn-fan--aside .cn-fan-deck :deep(.flashcards__stack),
.cn-fan--compact.cn-fan--aside .cn-fan-deck :deep(.flashcards__cards),
.cn-fan--compact.cn-fan--aside .cn-fan-deck :deep(.flashcards__card-wrapper) {
  min-height: 9.6rem !important;
  height: 9.6rem !important;
  max-height: 9.6rem !important;
}

.cn-fan--compact.cn-fan--aside .cn-fan-deck :deep(.flip-card),
.cn-fan--compact.cn-fan--aside .cn-fan-deck :deep(.flip-card__inner) {
  height: 9.6rem !important;
}

.cn-fan--compact .cn-card {
  padding: 0.55rem 0.7rem 0.62rem;
}

.cn-fan--compact .pane-improved--center {
  font-size: 0.95rem;
  line-height: 1.36;
  -webkit-line-clamp: 4;
}

.cn-fan--compact .pane-improved--term {
  font-size: 1.18rem;
  line-height: 1.25;
}

.cn-fan--compact.cn-fan--aside .cn-actions {
  gap: 0.62rem;
  margin-top: 0.4rem;
  padding-top: 0;
}

.cn-fan--compact .cn-action-btn {
  width: 2.32rem;
  height: 2.32rem;
}

.cn-fan--main .cn-fan-title,
.cn-fan--aside .cn-fan-title {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  font-family: var(--kk-font-display);
  font-size: 1.15rem;
  font-weight: 700;
  color: var(--kk-color-primary);
}

.cn-fan-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.5rem;
}

.cn-fan-count {
  font-size: 0.78rem;
  font-weight: 700;
  color: var(--kk-color-accent-text);
  padding: 0.15rem 0.55rem;
  border-radius: var(--kk-radius-pill);
  background: var(--kk-color-accent-bg);
}

.cn-fan-hint {
  margin: 0.25rem 0 0;
  font-size: 0.74rem;
  color: var(--kk-color-text-subtle);
  line-height: 1.45;
}

.cn-fan-hint kbd {
  display: inline-block;
  padding: 0.05rem 0.35rem;
  border-radius: 0.3rem;
  border: 1px solid color-mix(in srgb, var(--kk-color-primary) 14%, transparent);
  background: color-mix(in srgb, var(--kk-color-primary) 5%, white);
  font-family: var(--kk-font-mono);
  font-size: 0.7rem;
  color: var(--kk-color-text-muted);
}

.cn-fan-stage {
  width: min(100%, 30rem);
  margin: 0.7rem auto 0;
  display: flex;
  flex-direction: column;
  align-items: stretch;
}

.cn-fan-deck {
  position: relative;
  z-index: 6;
  width: 100%;
  min-height: calc(17.25rem + 3.25rem);
  margin-bottom: 0.25rem;
  overflow: visible;
}

.cn-flashcards {
  width: 100%;
}

.cn-fan-deck :deep(.flashcards) {
  width: 100%;
  padding-top: 3rem;
  box-sizing: content-box;
  overflow: visible;
  isolation: auto;
}

.cn-fan-deck :deep(.flashcards__card-wrapper) {
  contain: none;
}

.cn-fan-deck :deep(.flashcards__card-wrapper--animating) {
  z-index: 90 !important;
  pointer-events: none;
}

.cn-flip {
  width: 100%;
}

.cn-flip-wrap {
  position: relative;
  width: 100%;
  height: 100%;
}

.cn-flip-wrap--deleting {
  animation: cn-card-delete 0.32s var(--kk-ease-out) forwards;
  pointer-events: none;
}

@keyframes cn-card-delete {
  from {
    opacity: 1;
    transform: scale(1) translateY(0);
  }
  to {
    opacity: 0;
    transform: scale(0.88) translateY(18px);
  }
}

.cn-prog-indicator {
  position: absolute;
  left: 50%;
  top: 50%;
  transform: translate(-50%, -50%);
  z-index: 12;
  pointer-events: none;
  line-height: 0;
  animation: cn-prog-in 0.14s ease-out;
}

.cn-prog-indicator--yes {
  color: var(--kk-color-primary);
}

.cn-prog-indicator--no {
  color: var(--kk-color-danger);
}

@keyframes cn-prog-in {
  from {
    opacity: 0;
    transform: translate(-50%, -50%) scale(0.86);
  }
  to {
    opacity: 1;
    transform: translate(-50%, -50%) scale(1);
  }
}

.cn-fan-deck :deep(.flip-card),
.cn-fan-deck :deep(.flip-card__inner) {
  width: 100%;
  height: 17.25rem;
}

.cn-fan-deck :deep(.flip-card__front),
.cn-fan-deck :deep(.flip-card__back) {
  height: 100%;
}

.cn-card {
  box-sizing: border-box;
  width: 100%;
  height: 100%;
  padding: 0.65rem 0.8rem 0.7rem;
  border-radius: var(--kk-radius-md);
  border: 1px solid var(--kk-color-border);
  background: var(--kk-color-surface-solid);
  box-shadow: var(--kk-shadow-card);
  cursor: pointer;
  user-select: none;
  display: flex;
  flex-direction: column;
}

.cn-card--back {
  border-color: color-mix(in srgb, var(--kk-color-accent) 36%, var(--kk-color-border));
  box-shadow:
      var(--kk-shadow-card),
      inset 0 0 0 1px color-mix(in srgb, var(--kk-color-accent) 12%, transparent);
}

.cn-card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.35rem;
  margin-bottom: 0.35rem;
  flex-shrink: 0;
}

.cn-badge {
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

.cn-badge--back {
  color: var(--kk-color-accent-text);
  background: var(--kk-color-accent-bg);
  border-color: color-mix(in srgb, var(--kk-color-accent) 28%, transparent);
}

.cn-card-index {
  font-family: var(--kk-font-mono);
  font-size: 0.68rem;
  font-variant-numeric: tabular-nums;
  color: var(--kk-color-text-subtle);
}

.cn-pane {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.cn-pane--back {
  gap: 0.4rem;
}

.cn-evidence-btn {
  flex-shrink: 0;
  align-self: flex-start;
  margin-top: 0.15rem;
  padding: 0.15rem 0.55rem;
  border-radius: var(--kk-radius-pill);
  border: 1px solid color-mix(in srgb, var(--kk-color-primary) 18%, var(--kk-color-border));
  background: color-mix(in srgb, var(--kk-color-primary) 6%, white);
  font-family: var(--kk-font-mono);
  font-size: 0.7rem;
  font-weight: 700;
  line-height: 1.3;
  color: var(--kk-color-primary);
  cursor: pointer;
  transition: background 0.15s ease, border-color 0.15s ease;
}

.cn-evidence-btn:hover {
  background: color-mix(in srgb, var(--kk-color-primary) 12%, white);
  border-color: color-mix(in srgb, var(--kk-color-primary) 28%, transparent);
}

.pane-tag {
  font-size: 0.6rem;
  font-weight: 700;
  letter-spacing: 0.04em;
  text-transform: uppercase;
  color: var(--kk-color-text-subtle);
  line-height: 1.2;
}

.cn-orig-block {
  flex: 0 1 auto;
  max-height: 38%;
  min-height: 0;
  display: flex;
  flex-direction: column;
  gap: 0.15rem;
  overflow: hidden;
}

.pane-orig {
  margin: 0;
  font-size: 0.8rem;
  line-height: 1.4;
  color: var(--kk-color-text-muted);
  font-weight: 400;
  word-break: break-word;
  overflow: hidden;
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 3;
}

.cn-suggest-block {
  flex: 1 1 auto;
  min-height: 0;
  display: flex;
  flex-direction: column;
  gap: 0.15rem;
  padding-top: 0.35rem;
  border-top: 1px dashed color-mix(in srgb, var(--kk-color-accent) 24%, transparent);
  overflow: hidden;
}

.cn-suggest-block--solo {
  flex: 1;
  justify-content: center;
  padding-top: 0;
  border-top: none;
  text-align: center;
}

.pane-improved {
  margin: 0;
  flex: 1;
  min-height: 0;
  font-family: var(--kk-font-mono);
  font-size: 0.84rem;
  font-weight: 600;
  line-height: 1.4;
  color: var(--kk-color-primary);
  word-break: break-word;
  overflow: hidden;
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 6;
}

.pane-improved--center {
  flex: 0 1 auto;
  font-size: 1.05rem;
  line-height: 1.45;
  -webkit-line-clamp: 5;
}

.pane-improved--term {
  font-family: var(--kk-font-display);
  font-size: 1.35rem;
  font-weight: 700;
  line-height: 1.35;
  -webkit-line-clamp: 4;
}

.cn-orig-mini {
  margin: 0.35rem 0 0;
  padding-top: 0.35rem;
  border-top: 1px dashed color-mix(in srgb, var(--kk-color-accent) 24%, transparent);
  font-size: 0.7rem;
  line-height: 1.35;
  color: var(--kk-color-text-muted);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.cn-empty-hint {
  margin: 0;
  font-size: 0.78rem;
  color: var(--kk-color-text-muted);
}

.cn-done {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 0.3rem;
  width: 100%;
  min-height: 17.25rem;
  padding: 1.25rem 1rem;
  border-radius: var(--kk-radius-md);
  border: 1px dashed color-mix(in srgb, var(--kk-color-primary) 22%, transparent);
  background: var(--kk-glass-inner-bg-muted);
  text-align: center;
  box-sizing: border-box;
}

.cn-fan--aside .cn-done {
  min-height: 14.5rem;
}

.cn-done-title {
  margin: 0;
  font-family: var(--kk-font-display);
  font-size: 1.15rem;
  font-weight: 700;
  color: var(--kk-color-primary);
}

.cn-done-desc {
  margin: 0;
  font-size: 0.82rem;
  color: var(--kk-color-text-muted);
}

.cn-done-btn {
  margin-top: 0.55rem;
  padding: 0.4rem 1rem;
  border-radius: var(--kk-radius-pill);
  border: 1px solid color-mix(in srgb, var(--kk-color-primary) 18%, transparent);
  background: var(--kk-glass-inner-bg);
  color: var(--kk-color-primary);
  font-size: 0.82rem;
  font-weight: 600;
  cursor: pointer;
  transition:
      transform 0.18s ease,
      box-shadow 0.18s ease;
}

.cn-done-btn:hover {
  transform: translateY(-1px);
  box-shadow: var(--kk-shadow-card);
}

.cn-actions {
  position: relative;
  z-index: 2;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-wrap: wrap;
  gap: 0.75rem;
  margin-top: 1.1rem;
  padding-top: 0.15rem;
}

.cn-actions--done {
  gap: 0.65rem;
}

.cn-action-btn {
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 2.7rem;
  height: 2.7rem;
  border-radius: 50%;
  border: 1px solid color-mix(in srgb, var(--kk-color-primary) 14%, transparent);
  background: #ffffff;
  color: var(--kk-color-primary);
  cursor: pointer;
  box-shadow: 0 4px 12px rgba(36, 39, 64, 0.08);
  transition:
      transform 0.18s ease,
      box-shadow 0.18s ease,
      background 0.18s ease,
      opacity 0.18s ease,
      border-color 0.18s ease;
}

.cn-action-btn::after {
  content: attr(data-tip);
  position: absolute;
  left: 50%;
  bottom: calc(100% + 0.45rem);
  transform: translateX(-50%) translateY(4px);
  padding: 0.28rem 0.55rem;
  border-radius: 0.4rem;
  background: color-mix(in srgb, var(--kk-color-primary) 92%, black);
  color: #fff;
  font-size: 0.7rem;
  font-weight: 600;
  white-space: nowrap;
  opacity: 0;
  pointer-events: none;
  transition:
      opacity 0.16s ease,
      transform 0.16s ease;
  z-index: 8;
}

.cn-action-btn:hover:not(:disabled)::after,
.cn-action-btn:focus-visible:not(:disabled)::after {
  opacity: 1;
  transform: translateX(-50%) translateY(0);
}

.cn-action-btn:hover:not(:disabled) {
  transform: translateY(-2px);
  box-shadow: 0 8px 18px rgba(36, 39, 64, 0.14);
}

.cn-action-btn:disabled {
  opacity: 0.35;
  cursor: not-allowed;
  box-shadow: none;
}

.cn-action-btn--no {
  width: 2.95rem;
  height: 2.95rem;
  color: #b42318;
  border-color: color-mix(in srgb, #b42318 22%, transparent);
  background: color-mix(in srgb, #b42318 6%, white);
}

.cn-action-btn--no:hover:not(:disabled) {
  background: color-mix(in srgb, #b42318 12%, white);
  border-color: color-mix(in srgb, #b42318 36%, transparent);
}

.cn-action-btn--yes {
  width: 3.05rem;
  height: 3.05rem;
  color: #fff;
  border-color: transparent;
  background: linear-gradient(
      145deg,
      #2f6b4f,
      #3d8b66
  );
  box-shadow: 0 8px 18px color-mix(in srgb, #2f6b4f 28%, transparent);
}

.cn-action-btn--yes:hover:not(:disabled) {
  box-shadow: 0 10px 22px color-mix(in srgb, #2f6b4f 36%, transparent);
}

.cn-action-btn--delete {
  width: 2.15rem;
  height: 2.15rem;
  margin-left: 0.1rem;
  color: var(--kk-color-text-subtle);
  border-color: color-mix(in srgb, var(--kk-color-primary) 10%, transparent);
  background: color-mix(in srgb, var(--kk-color-primary) 3%, white);
  box-shadow: 0 2px 8px rgba(36, 39, 64, 0.05);
  font-size: 0.85rem;
}

.cn-action-btn--delete:hover:not(:disabled) {
  color: var(--kk-color-danger);
  border-color: color-mix(in srgb, var(--kk-color-danger) 28%, transparent);
  background: color-mix(in srgb, var(--kk-color-danger) 8%, white);
  box-shadow: 0 4px 12px color-mix(in srgb, var(--kk-color-danger) 14%, transparent);
}

.cn-action-btn--spin .el-icon {
  animation: cn-reset-spin 0.7s linear infinite;
}

@keyframes cn-reset-spin {
  to {
    transform: rotate(360deg);
  }
}

@media (max-width: 520px) {
  .cn-fan-stage {
    width: 100%;
  }
}

@media (prefers-reduced-motion: reduce) {
  .cn-action-btn--spin .el-icon {
    animation: none;
  }

  .cn-prog-indicator {
    animation: none;
  }

  .cn-flip-wrap--deleting {
    animation: none;
    opacity: 0;
  }

  .cn-action-btn,
  .cn-done-btn {
    transition: none;
  }
}
</style>
