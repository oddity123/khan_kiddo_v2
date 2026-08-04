<script setup lang="ts">
import {ArrowRight} from '@element-plus/icons-vue'
import {ElMessage} from 'element-plus'
import {computed, onMounted, ref} from 'vue'
import {useRouter} from 'vue-router'

import {fetchHomePage} from '@/api/home'
import type {HomePageResponse} from '@/types/home'
import {getErrorMessage} from '@/utils/error'

const router = useRouter()
const loading = ref(true)
const home = ref<HomePageResponse | null>(null)

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

async function loadHome() {
  loading.value = true
  try {
    const {data} = await fetchHomePage()
    home.value = data
  } catch (error) {
    home.value = null
    ElMessage.error(getErrorMessage(error, '加载复盘数据失败'))
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  void loadHome()
})
</script>

<template>
  <div class="review-center" v-loading="loading">
    <header class="page-head">
      <div class="head-copy">
        <p class="page-eyebrow">Review Center</p>
        <h1 class="page-title">复盘中心</h1>
        <p class="page-desc">把最近练习沉淀成可行动的复盘，并用右下角助手追问薄弱点。</p>
      </div>
      <button type="button" class="dashboard-link" @click="router.push('/conversation/analyze')">
        去分析
        <el-icon><ArrowRight/></el-icon>
      </button>
    </header>

    <template v-if="home?.authenticated && home.analysisStats">
    <section class="dashboard">
      <header class="dashboard-head">
        <div class="dashboard-head-copy">
          <h2 class="dashboard-title">学习概览</h2>
          <p class="dashboard-lead">最近练习与薄弱点一览</p>
        </div>
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
    </template>

    <section v-else-if="!loading" class="dashboard dashboard--empty">
      <h2 class="dashboard-title">还没有复盘数据</h2>
      <p class="dashboard-lead">完成一次对话分析后，这里会汇总你的练习趋势、薄弱点与最近纠正。</p>
      <button type="button" class="btn-primary" @click="router.push('/conversation/analyze')">
        开始分析
      </button>
    </section>
  </div>
</template>

<style scoped>
.review-center {
  font-family: var(--kk-font-body);
  color: var(--kk-color-text);
  display: flex;
  flex-direction: column;
  gap: 1rem;
  padding-bottom: 1rem;
}

.page-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 1rem;
}

.head-copy {
  min-width: 0;
}

.page-eyebrow {
  margin: 0 0 0.2rem;
  font-family: var(--kk-font-mono);
  font-size: 0.72rem;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: var(--kk-color-text-subtle);
}

.page-title {
  margin: 0 0 0.25rem;
  font-family: var(--kk-font-display);
  font-size: clamp(1.35rem, 2.6vw, 1.85rem);
  font-weight: 800;
  color: var(--kk-color-primary);
  line-height: 1.2;
}

.page-desc {
  margin: 0;
  font-size: 0.9rem;
  color: var(--kk-color-text-muted);
  line-height: 1.55;
}

.dashboard--empty {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 0.75rem;
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
  .dashboard-analytics {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .analytics-card--growth {
    grid-column: 1 / -1;
  }
}

@media (max-width: 992px) {
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
