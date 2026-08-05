<script setup lang="ts">
import {MagicStick, Promotion,} from '@element-plus/icons-vue'
import {storeToRefs} from 'pinia'
import {computed, onMounted, onUnmounted, ref, watch} from 'vue'
import {useRouter} from 'vue-router'
import {FlashCards, FlipCard} from 'vue3-flashcards'

import PillarMark from '@/components/home/PillarMark.vue'
import {useAuthStore} from '@/stores/auth'
import type {DailyPracticeStat} from '@/types/home'
import {signalPrerenderReady} from '@/utils/prerender'

const router = useRouter()
const auth = useAuthStore()
const {isAuthenticated} = storeToRefs(auth)
const revealed = ref(false)

const DEMO_CARDS = [
  {
    id: 'demo-1',
    zh: '说话说到一半，突然忘了接下来要说什么',
    en: 'lose my train of thought',
    example: 'I lost my train of thought mid-sentence.',
  },
  {
    id: 'demo-2',
    zh: '先把这件事放一放，以后再说',
    en: 'put it on the back burner',
    example: "Let's put this on the back burner for now.",
  },
  {
    id: 'demo-3',
    zh: '我完全明白你的意思',
    en: 'I see where you are coming from',
    example: 'I see where you are coming from, but I disagree.',
  },
  {
    id: 'demo-4',
    zh: '这个点子听起来很靠谱',
    en: 'that sounds about right',
    example: 'Yeah, that sounds about right to me.',
  },
  {
    id: 'demo-5',
    zh: '别担心，我会处理的',
    en: "I've got it covered",
    example: "Don't worry — I've got it covered.",
  },
] as const

type DemoCard = (typeof DEMO_CARDS)[number]

interface DeckExpose {
  swipeLeft: () => void
  swipeRight: () => void
  reset: (options?: {animate?: boolean; delay?: number}) => void | Promise<void>
}

const deckRootRef = ref<HTMLElement | null>(null)
const deckRef = ref<DeckExpose | null>(null)
const flashcardCount = DEMO_CARDS.length
const demoReviewed = ref(0)
const demoResetting = ref(false)

function demoCardOrdinal(card: DemoCard): number {
  const idx = DEMO_CARDS.findIndex((item) => item.id === card.id)
  return idx >= 0 ? idx + 1 : 1
}

function asDemoCard(item: Record<string, unknown>): DemoCard {
  return item as DemoCard
}

const SCORE_OVERALL = 86
const SCORE_DIMS = [
  {label: '表达自然', value: 88, width: 81, accent: true},
  {label: '语法准确', value: 84, width: 74, accent: false},
  {label: '文本流畅', value: 90, width: 85, accent: false},
  {label: '词汇表达', value: 82, width: 70, accent: false},
] as const

const displayOverall = ref(0)
const dimStates = ref(
    SCORE_DIMS.map((dim) => ({
      ...dim,
      display: 0,
      fill: 0,
      shown: false,
    })),
)

interface HeatCell extends DailyPracticeStat {
  level: number
}

/** Fixed deterministic counts for the home pillar mock (30 days). */
const MONTH_HEATMAP_DAYS = 30
const MOCK_MONTH_HEATMAP_COUNTS = [
  0, 0, 1, 0, 2, 3, 2, 0, 0, 0,
  0, 1, 0, 0, 4, 3, 2, 4, 3, 1,
  0, 0, 0, 2, 3, 1, 0, 0, 1, 2,
] as const

const monthHeatmap = ref<HeatCell[]>(buildMockMonthHeatmap())

const monthHeatmapTotal = computed(() =>
    monthHeatmap.value.reduce((sum, day) => sum + day.count, 0),
)

function padDatePart(value: number): string {
  return String(value).padStart(2, '0')
}

function buildHeatmapDays(counts: readonly number[]): HeatCell[] {
  const today = new Date()
  const cells: HeatCell[] = []
  for (let i = 0; i < counts.length; i += 1) {
    const offset = counts.length - 1 - i
    const day = new Date(today)
    day.setDate(today.getDate() - offset)
    cells.push({
      date: `${day.getFullYear()}-${padDatePart(day.getMonth() + 1)}-${padDatePart(day.getDate())}`,
      label: `${day.getMonth() + 1}/${day.getDate()}`,
      count: counts[i] ?? 0,
      level: 0,
    })
  }
  return toHeatmapCells(cells)
}

function toHeatmapCells(items: DailyPracticeStat[]): HeatCell[] {
  const max = Math.max(...items.map((item) => item.count), 1)
  return items.map((item) => {
    const ratio = item.count / max
    const level = item.count === 0 ? 0 : Math.min(4, Math.max(1, Math.ceil(ratio * 4)))
    return {...item, level}
  })
}

function buildMockMonthHeatmap(): HeatCell[] {
  return buildHeatmapDays(MOCK_MONTH_HEATMAP_COUNTS.slice(0, MONTH_HEATMAP_DAYS))
}

const quotes = [
  {text: '面试前连续复盘两周，开口明显稳了不少。', cite: '远哥'},
  {text: '终于知道自己哪里不地道，不是再死背模板。', cite: '小桐同学'},
  {text: '跨国会议前翻一遍分析记录，比临时抱佛脚管用。', cite: '浩浩'},
] as const
const visibleQuoteCount = ref(0)

const visibleQuotes = computed(() => quotes.slice(0, visibleQuoteCount.value))


const prefersReducedMotion = () =>
    typeof window !== 'undefined' &&
    window.matchMedia('(prefers-reduced-motion: reduce)').matches

