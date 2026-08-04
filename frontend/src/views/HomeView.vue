<script setup lang="ts">
import {ArrowRight, MagicStick, Promotion,} from '@element-plus/icons-vue'
import {ElMessage} from 'element-plus'
import {computed, onMounted, onUnmounted, ref, watch} from 'vue'
import {useRouter} from 'vue-router'
import {FlashCards, FlipCard} from 'vue3-flashcards'

import PillarMark from '@/components/home/PillarMark.vue'
import {fetchHomePage} from '@/api/home'
import type {HomePageResponse} from '@/types/home'
import {getErrorMessage} from '@/utils/error'
import {signalPrerenderReady} from '@/utils/prerender'

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

const RAG_ANSWER =
    '近 30 天共记录 47 处优化点。出现最多的是冠词（18），其次是时态（12）和主谓一致（9）。冠词问题多半出在可数名词前漏加 a / an，下次开口前可以先自查一遍。主谓一致则多在 everyone / each 后误用 are。'
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
    () => (home.value?.analysisStats?.recentSentences ?? []).slice(0, 3),
)

const dashboardMetricCards = computed(() => {
  const stats = home.value?.analysisStats
  if (!stats) {
    return []
  }
  return [
    {
      key: 'sentences',
      kicker: 'ALL',
      value: stats.analyzedSentenceCount,
      label: '累计分析句子',
      hint: '全部有效练习样本',
    },
    {
      key: 'sessions',
      kicker: 'TIMES',
      value: stats.analysisCount,
      label: '累计复盘次数',
      hint: '完成分析的对话',
    },
    {
      key: 'cards',
      kicker: 'DUE',
      value: stats.dueGrowthCardCount,
      label: '待复习成长卡',
      hint: '今天需要回看',
    },
    {
      key: 'focus',
      kicker: 'FOCUS',
      value: stats.mostCommonErrorType || '—',
      label: '最常见优化类型',
      hint: '历史高频薄弱点',
      text: true,
    },
  ]
})

const dailyPracticeTrend = computed(
    () => home.value?.analysisStats?.dailyPracticeTrend ?? [],
)

const weeklySentenceDelta = computed(
    () => home.value?.analysisStats?.weeklySentenceDelta ?? {current: 0, previous: 0, delta: 0, percent: 0},
)

const problemTypeDistribution = computed(
    () => home.value?.analysisStats?.recent7DaysProblemTypeDistribution ?? [],
)

const growthCardStatusCounts = computed(
    () => home.value?.analysisStats?.growthCardStatusCounts ?? [],
)

const dailyIssueHeatmap = computed(
    () => home.value?.analysisStats?.dailyIssueHeatmap ?? [],
)

const issueHeatmapMax = computed(() =>
    Math.max(...dailyIssueHeatmap.value.map((item) => item.count), 1),
)

const issueHeatmapTotal = computed(() =>
    dailyIssueHeatmap.value.reduce((sum, item) => sum + item.count, 0),
)

const issueHeatmapCells = computed(() =>
    dailyIssueHeatmap.value.map((item) => {
      const ratio = item.count / issueHeatmapMax.value
      const level = item.count === 0 ? 0 : Math.min(4, Math.max(1, Math.ceil(ratio * 4)))
      return {
        ...item,
        level,
      }
    }),
)

const chartColors = ['#0b1a7d', '#b8941f', '#2d6a4f', '#1f4da9', '#7a6200', '#7a8094'] as const

const trendMax = computed(() =>
    Math.max(...dailyPracticeTrend.value.map((item) => item.count), 1),
)

const trendPoints = computed(() => {
  const items = dailyPracticeTrend.value
  if (!items.length) {
    return ''
  }
  return items.map((item, index) => {
    const x = items.length <= 1 ? 0 : (index / (items.length - 1)) * 240
    const y = 70 - (item.count / trendMax.value) * 54
    return `${x.toFixed(1)},${y.toFixed(1)}`
  }).join(' ')
})

const trendPath = computed(() => {
  const points = trendPoints.value.split(' ').filter(Boolean)
  if (!points.length) {
    return ''
  }
  return points.map((point, index) => `${index === 0 ? 'M' : 'L'} ${point}`).join(' ')
})

const trendAreaPath = computed(() => {
  if (!trendPath.value) {
    return ''
  }
  return `${trendPath.value} L 240,76 L 0,76 Z`
})

const trendLastCount = computed(() =>
    dailyPracticeTrend.value[dailyPracticeTrend.value.length - 1]?.count ?? 0,
)

