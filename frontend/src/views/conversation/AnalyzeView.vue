<script setup lang="ts">
import {Cpu, Document, RefreshRight, Upload} from '@element-plus/icons-vue'
import {ElMessage} from 'element-plus'
import {computed, nextTick, onMounted, ref, watch} from 'vue'
import {useRouter} from 'vue-router'

import {analyzeConversationStream, listConversationLlmModels} from '@/api/conversationAnalysis'
import type {ConversationAnalysisProgress, LlmModelOption} from '@/types/conversation'
import {PROGRESS_STATUS} from '@/types/conversation'
import {getErrorMessage} from '@/utils/error'

const router = useRouter()

const MODEL_STORAGE_KEY = 'kk.conversation.analysis.modelId'

const MIN_LENGTH = 10
const content = ref('')
const modelOptions = ref<LlmModelOption[]>([])
const selectedModelId = ref('')
const modelsLoading = ref(false)
const analyzing = ref(false)
const showProgress = ref(false)
const progressLog = ref<ConversationAnalysisProgress[]>([])
const abortController = ref<AbortController | null>(null)

interface StreamingCommitRow {
  original: string
  suggestion: string
  errorsHint: string
}

const streamingCommitted = ref<StreamingCommitRow[]>([])
const streamingLive = ref({
  original: '',
  suggestion: '',
  errorsHint: '',
})
let lastStreamingCommitKey = ''

const progressListRef = ref<HTMLElement | null>(null)
const streamingScrollRef = ref<HTMLElement | null>(null)

function scrollPaneToBottom(el: HTMLElement | null) {
  if (!el) {
    return
  }
  el.scrollTop = el.scrollHeight
}

async function scrollProgressToLatest() {
  await nextTick()
  scrollPaneToBottom(progressListRef.value)
}

async function scrollStreamingToLatest() {
  await nextTick()
  scrollPaneToBottom(streamingScrollRef.value)
}

const charCount = computed(() => content.value.length)

const selectedModelLabel = computed(() => {
  const found = modelOptions.value.find((m) => m.id === selectedModelId.value)
  return found?.displayName ?? ''
})

async function loadModelOptions() {
  modelsLoading.value = true
  try {
    const {data} = await listConversationLlmModels()
    modelOptions.value = data ?? []
    const stored = localStorage.getItem(MODEL_STORAGE_KEY)
    const storedValid = stored && modelOptions.value.some((m) => m.id === stored)
    const defaultModel = modelOptions.value.find((m) => m.defaultModel)
    selectedModelId.value = storedValid
        ? stored!
        : (defaultModel?.id ?? modelOptions.value[0]?.id ?? '')
  } catch (error) {
    modelOptions.value = []
    selectedModelId.value = ''
    ElMessage.warning(getErrorMessage(error, '加载模型列表失败'))
  } finally {
    modelsLoading.value = false
  }
}

onMounted(loadModelOptions)

const statusLabels: Record<string, { emoji: string; title: string }> = {
  [PROGRESS_STATUS.START]: {emoji: '🚀', title: '开始分析'},
  [PROGRESS_STATUS.VALIDATING]: {emoji: '🔍', title: '验证请求'},
  [PROGRESS_STATUS.SEPARATING]: {emoji: '✂️', title: '分离对话'},
  [PROGRESS_STATUS.ANALYZING]: {emoji: '🤖', title: 'AI 分析'},
  [PROGRESS_STATUS.PARSING]: {emoji: '📊', title: '解析结果'},
  [PROGRESS_STATUS.SUMMARIZING]: {emoji: '📝', title: '学习概要'},
}

function hasStreamingPreview(event: ConversationAnalysisProgress) {
  return (
      event.streamingOriginal != null ||
      event.streamingSuggestion != null ||
      event.streamingErrorsHint != null
  )
}

