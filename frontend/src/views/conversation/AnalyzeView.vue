<script setup lang="ts">
import {Document, RefreshRight, Upload} from '@element-plus/icons-vue'
import {ElMessage} from 'element-plus'
import {computed, ref} from 'vue'
import {useRouter} from 'vue-router'

import {analyzeConversationStream, saveConversationAnalysis,} from '@/api/conversationAnalysis'
import PerformanceDimensionBars from '@/components/conversation/PerformanceDimensionBars.vue'
import type {
  AnalysisItem,
  ConversationAnalysisProgress,
  ConversationAnalysisResult,
  ConversationAnalysisSaveRequest,
  SaveAnalysisItem,
  StreamingPreviewCard,
} from '@/types/conversation'
import {PROGRESS_STATUS} from '@/types/conversation'
import {resolvePerformanceScore} from '@/utils/analysisDisplay'
import {getErrorMessage} from '@/utils/error'

const router = useRouter()

const MIN_LENGTH = 10
const content = ref('')
const analyzing = ref(false)
const saving = ref(false)
const showResults = ref(false)
const progressLog = ref<ConversationAnalysisProgress[]>([])
const committedPreviews = ref<StreamingPreviewCard[]>([])
const currentPreview = ref<{ original: string; suggestion?: string; errorsHint?: string } | null>(null)
const result = ref<ConversationAnalysisResult | null>(null)
const abortController = ref<AbortController | null>(null)

const charCount = computed(() => content.value.length)

const analysisItems = computed(
    () => result.value?.analysisResults?.items ?? [],
)

const summaryRoot = computed(
    () => result.value?.analysisResults?.educationalSummary,
)
const summaryReport = computed(() => summaryRoot.value?.report)

const summaryStats = computed(() => summaryReport.value?.overallStats)

const revisionCount = computed(() =>
    analysisItems.value.reduce((sum, item) => sum + (item.errors?.length ?? 0), 0),
)

const performanceScore = computed(() => {
  const st = summaryStats.value
  const issues = st?.totalIssues ?? revisionCount.value
  const sentences = st?.totalSentences ?? analysisItems.value.length
  return resolvePerformanceScore(st, issues, sentences)
})

const distribution = computed(
    () => result.value?.analysisResults?.errorTypeDistribution ?? [],
)

