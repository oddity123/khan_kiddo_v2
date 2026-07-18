<script setup lang="ts">
import {ArrowRight, ChatDotRound, DataAnalysis, MagicStick, Promotion, Tickets,} from '@element-plus/icons-vue'
import {ElMessage} from 'element-plus'
import {computed, onMounted, onUnmounted, ref, watch} from 'vue'
import {useRouter} from 'vue-router'
import {FlashCards, FlipCard} from 'vue3-flashcards'

import {fetchHomePage} from '@/api/home'
import type {HomePageResponse} from '@/types/home'
import {getErrorMessage} from '@/utils/error'

const router = useRouter()
const loading = ref(true)
const home = ref<HomePageResponse | null>(null)
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
    zh: "别担心，我会处理的",
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

const RAG_ANSWER =
    '近 30 天共记录 47 处优化点。出现最多的是冠词（18），其次是时态（12）和主谓一致（9）。冠词问题多半出在可数名词前漏加 a / an，下次开口前可以先自查一遍。'
const ragStreamed = ref('')
const ragStreaming = ref(false)

const quotes = [
  {text: '面试前连续复盘两周，开口明显稳了不少。', cite: '远哥'},
  {text: '终于知道自己哪里不地道，不是再死背模板。', cite: '小桐同学'},
  {text: '跨国会议前翻一遍分析记录，比临时抱佛脚管用。', cite: '浩浩'},
] as const
const visibleQuoteCount = ref(0)

const visibleQuotes = computed(() => quotes.slice(0, visibleQuoteCount.value))

const recentSentences = computed(
    () => home.value?.analysisStats?.recentSentences ?? [],
)

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

function startRagStream() {
  ragStreamed.value = ''
  ragStreaming.value = false

  if (prefersReducedMotion()) {
    ragStreamed.value = RAG_ANSWER
    return
  }

  schedule(() => {
    ragStreaming.value = true
    let i = 0
    const step = () => {
      if (i > RAG_ANSWER.length) {
        ragStreaming.value = false
        return
      }
      ragStreamed.value = RAG_ANSWER.slice(0, i)
      i += 1
      schedule(step, 24 + Math.floor(Math.random() * 20))
    }
    step()
  }, 550)
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
  startRagStream()
  startQuoteReveal()
}

async function loadHome() {
  loading.value = true
  try {
    const {data} = await fetchHomePage()
    home.value = data
  } catch (error) {
    home.value = null
    ElMessage.error(getErrorMessage(error, '加载首页数据失败'))
  } finally {
    loading.value = false
    requestAnimationFrame(() => {
      revealed.value = true
    })
  }
}

watch(revealed, (on) => {
  if (!on) {
    return
  }
  startPillarAnimations()
})

onMounted(loadHome)

onUnmounted(() => {
  clearAllTimers()
})
</script>