const weeklyDeltaText = computed(() => {
  const delta = weeklySentenceDelta.value.delta
  return `${delta >= 0 ? '+' : ''}${delta}`
})

const weeklyDeltaPercentText = computed(() => {
  const percent = weeklySentenceDelta.value.percent
  return `${percent >= 0 ? '+' : ''}${percent}%`
})

const weeklyDeltaTone = computed(() =>
    weeklySentenceDelta.value.delta >= 0 ? 'up' : 'down',
)

const problemDistributionTotal = computed(() =>
    problemTypeDistribution.value.reduce((sum, item) => sum + item.count, 0),
)

const problemDonutGradient = computed(() => {
  if (!problemDistributionTotal.value) {
    return 'conic-gradient(rgba(11, 26, 125, 0.1) 0 100%)'
  }
  let cursor = 0
  const segments = problemTypeDistribution.value.map((item, index) => {
    const start = cursor
    const end = cursor + (item.count / problemDistributionTotal.value) * 100
    cursor = end
    return `${chartColors[index % chartColors.length]} ${start.toFixed(2)}% ${end.toFixed(2)}%`
  })
  return `conic-gradient(${segments.join(', ')})`
})

const growthDueCount = computed(() =>
    growthCardStatusCounts.value.find((item) => item.key === 'due')?.count ?? 0,
)

const growthStatusBreakdown = computed(() =>
    growthCardStatusCounts.value.filter((item) => item.key !== 'due'),
)

const growthStatusTotal = computed(() =>
    Math.max(growthStatusBreakdown.value.reduce((sum, item) => sum + item.count, 0), 1),
)