function applyStreamingProgress(event: ConversationAnalysisProgress) {
  streamingLive.value = {
    original: event.streamingOriginal ?? '',
    suggestion: event.streamingSuggestion ?? '',
    errorsHint: event.streamingErrorsHint ?? '',
  }

  const commitOriginal = (event.streamingCommitOriginal ?? '').trim()
  const commitSuggestion = (event.streamingCommitSuggestion ?? '').trim()
  const commitErrors = (event.streamingCommitErrorsHint ?? '').trim()
  if (!commitOriginal && !commitSuggestion) {
    return
  }
  const key = `${commitOriginal}\u0000${commitSuggestion}\u0000${commitErrors}`
  if (key === lastStreamingCommitKey) {
    return
  }
  lastStreamingCommitKey = key
  streamingCommitted.value.push({
    original: commitOriginal,
    suggestion: commitSuggestion,
    errorsHint: commitErrors,
  })
}

function resetStreamingPreview() {
  streamingCommitted.value = []
  streamingLive.value = {original: '', suggestion: '', errorsHint: ''}
  lastStreamingCommitKey = ''
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
    applyStreamingProgress(event)
    return
  }

  const last = progressLog.value[progressLog.value.length - 1]
  if (!last || last.status !== event.status || last.message !== event.message) {
    progressLog.value.push(event)
  }
}

const showStreamingPanel = computed(() => {
  if (!analyzing.value) {
    return false
  }
  const live = streamingLive.value
  return (
      streamingCommitted.value.length > 0 ||
      !!live.original ||
      !!live.suggestion ||
      !!live.errorsHint
  )
})

watch(progressLog, () => {
  void scrollProgressToLatest()
}, {deep: true})

watch([streamingCommitted, streamingLive, showStreamingPanel], () => {
  void scrollStreamingToLatest()
}, {deep: true})

const isStreamingPlaceholder = computed(() => {
  const orig = streamingLive.value.original.trim()
  const sugg = streamingLive.value.suggestion.trim()
  return (orig === '...' || orig === '') && !sugg
})

