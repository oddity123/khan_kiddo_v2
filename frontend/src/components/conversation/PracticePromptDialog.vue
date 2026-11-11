<script setup lang="ts">
import {CircleCheck, CopyDocument, Lock, RefreshRight} from '@element-plus/icons-vue'
import {ElMessage} from 'element-plus'
import {computed, onBeforeUnmount, onMounted, ref, watch} from 'vue'

import {generatePracticePrompt} from '@/api/conversationAnalysis'
import type {PracticePromptGoal, PracticeVocabulary} from '@/types/conversation'
import {getErrorMessage} from '@/utils/error'
import {MAX_PRACTICE_VOCAB, type PracticeVocabCandidate} from '@/utils/practicePrompt'

const open = defineModel<boolean>({default: false})

const props = withDefaults(
    defineProps<{
      goals: PracticePromptGoal[]
      vocabCandidates: PracticeVocabCandidate[]
    }>(),
    {
      goals: () => [],
      vocabCandidates: () => [],
    },
)

const viewportWidth = ref(typeof window !== 'undefined' ? window.innerWidth : 1024)
const loading = ref(false)
const copying = ref(false)
const errorMessage = ref('')
const promptText = ref('')
const selectedKeys = ref<string[]>([])
const copied = ref(false)
let copyResetTimer: ReturnType<typeof setTimeout> | null = null

function onResize() {
  viewportWidth.value = window.innerWidth
}

onMounted(() => {
  window.addEventListener('resize', onResize, {passive: true})
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  if (copyResetTimer) {
    clearTimeout(copyResetTimer)
  }
})

const fullscreen = computed(() => viewportWidth.value <= 640)
const dialogWidth = computed(() => (fullscreen.value ? '100%' : 'min(720px, 96vw)'))
const selectedCount = computed(() => selectedKeys.value.length)
const selectedSet = computed(() => new Set(selectedKeys.value))

function selectedVocabulary(): PracticeVocabulary[] {
  const byKey = new Map(props.vocabCandidates.map((item) => [item.key, item]))
  return selectedKeys.value
      .map((key) => byKey.get(key))
      .filter((item): item is PracticeVocabCandidate => Boolean(item))
      .map((item) => ({
        front: item.front,
        back: item.back,
        originalSentence: item.originalSentence,
      }))
}

function resetState() {
  selectedKeys.value = []
  promptText.value = ''
  errorMessage.value = ''
  copied.value = false
}

async function requestPrompt() {
  loading.value = true
  errorMessage.value = ''
  try {
    const {data} = await generatePracticePrompt({
      goals: props.goals,
      vocabulary: selectedVocabulary(),
    })
    promptText.value = data.prompt ?? ''
  } catch (error) {
    errorMessage.value = getErrorMessage(error, '生成复练提示词失败')
  } finally {
    loading.value = false
  }
}

function toggleVocab(key: string, checked: boolean) {
  if (checked) {
    if (selectedKeys.value.includes(key) || selectedKeys.value.length >= MAX_PRACTICE_VOCAB) {
      return
    }
    selectedKeys.value = [...selectedKeys.value, key]
  } else {
    selectedKeys.value = selectedKeys.value.filter((item) => item !== key)
  }
  requestPrompt()
}

async function copyPrompt() {
  copying.value = true
  errorMessage.value = ''
  try {
    const {data} = await generatePracticePrompt({
      goals: props.goals,
      vocabulary: selectedVocabulary(),
    })
    const text = data.prompt ?? ''
    promptText.value = text

    if (!text) {
      ElMessage.warning('生成的提示词为空')
      return
    }

    await navigator.clipboard.writeText(text)
    copied.value = true
    ElMessage.success('提示词已复制到剪贴板')
    if (copyResetTimer) {
      clearTimeout(copyResetTimer)
    }
    copyResetTimer = setTimeout(() => {
      copied.value = false
    }, 2000)
  } catch (error) {
    errorMessage.value = getErrorMessage(error, '生成复练提示词失败')
    ElMessage.error(errorMessage.value)
  } finally {
    copying.value = false
  }
}