<template>
  <div v-loading="loading" class="landing" :class="{ 'landing--revealed': revealed }">
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
            {{ home?.description ?? '粘贴你与 AI 的英文对话，系统会标出可优化表达并给出更地道的改写建议。' }}
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
      <article class="pillar">
        <span class="pillar-icon pillar-icon--blue"><el-icon><DataAnalysis/></el-icon></span>
        <h3 class="pillar-heading">智能 AI 语境助手</h3>
        <p class="pillar-body">
          逐句分析对话字幕，定位语法与表达可优化点，给出可直接复用的改写建议。
        </p>
        <div class="score-showcase" aria-label="表现评分示意">
          <div class="score-showcase-head">
            <div class="score-showcase-overall">
              <span class="score-showcase-num">{{ displayOverall }}</span>
            </div>
            <div class="score-showcase-meta">
              <span class="score-showcase-lbl">综合自然度</span>
              <span class="score-showcase-hint">四维诊断 · 即时可见</span>
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
      </article>

      <article class="pillar">
        <span class="pillar-icon pillar-icon--blue"><el-icon><Tickets/></el-icon></span>
        <h3 class="pillar-heading">自动生成知识卡片</h3>
        <p class="pillar-body">
          分析结束后自动抽出可练表达，生成正反面知识卡片；先看中文场景，再翻出地道英文。
        </p>
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
              :stack-offset="10"
              :stack-scale="0.02"
              swipe-direction="horizontal"
              :wait-animation-end="true"
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
          </FlashCards>
        </div>
      </article>

      <article class="pillar">
        <span class="pillar-icon pillar-icon--teal"><el-icon><ChatDotRound/></el-icon></span>
        <h3 class="pillar-heading pillar-heading--teal">复盘助手</h3>
        <p class="pillar-body">
          用自然语言追问你的历史分析：常见错误、典型例句与改进方向。把零散纠正收成清晰重点，下次开口知道先练什么、怎么改。
        </p>
        <div class="rag-snippet" aria-label="复盘问答示意">
          <p class="rag-q">我最近常犯哪些语法错误？</p>
          <div class="rag-a">
            <p class="rag-a-stream">
              {{ ragStreamed }}<span
                v-if="ragStreaming"
                class="rag-cursor"
                aria-hidden="true"
            />
            </p>
          </div>
        </div>
      </article>

      <article class="pillar pillar--learners">
        <span class="pillar-icon pillar-icon--photo">
          <img
              src="https://images.unsplash.com/photo-1573496359142-b8d87734a5a2?w=96&h=96&fit=crop&crop=face"
              alt=""
              loading="lazy"
          />
        </span>
        <h3 class="pillar-heading pillar-heading--gold">致力于深度进阶的学习者</h3>
        <p class="pillar-body">
          适合已有基础、希望说得更准更自然的学习者：备考、面试或跨国协作，都能通过复盘转化表达优势。
        </p>
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
      </article>
    </section>

    <section
        v-if="home?.authenticated && home.analysisStats"
        class="dashboard reveal"
        style="--reveal-delay: 280ms"
    >
      <header class="dashboard-head">
        <div class="dashboard-head-copy">
          <h2 class="dashboard-title">学习诊断</h2>
          <p class="dashboard-lead">把最近练习沉淀成可行动的复盘</p>
        </div>
        <button type="button" class="dashboard-link" @click="router.push('/conversation/analyze')">
          去分析
          <el-icon><ArrowRight/></el-icon>
        </button>
      </header>

      <div class="stats-layout">
        <div class="stat-hero">
          <p class="stat-hero-value">{{ home.analysisStats.recent7DaysSentenceCount }}</p>
          <p class="stat-hero-label">近 7 天分析句子</p>
          <p class="stat-hero-hint">本周练习密度</p>
        </div>
        <div class="stat-side">
          <div class="stat-side-card">
            <p class="stat-side-value">{{ home.analysisStats.seriousIssueCount }}</p>
            <p class="stat-side-label">历史累计优化点</p>
          </div>
          <div class="stat-side-card">
            <p class="stat-side-value stat-side-value--text">{{ home.analysisStats.mostCommonErrorType }}</p>
            <p class="stat-side-label">最常见优化类型</p>
          </div>
        </div>
      </div>

      <template v-if="recentSentences.length">
        <h3 class="recent-head">最近复盘</h3>
        <article
            v-for="(item, idx) in recentSentences"
            :key="idx"
            class="recent-item"
            :style="{ '--item-delay': `${idx * 60}ms` }"
        >
          <p class="recent-original">{{ item.originalSentence }}</p>
          <p v-if="item.suggestion" class="recent-suggestion">
            <span class="recent-arrow" aria-hidden="true">→</span>
            {{ item.suggestion }}
          </p>
          <div v-if="item.problemTypeTags?.length" class="recent-tags">
            <span v-for="tag in item.problemTypeTags.slice(0, 3)" :key="tag">{{ tag }}</span>
          </div>
        </article>
      </template>
      <div v-else class="dashboard-empty">
        <p class="dashboard-empty-text">近 7 天还没有分析记录</p>
        <button type="button" class="btn-primary" @click="router.push('/conversation/analyze')">
          开始分析
        </button>
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
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 1rem;
  margin-bottom: 1.25rem;
}

.pillar {
  display: flex;
  flex-direction: column;
  background: #fff;
  border-radius: var(--kk-radius-lg);
  padding: 1.25rem 1.2rem 1.15rem;
  border: 1px solid rgba(11, 26, 125, 0.06);
  box-shadow: var(--kk-shadow-card);
  transition: transform 0.25s ease, box-shadow 0.25s ease;
}

.pillar:hover {
  transform: translateY(-3px);
  box-shadow: var(--kk-shadow-card-hover);
}

