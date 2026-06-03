<script setup lang="ts">
import {ArrowLeft, ChatDotRound, Clock, DataAnalysis, Delete, Document, EditPen, Timer,} from '@element-plus/icons-vue'
import {ElMessage, ElMessageBox} from 'element-plus'
import {computed, onMounted, ref, watch} from 'vue'
import {useRoute, useRouter} from 'vue-router'

import {deleteConversationAnalysis, getConversationAnalysisDetail,} from '@/api/conversationAnalysis'
import ErrorTypePieChart from '@/components/conversation/ErrorTypePieChart.vue'
import SentenceAnalysisCard from '@/components/conversation/SentenceAnalysisCard.vue'
import type {ConversationAnalysisDetail} from '@/types/conversation'
import {estimatePerformanceScore, formatProcessingTime, sortItemsByPriority,} from '@/utils/analysisDisplay'
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

const performanceScore = computed(() => {
  const st = summaryReport.value?.overallStats
  const issues = st?.totalIssues ?? revisionCount.value
  const sentences = st?.totalSentences ?? sortedItems.value.length
  return estimatePerformanceScore(issues, sentences)
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
          <section class="summary-panel kk-glass kk-glass--panel">
            <header class="summary-head">
              <h2 class="summary-title">会话概要</h2>
              <div class="summary-score" aria-label="综合表现得分">
                <span class="summary-score-num">{{ performanceScore }}</span>
                <span class="summary-score-label">综合得分</span>
              </div>
            </header>

            <div class="summary-metrics">
              <article class="metric-tile">
                <span class="metric-tile-icon metric-tile-icon--primary">
                  <el-icon><EditPen/></el-icon>
                </span>
                <div class="metric-tile-body">
                  <span class="metric-tile-value">
                    {{ summaryReport?.overallStats?.totalIssues ?? revisionCount ?? '—' }}
                  </span>
                  <span class="metric-tile-label">优化点</span>
                </div>
              </article>
              <article class="metric-tile">
                <span class="metric-tile-icon metric-tile-icon--primary">
                  <el-icon><ChatDotRound/></el-icon>
                </span>
                <div class="metric-tile-body">
                  <span class="metric-tile-value">
                    {{ summaryReport?.overallStats?.totalSentences ?? sortedItems.length ?? '—' }}
                  </span>
                  <span class="metric-tile-label">分析句子数</span>
                </div>
              </article>
            </div>

            <div
                v-if="detail.errorTypeDistribution?.length"
                class="summary-distribution"
            >
              <h3 class="summary-subheading">
                <el-icon>
                  <DataAnalysis/>
                </el-icon>
                优化类型分布
              </h3>
              <ErrorTypePieChart
                  compact
                  :items="detail.errorTypeDistribution"
                  :animate="pageReady"
              />
            </div>

            <div class="summary-challenge">
              <span class="summary-challenge-label">
                <el-icon><DataAnalysis/></el-icon>
                主要挑战
              </span>
              <p class="summary-challenge-value">
                {{ summaryReport?.overallStats?.mainCategory || '—' }}
              </p>
            </div>

            <blockquote
                v-if="summaryReport?.overallSummary?.levelSummary"
                class="summary-quote"
            >
              <span class="summary-quote-label">整体总结</span>
              <p class="summary-quote-text">{{ summaryReport.overallSummary.levelSummary }}</p>
            </blockquote>

            <footer class="summary-foot">
              <div class="summary-foot-item">
                <span class="summary-foot-key">
                  <el-icon><Clock/></el-icon>
                  分析时间
                </span>
                <span class="summary-foot-val">{{ formatTime(detail.createdAt) }}</span>
              </div>
              <div class="summary-foot-item">
                <span class="summary-foot-key">
                  <el-icon><Timer/></el-icon>
                  分析耗时
                </span>
                <span class="summary-foot-val">
                  {{ formatProcessingTime(detail.processingTimeMs) }}
                </span>
              </div>
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

.summary-panel,
.raw-fold {
  padding: 1.25rem 1.35rem;
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

.summary-panel {
  margin-bottom: 1rem;
  border-top: 3px solid var(--kk-color-accent);
  overflow: hidden;
}

.summary-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 0.85rem;
  margin-bottom: 1.15rem;
}

.summary-title {
  margin: 0;
  font-family: var(--kk-font-display);
  font-size: 1.25rem;
  font-weight: 800;
  color: var(--kk-color-primary);
  line-height: 1.2;
}

.summary-score {
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-width: 4.25rem;
  padding: 0.55rem 0.65rem;
  border-radius: var(--kk-radius-md);
  background: linear-gradient(
      145deg,
      color-mix(in srgb, var(--kk-color-primary) 14%, white),
      var(--kk-glass-inner-bg)
  );
  border: 1px solid color-mix(in srgb, var(--kk-color-primary) 18%, transparent);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.7);
}

.summary-score-num {
  font-family: var(--kk-font-display);
  font-size: 1.75rem;
  font-weight: 900;
  line-height: 1;
  color: var(--kk-color-primary);
}

.summary-score-label {
  margin-top: 0.15rem;
  font-size: 0.62rem;
  font-weight: 600;
  color: var(--kk-color-text-subtle);
  white-space: nowrap;
}

.summary-metrics {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0.55rem;
  margin-bottom: 0.85rem;
}

.metric-tile {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  padding: 0.85rem 0.75rem;
  border-radius: var(--kk-radius-md);
  background: var(--kk-glass-inner-bg);
  border: 1px solid var(--kk-glass-inner-border);
}

.metric-tile-icon {
  width: 2rem;
  height: 2rem;
  border-radius: var(--kk-radius-sm);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 1rem;
}

.metric-tile-icon--primary {
  background: color-mix(in srgb, var(--kk-color-primary) 12%, white);
  color: var(--kk-color-primary);
}

.metric-tile-value {
  display: block;
  font-family: var(--kk-font-display);
  font-size: 1.5rem;
  font-weight: 800;
  line-height: 1;
  color: var(--kk-color-primary);
}

.metric-tile-label {
  display: block;
  margin-top: 0.2rem;
  font-size: 0.7rem;
  font-weight: 600;
  color: var(--kk-color-text-subtle);
}

.summary-distribution {
  margin-bottom: 0.85rem;
  padding: 0.75rem 0.65rem;
  border-radius: var(--kk-radius-md);
  background: var(--kk-glass-inner-bg);
  border: 1px solid var(--kk-glass-inner-border);
}

.summary-subheading {
  display: flex;
  align-items: center;
  gap: 0.35rem;
  margin: 0 0 0.65rem;
  font-family: var(--kk-font-display);
  font-size: 0.78rem;
  font-weight: 700;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: var(--kk-color-primary-soft);
}

.summary-challenge {
  padding: 0.85rem 0.9rem;
  margin-bottom: 0.85rem;
  border-radius: var(--kk-radius-md);
  background: var(--kk-color-accent-bg);
  border: 1px solid rgba(184, 148, 31, 0.28);
}

.summary-challenge-label {
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
  font-size: 0.68rem;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--kk-color-accent-text);
}

