<script setup lang="ts">
import {
  ArrowLeft,
  ArrowRight,
  ChatDotRound,
  Clock,
  Cpu,
  DataAnalysis,
  Delete,
  Document,
  Flag,
  Medal,
  PieChart,
  Reading,
  Timer,
  TrendCharts,
} from '@element-plus/icons-vue'
import {ElMessage, ElMessageBox} from 'element-plus'
import {computed, nextTick, onBeforeUnmount, onMounted, ref, watch} from 'vue'
import {useRoute, useRouter} from 'vue-router'

import {deleteConversationAnalysis, getConversationAnalysisDetail,} from '@/api/conversationAnalysis'
import {deleteGrowthCard, retryMintGrowthCards} from '@/api/growthCard'
import ActionCardsPanel from '@/components/conversation/ActionCardsPanel.vue'
import ChineseExpressionFan from '@/components/conversation/ChineseExpressionFan.vue'
import ErrorTypePieChart from '@/components/conversation/ErrorTypePieChart.vue'
import PerformanceDimensionBars from '@/components/conversation/PerformanceDimensionBars.vue'
import SentenceAnalysisCard from '@/components/conversation/SentenceAnalysisCard.vue'
import TopHabitHero from '@/components/conversation/TopHabitHero.vue'
import type {
  AnalysisItem,
  ChineseExpressionItem,
  ConversationAnalysisDetail,
  ErrorTypeDistribution,
} from '@/types/conversation'
import type {GrowthCard} from '@/types/growthCard'
import {displayTypeLabel, formatProcessingTime, resolvePerformanceScore, sortItemsByPriority,} from '@/utils/analysisDisplay'
import {getErrorMessage} from '@/utils/error'

const route = useRoute()
const router = useRouter()

const loading = ref(true)
const detail = ref<ConversationAnalysisDetail | null>(null)
const pageReady = ref(false)

const analysisId = computed(() => String(route.params.id ?? ''))
const summaryReport = computed(() => detail.value?.educationalSummary?.report)

const overallStats = computed(() => summaryReport.value?.overallStats)

const sortedItems = computed(() => {
  const items = detail.value?.items ?? []
  return sortItemsByPriority(items)
})

const topHabit = computed(() => detail.value?.topHabit)

const habitGrowthMintStatus = computed(() => detail.value?.habitGrowthMintStatus ?? 'none')
const growthCards = computed((): GrowthCard[] => detail.value?.growthCards ?? [])
const asideTab = ref<'summary' | 'cards'>('summary')
const growthCardCountLabel = computed(() => {
  const n = growthCards.value.length
  return n > 0 ? `卡片库 · ${n}` : '卡片库'
})

const MINT_POLL_INTERVAL_MS = 1500
const MINT_POLL_MAX = 10
let mintPollTimer: ReturnType<typeof setTimeout> | null = null
let mintPollCount = 0
const retryingMint = ref(false)

function clearMintPoll() {
  if (mintPollTimer) {
    clearTimeout(mintPollTimer)
    mintPollTimer = null
  }
  mintPollCount = 0
}

async function refreshDetailSilent() {
  if (!analysisId.value) {
    return undefined
  }
  try {
    const {data} = await getConversationAnalysisDetail(analysisId.value)
    detail.value = data
    return data
  } catch {
    return undefined
  }
}

function scheduleMintPoll() {
  clearMintPoll()
  if (habitGrowthMintStatus.value !== 'pending') {
    return
  }
  const poll = async () => {
    mintPollCount += 1
    const data = await refreshDetailSilent()
    if (data?.habitGrowthMintStatus !== 'pending' || mintPollCount >= MINT_POLL_MAX) {
      clearMintPoll()
      return
    }
    mintPollTimer = setTimeout(poll, MINT_POLL_INTERVAL_MS)
  }
  mintPollTimer = setTimeout(poll, MINT_POLL_INTERVAL_MS)
}

async function onRetryMint() {
  if (!analysisId.value || retryingMint.value) {
    return
  }
  retryingMint.value = true
  try {
    await retryMintGrowthCards(analysisId.value)
    if (detail.value) {
      detail.value = {
        ...detail.value,
        habitGrowthMintStatus: 'pending',
        habitGrowthCard: undefined,
        growthCards: [],
      }
    }
    asideTab.value = 'cards'
    scheduleMintPoll()
    ElMessage.success('已开始重新生成成长卡')
  } catch (error) {
    ElMessage.error(getErrorMessage(error, '重新生成失败'))
  } finally {
    retryingMint.value = false
  }
}

// rank 1 已由 topHabit 独占展示为 Hero，面板只需 rank>1，避免重复
const actionCards = computed(() => (detail.value?.actionCards ?? []).filter((card) => card.rank > 1))
const hasHabitFocus = computed(() => Boolean(topHabit.value || actionCards.value.length))