const statusLabels: Record<string, { emoji: string; title: string }> = {
  [PROGRESS_STATUS.START]: {emoji: '🚀', title: '开始分析'},
  [PROGRESS_STATUS.VALIDATING]: {emoji: '🔍', title: '验证请求'},
  [PROGRESS_STATUS.SEPARATING]: {emoji: '✂️', title: '分离对话'},
  [PROGRESS_STATUS.ANALYZING]: {emoji: '🤖', title: 'AI 分析'},
  [PROGRESS_STATUS.PARSING]: {emoji: '📊', title: '解析结果'},
  [PROGRESS_STATUS.SUMMARIZING]: {emoji: '📝', title: '学习概要'},
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

function hasStreamingPreview(event: ConversationAnalysisProgress) {
  return event.streamingOriginal != null
      || event.streamingSuggestion != null
      || event.streamingErrorsHint != null
      || event.streamingCommitOriginal != null
}

function applyStreamingPreview(event: ConversationAnalysisProgress) {
  const commitOrig = (event.streamingCommitOriginal ?? '').trim()
  const commitSugg = (event.streamingCommitSuggestion ?? '').trim()
  if (commitOrig || commitSugg) {
    committedPreviews.value.push({
      original: commitOrig,
      suggestion: commitSugg || undefined,
    })
  }

  const orig = (event.streamingOriginal ?? '').trim()
  const sugg = (event.streamingSuggestion ?? '').trim()
  const errHint = (event.streamingErrorsHint ?? '').trim()

  if (!orig && !sugg && !errHint && !commitOrig) {
    return
  }

  currentPreview.value = {
    original: orig,
    suggestion: sugg || undefined,
    errorsHint: errHint || undefined,
  }
}

function onProgress(event: ConversationAnalysisProgress) {
  if (event.status === PROGRESS_STATUS.COMPLETED) {
    return
  }
  if (event.status === PROGRESS_STATUS.ERROR) {
    progressLog.value.push(event)
    return
  }
  if (hasStreamingPreview(event)) {
    applyStreamingPreview(event)
    return
  }

  const last = progressLog.value[progressLog.value.length - 1]
  if (!last || last.status !== event.status || last.message !== event.message) {
    progressLog.value.push(event)
  }
}

function resetStreamingPreview() {
  committedPreviews.value = []
  currentPreview.value = null
}

function isPreviewPlaceholder(original?: string) {
  const value = (original ?? '').trim()
  return !value || value === '...'
}

function resetForm() {
  if (analyzing.value) {
    return
  }
  content.value = ''
  progressLog.value = []
  resetStreamingPreview()
  result.value = null
  showResults.value = false
}

async function onAnalyze() {
  const text = content.value.trim()
  if (!text) {
    ElMessage.warning('请输入对话字幕内容')
    return
  }
  if (text.length < MIN_LENGTH) {
    ElMessage.warning(`对话内容至少 ${MIN_LENGTH} 个字符`)
    return
  }

  analyzing.value = true
  showResults.value = true
  progressLog.value = []
  resetStreamingPreview()
  result.value = null
  abortController.value?.abort()
  abortController.value = new AbortController()

  try {
    const analysisResult = await analyzeConversationStream(
        {conversationContent: text},
        onProgress,
        abortController.value.signal,
    )
    result.value = analysisResult
    resetStreamingPreview()
    progressLog.value.push({
      status: PROGRESS_STATUS.COMPLETED,
      message: '分析完成',
    })
    ElMessage.success('分析完成，可保存到历史记录')
  } catch (error) {
    if ((error as Error).name === 'AbortError') {
      return
    }
    showResults.value = false
    ElMessage.error(getErrorMessage(error, '分析失败，请稍后重试'))
  } finally {
    analyzing.value = false
  }
}

function toSaveItems(items: AnalysisItem[]): SaveAnalysisItem[] {
  return items
      .filter((item) => item.errors?.length)
      .map((item) => ({
        originalSentence: item.originalSentence,
        suggestion: item.suggestion,
        errors: (item.errors ?? []).map((err) => ({
          type: err.type,
          point: err.point,
        })),
      }))
}

async function onSave() {
  if (!result.value?.analysisId) {
    ElMessage.warning('暂无分析结果可保存')
    return
  }
  const items = toSaveItems(analysisItems.value)
  if (!items.length) {
    ElMessage.warning('未发现可保存的错误项')
    return
  }

  const payload: ConversationAnalysisSaveRequest = {
    conversationContent: content.value.trim(),
    items,
    analysisId: result.value.analysisId,
    analyzedAt: result.value.analyzedAt,
    processingTimeMs: result.value.processingTimeMs,
  }
  if (result.value.educationalSummaryJson) {
    payload.educationalSummary = result.value.educationalSummaryJson
  } else if (summaryRoot.value) {
    payload.educationalSummary = JSON.stringify(summaryRoot.value)
  }

  saving.value = true
  try {
    await saveConversationAnalysis(payload)
    ElMessage.success('已保存到历史记录')
    await router.push(`/conversation/analyses/${result.value.analysisId}`)
  } catch (error) {
    ElMessage.error(getErrorMessage(error, '保存失败'))
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div class="analyze-page">
    <header class="page-head">
      <h1 class="page-title">对话分析</h1>
      <p class="page-desc">粘贴你与 AI 的英文对话字幕，系统将逐句标出可优化表达并给出改写建议。</p>
    </header>

    <div class="analyze-grid">
      <section class="input-panel kk-glass kk-glass--panel">
        <div class="panel-label">
          <el-icon><Document/></el-icon>
          对话字幕
        </div>
        <el-input
            v-model="content"
            type="textarea"
            :rows="14"
            placeholder="粘贴对话内容，例如：&#10;User: When I give presentation...&#10;Assistant: ..."
            :disabled="analyzing"
            resize="vertical"
        />
        <div class="input-meta">
          <span>已输入 {{ charCount }} 字</span>
          <span class="hint">至少 {{ MIN_LENGTH }} 字</span>
        </div>
        <div class="input-actions">
          <el-button
              type="primary"
              size="large"
              :loading="analyzing"
              :icon="Upload"
              @click="onAnalyze"
          >
            {{ analyzing ? '分析中…' : '开始分析' }}
          </el-button>
          <el-button size="large" :icon="RefreshRight" :disabled="analyzing" @click="resetForm">
            清空
          </el-button>
        </div>
      </section>

      <section v-if="showResults" class="result-panel kk-glass kk-glass--panel">
        <div class="panel-label">分析进度与结果</div>

        <div v-if="progressLog.length" class="progress-list">
          <div v-for="(step, idx) in progressLog" :key="idx" class="progress-item">
            <span class="progress-emoji">{{ statusLabels[step.status]?.emoji ?? '⏳' }}</span>
            <div>
              <p class="progress-title">{{ statusLabels[step.status]?.title ?? '处理中' }}</p>
              <p v-if="step.message" class="progress-msg">{{ step.message }}</p>
              <p
                  v-if="step.messageStats"
                  class="progress-msg"
              >
                共 {{ step.messageStats.totalMessages }} 条消息（用户 {{ step.messageStats.userMessages }} /
                AI {{ step.messageStats.aiMessages }}）
              </p>
            </div>
          </div>
        </div>

        <div
            v-if="analyzing && (committedPreviews.length || currentPreview)"
            class="streaming-block"
        >
          <p class="streaming-label">实时解析预览</p>
          <p v-if="currentPreview?.errorsHint" class="streaming-errors-hint">
            错误 <span class="errors-badge">{{ currentPreview.errorsHint }}</span>
          </p>
          <article
              v-for="(card, idx) in committedPreviews"
              :key="`committed-${idx}`"
              class="preview-card preview-card--done"
          >
            <p class="preview-row">
              <span class="preview-key">原句</span>
              <span>{{ card.original }}</span>
            </p>
            <p v-if="card.suggestion" class="preview-row preview-row--suggestion">
              <span class="preview-key">建议</span>
              <span>{{ card.suggestion }}</span>
            </p>
          </article>
          <article v-if="currentPreview" class="preview-card preview-card--current">
            <div v-if="isPreviewPlaceholder(currentPreview.original) && !currentPreview.suggestion" class="preview-waiting">
              <span class="preview-spinner"/>
              正在接收分析结果…
            </div>
            <template v-else>
              <p class="preview-row">
                <span class="preview-key">原句</span>
                <span>{{ currentPreview.original }}</span>
              </p>
              <p v-if="currentPreview.suggestion" class="preview-row preview-row--suggestion">
                <span class="preview-key">建议</span>
                <span>{{ currentPreview.suggestion }}</span>
              </p>
              <p v-else-if="!isPreviewPlaceholder(currentPreview.original)" class="preview-row preview-row--muted">
                <span class="preview-key">建议</span>
                <span>—</span>
              </p>
            </template>
          </article>
        </div>

        <div v-if="analyzing && !committedPreviews.length && !currentPreview && !analysisItems.length" class="result-loading">
          <el-skeleton :rows="4" animated/>
        </div>

        <template v-else-if="result">
          <div v-if="distribution.length" class="dist-row">
            <span
                v-for="d in distribution"
                :key="d.type"
                class="dist-chip"
            >{{ d.type }} × {{ d.count }}</span>
          </div>

          <article v-for="item in analysisItems" :key="item.sentenceId ?? item.originalSentence" class="item-card">
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

          <div v-if="summaryReport" class="summary-block">
            <header class="summary-hero">
              <h3 class="summary-title">学习诊断概要</h3>
              <div class="summary-hero-score" aria-label="综合口语自然度得分">
                <span class="summary-hero-num">{{ performanceScore }}</span>
                <span class="summary-hero-lbl">综合自然度</span>
              </div>
            </header>
            <div class="summary-strip">
              <span class="summary-kpi">
                <strong>{{ summaryStats?.totalIssues ?? revisionCount ?? '—' }}</strong>
                优化点
              </span>
              <span class="summary-dot" aria-hidden="true">·</span>
              <span class="summary-kpi">
                <strong>{{ summaryStats?.totalSentences ?? analysisItems.length ?? '—' }}</strong>
                句
              </span>
              <span v-if="summaryStats?.mainCategory" class="summary-challenge-inline">
                <span class="summary-challenge-tag">主要挑战</span>
                {{ summaryStats.mainCategory }}
              </span>
            </div>
            <div v-if="summaryStats?.dimensionScores" class="summary-dims">
              <PerformanceDimensionBars :scores="summaryStats.dimensionScores"/>
            </div>
            <p
                v-if="summaryReport.overallSummary?.levelSummary"
                class="summary-brief"
            >
              <span class="summary-brief-label">总结</span>
              {{ summaryReport.overallSummary.levelSummary }}
            </p>
          </div>

          <div class="save-bar">
            <el-button
                type="primary"
                size="large"
                :loading="saving"
                :disabled="analyzing || !analysisItems.length"
                @click="onSave"
            >
              保存到历史
            </el-button>
            <router-link to="/conversation/analyses" class="link-history">查看历史记录</router-link>
          </div>
        </template>
      </section>
    </div>
  </div>
</template>

<style scoped>
.analyze-page {
  font-family: var(--kk-font-body);
  color: var(--kk-color-text);
}

.page-head {
  margin-bottom: 1.25rem;
}

.page-title {
  margin: 0 0 0.35rem;
  font-family: var(--kk-font-display);
  font-size: clamp(1.5rem, 3vw, 2rem);
  font-weight: 800;
  color: var(--kk-color-primary);
}

.page-desc {
  margin: 0;
  color: var(--kk-color-text-muted);
  line-height: 1.6;
  max-width: 40rem;
}

.analyze-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 1rem;
  align-items: start;
}

.input-panel,
.result-panel {
  padding: 1.25rem 1.35rem;
}

.panel-label {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  margin-bottom: 0.85rem;
  font-weight: 700;
  color: var(--kk-color-primary);
  font-size: 0.92rem;
}

.input-meta {
  display: flex;
  justify-content: space-between;
  margin-top: 0.5rem;
  font-size: 0.82rem;
  color: var(--kk-color-text-subtle);
}

.hint {
  color: var(--kk-color-accent-text);
}

.input-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 0.65rem;
  margin-top: 1rem;
}

.progress-list {
  display: flex;
  flex-direction: column;
  gap: 0.55rem;
  margin-bottom: 1rem;
  max-height: 12rem;
  overflow-y: auto;
}

.progress-item {
  display: flex;
  gap: 0.65rem;
  padding: 0.65rem 0.75rem;
  border-radius: var(--kk-radius-md);
  background: var(--kk-glass-inner-bg);
  border: 1px solid var(--kk-glass-inner-border);
}

.progress-emoji {
  font-size: 1.1rem;
  line-height: 1.4;
}

.progress-title {
  margin: 0;
  font-weight: 600;
  font-size: 0.88rem;
  color: var(--kk-color-primary);
}

.progress-msg {
  margin: 0.2rem 0 0;
  font-size: 0.82rem;
  color: var(--kk-color-text-muted);
}

.streaming-block {
  margin-bottom: 1rem;
  padding: 0.85rem;
  border-radius: var(--kk-radius-md);
  background: var(--kk-glass-inner-bg);
  border: 1px solid var(--kk-glass-inner-border);
}

.streaming-label {
  margin: 0 0 0.65rem;
  font-size: 0.82rem;
  font-weight: 700;
  color: var(--kk-color-primary);
}

.streaming-errors-hint {
  margin: 0 0 0.55rem;
  font-size: 0.8rem;
  color: var(--kk-color-text-muted);
}

.errors-badge {
  display: inline-block;
  margin-left: 0.25rem;
  padding: 0.1rem 0.45rem;
  border-radius: 999px;
  background: #fff8e0;
  color: #7a6200;
  font-size: 0.72rem;
  font-weight: 700;
}

.preview-card {
  padding: 0.75rem 0.85rem;
  margin-bottom: 0.5rem;
  border-radius: var(--kk-radius-md);
  background: rgba(255, 255, 255, 0.55);
  border: 1px solid var(--kk-glass-inner-border);
}

.preview-card--current {
  border-color: color-mix(in srgb, var(--kk-color-primary) 35%, transparent);
}

.preview-row {
  margin: 0 0 0.35rem;
  font-size: 0.86rem;
  line-height: 1.55;
}

.preview-row:last-child {
  margin-bottom: 0;
}

.preview-key {
  display: inline-block;
  min-width: 2.2rem;
  margin-right: 0.45rem;
  font-weight: 700;
  color: var(--kk-color-text-subtle);
}

.preview-row--suggestion span:last-child {
  color: #2d6a4f;
  font-weight: 600;
}

.preview-row--muted span:last-child {
  color: var(--kk-color-text-subtle);
}

.preview-waiting {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-size: 0.86rem;
  color: var(--kk-color-text-muted);
}

.preview-spinner {
  width: 0.9rem;
  height: 0.9rem;
  border: 2px solid color-mix(in srgb, var(--kk-color-primary) 25%, transparent);
  border-top-color: var(--kk-color-primary);
  border-radius: 50%;
  animation: kk-spin 0.8s linear infinite;
}

@keyframes kk-spin {
  to {
    transform: rotate(360deg);
  }
}

.result-loading {
  padding: 0.5rem 0;
}

.dist-row {
  display: flex;
  flex-wrap: wrap;
  gap: 0.4rem;
  margin-bottom: 0.85rem;
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
  color: var(--kk-color-text);
}

.item-errors {
  margin-bottom: 0.35rem;
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
  color: var(--kk-color-danger, #b42318);
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

.summary-block {
  margin-top: 0.75rem;
  padding: 0.65rem 0.75rem;
  border-radius: var(--kk-radius-md);
  background: var(--kk-glass-inner-bg);
  border: 1px solid var(--kk-glass-inner-border);
  border-top: 2px solid var(--kk-color-accent);
}

.summary-hero {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.5rem;
  margin-bottom: 0.4rem;
}

.summary-title {
  margin: 0;
  font-family: var(--kk-font-display);
  font-size: 1rem;
  font-weight: 800;
  color: var(--kk-color-primary);
}

.summary-hero-score {
  display: flex;
  align-items: baseline;
  gap: 0.3rem;
  padding: 0.15rem 0.4rem;
  border-radius: var(--kk-radius-sm);
  background: color-mix(in srgb, var(--kk-color-primary) 10%, white);
}

.summary-hero-num {
  font-family: var(--kk-font-display);
  font-size: 1.5rem;
  font-weight: 900;
  line-height: 1;
  color: var(--kk-color-primary);
}

.summary-hero-lbl {
  font-size: 0.58rem;
  font-weight: 700;
  color: var(--kk-color-text-subtle);
}

.summary-strip {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.25rem 0.35rem;
  margin-bottom: 0.4rem;
  padding-bottom: 0.4rem;
  border-bottom: 1px solid var(--kk-glass-divider);
  font-size: 0.72rem;
  color: var(--kk-color-text-muted);
}

.summary-kpi strong {
  font-family: var(--kk-font-display);
  font-size: 0.9rem;
  font-weight: 800;
  color: var(--kk-color-primary);
  margin-right: 0.1rem;
}

.summary-dot {
  color: var(--kk-color-text-subtle);
}

.summary-challenge-inline {
  flex: 1 1 100%;
  margin-top: 0.08rem;
  padding: 0.25rem 0.38rem;
  border-radius: var(--kk-radius-sm);
  background: var(--kk-color-accent-bg);
  font-family: var(--kk-font-display);
  font-size: 0.76rem;
  font-weight: 700;
  color: var(--kk-color-primary);
}

.summary-challenge-tag {
  margin-right: 0.3rem;
  font-size: 0.56rem;
  font-weight: 700;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: var(--kk-color-accent-text);
}

.summary-dims {
  margin-bottom: 0.35rem;
}

.summary-brief {
  margin: 0;
  padding-left: 0.45rem;
  border-left: 2px solid var(--kk-color-primary-soft);
  font-size: 0.72rem;
  line-height: 1.5;
  color: var(--kk-color-text-muted);
}

.summary-brief-label {
  margin-right: 0.3rem;
  font-size: 0.56rem;
  font-weight: 700;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: var(--kk-color-primary-soft);
}

.save-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 1rem;
  margin-top: 1.25rem;
  padding-top: 1rem;
  border-top: 1px solid var(--kk-glass-divider);
}

.link-history {
  color: var(--kk-color-primary);
  font-weight: 600;
  text-decoration: none;
}

.link-history:hover {
  color: var(--kk-color-accent);
}

@media (max-width: 992px) {
  .analyze-grid {
    grid-template-columns: 1fr;
  }
}
</style>