.summary-challenge-value {
  margin: 0.4rem 0 0;
  font-family: var(--kk-font-display);
  font-size: 1rem;
  font-weight: 700;
  line-height: 1.45;
  color: var(--kk-color-primary);
}

.summary-quote {
  margin: 0 0 0.9rem;
  padding: 0.75rem 0.85rem 0.75rem 0.95rem;
  border-radius: var(--kk-radius-md);
  border-left: 3px solid var(--kk-color-primary-soft);
  background: rgba(255, 255, 255, 0.42);
}

.summary-quote-label {
  display: block;
  margin-bottom: 0.35rem;
  font-size: 0.68rem;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--kk-color-primary-soft);
}

.summary-quote-text {
  margin: 0;
  font-size: 0.84rem;
  line-height: 1.65;
  color: var(--kk-color-text-muted);
}

.summary-foot {
  display: flex;
  flex-direction: column;
  gap: 0.45rem;
  padding-top: 0.85rem;
  border-top: 1px solid var(--kk-glass-inner-border);
}

.summary-foot-item {
  display: grid;
  grid-template-columns: auto 1fr;
  align-items: baseline;
  gap: 0.5rem 0.75rem;
  font-size: 0.78rem;
}

.summary-foot-key {
  display: inline-flex;
  align-items: center;
  gap: 0.25rem;
  color: var(--kk-color-text-subtle);
  font-weight: 600;
  white-space: nowrap;
}

.summary-foot-val {
  text-align: right;
  color: var(--kk-color-text-secondary);
  font-weight: 500;
  font-family: var(--kk-font-mono);
  font-size: 0.76rem;
}

@media (prefers-reduced-motion: reduce) {
  .detail-page {
    transition: none;
    opacity: 1;
    transform: none;
  }
}
</style>