async function onHabitCardGenerated() {
  asideTab.value = 'cards'
  const beforeIds = new Set(growthCards.value.map((card) => card.cardId))
  const insertAt = growthFanRef.value?.getActiveIndex?.() ?? 0
  await refreshDetailSilent()
  await nextTick()
  const added = (detail.value?.growthCards ?? []).filter((card) => !beforeIds.has(card.cardId))
  if (!added.length) {
    return
  }
  // watch 可能已把新卡 append 到末尾；再挪到当前复习位之上
  let order = fanOrderIds.value.filter((id) =>
      (detail.value?.growthCards ?? []).some((card) => card.cardId === id),
  )
  for (const card of added) {
    order = order.filter((id) => id !== card.cardId)
    order.splice(Math.min(insertAt, order.length), 0, card.cardId)
  }
  fanOrderIds.value = order
  await nextTick()
  await nextTick()
  // 现有「滑出再飞回」动画，表现新卡落到当前顶位
  await growthFanRef.value?.playInsertRestore()
}

async function onDeleteGrowthCard(cardId: string) {
  await deleteGrowthCard(cardId)
  if (!detail.value) {
    return
  }
  const remaining = (detail.value.growthCards ?? []).filter((card) => card.cardId !== cardId)
  const habitLeft = remaining.find((card) => card.type === 'habit')
  if (fanOrderIds.value.length) {
    fanOrderIds.value = fanOrderIds.value.filter((id) => id !== cardId)
  }
  detail.value = {
    ...detail.value,
    growthCards: remaining,
    habitGrowthCard: habitLeft,
    habitGrowthMintStatus: habitLeft
        ? 'ready'
        : detail.value.topHabit
            ? 'failed'
            : 'none',
  }
  ElMessage.success('已删除成长卡')
}

interface FilterChip {
  key: string
  label: string
}

// 从证据里收集 pointId/familyId 作为筛选维度，兜底用 type 保证旧数据也能筛
function chipKey(item: AnalysisItem): string[] {
  return (item.errors ?? []).map((err) => err.pointId || err.familyId || err.type)
}

const filterChips = computed((): FilterChip[] => {
  const seen = new Map<string, string>()
  for (const item of sortedItems.value) {
    for (const err of item.errors ?? []) {
      const key = err.pointId || err.familyId || err.type
      if (key && !seen.has(key)) {
        seen.set(key, displayTypeLabel(err.type))
      }
    }
  }
  return Array.from(seen.entries()).map(([key, label]) => ({key, label}))
})

const activeFilter = ref<string | null>(null)

const filteredItems = computed(() => {
  if (!activeFilter.value) {
    return sortedItems.value
  }
  return sortedItems.value.filter((item) => chipKey(item).includes(activeFilter.value as string))
})

function toggleFilter(key: string) {
  activeFilter.value = activeFilter.value === key ? null : key
}

const sentencesFoldRef = ref<HTMLDetailsElement | null>(null)

function locate(sentenceId: string | number) {
  activeFilter.value = null
  if (sentencesFoldRef.value) {
    sentencesFoldRef.value.open = true
  }
  nextTick(() => {
    document
        .getElementById(`sentence-${sentenceId}`)
        ?.scrollIntoView({behavior: 'smooth', block: 'center'})
  })
}

const chineseExpressionCount = computed(() => {
  if (overallStats.value?.chineseExpressionCount != null) {
    return overallStats.value.chineseExpressionCount
  }
  const fromDetail = detail.value?.chineseExpressions?.length ?? 0
  const fromSummary = detail.value?.educationalSummary?.chineseExpressions?.length ?? 0
  return fromDetail || fromSummary
})

function growthCardTypeLabel(type: string): string {
  return type === 'habit' ? '习惯' : type === 'vocab' ? '词汇' : type
}

/** 侧栏牌序：新制卡插到当前卡之上；默认按创建时间新→旧 */
const fanOrderIds = ref<string[]>([])

function sortGrowthCardsNewestFirst(cards: GrowthCard[]): GrowthCard[] {
  return [...cards].sort((a, b) => {
    const ta = a.createdAt ? Date.parse(a.createdAt) : 0
    const tb = b.createdAt ? Date.parse(b.createdAt) : 0
    if (tb !== ta) {
      return tb - ta
    }
    return b.cardId.localeCompare(a.cardId)
  })
}

function syncFanOrderFromCards(cards: GrowthCard[]) {
  const ids = cards.map((card) => card.cardId)
  const idSet = new Set(ids)
  if (fanOrderIds.value.length === 0) {
    fanOrderIds.value = sortGrowthCardsNewestFirst(cards).map((card) => card.cardId)
    return
  }
  const kept = fanOrderIds.value.filter((id) => idSet.has(id))
  const known = new Set(kept)
  for (const id of sortGrowthCardsNewestFirst(cards).map((card) => card.cardId)) {
    if (!known.has(id)) {
      kept.push(id)
    }
  }
  fanOrderIds.value = kept
}

