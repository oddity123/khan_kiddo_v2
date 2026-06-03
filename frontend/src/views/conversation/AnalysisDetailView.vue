<script setup lang="ts">
import {
  ArrowLeft,
  ChatDotRound,
  Clock,
  Delete,
  Document,
  EditPen,
  Medal,
  PieChart,
  Reading,
  Timer,
  TrendCharts,
} from '@element-plus/icons-vue'
import {ElMessage, ElMessageBox} from 'element-plus'
import {computed, onMounted, ref, watch} from 'vue'
import {useRoute, useRouter} from 'vue-router'

import {deleteConversationAnalysis, getConversationAnalysisDetail,} from '@/api/conversationAnalysis'
import ErrorTypePieChart from '@/components/conversation/ErrorTypePieChart.vue'
import PerformanceDimensionBars from '@/components/conversation/PerformanceDimensionBars.vue'
import SentenceAnalysisCard from '@/components/conversation/SentenceAnalysisCard.vue'
import type {ConversationAnalysisDetail} from '@/types/conversation'
import {formatProcessingTime, resolvePerformanceScore, sortItemsByPriority,} from '@/utils/analysisDisplay'
import {getErrorMessage} from '@/utils/error'

const route = useRoute()
const router = useRouter()

const loading = ref(true)
const detail = ref<ConversationAnalysisDetail | null>(null)
const pageReady = ref(false)

const analysisId = computed(() => String(route.params.id ?? ''))
const summaryReport = computed(() => detail.value?.educationalSummary?.report)

const sortedItems = computed(() => {
  const items = detail.value?.items ?? []
  return sortItemsByPriority(items)
})

const revisionCount = computed(() =>
    sortedItems.value.reduce((sum, item) => sum + (item.errors?.length ?? 0), 0),
)

const overallStats = computed(() => summaryReport.value?.overallStats)

const performanceScore = computed(() => {
  const st = overallStats.value
  const issues = st?.totalIssues ?? revisionCount.value
  const sentences = st?.totalSentences ?? sortedItems.value.length
  return resolvePerformanceScore(st, issues, sentences)
})

const dimensionScores = computed(() => overallStats.value?.dimensionScores)