function growthStatusWidth(count: number) {
  if (count <= 0) {
    return '0%'
  }
  return `${Math.max(7, Math.round((count / growthStatusTotal.value) * 100))}%`
}

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
      signalPrerenderReady()
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
      <div class="pillars-panel">
        <article class="pillar">
          <header class="pillar-head">
            <PillarMark kind="analyze"/>
            <h3 class="pillar-heading">智能 AI 语境助手</h3>
          </header>
          <p class="pillar-body">
            逐句分析对话字幕，定位语法与表达可优化点，给出可直接复用的改写建议。
          </p>
          <div class="pillar-foot">
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
          </div>
        </article>

        <article class="pillar">
          <header class="pillar-head">
            <PillarMark kind="cards"/>
            <h3 class="pillar-heading">自动生成知识卡片</h3>
          </header>
          <p class="pillar-body">
            分析结束后自动抽出可练表达，生成正反面知识卡片；先看中文场景，再翻出地道英文。
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

        <article class="pillar">
          <header class="pillar-head">
            <PillarMark kind="review"/>
            <h3 class="pillar-heading pillar-heading--teal">复盘助手</h3>
          </header>
          <p class="pillar-body">
            用自然语言追问你的历史分析：常见错误、典型例句与改进方向。把零散纠正收成清晰重点，下次开口知道先练什么、怎么改。
          </p>
          <div class="pillar-foot">
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
          </div>
        </article>

        <article class="pillar pillar--learners">
          <header class="pillar-head">
            <PillarMark kind="learners"/>
            <h3 class="pillar-heading pillar-heading--gold">致力于深度进阶的学习</h3>
          </header>
          <p class="pillar-body">
            适合已有基础、希望说得更准更自然的学习者： 备考、面试或跨国协作，都能通过复盘转化表达优势。
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

    <section
        v-if="home?.authenticated && home.analysisStats"
        class="dashboard reveal"
        style="--reveal-delay: 280ms"
    >
      <header class="dashboard-head">
        <div class="dashboard-head-copy">
          <h2 class="dashboard-title">学习复盘</h2>
          <p class="dashboard-lead">把最近练习沉淀成可行动的复盘</p>
        </div>
        <button type="button" class="dashboard-link" @click="router.push('/conversation/analyze')">
          去分析
          <el-icon><ArrowRight/></el-icon>
        </button>
      </header>

      <div class="stats-layout">
        <div class="stat-hero">
          <div class="stat-hero-top">
            <span class="stat-kicker">7D</span>
            <span class="week-delta" :class="`week-delta--${weeklyDeltaTone}`">
              {{ weeklyDeltaText }} 句 · {{ weeklyDeltaPercentText }}
            </span>
          </div>
          <p class="stat-hero-value">{{ home.analysisStats.recent7DaysSentenceCount }}</p>
          <p class="stat-hero-label">近 7 天分析句子</p>
          <p class="stat-hero-hint">相比上个 7 天：{{ weeklySentenceDelta.previous }} 句</p>
          <div class="trend-chart" aria-label="最近 14 天练习趋势">
            <svg viewBox="0 0 240 82" role="img" aria-hidden="true">
              <path v-if="trendAreaPath" class="trend-area" :d="trendAreaPath"/>
              <polyline
                  v-if="trendPoints"
                  class="trend-line"
                  :points="trendPoints"
              />
              <circle
                  v-if="trendPoints"
                  class="trend-dot"
                  :cx="dailyPracticeTrend.length <= 1 ? 0 : 240"
                  :cy="70 - (trendLastCount / trendMax) * 54"
                  r="3.5"
              />
            </svg>
            <div class="trend-axis" aria-hidden="true">
              <span>{{ dailyPracticeTrend[0]?.label ?? '—' }}</span>
              <span>{{ dailyPracticeTrend[dailyPracticeTrend.length - 1]?.label ?? '—' }}</span>
            </div>
          </div>
        </div>
        <div class="stat-grid">
          <article
              v-for="metric in dashboardMetricCards"
              :key="metric.key"
              class="stat-card"
          >
            <span class="stat-kicker">{{ metric.kicker }}</span>
            <p class="stat-card-value" :class="{ 'stat-card-value--text': metric.text }">
              {{ metric.value }}
            </p>
            <p class="stat-card-label">{{ metric.label }}</p>
            <p class="stat-card-hint">{{ metric.hint }}</p>
          </article>
        </div>
      </div>

      <div class="dashboard-analytics">
        <section class="analytics-card analytics-card--donut" aria-label="近 7 天问题类型分布">
          <div class="analytics-card-head">
            <span class="stat-kicker">DONUT</span>
            <p>问题类型分布</p>
            <span class="analytics-range">最近 7 天</span>
          </div>
          <div class="donut-row">
            <div class="problem-donut" :style="{ background: problemDonutGradient }">
              <span>{{ problemDistributionTotal }}</span>
              <small>优化点</small>
            </div>
            <div class="donut-legend">
              <div
                  v-for="(problem, index) in problemTypeDistribution"
                  :key="problem.label"
                  class="donut-legend-row"
              >
                <span class="donut-swatch" :style="{ background: chartColors[index % chartColors.length] }"></span>
                <span class="donut-name">{{ problem.label }}</span>
                <span class="donut-count">{{ problem.count }}</span>
              </div>
              <p v-if="!problemTypeDistribution.length" class="chart-empty">近 7 天暂无优化记录</p>
            </div>
          </div>
        </section>

        <section class="analytics-card analytics-card--rank" aria-label="近 30 天优化热力图">
          <div class="analytics-card-head">
            <span class="stat-kicker">HEAT</span>
            <p>每日优化热力图</p>
            <span class="analytics-range">近 30 天</span>
          </div>
          <div v-if="issueHeatmapCells.length" class="github-heatmap">
            <div class="github-heatmap-meta">
              <div class="github-heatmap-summary">
                <strong>{{ issueHeatmapTotal }}</strong>
                <span>个优化点</span>
              </div>
              <div class="github-heatmap-scale" aria-hidden="true">
                <span>少</span>
                <i class="github-heat-cell github-heat-cell--0"></i>
                <i class="github-heat-cell github-heat-cell--1"></i>
                <i class="github-heat-cell github-heat-cell--2"></i>
                <i class="github-heat-cell github-heat-cell--3"></i>
                <i class="github-heat-cell github-heat-cell--4"></i>
                <span>多</span>
              </div>
            </div>
            <div class="github-heatmap-grid" role="img" :aria-label="`近 30 天共 ${issueHeatmapTotal} 个优化点`">
              <span
                  v-for="day in issueHeatmapCells"
                  :key="day.date"
                  class="github-heat-cell"
                  :class="`github-heat-cell--${day.level}`"
                  :title="`${day.label} · ${day.count} 个优化点`"
              ></span>
            </div>
          </div>
          <p v-else class="chart-empty">近 30 天暂无优化记录</p>
        </section>

        <section class="analytics-card analytics-card--growth" aria-label="成长卡状态">
          <div class="analytics-card-head">
            <span class="stat-kicker">CARDS</span>
            <p>成长卡状态</p>
          </div>
          <div class="growth-load">
            <span class="growth-load-value">{{ growthDueCount }}</span>
            <span class="growth-load-copy">今日待复习</span>
          </div>
          <div class="growth-stack" aria-hidden="true">
            <span
                v-for="status in growthStatusBreakdown"
                :key="status.key"
                :class="`growth-stack-seg growth-stack-seg--${status.key}`"
                :style="{ width: growthStatusWidth(status.count) }"
            ></span>
          </div>
          <div class="growth-legend">
            <span
                v-for="status in growthStatusBreakdown"
                :key="status.key"
                :class="`growth-legend-item growth-legend-item--${status.key}`"
            >
              {{ status.label }} {{ status.count }}
            </span>
          </div>
        </section>
      </div>

      <div class="dashboard-panels">
        <!-- 今日复习卡暂时下线，稍后恢复 -->
        <section class="dashboard-panel dashboard-panel--recent" aria-labelledby="recent-review-title">
          <template v-if="recentSentences.length">
            <h3 id="recent-review-title" class="recent-head">最近复盘</h3>
            <article
                v-for="(item, idx) in recentSentences"
                :key="idx"
                class="recent-item"
                :style="{ '--item-delay': `${idx * 60}ms` }"
            >
              <span class="recent-index">{{ String(idx + 1).padStart(2, '0') }}</span>
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
            <h3 id="recent-review-title" class="recent-head">最近复盘</h3>
            <p class="dashboard-empty-text">近 7 天还没有分析记录</p>
            <button type="button" class="btn-primary" @click="router.push('/conversation/analyze')">
              开始分析
            </button>
          </div>
        </section>
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