watch(
    growthCards,
    (cards) => {
      syncFanOrderFromCards(cards)
    },
    {immediate: true},
)

/** 复用知识卡片 Fan：按 fanOrder 排列（新卡可插在当前卡之上） */
const growthFanItems = computed((): ChineseExpressionItem[] => {
  const cards = growthCards.value
  const byId = new Map(cards.map((card) => [card.cardId, card]))
  const order = fanOrderIds.value.length
      ? fanOrderIds.value
      : sortGrowthCardsNewestFirst(cards).map((card) => card.cardId)
  return order
      .map((id) => byId.get(id))
      .filter((card): card is GrowthCard => Boolean(card))
      .map((card, index) => ({
        cardKey: card.cardId,
        originalIndex: index,
        originalSentence: card.front,
        focusPhrase: card.type === 'vocab' ? card.front : undefined,
        suggestion: card.back,
        kindLabel: growthCardTypeLabel(card.type),
      }))
})

const growthFanRef = ref<{
  playInsertRestore: () => Promise<void>
  getActiveIndex: () => number
} | null>(null)

const englishPracticeCount = computed(() => {
  const total = overallStats.value?.totalSentences ?? sortedItems.value.length + chineseExpressionCount.value
  return Math.max(0, total - chineseExpressionCount.value)
})

const revisionCount = computed(() =>
    sortedItems.value.reduce((sum, item) => sum + (item.errors?.length ?? 0), 0),
)

const performanceScore = computed(() => {
  const st = overallStats.value
  const issues = st?.totalIssues ?? revisionCount.value
  const sentences = st?.totalSentences ?? sortedItems.value.length
  return resolvePerformanceScore(st, issues, sentences)
})

const dimensionScores = computed(() => overallStats.value?.dimensionScores)

const mainCategory = computed(() => overallStats.value?.mainCategory)

// 有语法家族分布则优先展示（颗粒度更贴近行动卡），旧数据回退 errorTypeDistribution
const pieDistribution = computed((): ErrorTypeDistribution[] => {
  const family = detail.value?.familyDistribution
  if (family?.length) {
    return family.map((item) => ({type: item.titleZh, count: item.count}))
  }
  return detail.value?.errorTypeDistribution ?? []
})

function formatTime(value?: string) {
  if (!value) {
    return '—'
  }
  return value.replace('T', ' ').slice(0, 19)
}

async function loadDetail() {
  if (!analysisId.value) {
    return
  }
  loading.value = true
  pageReady.value = false
  fanOrderIds.value = []
  clearMintPoll()
  try {
    const {data} = await getConversationAnalysisDetail(analysisId.value)
    detail.value = data
    fanOrderIds.value = sortGrowthCardsNewestFirst(data.growthCards ?? []).map((card) => card.cardId)
    requestAnimationFrame(() => {
      pageReady.value = true
    })
    if (data.habitGrowthMintStatus === 'pending') {
      asideTab.value = 'cards'
      scheduleMintPoll()
    } else if ((data.growthCards?.length ?? 0) > 0) {
      // keep summary as default unless user already switched
    }
  } catch (error) {
    detail.value = null
    ElMessage.error(getErrorMessage(error, '加载详情失败'))
  } finally {
    loading.value = false
  }
}