watch(open, async (visible) => {
  if (!visible) {
    return
  }
  resetState()
  await requestPrompt()
})
</script>

<template>
  <el-dialog
      v-model="open"
      class="practice-prompt-dialog"
      title="带着薄弱点再练一轮"
      :width="dialogWidth"
      append-to-body
      align-center
      :fullscreen="fullscreen"
      :close-on-click-modal="!loading && !copying"
  >
    <div class="pp-body" v-loading="loading">
      <section v-if="goals.length" class="pp-block" aria-label="本场薄弱点">
        <div class="pp-heading-row pp-heading-row--start">
          <h3 class="pp-heading">本场薄弱点</h3>
          <el-icon
              class="pp-heading-icon"
              title="按排名固定带上，不可取消"
              aria-label="按排名固定带上，不可取消"
          >
            <Lock/>
          </el-icon>
        </div>
        <ol class="pp-goal-list">
          <li v-for="goal in goals" :key="goal.rank" class="pp-goal">
            <div class="pp-goal-head">
              <span class="pp-goal-rank">Top {{ goal.rank }}</span>
              <strong class="pp-goal-title">{{ goal.title }}</strong>
            </div>
            <p v-if="goal.diagnosis" class="pp-goal-meta">{{ goal.diagnosis }}</p>
          </li>
        </ol>
      </section>

      <section v-if="vocabCandidates.length" class="pp-block" aria-label="附加词汇">
        <div class="pp-heading-row">
          <div class="pp-heading-row--start">
            <h3 class="pp-heading">附加词汇</h3>
            <el-icon
                class="pp-heading-icon"
                title="默认不选，最多勾选 3 项作为加练"
                aria-label="默认不选，最多勾选 3 项作为加练"
            >
              <CircleCheck/>
            </el-icon>
          </div>
          <span class="pp-count">已选 {{ selectedCount }}/{{ MAX_PRACTICE_VOCAB }}</span>
        </div>
        <ul class="pp-vocab-list">
          <li v-for="item in vocabCandidates" :key="item.key" class="pp-vocab-item">
            <el-checkbox
                :model-value="selectedSet.has(item.key)"
                :disabled="!selectedSet.has(item.key) && selectedCount >= MAX_PRACTICE_VOCAB"
                @change="(checked: boolean) => toggleVocab(item.key, checked)"
            >
              <span class="pp-vocab-label">
                <span class="pp-vocab-front">{{ item.front }}</span>
                <span class="pp-vocab-pair">
                  <span class="pp-vocab-arrow" aria-hidden="true">→</span>
                  <span class="pp-vocab-back">{{ item.back }}</span>
                </span>
              </span>
            </el-checkbox>
          </li>
        </ul>
      </section>

      <el-alert
          v-if="errorMessage"
          class="pp-error"
          type="error"
          :title="errorMessage"
          show-icon
          :closable="false"
      />

      <div class="pp-prompt-section">
        <label class="pp-prompt-label" for="practice-prompt-text">提示词预览</label>
        <el-input
            id="practice-prompt-text"
            v-model="promptText"
            type="textarea"
            :autosize="true"
            resize="none"
            placeholder="生成后可在此预览，点击复制即可带入 ChatGPT"
        />
      </div>

      <!-- 悬浮在弹窗底部、固定于可视区域上方的操作栏 -->
      <div class="pp-floating-footer">
        <el-button
            v-if="errorMessage"
            class="pp-btn-glass"
            size="large"
            :icon="RefreshRight"
            :disabled="loading || copying"
            @click="requestPrompt"
        >
          重新生成
        </el-button>
        <el-button
            class="pp-btn-copy"
            type="primary"
            size="large"
            :icon="CopyDocument"
            :loading="copying || loading"
            @click="copyPrompt"
        >
          {{ copied ? '已复制' : '复制提示词' }}
        </el-button>
      </div>
    </div>
  </el-dialog>
</template>

<style scoped>
.pp-body {
  display: flex;
  flex-direction: column;
  gap: 0.9rem;
  min-height: 8rem;
}