/* 复盘助手：流式输出 */
.rag-snippet {
  margin-top: 0;
  display: flex;
  flex-direction: column;
  justify-content: flex-start;
  gap: 0.7rem;
  min-height: 100%;
}

.rag-q {
  margin: 0;
  font-size: 0.8rem;
  line-height: 1.58;
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
  flex: 1 1 auto;
  min-height: 4.6rem;
  padding-left: 0.82rem;
  border-left: 2px solid rgba(26, 92, 92, 0.32);
}

.rag-a-stream {
  margin: 0;
  font-size: 0.82rem;
  line-height: 1.72;
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

/* Dashboard — 复盘工作台 */
.dashboard {
  position: relative;
  background:
      linear-gradient(180deg, rgba(255, 255, 255, 0.88), rgba(246, 247, 241, 0.92)),
      var(--kk-color-surface-solid);
  border-radius: var(--kk-radius-lg);
  padding: clamp(1.2rem, 2.1vw, 1.85rem);
  border: 1px solid rgba(11, 26, 125, 0.1);
  box-shadow:
      0 26px 56px rgba(11, 26, 125, 0.13),
      0 8px 18px rgba(20, 24, 36, 0.06),
      inset 0 1px 0 rgba(255, 255, 255, 0.94);
  overflow: hidden;
  isolation: isolate;
}

.dashboard::before {
  content: '';
  position: absolute;
  inset: 0;
  z-index: -1;
  background:
      linear-gradient(rgba(11, 26, 125, 0.035) 1px, transparent 1px),
      linear-gradient(90deg, rgba(11, 26, 125, 0.03) 1px, transparent 1px);
  background-size: 26px 26px;
  mask-image: linear-gradient(180deg, rgba(0, 0, 0, 0.8), transparent 82%);
  pointer-events: none;
}

.dashboard-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1.25rem;
  margin-bottom: 1.15rem;
  padding-bottom: 1.05rem;
  border-bottom: 1px solid rgba(11, 26, 125, 0.08);
}

.dashboard-head-copy {
  min-width: 0;
}

.dashboard-title {
  margin: 0;
  font-family: var(--kk-font-display);
  font-size: clamp(1.2rem, 2vw, 1.55rem);
  font-weight: 700;
  letter-spacing: 0;
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
  justify-content: center;
  gap: 0.35rem;
  flex-shrink: 0;
  min-height: 2.45rem;
  padding: 0.55rem 0.78rem 0.55rem 0.9rem;
  border: 1px solid rgba(11, 26, 125, 0.12);
  border-radius: var(--kk-radius-sm);
  background: rgba(255, 255, 255, 0.72);
  color: var(--kk-color-primary);
  font-family: inherit;
  font-size: 0.88rem;
  font-weight: 700;
  cursor: pointer;
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.86);
  transition:
      color var(--kk-duration-normal) var(--kk-ease-out),
      border-color var(--kk-duration-normal) var(--kk-ease-out),
      background var(--kk-duration-normal) var(--kk-ease-out),
      transform var(--kk-duration-normal) var(--kk-ease-out);
}