let pauseAutoFlipUntil = 0
const timers: Array<ReturnType<typeof setTimeout>> = []
let rafIds: number[] = []
let deckLoopActive = false

function schedule(fn: () => void, ms: number) {
  const id = setTimeout(fn, ms)
  timers.push(id)
  return id
}

function clearAllTimers() {
  timers.forEach(clearTimeout)
  timers.length = 0
  rafIds.forEach(cancelAnimationFrame)
  rafIds = []
  deckLoopActive = false
}

function animateCount(
    from: number,
    to: number,
    duration: number,
    onUpdate: (value: number) => void,
) {
  if (prefersReducedMotion()) {
    onUpdate(to)
    return
  }
  const start = performance.now()
  const tick = (now: number) => {
    const t = Math.min(1, (now - start) / duration)
    const eased = 1 - (1 - t) ** 3
    onUpdate(Math.round(from + (to - from) * eased))
    if (t < 1) {
      const id = requestAnimationFrame(tick)
      rafIds.push(id)
    }
  }
  const id = requestAnimationFrame(tick)
  rafIds.push(id)
}

function pauseFlashcardAuto() {
  pauseAutoFlipUntil = Date.now() + 5000
}

function flipActiveDemoCard() {
  const card = deckRootRef.value?.querySelector(
      '.flashcards__card--active .flip-card, [data-active-card="true"] .flip-card',
  ) as HTMLElement | null
  card?.dispatchEvent(new PointerEvent('pointerup', {bubbles: true}))
}

function swipeDemoRight() {
  deckRef.value?.swipeRight()
}

function onDemoSwipe() {
  if (demoResetting.value) {
    return
  }
  pauseFlashcardAuto()
  demoReviewed.value = Math.min(flashcardCount, demoReviewed.value + 1)
  if (demoReviewed.value >= flashcardCount) {
    schedule(() => {
      void resetDemoDeck()
    }, 420)
  }
}

async function resetDemoDeck() {
  if (demoResetting.value || !deckRef.value?.reset) {
    return
  }
  demoResetting.value = true
  try {
    await deckRef.value.reset({animate: true, delay: 70})
    demoReviewed.value = 0
  } finally {
    demoResetting.value = false
  }
}

function runFlashcardDeckStep() {
  if (!deckLoopActive || demoResetting.value) {
    if (deckLoopActive) {
      schedule(runFlashcardDeckStep, 400)
    }
    return
  }
  if (Date.now() < pauseAutoFlipUntil) {
    schedule(runFlashcardDeckStep, 400)
    return
  }

  const onLastCard = demoReviewed.value >= flashcardCount - 1

  flipActiveDemoCard()
  schedule(() => {
    if (!deckLoopActive) {
      return
    }
    if (Date.now() < pauseAutoFlipUntil) {
      schedule(runFlashcardDeckStep, 400)
      return
    }
    if (onLastCard) {
      void resetDemoDeck().then(() => {
        if (deckLoopActive) {
          schedule(runFlashcardDeckStep, 2800)
        }
      })
      return
    }
    swipeDemoRight()
    schedule(runFlashcardDeckStep, 2800)
  }, 2800)
}

function startFlashcardAuto() {
  demoReviewed.value = 0
  demoResetting.value = false
  pauseAutoFlipUntil = 0
  if (prefersReducedMotion()) {
    return
  }
  deckLoopActive = true
  schedule(runFlashcardDeckStep, 2800)
}

function startScoreAnimation() {
  displayOverall.value = 0
  dimStates.value = SCORE_DIMS.map((dim) => ({
    ...dim,
    display: 0,
    fill: 0,
    shown: false,
  }))

  if (prefersReducedMotion()) {
    displayOverall.value = SCORE_OVERALL
    dimStates.value = SCORE_DIMS.map((dim) => ({
      ...dim,
      display: dim.value,
      fill: dim.width,
      shown: true,
    }))
    return
  }

  animateCount(0, SCORE_OVERALL, 990, (v) => {
    displayOverall.value = v
  })

  SCORE_DIMS.forEach((dim, index) => {
    schedule(() => {
      dimStates.value[index].shown = true
      requestAnimationFrame(() => {
        dimStates.value[index].fill = dim.width
      })
      animateCount(0, dim.value, 715, (v) => {
        dimStates.value[index].display = v
      })
    }, 308 + index * 418)
  })
}

function startQuoteReveal() {
  visibleQuoteCount.value = 0
  if (prefersReducedMotion()) {
    visibleQuoteCount.value = quotes.length
    return
  }
  quotes.forEach((_, index) => {
    schedule(() => {
      visibleQuoteCount.value = index + 1
    }, 440 + index * 660)
  })
}

function startPillarAnimations() {
  clearAllTimers()
  startScoreAnimation()
  startFlashcardAuto()
  startQuoteReveal()
}

function goToPath(path: string, options?: {requireAuth?: boolean}) {
  if (options?.requireAuth && !isAuthenticated.value) {
    void router.push({path: '/login', query: {redirect: path}})
    return
  }
  void router.push(path)
}

function goToAnalyze() {
  goToPath('/conversation/analyze')
}

function goToReviewCenter() {
  goToPath('/review', {requireAuth: true})
}

function goToGrowthCards() {
  goToPath('/review/cards', {requireAuth: true})
}

watch(revealed, (on) => {
  if (!on) {
    return
  }
  startPillarAnimations()
})

onMounted(() => {
  requestAnimationFrame(() => {
    revealed.value = true
    signalPrerenderReady()
  })
})