.pp-block {
  padding: 0.75rem 0.85rem;
  border-radius: var(--kk-radius-md);
  background: var(--kk-glass-inner-bg);
  border: 1px solid color-mix(in srgb, var(--kk-color-primary) 10%, var(--kk-glass-inner-border));
  min-width: 0;
  overflow: hidden;
}

.pp-heading-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
}

.pp-heading-row--start {
  display: flex;
  align-items: center;
  justify-content: flex-start;
  flex-wrap: wrap;
  gap: 0.35rem 0.45rem;
}

.pp-heading {
  margin: 0;
  font-family: var(--kk-font-display);
  font-size: 0.95rem;
  font-weight: 700;
  color: var(--kk-color-primary);
}

.pp-heading-icon {
  flex-shrink: 0;
  font-size: 0.95rem;
  color: var(--kk-color-text-muted);
}

.pp-count {
  margin: 0;
  font-size: 0.78rem;
  font-weight: 700;
  color: var(--kk-color-text-muted);
  white-space: nowrap;
}

.pp-goal-list,
.pp-vocab-list {
  margin: 0.55rem 0 0;
  padding: 0;
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 0.45rem;
}

.pp-goal {
  display: flex;
  flex-direction: column;
  gap: 0.2rem;
  min-width: 0;
}

.pp-goal-head {
  display: flex;
  align-items: center;
  gap: 0.55rem;
  min-width: 0;
}

.pp-goal-rank {
  flex-shrink: 0;
  padding: 0.12rem 0.45rem;
  border-radius: var(--kk-radius-pill);
  background: var(--kk-color-primary);
  color: #fff;
  font-size: 0.72rem;
  font-weight: 700;
}

.pp-goal-title {
  font-size: 0.88rem;
  color: var(--kk-color-text);
  min-width: 0;
}

.pp-goal-meta {
  margin: 0;
  font-size: 0.78rem;
  line-height: 1.45;
  color: var(--kk-color-text-muted);
}

.pp-vocab-item {
  min-width: 0;
}

.pp-vocab-item :deep(.el-checkbox) {
  display: flex;
  align-items: flex-start;
  width: 100%;
  height: auto;
  margin-right: 0;
  white-space: normal;
}

.pp-vocab-item :deep(.el-checkbox__input) {
  flex-shrink: 0;
  margin-top: 0.18rem;
}

.pp-vocab-item :deep(.el-checkbox__label) {
  flex: 1 1 auto;
  min-width: 0;
  white-space: normal;
  line-height: 1.45;
  padding-left: 0.45rem;
}

.pp-vocab-label {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 0.15rem 0.35rem;
  min-width: 0;
}

.pp-vocab-front {
  font-weight: 700;
  color: var(--kk-color-primary);
  overflow-wrap: anywhere;
}

.pp-vocab-pair {
  display: inline-flex;
  align-items: baseline;
  gap: 0.3rem;
  min-width: 0;
  max-width: 100%;
}

.pp-vocab-arrow {
  flex-shrink: 0;
  color: var(--kk-color-text-subtle);
}

.pp-vocab-back {
  font-family: var(--kk-font-mono);
  font-size: 0.84rem;
  overflow-wrap: anywhere;
  word-break: break-word;
}

@media (max-width: 640px) {
  .pp-vocab-label {
    flex-direction: column;
    align-items: stretch;
    gap: 0.2rem;
  }

  .pp-vocab-pair {
    display: flex;
  }
}

.pp-error {
  margin: 0;
}

.pp-prompt-section {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
}

.pp-prompt-label {
  font-size: 0.82rem;
  font-weight: 700;
  color: var(--kk-color-primary);
}

.pp-body :deep(.el-textarea__inner) {
  font-family: var(--kk-font-mono);
  font-size: 0.82rem;
  line-height: 1.55;
  border-radius: var(--kk-radius-md);
  padding: 0.75rem 0.85rem;
  background: var(--kk-glass-inner-bg);
  border: 1px solid color-mix(in srgb, var(--kk-color-primary) 10%, var(--kk-glass-inner-border));
  color: var(--kk-color-text);
  overflow: hidden;
  resize: none;
  box-shadow: none;
  transition: border-color 0.2s ease, background 0.2s ease;
}