const mainCategory = computed(() => overallStats.value?.mainCategory)

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
  try {
    const {data} = await getConversationAnalysisDetail(analysisId.value)
    detail.value = data
    requestAnimationFrame(() => {
      pageReady.value = true
    })
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
      <div class="detail-grid">
        <main class="detail-main">
          <section class="sentences-panel">
            <header class="sentences-head">
              <h2 class="section-title">
                <el-icon>
                  <ChatDotRound/>
                </el-icon>
                句子级检查
              </h2>
            </header>

            <el-empty
                v-if="!sortedItems.length"
                description="恭喜！暂未发现需要优化的表达"
            />
            <SentenceAnalysisCard
                v-for="(item, idx) in sortedItems"
                :key="item.sentenceId ?? item.originalSentence"
                :item="item"
                :index="idx"
            />
          </section>

          <details v-if="detail.conversationContent" class="raw-fold kk-glass kk-glass--panel">
            <summary>
              <el-icon>
                <Document/>
              </el-icon>
              原始对话内容
            </summary>
            <pre class="content-pre">{{ detail.conversationContent }}</pre>
          </details>
        </main>

        <aside class="detail-aside">
          <h2 class="summary-aside-title">会话概要</h2>
          <section class="summary-panel kk-glass kk-glass--panel">
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
              <div class="summary-kpis">
                <div class="summary-kpi">
                  <el-icon class="summary-kpi-icon">
                    <EditPen/>
                  </el-icon>
                  <span class="summary-kpi-text">
                    <strong>{{ overallStats?.totalIssues ?? revisionCount ?? '—' }}</strong>
                    优化点
                  </span>
                </div>
                <div class="summary-kpi">
                  <el-icon class="summary-kpi-icon">
                    <ChatDotRound/>
                  </el-icon>
                  <span class="summary-kpi-text">
                    <strong>{{ overallStats?.totalSentences ?? sortedItems.length ?? '—' }}</strong>
                    分析句
                  </span>
                </div>
              </div>
            </div>

            <div v-if="mainCategory" class="summary-challenge">
              <span class="summary-challenge-tag">主要挑战</span>
              <span class="summary-challenge-text">{{ mainCategory }}</span>
            </div>

            <div
                v-if="dimensionScores || detail.errorTypeDistribution?.length || summaryReport?.overallSummary?.levelSummary"
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
                  v-if="detail.errorTypeDistribution?.length"
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
                    :items="detail.errorTypeDistribution"
                    :animate="pageReady"
                />
              </section>

              <section
                  v-if="summaryReport?.overallSummary?.levelSummary"
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
              <span class="summary-meta-item">
                <el-icon><Timer/></el-icon>
                {{ formatProcessingTime(detail.processingTimeMs) }}
              </span>
            </footer>
          </section>
        </aside>
      </div>
    </template>
  </div>
</template>

<style scoped>
.detail-page {
  font-family: var(--kk-font-body);
  color: var(--kk-color-text);
  opacity: 0;
  transform: translateY(10px);
  transition: opacity 0.45s var(--kk-ease-out), transform 0.45s var(--kk-ease-out);
}

.detail-page--ready {
  opacity: 1;
  transform: translateY(0);
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

.detail-aside {
  order: -1;
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

@media (min-width: 1024px) {
  .detail-grid {
    grid-template-columns: minmax(0, 1fr) minmax(17rem, 22rem);
  }

  .detail-aside {
    order: 0;
    position: sticky;
    top: 5.5rem;
  }
}

.summary-panel {
  padding: 0.85rem 0.95rem;
}

.raw-fold {
  padding: 1rem 1.1rem;
}

.section-title {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  margin: 0;
  font-family: var(--kk-font-display);
  font-size: 1.2rem;
  font-weight: 700;
  color: var(--kk-color-primary);
}

.sentences-head {
  margin-bottom: 1rem;
  padding: 0 0.15rem;
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

.summary-aside-title {
  margin: 0;
  padding: 0 0.1rem;
  font-family: var(--kk-font-display);
  font-size: 1.2rem;
  font-weight: 700;
  color: var(--kk-color-primary);
  line-height: 1.2;
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
  gap: 0.65rem;
  margin-bottom: 0.5rem;
}

.summary-badge {
  flex: 0 0 auto;
  display: flex;
  align-items: center;
  gap: 0.5rem;
  min-width: 0;
  padding: 0.45rem 0.55rem 0.45rem 0.45rem;
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
  width: 2.1rem;
  height: 2.1rem;
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
  font-size: 1.15rem;
}

.summary-badge-body {
  display: flex;
  flex-direction: column;
  gap: 0.1rem;
  min-width: 0;
}

.summary-badge-score {
  font-family: var(--kk-font-display);
  font-size: 1.85rem;
  font-weight: 900;
  line-height: 1;
  color: var(--kk-color-primary);
  font-variant-numeric: tabular-nums;
}

.summary-badge-caption {
  font-size: var(--summary-body);
  font-weight: 700;
  color: var(--kk-color-accent-text);
  white-space: nowrap;
}

.summary-kpis {
  flex: 1;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 0.4rem;
  min-width: 0;
  padding-left: 0.55rem;
  border-left: 1px solid color-mix(in srgb, var(--kk-color-primary) 16%, transparent);
}

.summary-kpi {
  display: flex;
  align-items: center;
  gap: 0.35rem;
  font-size: var(--summary-body);
  color: var(--kk-color-text-muted);
  line-height: 1.3;
}

.summary-kpi-icon {
  flex-shrink: 0;
  font-size: 0.95rem;
  color: var(--kk-color-primary-soft);
}

.summary-kpi-text strong {
  font-family: var(--kk-font-display);
  font-size: var(--summary-heading);
  font-weight: 800;
  color: var(--kk-color-primary);
  margin-right: 0.2rem;
}

.summary-challenge {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 0.3rem 0.4rem;
  margin-bottom: 0.45rem;
  padding: 0.4rem 0.5rem;
  border-radius: var(--kk-radius-sm);
  background: var(--kk-color-accent-bg);
  border: 1px solid rgba(184, 148, 31, 0.28);
}

.summary-challenge-tag {
  font-size: var(--summary-body);
  font-weight: 700;
  color: var(--kk-color-accent-text);
}

.summary-challenge-text {
  font-size: var(--summary-body);
  font-weight: 700;
  font-family: var(--kk-font-display);
  color: var(--kk-color-primary);
  line-height: 1.4;
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