function resetForm() {
  if (analyzing.value) {
    return
  }
  content.value = ''
  progressLog.value = []
  resetStreamingPreview()
  showProgress.value = false
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
  if (!selectedModelId.value) {
    ElMessage.warning('请选择分析模型')
    return
  }

  localStorage.setItem(MODEL_STORAGE_KEY, selectedModelId.value)
  analyzing.value = true
  showProgress.value = true
  progressLog.value = []
  resetStreamingPreview()
  abortController.value?.abort()
  abortController.value = new AbortController()

  try {
    const analysisResult = await analyzeConversationStream(
        {conversationContent: text, modelId: selectedModelId.value},
        onProgress,
        abortController.value.signal,
    )
    ElMessage.success('分析完成')
    await router.replace(`/conversation/analyses/${analysisResult.analysisId}`)
  } catch (error) {
    if ((error as Error).name === 'AbortError') {
      return
    }
    const failed = error as Error & { analysisId?: string }
    if (failed.analysisId) {
      ElMessage.error(getErrorMessage(error, '分析失败'))
      await router.replace(`/conversation/analyses/${failed.analysisId}`)
      return
    }
    showProgress.value = false
    ElMessage.error(getErrorMessage(error, '分析失败，请稍后重试'))
  } finally {
    analyzing.value = false
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
        <div class="model-row">
          <label class="model-label">
            <el-icon aria-hidden="true">
              <Cpu/>
            </el-icon>
            分析模型
          </label>
          <el-select
              v-model="selectedModelId"
              class="model-select"
              placeholder="选择模型"
              :loading="modelsLoading"
              :disabled="analyzing || !modelOptions.length"
          >
            <el-option
                v-for="opt in modelOptions"
                :key="opt.id"
                :label="opt.displayName"
                :value="opt.id"
            >
              <span class="model-option-name">{{ opt.displayName }}</span>
              <span class="model-option-provider">{{ opt.provider }}</span>
            </el-option>
          </el-select>
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
          <router-link to="/conversation/analyses" class="link-history">查看历史记录</router-link>
        </div>
      </section>

      <section v-if="showProgress" class="result-panel kk-glass kk-glass--panel">
        <div class="panel-label">分析进度</div>
        <p v-if="selectedModelLabel && analyzing" class="progress-model-hint">
          本次使用：{{ selectedModelLabel }}
        </p>

        <div class="result-panel-body">
          <div
              v-if="progressLog.length"
              ref="progressListRef"
              class="progress-list"
          >
            <div v-for="(step, idx) in progressLog" :key="idx" class="progress-item">
              <span class="progress-emoji">{{ statusLabels[step.status]?.emoji ?? '⏳' }}</span>
              <div>
                <p class="progress-title">{{ statusLabels[step.status]?.title ?? '处理中' }}</p>
                <p v-if="step.message" class="progress-msg">{{ step.message }}</p>
                <p v-if="step.errorMessage" class="progress-msg progress-msg--error">{{ step.errorMessage }}</p>
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

          <section
              v-if="showStreamingPanel"
              ref="streamingScrollRef"
              class="streaming-panel"
              aria-label="实时句子预览"
          >
            <span class="streaming-float-btn" role="status">实时分析</span>
            <p v-if="streamingLive.errorsHint" class="streaming-errors-hint">
              {{ streamingLive.errorsHint }}
            </p>
            <div
                v-for="(row, idx) in streamingCommitted"
                :key="`commit-${idx}`"
                class="stream-card stream-card--done"
            >
              <p v-if="row.original" class="stream-line">
                <span class="stream-label">原句</span>{{ row.original }}
              </p>
              <p class="stream-line">
                <span class="stream-label">建议</span>
                <span v-if="row.suggestion" class="stream-suggestion">{{ row.suggestion }}</span>
                <span v-else class="stream-muted">—</span>
              </p>
            </div>
            <div v-if="streamingLive.original || streamingLive.suggestion" class="stream-card stream-card--live">
              <p v-if="isStreamingPlaceholder" class="stream-placeholder">
                正在接收分析结果…
              </p>
              <template v-else>
                <p v-if="streamingLive.original" class="stream-line">
                  <span class="stream-label">原句</span>{{ streamingLive.original }}
                </p>
                <p class="stream-line">
                  <span class="stream-label">建议</span>
                  <span v-if="streamingLive.suggestion" class="stream-suggestion">{{
                      streamingLive.suggestion
                    }}</span>
                  <span v-else class="stream-muted">—</span>
                </p>
              </template>
            </div>
          </section>

          <div v-else-if="analyzing" class="result-loading">
            <el-skeleton :rows="4" animated/>
          </div>
        </div>
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

.result-panel {
  height: 36rem;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.result-panel-body {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  gap: 0.65rem;
}

.progress-model-hint {
  margin: 0 0 0.75rem;
  font-size: 0.82rem;
  font-weight: 600;
  color: var(--kk-color-text-muted);
  flex-shrink: 0;
}

.panel-label {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  margin-bottom: 0.85rem;
  font-weight: 700;
  color: var(--kk-color-primary);
  font-size: 0.92rem;
  flex-shrink: 0;
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

.model-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.55rem 0.75rem;
  margin-top: 0.85rem;
  padding: 0.65rem 0.75rem;
  border-radius: var(--kk-radius-md);
  background: var(--kk-glass-inner-bg);
  border: 1px solid var(--kk-glass-inner-border);
}

.model-label {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  font-size: 0.82rem;
  font-weight: 700;
  color: var(--kk-color-primary);
  flex-shrink: 0;
}

.model-select {
  flex: 1;
  min-width: 10rem;
  max-width: 20rem;
}

.model-option-name {
  margin-right: 0.5rem;
}

.model-option-provider {
  font-size: 0.75rem;
  color: var(--kk-color-text-subtle);
}

.input-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.65rem;
  margin-top: 1rem;
}

.link-history {
  color: var(--kk-color-primary);
  font-weight: 600;
  text-decoration: none;
  margin-left: 0.25rem;
}

.link-history:hover {
  color: var(--kk-color-accent);
}

.progress-list {
  flex: 0 1 auto;
  max-height: 40%;
  min-height: 5.5rem;
  display: flex;
  flex-direction: column;
  gap: 0.55rem;
  overflow-y: auto;
  overscroll-behavior: contain;
  padding-right: 0.15rem;
}

.result-panel-body:not(:has(.streaming-panel)) .progress-list {
  flex: 1 1 auto;
  max-height: none;
}

.progress-item {
  display: flex;
  gap: 0.65rem;
  padding: 0.65rem 0.75rem;
  border-radius: var(--kk-radius-md);
  background: var(--kk-glass-inner-bg);
  border: 1px solid var(--kk-glass-inner-border);
  flex-shrink: 0;
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

.progress-msg--error {
  color: var(--kk-color-danger, #b42318);
}

.streaming-panel {
  position: relative;
  flex: 1 1 auto;
  min-height: 0;
  margin-top: 0;
  padding-top: 0.35rem;
  border-top: 1px solid var(--kk-glass-inner-border);
  overflow-y: auto;
  overscroll-behavior: contain;
  padding-right: 0.15rem;
}

.streaming-float-btn {
  position: sticky;
  top: 0.35rem;
  float: right;
  z-index: 2;
  display: inline-flex;
  align-items: center;
  margin: 0 0.1rem 0.55rem 0.75rem;
  padding: 0.28rem 0.72rem;
  border-radius: var(--kk-radius-pill);
  border: 1px solid color-mix(in srgb, var(--kk-color-primary) 16%, transparent);
  background: #fff;
  color: var(--kk-color-primary);
  font-size: 0.72rem;
  font-weight: 700;
  letter-spacing: 0.02em;
  line-height: 1.2;
  box-shadow: 0 4px 12px rgba(36, 39, 64, 0.1);
  transition:
      transform 0.16s ease,
      box-shadow 0.16s ease,
      background 0.16s ease,
      border-color 0.16s ease;
}

.streaming-float-btn:hover {
  transform: translateY(-1px);
  background: color-mix(in srgb, var(--kk-color-primary) 6%, white);
  border-color: color-mix(in srgb, var(--kk-color-primary) 28%, transparent);
  box-shadow: 0 8px 18px rgba(36, 39, 64, 0.14);
}

.streaming-errors-hint {
  margin: 0 0 0.5rem;
  font-size: 0.75rem;
  font-weight: 600;
  color: var(--kk-color-warn);
}

.stream-card {
  padding: 0.65rem 0.75rem;
  margin-bottom: 0.5rem;
  border-radius: var(--kk-radius-md);
  background: var(--kk-glass-inner-bg);
  border: 1px solid var(--kk-glass-inner-border);
  font-size: 0.82rem;
  line-height: 1.55;
}

.stream-card--live {
  border-color: color-mix(in srgb, var(--kk-color-primary) 22%, var(--kk-glass-inner-border));
  box-shadow: inset 0 0 0 1px color-mix(in srgb, var(--kk-color-primary) 8%, transparent);
}

.stream-line {
  margin: 0 0 0.35rem;
  color: var(--kk-color-text-muted);
}

.stream-line:last-child {
  margin-bottom: 0;
}

.stream-label {
  display: inline-block;
  min-width: 2.2rem;
  margin-right: 0.35rem;
  font-weight: 700;
  color: var(--kk-color-primary);
}

.stream-suggestion {
  color: var(--kk-color-link);
  font-weight: 600;
}

.stream-muted {
  color: var(--kk-color-text-subtle);
}

.stream-placeholder {
  margin: 0;
  color: var(--kk-color-text-subtle);
  font-style: italic;
}

.result-loading {
  padding: 0.5rem 0;
}

@media (max-width: 992px) {
  .analyze-grid {
    grid-template-columns: 1fr;
  }

  .result-panel {
    height: 32rem;
  }
}
</style>