.dashboard-link:hover {
  color: var(--kk-color-accent-text);
  border-color: rgba(184, 148, 31, 0.3);
  background: var(--kk-color-accent-bg);
  transform: translateY(-1px);
}

.stats-layout {
  display: grid;
  grid-template-columns: minmax(16rem, 0.92fr) minmax(0, 1.55fr);
  gap: 0.75rem;
  margin-bottom: 0.75rem;
}

.dashboard-analytics {
  display: grid;
  grid-template-columns: minmax(16rem, 1fr) minmax(15rem, 0.9fr) minmax(15rem, 0.95fr);
  gap: 0.75rem;
  margin-bottom: 0.75rem;
}

.dashboard-panels {
  display: grid;
  grid-template-columns: 1fr;
  gap: 0.75rem;
}

.dashboard-panel {
  min-width: 0;
  border: 1px solid rgba(11, 26, 125, 0.08);
  border-radius: var(--kk-radius-md);
  background:
      linear-gradient(180deg, rgba(255, 255, 255, 0.82), rgba(249, 250, 246, 0.72));
  box-shadow:
      inset 0 1px 0 rgba(255, 255, 255, 0.86),
      0 16px 34px rgba(11, 26, 125, 0.09),
      0 4px 10px rgba(20, 24, 36, 0.04);
}

.dashboard-panel--recent .recent-head {
  margin: 0;
  padding: 1rem 1rem 0.25rem;
}

.dashboard-panel--recent .dashboard-empty {
  padding-top: 0.25rem;
}

.dashboard-panel--recent .dashboard-empty .recent-head {
  margin-bottom: 0.65rem;
}

.stat-hero {
  position: relative;
  min-height: 100%;
  padding: 1.15rem 1.2rem;
  border-radius: var(--kk-radius-md);
  background:
      linear-gradient(135deg, rgba(255, 255, 255, 0.92), rgba(247, 248, 252, 0.82)),
      linear-gradient(135deg, rgba(11, 26, 125, 0.05), rgba(184, 148, 31, 0.06));
  border: 1px solid rgba(11, 26, 125, 0.08);
  color: var(--kk-color-text);
  overflow: hidden;
  animation: stat-in 0.7s var(--kk-ease-out) both;
  box-shadow:
      inset 0 1px 0 rgba(255, 255, 255, 0.88),
      0 22px 38px rgba(11, 26, 125, 0.14),
      0 6px 14px rgba(20, 24, 36, 0.06);
}

.stat-hero::after {
  content: '';
  position: absolute;
  right: 1rem;
  bottom: 1rem;
  width: 5.5rem;
  height: 4.4rem;
  border-right: 1px solid rgba(184, 148, 31, 0.24);
  border-bottom: 1px solid rgba(184, 148, 31, 0.24);
  background:
      linear-gradient(90deg, transparent 0 62%, rgba(184, 148, 31, 0.13) 62% 65%, transparent 65%),
      linear-gradient(180deg, transparent 0 34%, rgba(11, 26, 125, 0.12) 34% 37%, transparent 37% 66%, rgba(11, 26, 125, 0.12) 66% 69%, transparent 69%);
  opacity: 0.78;
  pointer-events: none;
}

.stat-hero-top {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
}

.week-delta {
  display: inline-flex;
  align-items: center;
  min-height: 1.45rem;
  padding: 0.16rem 0.48rem;
  border-radius: var(--kk-radius-sm);
  font-family: var(--kk-font-mono);
  font-size: 0.68rem;
  font-weight: 600;
  line-height: 1.2;
  white-space: nowrap;
}

.week-delta--up {
  background: rgba(45, 106, 79, 0.1);
  color: var(--kk-color-success);
}

.week-delta--down {
  background: rgba(160, 24, 24, 0.09);
  color: var(--kk-color-danger);
}

.stat-kicker {
  position: relative;
  display: inline-flex;
  align-items: center;
  width: fit-content;
  padding: 0.16rem 0.42rem;
  border-radius: var(--kk-radius-sm);
  background: rgba(11, 26, 125, 0.06);
  color: var(--kk-color-primary);
  font-family: var(--kk-font-mono);
  font-size: 0.68rem;
  font-weight: 600;
  line-height: 1.2;
  letter-spacing: 0.04em;
}