onUnmounted(() => {
  clearAllTimers()
})
</script>

<template>
  <div class="landing" :class="{ 'landing--revealed': revealed }">
    <section class="hero reveal" style="--reveal-delay: 0ms">
      <div class="hero-glow" aria-hidden="true"/>
      <div class="hero-grid">
        <div class="hero-copy">
          <span class="hero-badge">
            <el-icon><MagicStick/></el-icon>
            AI 纠正助手
          </span>
          <h1 class="hero-title">
            从「会说」到「精通」的
            <span class="hero-title-accent">语境进化之路</span>
          </h1>
          <p class="hero-desc">
            让 AI 对话练习真正变得有效。
          </p>
          <div class="hero-actions">
            <button type="button" class="btn-primary" @click="router.push('/conversation/analyze')">
              <el-icon>
                <Promotion/>
              </el-icon>
              开始分析
            </button>
          </div>
        </div>

        <div class="hero-demo reveal" style="--reveal-delay: 120ms">
          <div class="hero-demo-stage">
            <div class="demo-shadow" aria-hidden="true"/>
            <div class="demo-float-layer">
              <div class="demo-window kk-glass">
                <div class="demo-chrome">
              <span class="demo-dots">
                <i/><i/><i/>
              </span>
                  <span class="demo-label">Doubao · Qwen · GPT</span>
                </div>
                <div class="demo-pane demo-pane--before">
                  <span class="demo-tag">原句</span>
                  <p class="demo-quote">
                    "When I give presentation, I always forget what to say next and feel very embarrassing."
                  </p>
                </div>
                <div class="demo-pane demo-pane--after">
                  <span class="demo-tag demo-tag--ai">AI 建议</span>
                  <p class="demo-improved">
                    When I give presentations, I always lose my train of thought and feel embarrassed.
                  </p>
                  <ul class="correction-list">
                    <li class="correction correction--fatal">可数名词：presentation → presentations</li>
                    <li class="correction correction--warn">词性搭配：embarrassing → embarrassed</li>
                    <li class="correction correction--soft">表达地道：forget what to say → lose my train of thought</li>
                  </ul>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>

    <section class="pillars reveal" style="--reveal-delay: 200ms">
      <div class="pillars-panel">
        <article
            class="pillar pillar--link"
            role="link"
            tabindex="0"
            @click="goToAnalyze"
            @keydown.enter.prevent="goToAnalyze"
            @keydown.space.prevent="goToAnalyze"
        >
          <header class="pillar-head">
            <PillarMark kind="analyze"/>
            <h3 class="pillar-heading">Khan AI 分析助手</h3>
          </header>
          <p class="pillar-body">
            Khan Kiddo 通过三段式分析流水线，逐句解析对话字幕，定位语法与表达中的可优化点，并给出可直接复用的改写建议。同时结合 Khan 规则引擎，对整段对话进行多维评分。
          </p>
          <div class="pillar-foot">
            <div class="score-showcase" aria-label="表现评分示意">
              <div class="score-showcase-head">
                <div class="score-showcase-overall">
                  <span class="score-showcase-num">{{ displayOverall }}</span>
                </div>
                <div class="score-showcase-meta">
                  <span class="score-showcase-lbl">综合自然度</span>
                  <span class="score-showcase-hint">四维诊断 · 分数可追溯</span>
                </div>
              </div>
              <ul class="score-showcase-dims">
                <li
                    v-for="(dim, index) in dimStates"
                    :key="dim.label"
                    class="score-dim"
                    :class="{
                  'score-dim--accent': dim.accent,
                  'score-dim--shown': dim.shown,
                }"
                    :style="{ '--dim-delay': `${index * 80}ms` }"
                >
                  <span class="score-dim-label">{{ dim.label }}</span>
                  <span class="score-dim-track" aria-hidden="true">
                <span class="score-dim-fill" :style="{ width: `${dim.fill}%` }"/>
              </span>
                  <span class="score-dim-value">{{ dim.display }}</span>
                </li>
              </ul>
            </div>
          </div>
        </article>

        <article
            class="pillar pillar--link"
            role="link"
            tabindex="0"
            @click="goToReviewCenter"
            @keydown.enter.prevent="goToReviewCenter"
            @keydown.space.prevent="goToReviewCenter"
        >
          <header class="pillar-head">
            <PillarMark kind="review"/>
            <h3 class="pillar-heading pillar-heading--teal">复盘中心</h3>
          </header>
          <p class="pillar-body">
            在复盘中心看趋势，还能追问过往分析：常见错误、例句与改进方向。把零散纠正收成重点，下次开口知道先练什么、怎么改，并形成持续提升路径与学习节奏。
          </p>
          <div class="pillar-foot">
            <div class="month-heatmap" aria-label="近 30 天优化热力图">
              <div class="month-heatmap-meta">
                <div class="month-heatmap-summary">
                  <strong>{{ monthHeatmapTotal }}</strong>
                  <span>近 30 天优化点</span>
                </div>
                <div class="month-heatmap-scale" aria-hidden="true">
                  <span>少</span>
                  <span class="github-heat-cell"/>
                  <span class="github-heat-cell github-heat-cell--1"/>
                  <span class="github-heat-cell github-heat-cell--2"/>
                  <span class="github-heat-cell github-heat-cell--3"/>
                  <span class="github-heat-cell github-heat-cell--4"/>
                  <span>多</span>
                </div>
              </div>
              <div
                  class="month-heatmap-grid"
                  role="img"
                  :aria-label="`近 30 天共 ${monthHeatmapTotal} 个优化点`"
              >
                <span
                    v-for="day in monthHeatmap"
                    :key="day.date"
                    class="github-heat-cell"
                    :class="day.level ? `github-heat-cell--${day.level}` : undefined"
                    :title="`${day.label} · ${day.count}`"
                />
              </div>
            </div>
          </div>
        </article>

        <article
            class="pillar pillar--link"
            role="link"
            tabindex="0"
            @click="goToGrowthCards"
            @keydown.enter.prevent="goToGrowthCards"
            @keydown.space.prevent="goToGrowthCards"
        >
          <header class="pillar-head">
            <PillarMark kind="cards"/>
            <h3 class="pillar-heading">自动生成成长卡片</h3>
          </header>
          <p class="pillar-body">
            分析完成后，自动将表达缺口整理成复习卡片，也支持自定义制卡，让每一次练习都能持续积累、随时复习，提升学习效率与记忆效果，并形成长期记忆闭环体系。
          </p>
          <div class="pillar-foot">
            <div
                ref="deckRootRef"
                class="flashcard-deck"
                aria-label="知识卡片演示"
                @pointerdown="pauseFlashcardAuto"
            >
              <FlashCards
                  ref="deckRef"
                  class="flashcard-cards"
                  :items="[...DEMO_CARDS]"
                  item-key="id"
                  :loop="false"
                  :stack="2"
                  stack-direction="top"
                  :stack-offset="14"
                  :stack-scale="0.02"
                  swipe-direction="horizontal"
                  :wait-animation-end="true"
                  :a11y="{ enabled: true, keyboard: false, manageFocus: false }"
                  @swipe-left="onDemoSwipe"
                  @swipe-right="onDemoSwipe"
              >
                <template #default="{ item: rawItem }">
                  <template v-for="item in [asDemoCard(rawItem)]" :key="item.id">
                    <FlipCard class="flashcard-flip" flip-axis="y">
                      <template #front>
                        <article class="flashcard-face flashcard-face--front">
                          <header class="flashcard-head">
                            <span class="flashcard-badge">正面</span>
                            <span class="flashcard-index">{{ demoCardOrdinal(item) }}/{{ flashcardCount }}</span>
                          </header>
                          <section class="flashcard-pane">
                            <div class="flashcard-block flashcard-block--solo">
                              <span class="flashcard-tag">目标词</span>
                              <p class="flashcard-main">{{ item.zh }}</p>
                            </div>
                          </section>
                        </article>
                      </template>
                      <template #back>
                        <article class="flashcard-face flashcard-face--back">
                          <header class="flashcard-head">
                            <span class="flashcard-badge flashcard-badge--back">反面</span>
                            <span class="flashcard-index">{{ demoCardOrdinal(item) }}/{{ flashcardCount }}</span>
                          </header>
                          <section class="flashcard-pane">
                            <div class="flashcard-block flashcard-block--solo">
                              <span class="flashcard-tag">英文</span>
                              <p class="flashcard-main flashcard-main--en">{{ item.en }}</p>
                            </div>
                            <p class="flashcard-mini">例句：{{ item.example }}</p>
                          </section>
                        </article>
                      </template>
                    </FlipCard>
                  </template>
                </template>
                <template #empty>
                  <div class="flashcard-empty" aria-hidden="true"/>
                </template>
              </FlashCards>
            </div>
          </div>
        </article>

        <article class="pillar pillar--learners">
          <header class="pillar-head">
            <PillarMark kind="learners"/>
            <h3 class="pillar-heading pillar-heading--gold">致力于深度进阶的学习</h3>
          </header>
          <p class="pillar-body">
            适合已有一定英语基础、希望表达更准确、更自然的学习者。无论是备考、面试还是跨国协作，都能通过持续复盘，把问题转化为可见的表达优势，并提升长期表达自信力。
          </p>
          <div class="pillar-foot">
            <TransitionGroup name="quote" tag="div" class="quote-list">
              <p
                  v-for="quote in visibleQuotes"
                  :key="quote.cite"
                  class="quote-line"
              >
                「{{ quote.text }}」
                <span class="quote-cite">—— {{ quote.cite }}</span>
              </p>
            </TransitionGroup>
          </div>
        </article>
      </div>
    </section>

  </div>