async function onDelete() {
  if (!analysisId.value) {
    return
  }
  try {
    await ElMessageBox.confirm('确定删除这条分析记录？', '删除确认', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  try {
    await deleteConversationAnalysis(analysisId.value)
    ElMessage.success('已删除')
    await router.replace('/conversation/analyses')
  } catch (error) {
    ElMessage.error(getErrorMessage(error, '删除失败'))
  }
}

onMounted(loadDetail)
watch(analysisId, loadDetail)
onBeforeUnmount(clearMintPoll)
</script>

<template>
  <div
      v-loading="loading"
      class="detail-page"
      :class="{ 'detail-page--ready': pageReady }"
  >
    <header class="detail-topbar kk-glass">
      <router-link to="/conversation/analyses" class="back-link">
        <el-icon><ArrowLeft/></el-icon>
        返回列表
      </router-link>
      <h1 class="topbar-title">对话分析详情</h1>
      <el-button type="danger" plain :icon="Delete" @click="onDelete">删除</el-button>
    </header>

    <template v-if="detail">
      <el-alert
          v-if="detail.status === 'failed' && detail.errorMessage"
          class="detail-error-alert"
          type="error"
          :title="'分析未完成'"
          :description="detail.errorMessage"
          show-icon
          :closable="false"
      />
      <div class="detail-grid">
        <main class="detail-main">
          <section
              v-if="topHabit || actionCards.length"
              class="habit-ladder kk-glass kk-glass--panel"
              aria-label="本场优先改的说话习惯"
          >
            <header class="habit-ladder-head">
              <h2 class="habit-ladder-title">本场优先改</h2>
            </header>

            <TopHabitHero
                v-if="topHabit"
                :card="topHabit"
                :mint-status="habitGrowthMintStatus"
                @locate="locate"
                @open-cards="asideTab = 'cards'"
            />

            <ActionCardsPanel
                :cards="actionCards"
                :analysis-id="analysisId"
                :growth-cards="growthCards"
                @locate="locate"
                @generated="onHabitCardGenerated"
            />
          </section>

          <details ref="sentencesFoldRef" class="sentences-fold raw-fold kk-glass kk-glass--panel">
            <summary>
              <el-icon class="fold-chevron"><ArrowRight/></el-icon>
              <el-icon>
                <ChatDotRound/>
              </el-icon>
              句子级检查（证据）· {{ sortedItems.length }} 句
            </summary>

            <div class="sentences-fold-body">
              <p v-if="englishPracticeCount > 0" class="section-subtitle">
                已分析 {{ englishPracticeCount }} 句纯英文表达
              </p>

              <div v-if="filterChips.length" class="filter-chip-row">
                <button
                    v-for="chip in filterChips"
                    :key="chip.key"
                    type="button"
                    class="filter-chip"
                    :class="{ 'filter-chip--active': activeFilter === chip.key }"
                    @click="toggleFilter(chip.key)"
                >
                  {{ chip.label }}
                </button>
              </div>

              <el-empty
                  v-if="!sortedItems.length"
                  :description="detail.status === 'failed'
                    ? '分析过程中出错，可查看下方原始对话'
                    : '恭喜！暂未发现需要优化的表达'"
              />
              <el-empty
                  v-else-if="!filteredItems.length"
                  description="没有匹配当前筛选的句子"
              />
              <SentenceAnalysisCard
                  v-for="(item, idx) in filteredItems"
                  :id="`sentence-${item.sentenceId}`"
                  :key="item.sentenceId ?? item.originalSentence"
                  :item="item"
                  :index="idx"
              />
            </div>
          </details>

          <details v-if="detail.conversationContent" class="raw-fold kk-glass kk-glass--panel">
            <summary>
              <el-icon class="fold-chevron"><ArrowRight/></el-icon>
              <el-icon>
                <Document/>
              </el-icon>
              原始对话内容
            </summary>
            <pre class="content-pre">{{ detail.conversationContent }}</pre>
          </details>
        </main>

        <aside class="detail-aside">
          <div class="aside-tabs" role="tablist" aria-label="侧栏视图">
            <button
                type="button"
                role="tab"
                class="aside-tab"
                :class="{ 'aside-tab--active': asideTab === 'summary' }"
                :aria-selected="asideTab === 'summary'"
                @click="asideTab = 'summary'"
            >
              会话概要
            </button>
            <button
                type="button"
                role="tab"
                class="aside-tab"
                :class="{ 'aside-tab--active': asideTab === 'cards' }"
                :aria-selected="asideTab === 'cards'"
                @click="asideTab = 'cards'"
            >
              {{ growthCardCountLabel }}
            </button>
          </div>

          <section
              v-show="asideTab === 'summary'"
              class="summary-panel kk-glass kk-glass--panel"
              role="tabpanel"
          >
            <div class="summary-top">
              <div class="summary-badge" aria-label="综合口语自然度得分">
                <span class="summary-badge-icon-wrap" aria-hidden="true">
                  <el-icon class="summary-badge-icon"><Medal/></el-icon>
                </span>
                <div class="summary-badge-body">
                  <span class="summary-badge-score">{{ performanceScore }}</span>
                  <span class="summary-badge-caption">综合自然度</span>
                </div>
              </div>
              <div class="summary-mini-cards">
                <article class="summary-mini-card summary-mini-card--challenge">
                  <span class="summary-mini-card-icon summary-mini-card-icon--accent" aria-hidden="true">
                    <el-icon><Flag/></el-icon>
                  </span>
                  <div class="summary-mini-card-body">
                    <span class="summary-mini-card-label">主要挑战</span>
                    <p class="summary-mini-card-text">{{ mainCategory || '—' }}</p>
                  </div>
                </article>
                <div class="summary-mini-cards-row">
                  <article class="summary-mini-card summary-mini-card--depth">
                    <span class="summary-mini-card-icon" aria-hidden="true">
                      <el-icon><DataAnalysis/></el-icon>
                    </span>
                    <div class="summary-mini-card-body">
                      <span class="summary-mini-card-label">深度分析</span>
                      <p class="summary-mini-card-text summary-mini-card-text--stat">
                        <span class="summary-stat-line">
                          <strong>{{ englishPracticeCount }}</strong>句英文
                        </span>
                      </p>
                    </div>
                  </article>
                  <article
                      v-if="chineseExpressionCount > 0"
                      class="summary-mini-card summary-mini-card--cn"
                  >
                    <span class="summary-mini-card-icon summary-mini-card-icon--accent" aria-hidden="true">
                      <el-icon><ChatDotRound/></el-icon>
                    </span>
                    <div class="summary-mini-card-body">
                      <span class="summary-mini-card-label">中文表达</span>
                      <p class="summary-mini-card-text summary-mini-card-text--stat">
                        <span class="summary-stat-line">
                          <strong>{{ chineseExpressionCount }}</strong>处
                        </span>
                      </p>
                    </div>
                  </article>
                </div>
              </div>
            </div>

            <div
                v-if="dimensionScores || pieDistribution.length || summaryReport?.overallSummary?.levelSummary"
                class="summary-sections"
            >
              <section
                  v-if="dimensionScores"
                  class="summary-block"
                  aria-label="分项得分"
              >
                <h3 class="summary-block-title">
                  <span class="summary-block-title-icon summary-block-title-icon--dims" aria-hidden="true">
                    <el-icon><TrendCharts/></el-icon>
                  </span>
                  分项得分
                </h3>
                <PerformanceDimensionBars dense :scores="dimensionScores"/>
              </section>

              <section
                  v-if="pieDistribution.length"
                  class="summary-block"
                  aria-label="类型分布"
              >
                <h3 class="summary-block-title">
                  <span class="summary-block-title-icon summary-block-title-icon--chart" aria-hidden="true">
                    <el-icon><PieChart/></el-icon>
                  </span>
                  类型分布
                </h3>
                <ErrorTypePieChart
                    compact
                    legend-right
                    body-size="summary"
                    :size="136"
                    :items="pieDistribution"
                    :animate="pageReady"
                />
              </section>

              <section
                  v-if="summaryReport?.overallSummary?.levelSummary && !hasHabitFocus"
                  class="summary-block"
                  aria-label="整体总结"
              >
                <h3 class="summary-block-title">
                  <span class="summary-block-title-icon summary-block-title-icon--brief" aria-hidden="true">
                    <el-icon><Reading/></el-icon>
                  </span>
                  总结
                </h3>
                <p class="summary-body summary-body--text">
                  {{ summaryReport.overallSummary.levelSummary }}
                </p>
              </section>
            </div>

            <footer class="summary-meta">
              <span class="summary-meta-item">
                <el-icon><Clock/></el-icon>
                {{ formatTime(detail.createdAt) }}
              </span>
              <span v-if="detail.llmModelName" class="summary-meta-item">
                <el-icon><Cpu/></el-icon>
                {{ detail.llmModelName }}
              </span>
              <span class="summary-meta-item">
                <el-icon><Timer/></el-icon>
                {{ formatProcessingTime(detail.processingTimeMs) }}
              </span>
            </footer>
          </section>

          <section
              v-show="asideTab === 'cards'"
              class="growth-cards-tab"
              role="tabpanel"
              aria-label="卡片库"
          >
            <div
                v-if="habitGrowthMintStatus === 'pending'"
                class="growth-cards-status growth-cards-status--pending kk-glass kk-glass--panel"
            >
              成长卡生成中…
            </div>
            <div
                v-else-if="habitGrowthMintStatus === 'failed' && !growthCards.length"
                class="growth-cards-status growth-cards-status--failed kk-glass kk-glass--panel"
            >
              <p>成长卡生成失败</p>
              <el-button
                  size="small"
                  type="primary"
                  plain
                  :loading="retryingMint"
                  @click="onRetryMint"
              >
                重新生成
              </el-button>
            </div>
            <el-empty
                v-else-if="!growthCards.length"
                class="growth-cards-empty kk-glass kk-glass--panel"
                description="本场还没有生成成长卡"
                :image-size="64"
            />
            <template v-else>
              <ChineseExpressionFan
                  ref="growthFanRef"
                  layout="aside"
                  variant="growth"
                  heading="成长卡"
                  :show-hint="false"
                  :deletable="true"
                  :items="growthFanItems"
                  :remove-card="onDeleteGrowthCard"
              />
            </template>
          </section>
        </aside>
      </div>

    </template>
  </div>
</template>

<style scoped>
.detail-error-alert {
  margin-bottom: 1rem;
}

.detail-page {
  font-family: var(--kk-font-body);
  color: var(--kk-color-text);
  opacity: 0;
  /* 不用 transform：会破坏侧栏 position:sticky */
  transition: opacity 0.45s var(--kk-ease-out);
}

.detail-page--ready {
  opacity: 1;
}

.detail-topbar {
  display: grid;
  grid-template-columns: auto 1fr auto;
  align-items: center;
  gap: 1rem;
  padding: 0.75rem 1.1rem;
  margin-bottom: 1.25rem;
  border-radius: var(--kk-radius-lg);
  position: sticky;
  top: 0.5rem;
  z-index: 20;
}

.back-link {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  color: var(--kk-color-primary);
  font-weight: 600;
  text-decoration: none;
  transition: color 0.2s ease, transform 0.2s ease;
}

.back-link:hover {
  color: var(--kk-color-accent);
  transform: translateX(-2px);
}

.topbar-title {
  margin: 0;
  font-family: var(--kk-font-display);
  font-size: clamp(1.15rem, 2.5vw, 1.45rem);
  font-weight: 800;
  color: var(--kk-color-primary);
  text-align: center;
}

.detail-grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: 1.25rem;
  align-items: start;
}

.detail-main {
  display: flex;
  flex-direction: column;
  gap: 1.35rem;
  min-width: 0;
}

.habit-ladder {
  padding: 1.15rem 1.2rem 1.25rem;
  border-top: 2px solid var(--kk-color-accent);
}

.habit-ladder-head {
  margin-bottom: 0.85rem;
}

.habit-ladder-title {
  margin: 0;
  font-family: var(--kk-font-display);
  font-size: clamp(1.15rem, 2.2vw, 1.4rem);
  font-weight: 800;
  line-height: 1.25;
  color: var(--kk-color-primary);
}

.habit-ladder :deep(.ac-panel) {
  margin-top: 0.75rem;
}

.detail-aside {
  order: -1;
  display: flex;
  flex-direction: column;
  gap: 0.65rem;
  min-width: 0;
}

.aside-tabs {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0.3rem;
  padding: 0.3rem;
  border-radius: var(--kk-radius-pill);
  background: var(--kk-glass-inner-bg);
  border: 1px solid var(--kk-glass-inner-border);
}

.aside-tab {
  margin: 0;
  padding: 0.45rem 0.55rem;
  border: none;
  border-radius: var(--kk-radius-pill);
  background: transparent;
  color: var(--kk-color-text-muted);
  font-family: var(--kk-font-body);
  font-size: 0.8rem;
  font-weight: 700;
  cursor: pointer;
  transition: background 0.15s ease, color 0.15s ease;
}

.aside-tab:hover {
  color: var(--kk-color-primary);
}

.aside-tab--active {
  background: var(--kk-color-primary);
  color: #fff;
}

.growth-cards-tab {
  display: flex;
  flex-direction: column;
  gap: 0.65rem;
  min-width: 0;
}

.growth-cards-status,
.growth-cards-empty {
  margin: 0;
  padding: 0.85rem 0.95rem;
  font-size: 0.86rem;
  font-weight: 600;
  color: var(--kk-color-text-muted);
}

.growth-cards-status--failed {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 0.55rem;
}

.growth-cards-status--failed p {
  margin: 0;
  color: var(--kk-color-danger);
}

@media (min-width: 1024px) {
  .detail-grid {
    grid-template-columns: minmax(0, 1fr) minmax(17rem, 22rem);
  }

  .detail-aside {
    order: 0;
    position: sticky;
    top: calc(var(--kk-navbar-offset) + 0.65rem);
    max-height: calc(100dvh - var(--kk-navbar-offset) - 1.25rem);
    overflow-x: hidden;
    overflow-y: auto;
    overscroll-behavior: contain;
    z-index: 5;
    align-self: start;
    /* 给面板外阴影留空，避免 overflow 裁成底部硬边黑条 */
    padding: 0.15rem 0.4rem 1.85rem;
    margin-inline: -0.4rem;
  }

  /* 卡片滑走时放开裁切，让飞出层盖过侧栏与主栏 */
  .detail-aside:has(.cn-fan--lift) {
    overflow: visible;
    z-index: 60;
  }

  .detail-page:has(.cn-fan--lift) {
    position: relative;
    z-index: 50;
  }

  /* 侧栏内减弱外阴影：大模糊在 sticky + overflow 下仍易被裁切成脏边 */
  .detail-aside .kk-glass--panel {
    box-shadow:
      0 6px 18px color-mix(in srgb, var(--kk-color-primary) 7%, transparent),
      inset 0 1px 0 var(--kk-glass-highlight);
  }
}

.summary-panel {
  padding: 0.85rem 0.95rem;
}

.raw-fold {
  padding: 1rem 1.1rem;
}

.sentences-fold {
  margin-bottom: 0;
}

.sentences-fold-body {
  margin-top: 0.85rem;
}

.section-subtitle {
  margin: 0 0 0.15rem;
  font-size: 0.84rem;
  color: var(--kk-color-text-muted);
}

.filter-chip-row {
  display: flex;
  flex-wrap: wrap;
  gap: 0.45rem;
  margin: 0.65rem 0 1rem;
}

.filter-chip {
  padding: 0.28rem 0.75rem;
  border-radius: var(--kk-radius-pill);
  border: 1px solid var(--kk-glass-inner-border);
  background: var(--kk-glass-inner-bg);
  color: var(--kk-color-text-subtle);
  font-family: var(--kk-font-body);
  font-size: 0.78rem;
  font-weight: 600;
  cursor: pointer;
  transition: background 0.15s ease, color 0.15s ease, border-color 0.15s ease;
}

.filter-chip:hover {
  border-color: color-mix(in srgb, var(--kk-color-primary) 30%, transparent);
}

.filter-chip--active {
  background: var(--kk-color-primary);
  border-color: var(--kk-color-primary);
  color: #fff;
}

.summary-mini-cards {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 0.4rem;
  min-width: 0;
}

.summary-mini-cards-row {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0.4rem;
  min-width: 0;
}

.summary-mini-cards-row:has(> :only-child) {
  grid-template-columns: 1fr;
}

.summary-mini-card--depth,
.summary-mini-card--cn {
  padding: 0.38rem 0.4rem;
  gap: 0.32rem;
}

.summary-mini-card {
  display: flex;
  align-items: flex-start;
  gap: 0.4rem;
  padding: 0.38rem 0.48rem;
  min-height: 2.15rem;
  border-radius: var(--kk-radius-md);
  background: var(--kk-glass-inner-bg);
  border: 1px solid color-mix(in srgb, var(--kk-color-primary) 10%, var(--kk-glass-inner-border));
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.55);
}