.pp-body :deep(.el-textarea__inner:focus) {
  background: var(--kk-color-surface-solid);
  border-color: var(--kk-color-primary);
  outline: none;
}

/* 浮动在弹窗底部的操作栏，固定位置不随内容滚动，底部留出空间 */
.pp-floating-footer {
  position: absolute;
  left: 1.15rem;
  right: 1.15rem;
  bottom: 1.15rem;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 0.65rem;
  pointer-events: none;
  z-index: 10;
}

.pp-floating-footer .el-button {
  pointer-events: auto;
}

.pp-btn-glass {
  border-radius: var(--kk-radius-pill);
  background: var(--kk-glass-bg);
  border: 1px solid var(--kk-glass-border);
  color: var(--kk-color-primary);
  font-weight: 600;
  box-shadow: var(--kk-shadow-card);
  backdrop-filter: blur(var(--kk-glass-blur));
  -webkit-backdrop-filter: blur(var(--kk-glass-blur));
  transition: all 0.2s ease;
  height: 2.75rem;
  padding: 0 1.25rem;
  font-size: 0.92rem;
}

.pp-btn-glass:hover:not(:disabled) {
  background: var(--kk-glass-hover-bg);
  border-color: var(--kk-color-primary);
}

.pp-btn-copy {
  border-radius: var(--kk-radius-pill);
  font-weight: 700;
  box-shadow: var(--kk-shadow-btn);
  transition: all 0.2s ease;
  height: 2.75rem;
  padding: 0 1.5rem;
  font-size: 0.95rem;
}

.pp-btn-copy:hover:not(:disabled) {
  box-shadow: var(--kk-shadow-btn-hover);
}

@media (max-width: 640px) {
  .pp-floating-footer {
    left: 0.85rem;
    right: 0.85rem;
    bottom: max(0.95rem, env(safe-area-inset-bottom, 0px));
    justify-content: stretch;
  }

  .pp-floating-footer .el-button {
    flex: 1 1 0;
    min-width: 0;
    margin-left: 0;
    height: 2.85rem;
    font-size: 0.95rem;
  }
}
</style>

<style>
.practice-prompt-dialog.el-dialog {
  position: relative;
  border-radius: var(--kk-radius-lg);
  overflow: hidden;
  display: flex;
  flex-direction: column;
  max-height: 90vh;
  background: var(--kk-glass-panel-bg);
  border: 1px solid var(--kk-glass-panel-border);
  box-shadow: var(--kk-glass-panel-shadow);
  backdrop-filter: blur(var(--kk-glass-blur));
  -webkit-backdrop-filter: blur(var(--kk-glass-blur));
}

.practice-prompt-dialog .el-dialog__header {
  flex-shrink: 0;
  padding: 1rem 1.15rem 0.65rem;
  margin-right: 0;
}

.practice-prompt-dialog .el-dialog__title {
  font-family: var(--kk-font-display);
  font-size: 1.12rem;
  font-weight: 800;
  color: var(--kk-color-primary);
}

.practice-prompt-dialog .el-dialog__body {
  flex: 1 1 auto;
  min-height: 0;
  padding: 0.35rem 1.15rem 4.5rem;
  max-height: min(76vh, 42rem);
  overflow-y: auto;
  -webkit-overflow-scrolling: touch;
}

@media (max-width: 640px) {
  .practice-prompt-dialog.el-dialog {
    border-radius: 0;
    margin: 0;
    height: 100%;
    max-height: 100dvh;
    display: flex;
    flex-direction: column;
    overflow: hidden;
  }

  .practice-prompt-dialog .el-dialog__header {
    flex-shrink: 0;
    padding: 0.85rem 1rem 0.5rem;
  }

  .practice-prompt-dialog .el-dialog__body {
    flex: 1 1 auto;
    min-height: 0;
    max-height: none;
    overflow-y: auto;
    -webkit-overflow-scrolling: touch;
    padding: 0.25rem 0.85rem max(4.75rem, calc(3.8rem + env(safe-area-inset-bottom, 0px)));
  }
}
</style>