</template>

<style scoped>
.landing {
  font-family: var(--kk-font-body);
  color: var(--kk-color-text);
}

.reveal {
  opacity: 0;
  transform: translateY(18px);
  transition: opacity var(--kk-duration-slow) var(--kk-ease-out),
  transform var(--kk-duration-slow) var(--kk-ease-out);
  transition-delay: var(--reveal-delay, 0ms);
}

.landing--revealed .reveal {
  opacity: 1;
  transform: translateY(0);
}

/* Hero — 单层容器，不再套第二层圆角矩形 */
.hero {
  position: relative;
  padding: 0.5rem 0 1.5rem;
  margin-bottom: 0.5rem;
}

.hero-glow {
  position: absolute;
  top: -20%;
  right: 0;
  width: 48%;
  height: 90%;
  background: radial-gradient(circle, rgba(184, 148, 31, 0.1) 0%, transparent 70%);
  pointer-events: none;
}

.hero-grid {
  position: relative;
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: clamp(1.5rem, 3vw, 2.5rem);
  align-items: center;
}

.hero-copy {
  display: flex;
  flex-direction: column;
  gap: 1.1rem;
}

.hero-badge {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  width: fit-content;
  padding: 0.35rem 0.85rem;
  border-radius: 999px;
  background: var(--kk-color-accent-bg);
  color: var(--kk-color-accent-text);
  font-size: 0.78rem;
  font-weight: 700;
  letter-spacing: 0.02em;
  border: 1px solid rgba(184, 148, 31, 0.35);
}