.summary-mini-card--challenge {
  align-items: center;
}

.summary-mini-card-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 1.35rem;
  height: 1.35rem;
  flex-shrink: 0;
  margin-top: 0.08rem;
  border-radius: var(--kk-radius-sm);
  background: color-mix(in srgb, var(--kk-color-primary) 10%, white);
  color: var(--kk-color-primary-soft);
  font-size: 0.85rem;
}

.summary-mini-card--challenge .summary-mini-card-icon {
  margin-top: 0;
}

.summary-mini-card-icon--accent {
  background: color-mix(in srgb, var(--kk-color-accent) 22%, white);
  color: var(--kk-color-accent-text);
  border: 1px solid color-mix(in srgb, var(--kk-color-accent) 28%, transparent);
}

.summary-mini-card-body {
  display: flex;
  flex-direction: column;
  gap: 0.12rem;
  min-width: 0;
  flex: 1;
}

.summary-mini-card-label {
  font-size: var(--summary-body);
  font-weight: 700;
  line-height: 1.2;
  color: var(--kk-color-accent-text);
}

.summary-mini-card-text {
  margin: 0;
  font-family: var(--kk-font-display);
  font-size: var(--summary-body);
  font-weight: 700;
  line-height: 1.4;
  color: var(--kk-color-primary);
  word-break: break-word;
}

