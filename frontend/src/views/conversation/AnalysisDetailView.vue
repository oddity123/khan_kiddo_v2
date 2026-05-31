<script setup lang="ts">
import {ArrowLeft, Clock, Delete} from '@element-plus/icons-vue'
import {ElMessage, ElMessageBox} from 'element-plus'
import {computed, onMounted, ref, watch} from 'vue'
import {useRoute, useRouter} from 'vue-router'

import {
  deleteConversationAnalysis,
  getConversationAnalysisDetail,
} from '@/api/conversationAnalysis'
import type {ConversationAnalysisDetail} from '@/types/conversation'
import {getErrorMessage} from '@/utils/error'

const route = useRoute()
const router = useRouter()

const loading = ref(true)
const detail = ref<ConversationAnalysisDetail | null>(null)

const analysisId = computed(() => String(route.params.id ?? ''))

const summaryReport = computed(() => detail.value?.educationalSummary?.report)

function formatTime(value?: string) {
  if (!value) {
    return '—'
  }
  return value.replace('T', ' ').slice(0, 19)
}

function formatDuration(ms?: number) {
  if (ms == null) {
    return '—'
  }
  if (ms < 1000) {
    return `${ms} ms`
  }
  return `${(ms / 1000).toFixed(1)} s`
}

function errorBadgeClass(level?: string) {
  if (level === 'FATAL' || level === 'BASIC') {
    return 'tag--fatal'
  }
  if (level === 'NATURAL') {
    return 'tag--warn'
  }
  return 'tag--soft'
}