.hero-title {
  margin: 0;
  font-family: var(--kk-font-display);
  font-size: clamp(2.1rem, 4.2vw, 3.75rem);
  font-weight: 800;
  line-height: 1.06;
  letter-spacing: -0.03em;
  color: var(--kk-color-primary);
}

.hero-title-accent {
  display: block;
  color: var(--kk-color-accent);
  font-style: italic;
}

.hero-desc {
  margin: 0;
  max-width: 34rem;
  font-size: 1.05rem;
  line-height: 1.7;
  color: var(--kk-color-text-muted);
}

.hero-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.75rem 1.25rem;
  margin-top: 0.25rem;
}

.btn-primary {
  display: inline-flex;
  align-items: center;
  gap: 0.45rem;
  padding: 0.85rem 1.75rem;
  border: none;
  border-radius: 999px;
  background: linear-gradient(135deg, var(--kk-color-primary) 0%, var(--kk-color-primary-soft) 100%);
  color: #fff;
  font-family: inherit;
  font-size: 0.95rem;
  font-weight: 700;
  cursor: pointer;
  box-shadow: var(--kk-shadow-btn);
  transition: transform var(--kk-duration-normal) ease, box-shadow var(--kk-duration-normal) ease;
}

.btn-primary:hover {
  transform: translateY(-2px);
  box-shadow: var(--kk-shadow-btn-hover);
}

/* Demo window — Hero 3D 透视展示 */
.hero-demo {
  display: flex;
  justify-content: center;
  align-items: center;
  padding: 0.5rem 0 1.5rem;
  perspective: 1400px;
}

.hero-demo-stage {
  position: relative;
  width: 100%;
  max-width: 31.2rem;
  transform-style: preserve-3d;
  transform: rotateY(-14deg) rotateX(7deg);
  transition: transform 1s var(--kk-ease-out);
  will-change: transform;
}

.hero-demo-stage:hover {
  transform: rotateY(-6deg) rotateX(3deg) translateZ(16px);
}

.demo-float-layer {
  transform-style: preserve-3d;
  animation: demo-float-y 7s ease-in-out infinite;
}

.hero-demo-stage:hover .demo-float-layer {
  animation-play-state: paused;
}

@keyframes demo-float-y {
  0%,
  100% {
    transform: translateY(0);
  }

  50% {
    transform: translateY(-10px);
  }
}

.demo-shadow {
  position: absolute;
  inset: 6% -4% -10% 8%;
  border-radius: var(--kk-radius-lg);
  background: linear-gradient(
      145deg,
      rgba(11, 26, 125, 0.28) 0%,
      rgba(11, 26, 125, 0.12) 55%,
      rgba(184, 148, 31, 0.14) 100%
  );
  filter: blur(22px);
  transform: translateZ(-36px) scale(0.96);
  opacity: 0.85;
  pointer-events: none;
}

.demo-window {
  position: relative;
  z-index: 1;
  border-radius: var(--kk-radius-lg);
  padding: 1.1rem;
  transform: translateZ(24px);
  backface-visibility: hidden;
  box-shadow: var(--kk-glass-shadow),
  inset 0 1px 0 var(--kk-glass-highlight),
  0 28px 56px rgba(11, 26, 125, 0.18);
  transition: box-shadow 1s var(--kk-ease-out);
}

.hero-demo-stage:hover .demo-window {
  box-shadow: var(--kk-glass-shadow),
  inset 0 1px 0 var(--kk-glass-highlight),
  0 36px 64px rgba(11, 26, 125, 0.22);
}

.demo-window::after {
  content: '';
  position: absolute;
  inset: 0;
  border-radius: inherit;
  background: linear-gradient(
      125deg,
      rgba(255, 255, 255, 0.42) 0%,
      transparent 38%,
      transparent 62%,
      rgba(11, 26, 125, 0.04) 100%
  );
  pointer-events: none;
}

@media (prefers-reduced-motion: reduce) {
  .demo-float-layer {
    animation: none;
  }

  .hero-demo-stage:hover {
    transform: rotateY(-14deg) rotateX(7deg);
  }
}

.demo-chrome {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 0.85rem;
}

.demo-dots {
  display: flex;
  gap: 0.35rem;
}

.demo-dots i {
  width: 9px;
  height: 9px;
  border-radius: 50%;
  background: #ddd;
  display: block;
}

.demo-dots i:nth-child(1) {
  background: #e8a0a0;
}

.demo-dots i:nth-child(2) {
  background: #e8d44d;
}

.demo-label {
  font-size: 0.68rem;
  font-weight: 700;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: #8b90a0;
}

.demo-pane {
  border-radius: var(--kk-radius-md);
  padding: 1rem 1.05rem;
}

.demo-pane--before {
  background: var(--kk-glass-inner-bg);
  border-left: 3px solid var(--kk-color-accent);
}

.demo-pane--after {
  margin-top: 0.85rem;
  background: var(--kk-glass-inner-bg-muted);
  border: 1px solid var(--kk-glass-inner-border);
}

.demo-tag {
  display: block;
  font-size: 0.68rem;
  font-weight: 700;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  color: rgba(11, 26, 125, 0.55);
  margin-bottom: 0.4rem;
}

