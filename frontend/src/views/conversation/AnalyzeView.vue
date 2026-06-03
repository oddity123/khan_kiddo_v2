<script setup lang="ts">
import {Document, RefreshRight, Upload} from '@element-plus/icons-vue'
import {ElMessage} from 'element-plus'
import {computed, ref} from 'vue'
import {useRouter} from 'vue-router'

import {analyzeConversationStream} from '@/api/conversationAnalysis'
import type {ConversationAnalysisProgress} from '@/types/conversation'
import {PROGRESS_STATUS} from '@/types/conversation'
import {getErrorMessage} from '@/utils/error'

const router = useRouter()

const MIN_LENGTH = 10
const content = ref('')
const analyzing = ref(false)
const showProgress = ref(false)
const progressLog = ref<ConversationAnalysisProgress[]>([])
const abortController = ref<AbortController | null>(null)

const charCount = computed(() => content.value.length)

const statusLabels: Record<string, { emoji: string; title: string }> = {
  [PROGRESS_STATUS.START]: {emoji: '🚀', title: '开始分析'},
  [PROGRESS_STATUS.VALIDATING]: {emoji: '🔍', title: '验证请求'},
  [PROGRESS_STATUS.SEPARATING]: {emoji: '✂️', title: '分离对话'},
  [PROGRESS_STATUS.ANALYZING]: {emoji: '🤖', title: 'AI 分析'},
  [PROGRESS_STATUS.PARSING]: {emoji: '📊', title: '解析结果'},
  [PROGRESS_STATUS.SUMMARIZING]: {emoji: '📝', title: '学习概要'},
}

function onProgress(event: ConversationAnalysisProgress) {
  if (event.status === PROGRESS_STATUS.COMPLETED) {
    return
  }
  if (event.status === PROGRESS_STATUS.ERROR) {
    progressLog.value.push(event)
    return
  }

  const last = progressLog.value[progressLog.value.length - 1]
  if (!last || last.status !== event.status || last.message !== event.message) {
    progressLog.value.push(event)
  }
}

function resetForm() {
  if (analyzing.value) {
    return
  }
  content.value = ''
  progressLog.value = []
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

  analyzing.value = true
  showProgress.value = true
  progressLog.value = []
  abortController.value?.abort()
  abortController.value = new AbortController()

  try {
    const analysisResult = await analyzeConversationStream(
        {conversationContent: text},
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

        <div v-if="progressLog.length" class="progress-list">
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

        <div v-if="analyzing" class="result-loading">
          <el-skeleton :rows="4" animated/>
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
  display: flex;
  flex-direction: column;
  gap: 0.55rem;
  margin-bottom: 1rem;
  max-height: 20rem;
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

.progress-msg--error {
  color: var(--kk-color-danger, #b42318);
}

.result-loading {
  padding: 0.5rem 0;
}

@media (max-width: 992px) {
  .analyze-grid {
    grid-template-columns: 1fr;
  }
}
</style>