.stat-hero-value {
  position: relative;
  margin: 0.82rem 0 0;
  font-family: var(--kk-font-display);
  font-size: clamp(2.8rem, 5.5vw, 4rem);
  font-weight: 800;
  line-height: 1;
  letter-spacing: 0;
  font-variant-numeric: tabular-nums;
  color: var(--kk-color-primary);
}

.stat-hero-label {
  position: relative;
  margin: 0.65rem 0 0;
  font-size: 0.95rem;
  font-weight: 600;
  color: var(--kk-color-text-secondary);
}

.stat-hero-hint {
  position: relative;
  margin: 0.25rem 0 0;
  font-size: 0.78rem;
  color: var(--kk-color-text-subtle);
}

.trend-chart {
  position: relative;
  margin-top: 0.85rem;
  padding: 0.55rem 0.55rem 0.35rem;
  border-radius: var(--kk-radius-sm);
  background: rgba(255, 255, 255, 0.5);
  border: 1px solid rgba(11, 26, 125, 0.06);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.7);
}

.trend-chart svg {
  display: block;
  width: 100%;
  height: 5.15rem;
}

.trend-area {
  fill: rgba(184, 148, 31, 0.13);
}

.trend-line {
  fill: none;
  stroke: var(--kk-color-primary);
  stroke-width: 3;
  stroke-linecap: round;
  stroke-linejoin: round;
  filter: drop-shadow(0 4px 7px rgba(11, 26, 125, 0.2));
}

.trend-dot {
  fill: var(--kk-color-accent);
  stroke: #fff;
  stroke-width: 2;
}

.trend-axis {
  display: flex;
  justify-content: space-between;
  gap: 0.75rem;
  margin-top: -0.15rem;
  color: var(--kk-color-text-subtle);
  font-family: var(--kk-font-mono);
  font-size: 0.66rem;
}

.stat-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0.75rem;
}

.stat-card {
  display: flex;
  flex-direction: column;
  min-height: 7.45rem;
  padding: 0.85rem 0.95rem;
  border-radius: var(--kk-radius-md);
  background:
      linear-gradient(180deg, rgba(255, 255, 255, 0.88), rgba(248, 249, 243, 0.78));
  border: 1px solid rgba(11, 26, 125, 0.08);
  animation: stat-in 0.7s var(--kk-ease-out) both;
  box-shadow:
      inset 0 1px 0 rgba(255, 255, 255, 0.9),
      0 14px 28px rgba(11, 26, 125, 0.09),
      0 3px 8px rgba(20, 24, 36, 0.045);
}

.stat-card:nth-child(1) {
  animation-delay: 80ms;
}

.stat-card:nth-child(2) {
  animation-delay: 140ms;
}

.stat-card:nth-child(3) {
  animation-delay: 200ms;
}

.stat-card:nth-child(4) {
  animation-delay: 260ms;
}

.stat-card-value {
  margin: 0.55rem 0 0;
  font-family: var(--kk-font-display);
  font-size: 1.65rem;
  font-weight: 700;
  line-height: 1.15;
  color: var(--kk-color-primary);
  font-variant-numeric: tabular-nums;
  letter-spacing: 0;
}