.summary-mini-card-text--stat {
  font-family: var(--kk-font-body);
  font-weight: 500;
  color: var(--kk-color-text-muted);
  line-height: 1.3;
  word-break: keep-all;
  overflow-wrap: normal;
}

.summary-stat-line {
  display: inline-flex;
  align-items: baseline;
  gap: 0.1rem;
  white-space: nowrap;
}

.summary-stat-line strong {
  font-family: var(--kk-font-display);
  font-size: var(--summary-heading);
  font-weight: 800;
  color: var(--kk-color-primary);
}

.summary-mini-card--depth .summary-mini-card-label,
.summary-mini-card--cn .summary-mini-card-label {
  white-space: nowrap;
}

.cn-panel {
  margin-bottom: 1.25rem;
}

@media (max-width: 1023px) {
  .summary-badge {
    flex: 1;
    min-width: 5.5rem;
  }
}

.raw-fold summary {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  cursor: pointer;
  font-weight: 700;
  color: var(--kk-color-primary);
  list-style: none;
}

.raw-fold summary::-webkit-details-marker {
  display: none;
}

.fold-chevron {
  flex-shrink: 0;
  color: var(--kk-color-text-subtle);
  transition: transform 0.2s var(--kk-ease-out);
}

.raw-fold[open] > summary .fold-chevron {
  transform: rotate(90deg);
}