.demo-tag--ai {
  color: var(--kk-color-primary-soft);
}

.demo-quote {
  margin: 0;
  font-family: var(--kk-font-mono);
  font-size: 0.88rem;
  line-height: 1.65;
  color: #4a5068;
  font-style: italic;
}

.demo-improved {
  margin: 0 0 0.65rem;
  font-family: var(--kk-font-mono);
  font-size: 0.88rem;
  line-height: 1.65;
  color: #1f4da9;
  font-weight: 500;
}

.correction-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 0.4rem;
}

.correction {
  display: inline-flex;
  width: fit-content;
  padding: 0.22rem 0.65rem;
  border-radius: 999px;
  font-size: 0.72rem;
  font-weight: 600;
}

.correction--fatal {
  background: #ffecec;
  color: #a01818;
  border: 1px solid rgba(160, 24, 24, 0.2);
}

.correction--warn {
  background: #fff8e0;
  color: #7a6200;
  border: 1px solid rgba(122, 98, 0, 0.22);
}

.correction--soft {
  background: #e8f2ff;
  color: #0e5080;
  border: 1px solid rgba(14, 80, 128, 0.18);
}

/* Pillars */
.pillars {
  position: relative;
  margin: 0 0 1.5rem;
}

.pillars-panel {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: clamp(0.9rem, 1.35vw, 1.15rem);
  align-items: stretch;
  padding: clamp(0.8rem, 1.25vw, 1.1rem);
  border-radius: calc(var(--kk-radius-lg) + 8px);
  background:
      linear-gradient(135deg, rgba(255, 255, 255, 0.72), rgba(247, 248, 252, 0.84)),
      radial-gradient(circle at 0% 0%, rgba(11, 26, 125, 0.05), transparent 38%),
      radial-gradient(circle at 100% 100%, rgba(184, 148, 31, 0.07), transparent 34%);
  border: 1px solid rgba(255, 255, 255, 0.72);
  box-shadow:
      0 18px 52px rgba(11, 26, 125, 0.08),
      inset 0 1px 0 rgba(255, 255, 255, 0.7);
}

.pillar {
  display: flex;
  flex-direction: column;
  gap: 0.8rem;
  height: 100%;
  min-height: 0;
  background:
      linear-gradient(180deg, rgba(255, 255, 255, 0.98), rgba(255, 255, 255, 0.94));
  border-radius: calc(var(--kk-radius-lg) - 2px);
  padding: clamp(1rem, 1.35vw, 1.25rem);
  border: 1px solid rgba(11, 26, 125, 0.07);
  box-shadow:
      0 12px 28px rgba(11, 26, 125, 0.06),
      inset 0 1px 0 rgba(255, 255, 255, 0.88);
  overflow: hidden;
  transition:
      transform 0.25s ease,
      box-shadow 0.25s ease,
      border-color 0.25s ease;
}

.pillar:hover {
  transform: translateY(-3px);
  border-color: rgba(11, 26, 125, 0.12);
  box-shadow:
      0 18px 34px rgba(11, 26, 125, 0.1),
      inset 0 1px 0 rgba(255, 255, 255, 0.92);
}

.pillar--link {
  cursor: pointer;
  text-align: left;
}

.pillar--link:focus-visible {
  outline: 2px solid color-mix(in srgb, var(--kk-color-primary) 45%, transparent);
  outline-offset: 2px;
}

/* 演示区不拦截点击，整卡跳转 */
.pillar--link .flashcard-deck {
  pointer-events: none;
}

.pillar-head {
  display: flex;
  align-items: center;
  gap: 0.7rem;
  min-width: 0;
}

.pillar-head :deep(.pillar-mark) {
  flex-shrink: 0;
  margin-bottom: 0;
}

.pillar-heading {
  margin: 0;
  font-family: var(--kk-font-display);
  font-size: clamp(1rem, 1.15vw, 1.14rem);
  font-weight: 700;
  line-height: 1.32;
  letter-spacing: 0;
  color: var(--kk-color-primary);
}

.pillar-heading--gold {
  color: var(--kk-color-accent-text);
}

.pillar-heading--teal {
  color: #1a5c5c;
}

.pillar-body {
  margin: 0;
  min-height: 0;
  font-size: 0.88rem;
  line-height: 1.55;
  color: var(--kk-color-text-muted);
}

/* 底部演示区：固定节奏，避免动态内容改变卡片高度 */
.pillar-foot {
  margin-top: auto;
  flex: 1 1 auto;
  display: flex;
  flex-direction: column;
  justify-content: stretch;
  min-height: 11.5rem;
  padding-top: 0.75rem;
  border-top: 1px solid rgba(11, 26, 125, 0.06);
}

.pillar--link .pillar-foot {
  justify-content: stretch;
  padding-bottom: 0.15rem;
}

.score-showcase {
  margin: 0;
  padding: 0;
  border: 0;
  display: flex;
  flex-direction: column;
  justify-content: center;
  min-height: 100%;
}

.score-showcase-head {
  display: flex;
  align-items: center;
  gap: 0.85rem;
  margin-bottom: 0.8rem;
}