async function loadDetail() {
  if (!analysisId.value) {
    return
  }
  loading.value = true
  try {
    const {data} = await getConversationAnalysisDetail(analysisId.value)
    detail.value = data
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
  <div v-loading="loading" class="detail-page">
    <header class="detail-head">
      <router-link to="/conversation/analyses" class="back-link">
        <el-icon><ArrowLeft/></el-icon>
        返回列表
      </router-link>
      <div class="head-actions">
        <el-button type="danger" plain :icon="Delete" @click="onDelete">删除</el-button>
      </div>
    </header>

    <template v-if="detail">
      <section class="meta-panel kk-glass kk-glass--panel">
        <h1 class="page-title">分析详情</h1>
        <div class="meta-row">
          <span><el-icon><Clock/></el-icon>{{ formatTime(detail.createdAt) }}</span>
          <span>耗时 {{ formatDuration(detail.processingTimeMs) }}</span>
          <span>ID {{ detail.analysisId }}</span>
        </div>
        <details v-if="detail.conversationContent" class="content-fold">
          <summary>原始对话内容</summary>
          <pre class="content-pre">{{ detail.conversationContent }}</pre>
        </details>
      </section>

      <section
          v-if="detail.errorTypeDistribution?.length"
          class="dist-panel kk-glass kk-glass--panel"
      >
        <h2 class="section-title">错误类型分布</h2>
        <div class="dist-row">
          <span
              v-for="d in detail.errorTypeDistribution"
              :key="d.type"
              class="dist-chip"
          >{{ d.type }} × {{ d.count }}</span>
        </div>
      </section>

      <section class="items-panel kk-glass kk-glass--panel">
        <h2 class="section-title">逐句分析</h2>
        <el-empty v-if="!detail.items?.length" description="暂无分析条目"/>
        <article
            v-for="item in detail.items"
            :key="item.sentenceId ?? item.originalSentence"
            class="item-card"
        >
          <p class="item-original">{{ item.originalSentence }}</p>
          <div v-if="item.errors?.length" class="item-errors">
            <span
                v-for="(err, i) in item.errors"
                :key="i"
                class="error-tag"
                :class="errorBadgeClass(err.errorLevel)"
            >{{ err.type }}</span>
            <p v-for="(err, i) in item.errors" :key="`p-${i}`" class="error-point">{{ err.point }}</p>
          </div>
          <p v-if="item.suggestion" class="item-suggestion">
            <span class="suggestion-label">建议</span>
            {{ item.suggestion }}
          </p>
        </article>
      </section>

      <section v-if="summaryReport" class="summary-panel kk-glass kk-glass--panel">
        <h2 class="section-title">学习诊断概要</h2>
        <div class="summary-stats">
          <div class="summary-stat">
            <span class="summary-stat-label">优化点</span>
            <span class="summary-stat-value">{{ summaryReport.overallStats?.totalIssues ?? '—' }}</span>
          </div>
          <div class="summary-stat">
            <span class="summary-stat-label">分析句子数</span>
            <span class="summary-stat-value">{{ summaryReport.overallStats?.totalSentences ?? '—' }}</span>
          </div>
          <div class="summary-stat summary-stat--wide">
            <span class="summary-stat-label">主要挑战</span>
            <span class="summary-stat-value summary-stat-value--text">
              {{ summaryReport.overallStats?.mainCategory || '—' }}
            </span>
          </div>
        </div>
        <div v-if="summaryReport.overallSummary?.levelSummary" class="summary-overall">
          <h3 class="sub-title">整体总结</h3>
          <p class="summary-text">{{ summaryReport.overallSummary.levelSummary }}</p>
        </div>
      </section>
    </template>
  </div>
</template>

<style scoped>
.detail-page {
  font-family: var(--kk-font-body);
  color: var(--kk-color-text);
}

.detail-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 1rem;
}

.back-link {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  color: var(--kk-color-primary);
  font-weight: 600;
  text-decoration: none;
}

.back-link:hover {
  color: var(--kk-color-accent);
}

.meta-panel,
.dist-panel,
.items-panel,
.summary-panel {
  padding: 1.25rem 1.35rem;
  margin-bottom: 1rem;
}

.page-title {
  margin: 0 0 0.5rem;
  font-family: var(--kk-font-display);
  font-size: 1.5rem;
  font-weight: 800;
  color: var(--kk-color-primary);
}

.meta-row {
  display: flex;
  flex-wrap: wrap;
  gap: 0.75rem 1.25rem;
  font-size: 0.85rem;
  color: var(--kk-color-text-subtle);
}

.meta-row span {
  display: inline-flex;
  align-items: center;
  gap: 0.25rem;
}

.content-fold {
  margin-top: 1rem;
}

.content-fold summary {
  cursor: pointer;
  font-weight: 600;
  color: var(--kk-color-primary);
}

.content-pre {
  margin: 0.65rem 0 0;
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

.section-title {
  margin: 0 0 0.85rem;
  font-family: var(--kk-font-display);
  font-size: 1.15rem;
  font-weight: 700;
  color: var(--kk-color-primary);
}

.sub-title {
  margin: 0.75rem 0 0.35rem;
  font-size: 0.92rem;
  color: var(--kk-color-text-secondary);
}

.dist-row {
  display: flex;
  flex-wrap: wrap;
  gap: 0.4rem;
}

.dist-chip {
  padding: 0.2rem 0.55rem;
  border-radius: 999px;
  background: var(--kk-color-accent-bg);
  color: var(--kk-color-accent-text);
  font-size: 0.72rem;
  font-weight: 700;
}

.item-card {
  padding: 0.9rem 1rem;
  margin-bottom: 0.65rem;
  border-radius: var(--kk-radius-md);
  background: var(--kk-glass-inner-bg);
  border-left: 3px solid var(--kk-color-primary);
}

.item-original {
  margin: 0 0 0.5rem;
  font-family: var(--kk-font-mono);
  font-size: 0.9rem;
  line-height: 1.55;
}

.error-tag {
  display: inline-block;
  margin: 0 0.35rem 0.35rem 0;
  padding: 0.15rem 0.5rem;
  border-radius: 999px;
  font-size: 0.72rem;
  font-weight: 700;
}

.tag--fatal {
  background: #ffecec;
  color: #a01818;
}

.tag--warn {
  background: #fff8e0;
  color: #7a6200;
}

.tag--soft {
  background: #e8f2ff;
  color: #0e5080;
}

.error-point {
  margin: 0.15rem 0;
  font-size: 0.85rem;
  color: #b42318;
}

.item-suggestion {
  margin: 0.35rem 0 0;
  font-size: 0.88rem;
  line-height: 1.55;
  color: var(--kk-color-primary-soft);
  font-weight: 600;
}

.suggestion-label {
  display: inline-block;
  margin-right: 0.35rem;
  font-size: 0.72rem;
  font-weight: 700;
  letter-spacing: 0.04em;
  text-transform: uppercase;
  color: var(--kk-color-text-subtle);
}

.summary-stats {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0.5rem;
  margin-bottom: 0.75rem;
}

.summary-stat {
  padding: 0.65rem 0.75rem;
  border-radius: var(--kk-radius-md);
  background: var(--kk-glass-inner-bg);
  border: 1px solid var(--kk-glass-inner-border);
}

.summary-stat--wide {
  grid-column: 1 / -1;
}

.summary-stat-label {
  display: block;
  font-size: 0.72rem;
  color: var(--kk-color-text-subtle);
  margin-bottom: 0.2rem;
}

.summary-stat-value {
  font-family: var(--kk-font-display);
  font-size: 1.25rem;
  font-weight: 800;
  color: var(--kk-color-primary);
  line-height: 1.2;
}

.summary-stat-value--text {
  font-size: 0.95rem;
  font-weight: 700;
}

.summary-overall {
  padding-top: 0.25rem;
}

.summary-text {
  margin: 0;
  font-size: 0.9rem;
  line-height: 1.65;
  color: var(--kk-color-text-muted);
}
</style>