.stat-card-value--text {
  font-size: 1.05rem;
  font-weight: 700;
  line-height: 1.35;
  overflow: hidden;
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.stat-card-label {
  margin: 0.25rem 0 0;
  font-size: 0.78rem;
  font-weight: 700;
  color: var(--kk-color-text-muted);
}

.stat-card-hint {
  margin: 0.22rem 0 0;
  font-size: 0.72rem;
  line-height: 1.35;
  color: var(--kk-color-text-subtle);
}

.analytics-card {
  min-width: 0;
  padding: 0.85rem 0.95rem;
  border-radius: var(--kk-radius-md);
  background:
      linear-gradient(180deg, rgba(255, 255, 255, 0.86), rgba(247, 248, 241, 0.76));
  border: 1px solid rgba(11, 26, 125, 0.08);
  box-shadow:
      inset 0 1px 0 rgba(255, 255, 255, 0.88),
      0 18px 34px rgba(11, 26, 125, 0.1),
      0 4px 10px rgba(20, 24, 36, 0.04);
  animation: stat-in 0.58s var(--kk-ease-out) both;
}

.analytics-card--donut {
  animation-delay: 320ms;
}

.analytics-card--rank {
  animation-delay: 380ms;
  display: flex;
  flex-direction: column;
  min-height: 100%;
}

.analytics-card--rank .github-heatmap {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.analytics-card--rank .github-heatmap-grid {
  flex: 1;
}

.analytics-card--growth {
  animation-delay: 440ms;
}

.analytics-card-head {
  display: flex;
  align-items: center;
  gap: 0.7rem;
  margin-bottom: 0.65rem;
}

.analytics-card-head p {
  margin: 0;
  color: var(--kk-color-text-secondary);
  font-size: 0.82rem;
  font-weight: 800;
}

.analytics-range {
  margin-left: auto;
  padding: 0.12rem 0.4rem;
  border-radius: var(--kk-radius-sm);
  background: rgba(184, 148, 31, 0.12);
  color: var(--kk-color-accent-text);
  font-size: 0.68rem;
  font-weight: 800;
  white-space: nowrap;
}

.donut-row {
  display: grid;
  grid-template-columns: 5.4rem minmax(0, 1fr);
  align-items: center;
  gap: 0.8rem;
}

.problem-donut {
  width: 5.4rem;
  aspect-ratio: 1;
  border-radius: 50%;
  display: grid;
  place-items: center;
  position: relative;
  box-shadow:
      0 14px 24px rgba(11, 26, 125, 0.13),
      inset 0 0 0 1px rgba(11, 26, 125, 0.08);
}

.problem-donut::after {
  content: '';
  position: absolute;
  inset: 1rem;
  border-radius: 50%;
  background: #fff;
  box-shadow: inset 0 2px 8px rgba(11, 26, 125, 0.08);
}

.problem-donut span,
.problem-donut small {
  position: relative;
  z-index: 1;
}

.problem-donut span {
  align-self: end;
  font-family: var(--kk-font-display);
  font-size: 1.2rem;
  font-weight: 800;
  line-height: 1;
  color: var(--kk-color-primary);
}

.problem-donut small {
  align-self: start;
  margin-top: 0.12rem;
  font-size: 0.62rem;
  font-weight: 700;
  color: var(--kk-color-text-subtle);
}

.donut-legend {
  display: grid;
  gap: 0.32rem;
  min-width: 0;
}

.donut-legend-row {
  display: grid;
  grid-template-columns: 0.55rem minmax(0, 1fr) 1.8rem;
  align-items: center;
  gap: 0.45rem;
}

.donut-swatch {
  width: 0.55rem;
  height: 0.55rem;
  border-radius: 50%;
  box-shadow: 0 0 0 2px rgba(255, 255, 255, 0.72);
}

.donut-name {
  min-width: 0;
  color: var(--kk-color-text-secondary);
  font-size: 0.74rem;
  font-weight: 700;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.donut-count {
  color: var(--kk-color-text-subtle);
  font-family: var(--kk-font-mono);
  font-size: 0.7rem;
  font-weight: 600;
  text-align: right;
}

.chart-empty {
  margin: 0;
  color: var(--kk-color-text-subtle);
  font-size: 0.78rem;
  line-height: 1.45;
}

.github-heatmap {
  display: flex;
  flex-direction: column;
  gap: 0.65rem;
}

.github-heatmap-meta {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 0.75rem;
  flex-wrap: wrap;
  flex-shrink: 0;
}

.github-heatmap-summary {
  display: flex;
  align-items: baseline;
  gap: 0.4rem;
  min-width: 0;
}

.github-heatmap-summary strong {
  font-family: var(--kk-font-display);
  font-size: 1.55rem;
  line-height: 1;
  font-weight: 800;
  color: var(--kk-color-success);
  font-variant-numeric: tabular-nums;
}

.github-heatmap-summary span {
  color: var(--kk-color-text-muted);
  font-size: 0.78rem;
  font-weight: 700;
  white-space: nowrap;
}

.github-heatmap-grid {
  display: grid;
  grid-template-columns: repeat(10, minmax(0, 1fr));
  grid-template-rows: repeat(3, minmax(0, 1fr));
  grid-auto-flow: row;
  gap: 3px;
  width: 100%;
  min-height: 4.75rem;
  min-width: 0;
}

.github-heat-cell {
  min-width: 0;
  min-height: 0;
  border-radius: 3px;
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

.github-heatmap-scale {
  display: inline-flex;
  align-items: center;
  gap: 0.22rem;
  color: var(--kk-color-text-subtle);
  font-size: 0.64rem;
  font-weight: 700;
}

.github-heatmap-scale .github-heat-cell {
  display: block;
  flex: 0 0 auto;
  width: 0.62rem;
  height: 0.62rem;
}

.growth-load {
  display: flex;
  align-items: baseline;
  gap: 0.4rem;
  margin-top: 0.1rem;
}

.growth-load-value {
  font-family: var(--kk-font-display);
  font-size: 1.8rem;
  font-weight: 800;
  line-height: 1;
  color: var(--kk-color-primary);
  font-variant-numeric: tabular-nums;
}

.growth-load-copy {
  color: var(--kk-color-text-muted);
  font-size: 0.76rem;
  font-weight: 700;
}

.growth-stack {
  display: flex;
  height: 0.72rem;
  margin-top: 0.7rem;
  border-radius: var(--kk-radius-pill);
  background: rgba(11, 26, 125, 0.07);
  overflow: hidden;
  box-shadow: inset 0 1px 2px rgba(11, 26, 125, 0.08);
}

.growth-stack-seg {
  min-width: 0;
  transition: width var(--kk-duration-slow) var(--kk-ease-out);
}

.growth-stack-seg--unfamiliar,
.growth-legend-item--unfamiliar::before {
  background: var(--kk-color-danger);
}

.growth-stack-seg--fuzzy,
.growth-legend-item--fuzzy::before {
  background: var(--kk-color-accent);
}

.growth-stack-seg--mastered,
.growth-legend-item--mastered::before {
  background: var(--kk-color-success);
}

.growth-legend {
  display: flex;
  flex-wrap: wrap;
  gap: 0.35rem 0.55rem;
  margin-top: 0.65rem;
}

.growth-legend-item {
  display: inline-flex;
  align-items: center;
  gap: 0.28rem;
  color: var(--kk-color-text-muted);
  font-size: 0.7rem;
  font-weight: 700;
}

.growth-legend-item::before {
  content: '';
  width: 0.44rem;
  height: 0.44rem;
  border-radius: 50%;
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
  position: relative;
  display: grid;
  grid-template-columns: 2rem minmax(0, 1fr);
  column-gap: 0.75rem;
  padding: 0.86rem 1rem 0.92rem;
  margin-bottom: 0;
  border-top: 1px solid rgba(11, 26, 125, 0.07);
  border-bottom: none;
  background: transparent;
  border-left: none;
  border-radius: 0;
  animation: stat-in 0.55s var(--kk-ease-out) both;
  animation-delay: var(--item-delay, 0ms);
}

.recent-item:last-of-type {
  padding-bottom: 0.92rem;
}

.recent-index {
  grid-row: 1 / span 3;
  align-self: start;
  width: 2rem;
  height: 2rem;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--kk-radius-sm);
  background: rgba(11, 26, 125, 0.055);
  color: var(--kk-color-primary);
  font-family: var(--kk-font-mono);
  font-size: 0.72rem;
  font-weight: 600;
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
  grid-column: 2;
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
  grid-column: 2;
  margin-top: 0.55rem;
}

.recent-tags span {
  padding: 0.14rem 0.48rem;
  border-radius: var(--kk-radius-sm);
  background: rgba(243, 236, 212, 0.74);
  border: 1px solid rgba(184, 148, 31, 0.12);
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
  padding: 0.75rem 1rem 1rem;
}

.dashboard-empty-text {
  margin: 0;
  color: var(--kk-color-text-muted);
  font-size: 0.92rem;
}

@media (max-width: 1200px) {
  .pillars-panel {
    grid-template-columns: repeat(2, 1fr);
  }

  .dashboard-analytics {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .analytics-card--growth {
    grid-column: 1 / -1;
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

  .dashboard-panels {
    grid-template-columns: 1fr;
    gap: 1.25rem;
  }

  .dashboard-analytics {
    grid-template-columns: 1fr;
  }

  .analytics-card--growth {
    grid-column: auto;
  }

  .stat-grid {
    grid-template-columns: 1fr 1fr;
  }
}

@media (max-width: 560px) {
  .dashboard-head {
    flex-direction: column;
    align-items: stretch;
  }

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

  .stat-grid {
    grid-template-columns: 1fr;
  }

  .donut-row {
    grid-template-columns: 4.8rem minmax(0, 1fr);
  }

  .problem-donut {
    width: 4.8rem;
  }

  .github-heatmap-meta {
    flex-direction: column;
    align-items: flex-start;
    gap: 0.45rem;
  }
}
</style>