.score-showcase-overall {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 3.75rem;
  height: 3.75rem;
  flex-shrink: 0;
  border-radius: 50%;
  background: linear-gradient(145deg, #101c6e 0%, var(--kk-color-primary) 55%, #223194 100%);
  box-shadow: 0 6px 16px rgba(11, 26, 125, 0.22);
}

.score-showcase-num {
  font-family: var(--kk-font-display);
  font-size: 1.55rem;
  font-weight: 800;
  line-height: 1;
  letter-spacing: -0.03em;
  color: #f4f6ff;
  font-variant-numeric: tabular-nums;
}

.score-showcase-meta {
  display: flex;
  flex-direction: column;
  gap: 0.15rem;
  min-width: 0;
}

.score-showcase-lbl {
  font-family: var(--kk-font-display);
  font-size: 0.95rem;
  font-weight: 700;
  color: var(--kk-color-primary);
}

.score-showcase-hint {
  font-size: 0.72rem;
  font-weight: 600;
  color: var(--kk-color-accent-text);
}

.score-showcase-dims {
  list-style: none;
  margin: 0;
  padding: 0.72rem 0.78rem;
  display: flex;
  flex-direction: column;
  gap: 0.44rem;
  border-radius: var(--kk-radius-md);
  background: linear-gradient(160deg, #f5f6fa 0%, #eceef5 100%);
  border: 1px solid var(--kk-color-border-subtle);
}

.score-dim {
  display: grid;
  grid-template-columns: 3.7rem minmax(3rem, 1fr) 1.65rem;
  align-items: center;
  gap: 0.42rem;
  opacity: 0;
  transform: translateY(6px);
  transition: opacity 0.385s var(--kk-ease-out),
  transform 0.385s var(--kk-ease-out);
}

.score-dim--shown {
  opacity: 1;
  transform: translateY(0);
}

.score-dim-label {
  font-size: 0.72rem;
  font-weight: 600;
  color: var(--kk-color-text-muted);
  white-space: nowrap;
}

.score-dim--accent .score-dim-label {
  color: var(--kk-color-accent-text);
  font-weight: 700;
}

.score-dim-track {
  height: 4.5px;
  border-radius: 999px;
  background: #e0e2ea;
  overflow: hidden;
}

.score-dim-fill {
  display: block;
  height: 100%;
  width: 0;
  border-radius: inherit;
  background: linear-gradient(90deg, var(--kk-color-primary-soft), var(--kk-color-primary));
  transition: width 0.77s var(--kk-ease-out);
}

.score-dim--accent .score-dim-fill {
  background: linear-gradient(
      90deg,
      var(--kk-color-accent),
      color-mix(in srgb, var(--kk-color-accent) 65%, var(--kk-color-primary))
  );
}

.score-dim-value {
  font-family: var(--kk-font-display);
  font-size: 0.85rem;
  font-weight: 700;
  text-align: right;
  color: var(--kk-color-primary);
  font-variant-numeric: tabular-nums;
}

/* 学习者评价：灰色纯文字，逐条出现 */
.quote-list {
  margin-top: 0;
  display: flex;
  flex-direction: column;
  justify-content: flex-start;
  gap: 0.62rem;
  min-height: 100%;
}

.quote-line {
  margin: 0;
  padding: 0.58rem 0;
  border-top: 1px solid rgba(11, 26, 125, 0.06);
  font-size: 0.82rem;
  line-height: 1.62;
  color: var(--kk-color-text-subtle);
}

.quote-line:first-child {
  border-top: 0;
  padding-top: 0;
}

.quote-cite {
  display: inline;
  margin-left: 0.25rem;
  font-size: 0.76rem;
  font-weight: 500;
  color: #9aa0b0;
}

.quote-enter-active {
  transition: opacity 0.55s var(--kk-ease-out),
  transform 0.55s var(--kk-ease-out);
}

.quote-enter-from {
  opacity: 0;
  transform: translateY(12px);
}

.quote-enter-to {
  opacity: 1;
  transform: translateY(0);
}

/* 知识卡片：三层叠卡 + 末张重置归位 */
.flashcard-deck {
  position: relative;
  flex: 1 1 auto;
  display: flex;
  flex-direction: column;
  justify-content: flex-end;
  min-height: 0;
  overflow: visible;
  outline: none;
  overflow-anchor: none;
}

.flashcard-deck :deep(.flashcards),
.flashcard-deck :deep(.flashcards__card-wrapper),
.flashcard-deck :deep(.flash-card),
.flashcard-deck :deep(.flip-card) {
  outline: none !important;
  box-shadow: none;
}

.flashcard-deck :deep(.flashcards) {
  width: 100%;
  /* stack=2 → 可见 3 层；padding ≈ 2 × stack-offset(14px) */
  padding-top: 1.75rem;
  box-sizing: content-box;
  overflow-anchor: none;
}

.flashcard-deck :deep(.flashcards__card-wrapper),
.flashcard-deck :deep(.flash-card) {
  overflow-anchor: none;
}

/* 隐藏叠层外的透明卡，避免边框叠成多道细线 */
.flashcard-deck :deep(.flashcards__card-wrapper[style*='opacity: 0']),
.flashcard-deck :deep(.flashcards__card-wrapper[style*='opacity:0']) {
  visibility: hidden;
  pointer-events: none;
}

.flashcard-deck :deep(.flip-card),
.flashcard-deck :deep(.flip-card__inner) {
  width: 100%;
  height: 9.8rem;
}

.flashcard-deck :deep(.flip-card__front),
.flashcard-deck :deep(.flip-card__back) {
  height: 100%;
}

.flashcard-flip {
  width: 100%;
}

.flashcard-empty {
  width: 100%;
  height: 9.8rem;
}

.flashcard-face {
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

.flashcard-face--back {
  border-color: color-mix(in srgb, var(--kk-color-accent) 36%, var(--kk-color-border));
  box-shadow:
      var(--kk-shadow-card),
      inset 0 0 0 1px color-mix(in srgb, var(--kk-color-accent) 12%, transparent);
}

.flashcard-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.35rem;
  margin-bottom: 0.35rem;
  flex-shrink: 0;
}

.flashcard-badge {
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

.flashcard-badge--back {
  color: var(--kk-color-accent-text);
  background: var(--kk-color-accent-bg);
  border-color: color-mix(in srgb, var(--kk-color-accent) 28%, transparent);
}

.flashcard-index {
  font-family: var(--kk-font-mono);
  font-size: 0.68rem;
  font-variant-numeric: tabular-nums;
  color: var(--kk-color-text-subtle);
}

.flashcard-pane {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  gap: 0.4rem;
}

.flashcard-block {
  display: flex;
  flex-direction: column;
  gap: 0.15rem;
}

.flashcard-block--solo {
  flex: 1;
  justify-content: center;
  text-align: center;
}

.flashcard-tag {
  font-size: 0.6rem;
  font-weight: 700;
  letter-spacing: 0.04em;
  text-transform: uppercase;
  color: var(--kk-color-text-subtle);
  line-height: 1.2;
}

.flashcard-main {
  margin: 0;
  font-family: var(--kk-font-display);
  font-size: clamp(0.9rem, 1.05vw, 1.02rem);
  font-weight: 700;
  line-height: 1.4;
  color: var(--kk-color-primary);
}

.flashcard-main--en {
  font-family: var(--kk-font-mono);
  font-size: 0.9rem;
  font-weight: 600;
}

.flashcard-mini {
  margin: 0;
  padding-top: 0.35rem;
  border-top: 1px dashed color-mix(in srgb, var(--kk-color-accent) 24%, transparent);
  font-size: 0.7rem;
  line-height: 1.35;
  color: var(--kk-color-text-muted);
}

/* 复盘中心：近 30 天热力图（色阶与复盘中心一致） */
.month-heatmap {
  display: flex;
  flex-direction: column;
  justify-content: flex-start;
  gap: 0.55rem;
  flex: 1 1 auto;
  min-height: 0;
  height: 100%;
}

.month-heatmap-meta {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 0.6rem;
  flex-wrap: wrap;
  flex-shrink: 0;
}

.month-heatmap-summary {
  display: flex;
  align-items: baseline;
  gap: 0.35rem;
  min-width: 0;
}

.month-heatmap-summary strong {
  font-family: var(--kk-font-display);
  font-size: 1.35rem;
  line-height: 1;
  font-weight: 800;
  color: var(--kk-color-success);
  font-variant-numeric: tabular-nums;
}

.month-heatmap-summary span {
  color: var(--kk-color-text-muted);
  font-size: 0.72rem;
  font-weight: 700;
  white-space: nowrap;
}

.month-heatmap-scale {
  display: inline-flex;
  align-items: center;
  gap: 0.2rem;
  color: var(--kk-color-text-subtle);
  font-size: 0.62rem;
  font-weight: 700;
}

.month-heatmap-scale .github-heat-cell {
  display: block;
  flex: 0 0 auto;
  width: 0.58rem;
  height: 0.58rem;
  aspect-ratio: auto;
}

.month-heatmap-grid {
  flex: 1 1 auto;
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  grid-template-rows: repeat(5, minmax(0, 1fr));
  gap: 0.28rem;
  width: 100%;
  min-height: 0;
  min-width: 0;
}

.month-heatmap-grid > .github-heat-cell {
  display: block;
  width: 100%;
  height: 100%;
  min-width: 0;
  min-height: 0;
  border-radius: 4px;
}

.github-heat-cell {
  background: color-mix(in srgb, var(--kk-color-primary) 8%, #ffffff);
  box-shadow: inset 0 0 0 1px rgba(11, 26, 125, 0.045);
}

.github-heat-cell--1 {
  background: color-mix(in srgb, var(--kk-color-success) 28%, #ffffff);
  box-shadow: none;
}

.github-heat-cell--2 {
  background: color-mix(in srgb, var(--kk-color-success) 48%, #ffffff);
  box-shadow: none;
}

.github-heat-cell--3 {
  background: color-mix(in srgb, var(--kk-color-success) 72%, #1a4d38);
  box-shadow: none;
}

.github-heat-cell--4 {
  background: var(--kk-color-success);
  box-shadow: none;
}

@media (prefers-reduced-motion: reduce) {
  .score-dim,
  .score-dim-fill {
    transition: none;
  }

  .quote-enter-active {
    transition: none;
  }
}


@media (max-width: 1200px) {
  .pillars-panel {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 992px) {
  .hero-grid {
    grid-template-columns: 1fr;
  }

  .hero-demo {
    perspective: none;
    padding: 0.25rem 0 1rem;
  }

  .hero-demo-stage {
    max-width: none;
    transform: none;
    transition: none;
  }

  .hero-demo-stage:hover {
    transform: none;
  }

  .demo-float-layer {
    animation: none;
  }

  .demo-shadow {
    display: none;
  }

  .demo-window {
    transform: none;
    box-shadow: var(--kk-glass-shadow),
    inset 0 1px 0 var(--kk-glass-highlight);
  }
}

@media (max-width: 560px) {
  .pillars-panel {
    grid-template-columns: 1fr;
    padding: 0.65rem;
    border-radius: var(--kk-radius-lg);
  }

  .pillar {
    min-height: 0;
  }

  .pillar-body,
  .pillar-foot {
    min-height: 0;
  }
}

</style>