@media (prefers-reduced-motion: reduce) {
  .fold-chevron {
    transition: none;
  }
}

.content-pre {
  margin: 0.75rem 0 0;
  padding: 0.85rem 1rem;
  border-radius: var(--kk-radius-md);
  background: var(--kk-glass-inner-bg);
  font-family: var(--kk-font-mono);
  font-size: 0.82rem;
  line-height: 1.55;
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 16rem;
  overflow-y: auto;
}

.summary-panel {
  --summary-heading: 0.88rem;
  --summary-body: 0.84rem;
  margin-bottom: 0.75rem;
  border-top: 2px solid var(--kk-color-accent);
  overflow: hidden;
}

.summary-top {
  display: flex;
  align-items: stretch;
  gap: 0.5rem;
  margin-bottom: 0.5rem;
}

.summary-badge {
  flex: 0 0 auto;
  align-self: stretch;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 0.35rem;
  min-width: 5.6rem;
  padding: 0.55rem 0.65rem;
  border-radius: var(--kk-radius-md);
  background: linear-gradient(
      145deg,
      color-mix(in srgb, var(--kk-color-accent) 18%, white),
      color-mix(in srgb, var(--kk-color-primary) 8%, white)
  );
  border: 1.5px solid color-mix(in srgb, var(--kk-color-accent) 45%, var(--kk-color-primary));
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.85),
  0 4px 14px color-mix(in srgb, var(--kk-color-primary) 12%, transparent);
}