.pillar-icon {
  width: 42px;
  height: 42px;
  border-radius: 50%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 0.75rem;
  color: #fff;
  font-size: 1.05rem;
  overflow: hidden;
}

.pillar-icon--blue {
  background: linear-gradient(145deg, var(--kk-color-primary-soft), var(--kk-color-primary));
}

.pillar-icon--teal {
  background: linear-gradient(145deg, #1a6b6b, #0f4a4a);
}

.pillar-icon--photo {
  border: 2px solid color-mix(in srgb, var(--kk-color-accent) 35%, transparent);
  padding: 0;
}

.pillar-icon--photo img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.pillar-heading {
  margin: 0 0 0.5rem;
  font-family: var(--kk-font-display);
  font-size: 1.15rem;
  font-weight: 700;
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
  font-size: 0.88rem;
  line-height: 1.55;
  color: var(--kk-color-text-muted);
}

.score-showcase {
  margin-top: auto;
  padding-top: 0.85rem;
}

.score-showcase-head {
  display: flex;
  align-items: center;
  gap: 0.7rem;
  margin-bottom: 0.7rem;
}

.score-showcase-overall {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 3.1rem;
  height: 3.1rem;
  flex-shrink: 0;
  border-radius: 50%;
  background: linear-gradient(145deg, #101c6e 0%, var(--kk-color-primary) 55%, #223194 100%);
  box-shadow: 0 6px 16px rgba(11, 26, 125, 0.22);
}

.score-showcase-num {
  font-family: var(--kk-font-display);
  font-size: 1.25rem;
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
  font-size: 0.88rem;
  font-weight: 700;
  color: var(--kk-color-primary);
}

.score-showcase-hint {
  font-size: 0.68rem;
  font-weight: 600;
  color: var(--kk-color-accent-text);
}

.score-showcase-dims {
  list-style: none;
  margin: 0;
  padding: 0.65rem 0.7rem;
  display: flex;
  flex-direction: column;
  gap: 0.38rem;
  border-radius: var(--kk-radius-md);
  background: linear-gradient(160deg, #f5f6fa 0%, #eceef5 100%);
  border: 1px solid var(--kk-color-border-subtle);
}

.score-dim {
  display: grid;
  grid-template-columns: 3.4rem 1fr 1.5rem;
  align-items: center;
  gap: 0.35rem;
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
  font-size: 0.65rem;
  font-weight: 600;
  color: var(--kk-color-text-muted);
  white-space: nowrap;
}

.score-dim--accent .score-dim-label {
  color: var(--kk-color-accent-text);
  font-weight: 700;
}

.score-dim-track {
  height: 4px;
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
  font-size: 0.78rem;
  font-weight: 700;
  text-align: right;
  color: var(--kk-color-primary);
  font-variant-numeric: tabular-nums;
}

/* 学习者评价：灰色纯文字，逐条出现 */
.quote-list {
  margin-top: auto;
  padding-top: 0.85rem;
  display: flex;
  flex-direction: column;
  justify-content: flex-start;
  gap: 0.55rem;
  border-top: 1px solid rgba(11, 26, 125, 0.06);
}

.quote-line {
  margin: 0;
  font-size: 0.78rem;
  line-height: 1.6;
  color: var(--kk-color-text-subtle);
}

.quote-cite {
  display: inline;
  margin-left: 0.25rem;
  font-size: 0.72rem;
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
  margin-top: auto;
  padding-top: 0.85rem;
  min-height: calc(9.6rem + 1.35rem);
  overflow: visible;
  outline: none;
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
  /* stack=2 → 可见 3 层；留白 = 2 × offset */
  padding-top: 1.25rem;
  box-sizing: content-box;
}

.flashcard-deck :deep(.flip-card),
.flashcard-deck :deep(.flip-card__inner) {
  width: 100%;
  height: 9.6rem;
}

.flashcard-deck :deep(.flip-card__front),
.flashcard-deck :deep(.flip-card__back) {
  height: 100%;
}

.flashcard-flip {
  width: 100%;
}

.flashcard-face {
  box-sizing: border-box;
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  padding: 0.55rem 0.7rem 0.6rem;
  border-radius: var(--kk-radius-md);
  border: 1px solid var(--kk-color-border);
  background: var(--kk-color-surface-solid);
  box-shadow: var(--kk-shadow-card);
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
  font-size: 0.98rem;
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
  font-size: 0.68rem;
  line-height: 1.35;
  color: var(--kk-color-text-muted);
}

/* 复盘助手：流式输出 */
.rag-snippet {
  margin-top: auto;
  padding-top: 0.85rem;
  display: flex;
  flex-direction: column;
  justify-content: flex-start;
  gap: 0.55rem;
  border-top: 1px solid rgba(11, 26, 125, 0.06);
}

.rag-q {
  margin: 0;
  font-size: 0.74rem;
  line-height: 1.5;
  font-style: italic;
  color: var(--kk-color-text-subtle);
}

.rag-q::before {
  content: '你 · ';
  font-style: normal;
  font-weight: 700;
  letter-spacing: 0.02em;
}

.rag-a {
  min-height: 4.6rem;
  padding-left: 0.7rem;
  border-left: 2px solid rgba(26, 92, 92, 0.35);
}

.rag-a-stream {
  margin: 0;
  font-size: 0.78rem;
  line-height: 1.65;
  color: var(--kk-color-text-secondary);
  white-space: pre-wrap;
}

.rag-cursor {
  display: inline-block;
  width: 0.45em;
  height: 1em;
  margin-left: 1px;
  vertical-align: -0.12em;
  background: #1a5c5c;
  animation: rag-blink 0.85s steps(1) infinite;
}

@keyframes rag-blink {
  0%,
  45% {
    opacity: 1;
  }
  50%,
  100% {
    opacity: 0;
  }
}

@media (prefers-reduced-motion: reduce) {
  .score-dim,
  .score-dim-fill {
    transition: none;
  }

  .quote-enter-active {
    transition: none;
  }

  .rag-cursor {
    animation: none;
    opacity: 0;
  }
}

/* Dashboard — 编辑式诊断台 */
.dashboard {
  position: relative;
  background: var(--kk-color-surface-solid);
  border-radius: var(--kk-radius-lg);
  padding: clamp(1.35rem, 2.2vw, 1.9rem);
  border: 1px solid var(--kk-color-border);
  box-shadow: var(--kk-shadow-card);
  overflow: hidden;
}

.dashboard::before {
  content: '';
  position: absolute;
  inset: 0 auto 0 0;
  width: 3px;
  background: linear-gradient(180deg, var(--kk-color-primary), var(--kk-color-accent));
}

.dashboard-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 1rem;
  margin-bottom: 1.35rem;
}

.dashboard-head-copy {
  min-width: 0;
}

.dashboard-title {
  margin: 0;
  font-family: var(--kk-font-display);
  font-size: 1.35rem;
  font-weight: 700;
  letter-spacing: -0.02em;
  color: var(--kk-color-primary);
}

.dashboard-lead {
  margin: 0.35rem 0 0;
  font-size: 0.9rem;
  line-height: 1.45;
  color: var(--kk-color-text-muted);
}

.dashboard-link {
  display: inline-flex;
  align-items: center;
  gap: 0.25rem;
  flex-shrink: 0;
  margin-top: 0.15rem;
  padding: 0.35rem 0.15rem;
  border: none;
  background: none;
  color: var(--kk-color-primary);
  font-family: inherit;
  font-size: 0.88rem;
  font-weight: 600;
  cursor: pointer;
  transition: color var(--kk-duration-normal) var(--kk-ease-out),
  gap var(--kk-duration-normal) var(--kk-ease-out);
}

.dashboard-link:hover {
  color: var(--kk-color-accent-text);
  gap: 0.4rem;
}

.stats-layout {
  display: grid;
  grid-template-columns: 1.35fr 1fr;
  gap: 0.85rem;
  margin-bottom: 1.5rem;
}

.stat-hero {
  position: relative;
  padding: 1.35rem 1.25rem 1.2rem;
  border-radius: var(--kk-radius-md);
  background: linear-gradient(145deg, #101c6e 0%, var(--kk-color-primary) 55%, #223194 100%);
  color: #f4f6ff;
  overflow: hidden;
  animation: stat-in 0.7s var(--kk-ease-out) both;
}

.stat-hero::after {
  content: '';
  position: absolute;
  right: -12%;
  top: -30%;
  width: 55%;
  height: 140%;
  background: radial-gradient(circle, rgba(184, 148, 31, 0.28) 0%, transparent 68%);
  pointer-events: none;
}

.stat-hero-value {
  position: relative;
  margin: 0;
  font-family: var(--kk-font-display);
  font-size: clamp(2.6rem, 5vw, 3.4rem);
  font-weight: 800;
  line-height: 1;
  letter-spacing: -0.03em;
  font-variant-numeric: tabular-nums;
}

.stat-hero-label {
  position: relative;
  margin: 0.65rem 0 0;
  font-size: 0.95rem;
  font-weight: 600;
  color: rgba(244, 246, 255, 0.92);
}

.stat-hero-hint {
  position: relative;
  margin: 0.25rem 0 0;
  font-size: 0.78rem;
  color: rgba(244, 246, 255, 0.58);
}

.stat-side {
  display: grid;
  grid-template-rows: 1fr 1fr;
  gap: 0.85rem;
}

.stat-side-card {
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: 0.95rem 1.05rem;
  border-radius: var(--kk-radius-md);
  background: linear-gradient(160deg, #f5f6fa 0%, #eceef5 100%);
  border: 1px solid var(--kk-color-border-subtle);
  animation: stat-in 0.7s var(--kk-ease-out) both;
}

.stat-side-card:nth-child(1) {
  animation-delay: 80ms;
}

.stat-side-card:nth-child(2) {
  animation-delay: 140ms;
}

.stat-side-value {
  margin: 0;
  font-family: var(--kk-font-display);
  font-size: 1.65rem;
  font-weight: 700;
  line-height: 1.15;
  color: var(--kk-color-primary);
  font-variant-numeric: tabular-nums;
}

.stat-side-value--text {
  font-size: 1.05rem;
  font-weight: 700;
  line-height: 1.35;
}

.stat-side-label {
  margin: 0.3rem 0 0;
  font-size: 0.78rem;
  color: var(--kk-color-text-muted);
}

@keyframes stat-in {
  from {
    opacity: 0;
    transform: translateY(10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.recent-head {
  margin: 0 0 0.85rem;
  font-size: 0.8rem;
  font-weight: 700;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: var(--kk-color-text-subtle);
}

.recent-item {
  padding: 1rem 0 1.05rem;
  margin-bottom: 0;
  border-bottom: 1px solid var(--kk-color-border);
  background: transparent;
  border-left: none;
  border-radius: 0;
  animation: stat-in 0.55s var(--kk-ease-out) both;
  animation-delay: var(--item-delay, 0ms);
}

.recent-item:last-of-type {
  border-bottom: none;
  padding-bottom: 0;
}

.recent-original {
  margin: 0;
  font-size: 0.88rem;
  line-height: 1.55;
  color: var(--kk-color-text-subtle);
  text-decoration: line-through;
  text-decoration-color: rgba(122, 128, 148, 0.45);
}

.recent-suggestion {
  display: flex;
  gap: 0.45rem;
  margin: 0.4rem 0 0;
  font-size: 0.95rem;
  line-height: 1.55;
  font-weight: 600;
  color: var(--kk-color-link);
}

.recent-arrow {
  flex-shrink: 0;
  color: var(--kk-color-accent);
  font-weight: 700;
}

.recent-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 0.35rem;
  margin-top: 0.55rem;
}

.recent-tags span {
  padding: 0.15rem 0.5rem;
  border-radius: var(--kk-radius-sm);
  background: var(--kk-color-accent-bg);
  color: var(--kk-color-accent-text);
  font-size: 0.7rem;
  font-weight: 600;
  letter-spacing: 0.01em;
}

.dashboard-empty {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 0.85rem;
  padding: 0.5rem 0 0.15rem;
}

.dashboard-empty-text {
  margin: 0;
  color: var(--kk-color-text-muted);
  font-size: 0.92rem;
}

@media (max-width: 1200px) {
  .pillars {
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

  .stats-layout {
    grid-template-columns: 1fr;
  }

  .stat-side {
    grid-template-columns: 1fr 1fr;
    grid-template-rows: none;
  }
}

@media (max-width: 560px) {
  .dashboard-head {
    flex-direction: column;
    align-items: stretch;
  }

  .pillars {
    grid-template-columns: 1fr;
  }

  .stat-side {
    grid-template-columns: 1fr;
  }
}
</style>