.summary-badge-icon-wrap {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 1.85rem;
  height: 1.85rem;
  flex-shrink: 0;
  border-radius: 50%;
  background: linear-gradient(
      160deg,
      var(--kk-color-primary),
      var(--kk-color-primary-soft)
  );
  color: var(--kk-color-accent-soft);
  box-shadow: 0 2px 8px color-mix(in srgb, var(--kk-color-primary) 35%, transparent);
}

.summary-badge-icon {
  font-size: 1rem;
}

.summary-badge-body {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.1rem;
  min-width: 0;
  text-align: center;
}

.summary-badge-score {
  font-family: var(--kk-font-display);
  font-size: 1.55rem;
  font-weight: 900;
  line-height: 1;
  color: var(--kk-color-primary);
  font-variant-numeric: tabular-nums;
}

.summary-badge-caption {
  font-size: 0.72rem;
  font-weight: 700;
  color: var(--kk-color-accent-text);
  white-space: nowrap;
}

.summary-sections {
  display: flex;
  flex-direction: column;
  gap: 0.45rem;
  margin-top: 0.5rem;
  padding-top: 0.5rem;
  border-top: 1px solid color-mix(in srgb, var(--kk-color-primary) 16%, var(--kk-glass-inner-border));
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.45);
}

.summary-block {
  padding: 0.5rem 0.55rem;
  border-radius: var(--kk-radius-md);
  background: var(--kk-glass-inner-bg);
  border: 1px solid color-mix(in srgb, var(--kk-color-primary) 10%, var(--kk-glass-inner-border));
}

.summary-block-title {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  margin: 0 0 0.4rem;
  font-family: var(--kk-font-display);
  font-size: var(--summary-heading);
  font-weight: 700;
  line-height: 1.25;
  color: var(--kk-color-primary);
}

.summary-block-title-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 1.5rem;
  height: 1.5rem;
  flex-shrink: 0;
  border-radius: var(--kk-radius-sm);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.65);
}

.summary-block-title-icon .el-icon {
  font-size: 0.9rem;
}

.summary-block-title-icon--dims {
  background: linear-gradient(
      145deg,
      color-mix(in srgb, var(--kk-color-primary) 16%, white),
      color-mix(in srgb, var(--kk-color-primary) 8%, white)
  );
  color: var(--kk-color-primary);
  border: 1px solid color-mix(in srgb, var(--kk-color-primary) 18%, transparent);
}

.summary-block-title-icon--chart {
  background: linear-gradient(
      145deg,
      color-mix(in srgb, var(--kk-color-accent) 28%, white),
      var(--kk-color-accent-bg)
  );
  color: var(--kk-color-accent-text);
  border: 1px solid color-mix(in srgb, var(--kk-color-accent) 35%, transparent);
}

.summary-block-title-icon--brief {
  background: linear-gradient(
      145deg,
      color-mix(in srgb, var(--kk-color-success) 14%, white),
      color-mix(in srgb, var(--kk-color-success) 6%, white)
  );
  color: var(--kk-color-success);
  border: 1px solid color-mix(in srgb, var(--kk-color-success) 22%, transparent);
}

.summary-body--text {
  margin: 0;
  font-size: var(--summary-body);
  line-height: 1.55;
  color: var(--kk-color-text-muted);
}

.summary-meta {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  gap: 0.25rem 0.5rem;
  margin-top: 0.5rem;
  padding-top: 0.5rem;
  border-top: 1px solid color-mix(in srgb, var(--kk-color-primary) 16%, var(--kk-glass-inner-border));
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.45);
  font-family: var(--kk-font-mono);
  font-size: var(--summary-body);
  color: var(--kk-color-text-subtle);
}

.summary-meta-item {
  display: inline-flex;
  align-items: center;
  gap: 0.2rem;
}

@media (prefers-reduced-motion: reduce) {
  .detail-page {
    transition: none;
    opacity: 1;
    transform: none;
  }
}
</style>
