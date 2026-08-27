<script setup lang="ts">
import {CopyDocument, RefreshRight} from '@element-plus/icons-vue'
import {ElMessage, ElMessageBox} from 'element-plus'
import {computed, nextTick, onBeforeUnmount, onMounted, ref, watch} from 'vue'

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
const errorMessage = ref('')
const promptText = ref('')
const lastServerPrompt = ref('')
const selectedKeys = ref<string[]>([])
const lastRequestedKeys = ref<string[]>([])
const copied = ref(false)
const promptAtBottom = ref(false)
const promptShellRef = ref<HTMLElement | null>(null)
let copyResetTimer: ReturnType<typeof setTimeout> | null = null

function onResize() {
  viewportWidth.value = window.innerWidth
}

function resolvePromptTextarea(): HTMLTextAreaElement | null {
  return promptShellRef.value?.querySelector('textarea') ?? null
}

function syncPromptScrollFade() {
  const el = resolvePromptTextarea()
  if (!el) {
    promptAtBottom.value = true
    return
  }
  const remaining = el.scrollHeight - el.scrollTop - el.clientHeight
  promptAtBottom.value = remaining <= 4
}

function bindPromptTextareaScroll() {
  const el = resolvePromptTextarea()
  if (!el || el.dataset.ppFadeBound === '1') {
    syncPromptScrollFade()
    return
  }
  el.dataset.ppFadeBound = '1'
  el.addEventListener('scroll', syncPromptScrollFade, {passive: true})
  syncPromptScrollFade()
}

onMounted(() => {
  window.addEventListener('resize', onResize, {passive: true})
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  const el = resolvePromptTextarea()
  if (el) {
    el.removeEventListener('scroll', syncPromptScrollFade)
  }
  if (copyResetTimer) {
    clearTimeout(copyResetTimer)
  }
})

const fullscreen = computed(() => viewportWidth.value <= 640)
const dialogWidth = computed(() => (fullscreen.value ? '100%' : 'min(720px, 96vw)'))
const selectedCount = computed(() => selectedKeys.value.length)
const selectedSet = computed(() => new Set(selectedKeys.value))
const selectionDirty = computed(() => {
  if (selectedKeys.value.length !== lastRequestedKeys.value.length) {
    return true
  }
  return selectedKeys.value.some((key, index) => key !== lastRequestedKeys.value[index])
})
const promptEdited = computed(() => promptText.value !== lastServerPrompt.value)
const canUpdate = computed(() => selectionDirty.value && !loading.value)

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
  lastRequestedKeys.value = []
  promptText.value = ''
  lastServerPrompt.value = ''
  errorMessage.value = ''
  copied.value = false
}

async function requestPrompt() {
  loading.value = true
  errorMessage.value = ''
  const requestedKeys = [...selectedKeys.value]
  try {
    const {data} = await generatePracticePrompt({
      goals: props.goals,
      vocabulary: selectedVocabulary(),
    })
    promptText.value = data.prompt ?? ''
    lastServerPrompt.value = promptText.value
    lastRequestedKeys.value = requestedKeys
    await nextTick()
    bindPromptTextareaScroll()
    const el = resolvePromptTextarea()
    if (el) {
      el.scrollTop = 0
    }
    syncPromptScrollFade()
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
    return
  }
  selectedKeys.value = selectedKeys.value.filter((item) => item !== key)
}

async function onUpdateSelection() {
  if (promptEdited.value) {
    try {
      await ElMessageBox.confirm(
          '按所选卡片更新会覆盖文本框中的手动修改，确定继续？',
          '覆盖确认',
          {
            type: 'warning',
            confirmButtonText: '覆盖并更新',
            cancelButtonText: '取消',
          },
      )
    } catch {
      return
    }
  }
  await requestPrompt()
}

async function copyPrompt() {
  const text = promptText.value
  if (!text) {
    ElMessage.warning('还没有可复制的提示词')
    return
  }
  try {
    await navigator.clipboard.writeText(text)
    copied.value = true
    ElMessage.success('提示词已复制')
    if (copyResetTimer) {
      clearTimeout(copyResetTimer)
    }
    copyResetTimer = setTimeout(() => {
      copied.value = false
    }, 2000)
  } catch {
    ElMessage.error('复制失败，请稍后重试')
  }
}

watch(open, async (visible) => {
  if (!visible) {
    return
  }
  resetState()
  await requestPrompt()
  await nextTick()
  bindPromptTextareaScroll()
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
      :close-on-click-modal="!loading"
  >
    <div class="pp-body" v-loading="loading">
      <section v-if="goals.length" class="pp-block" aria-label="本场薄弱点">
        <h3 class="pp-heading">本场薄弱点</h3>
        <p class="pp-hint">按排名固定带上，不可取消</p>
        <ol class="pp-goal-list">
          <li v-for="goal in goals" :key="goal.rank" class="pp-goal">
            <span class="pp-goal-rank">Top {{ goal.rank }}</span>
            <div class="pp-goal-body">
              <strong>{{ goal.title }}</strong>
              <span v-if="goal.diagnosis" class="pp-goal-meta">{{ goal.diagnosis }}</span>
            </div>
          </li>
        </ol>
      </section>

      <section v-if="vocabCandidates.length" class="pp-block" aria-label="附加词汇">
        <div class="pp-heading-row">
          <h3 class="pp-heading">附加词汇</h3>
          <span class="pp-count">已选 {{ selectedCount }}/{{ MAX_PRACTICE_VOCAB }}</span>
        </div>
        <p class="pp-hint">默认不选，最多勾选 3 项作为加练</p>
        <ul class="pp-vocab-list">
          <li v-for="item in vocabCandidates" :key="item.key">
            <el-checkbox
                :model-value="selectedSet.has(item.key)"
                :disabled="!selectedSet.has(item.key) && selectedCount >= MAX_PRACTICE_VOCAB"
                @change="(checked: boolean) => toggleVocab(item.key, checked)"
            >
              <span class="pp-vocab-front">{{ item.front }}</span>
              <span class="pp-vocab-arrow">→</span>
              <span class="pp-vocab-back">{{ item.back }}</span>
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

      <label class="pp-prompt-label" for="practice-prompt-text">复练提示词</label>
      <div
          ref="promptShellRef"
          class="pp-prompt-shell"
          :class="{ 'pp-prompt-shell--at-bottom': promptAtBottom }"
      >
        <el-input
            id="practice-prompt-text"
            v-model="promptText"
            type="textarea"
            resize="none"
            placeholder="生成后可在此编辑，再复制去 ChatGPT"
            @input="syncPromptScrollFade"
        />
        <div class="pp-prompt-fade" aria-hidden="true"/>
      </div>
    </div>

    <template #footer>
      <div class="pp-footer">
        <el-button
            v-if="errorMessage"
            :icon="RefreshRight"
            :disabled="loading"
            @click="requestPrompt"
        >
          重新生成
        </el-button>
        <el-button :disabled="!canUpdate" @click="onUpdateSelection">
          按所选卡片更新
        </el-button>
        <el-button
            type="primary"
            :icon="CopyDocument"
            :disabled="!promptText || loading"
            @click="copyPrompt"
        >
          {{ copied ? '已复制' : '复制提示词' }}
        </el-button>
      </div>
    </template>
  </el-dialog>
</template>

<style scoped>
.pp-body {
  display: flex;
  flex-direction: column;
  gap: 0.9rem;
  min-height: 12rem;
}

.pp-block {
  padding: 0.75rem 0.85rem;
  border-radius: var(--kk-radius-md);
  background: var(--kk-glass-inner-bg);
  border: 1px solid color-mix(in srgb, var(--kk-color-primary) 10%, var(--kk-glass-inner-border));
}

.pp-heading-row {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 0.75rem;
}

.pp-heading {
  margin: 0;
  font-family: var(--kk-font-display);
  font-size: 0.95rem;
  font-weight: 700;
  color: var(--kk-color-primary);
}

.pp-hint,
.pp-count,
.pp-goal-meta {
  margin: 0.25rem 0 0;
  font-size: 0.78rem;
  color: var(--kk-color-text-muted);
}

.pp-count {
  margin: 0;
  font-weight: 700;
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
  align-items: flex-start;
  gap: 0.55rem;
}

.pp-goal-rank {
  flex-shrink: 0;
  margin-top: 0.1rem;
  padding: 0.12rem 0.45rem;
  border-radius: var(--kk-radius-pill);
  background: var(--kk-color-primary);
  color: #fff;
  font-size: 0.72rem;
  font-weight: 700;
}

.pp-goal-body {
  display: flex;
  flex-direction: column;
  gap: 0.15rem;
  min-width: 0;
}

.pp-goal-body strong {
  font-size: 0.88rem;
  color: var(--kk-color-text);
}

.pp-vocab-front {
  font-weight: 700;
  color: var(--kk-color-primary);
}

.pp-vocab-arrow {
  margin: 0 0.3rem;
  color: var(--kk-color-text-subtle);
}

.pp-vocab-back {
  font-family: var(--kk-font-mono);
  font-size: 0.84rem;
}

.pp-error {
  margin: 0;
}

.pp-prompt-label {
  font-size: 0.82rem;
  font-weight: 700;
  color: var(--kk-color-primary);
}

.pp-prompt-shell {
  position: relative;
  height: 12.5rem;
  border-radius: var(--kk-radius-md);
  overflow: hidden;
}

.pp-prompt-shell :deep(.el-textarea) {
  height: 100%;
}

.pp-prompt-shell :deep(.el-textarea__inner) {
  height: 100% !important;
  min-height: 100% !important;
  max-height: 100% !important;
  padding-bottom: 1.6rem;
  font-family: var(--kk-font-mono);
  font-size: 0.82rem;
  line-height: 1.55;
  resize: none;
  overflow-y: auto;
}

.pp-prompt-fade {
  position: absolute;
  left: 1px;
  right: 1px;
  bottom: 1px;
  height: 2.8rem;
  pointer-events: none;
  border-radius: 0 0 calc(var(--kk-radius-md) - 1px) calc(var(--kk-radius-md) - 1px);
  background: linear-gradient(
      to bottom,
      color-mix(in srgb, var(--kk-color-surface-solid) 0%, transparent) 0%,
      color-mix(in srgb, var(--kk-color-surface-solid) 55%, transparent) 45%,
      color-mix(in srgb, var(--kk-color-surface-solid) 88%, transparent) 78%,
      var(--kk-color-surface-solid) 100%
  );
  opacity: 1;
  transition: opacity 0.18s ease;
}

.pp-prompt-shell--at-bottom .pp-prompt-fade {
  opacity: 0;
}

@media (max-width: 640px) {
  .pp-prompt-shell {
    height: 11rem;
  }
}

.pp-footer {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 0.5rem;
}

@media (max-width: 640px) {
  .pp-footer {
    justify-content: stretch;
  }

  .pp-footer .el-button {
    flex: 1 1 auto;
  }
}
</style>

<style>
.practice-prompt-dialog.el-dialog {
  border-radius: var(--kk-radius-lg);
  overflow: hidden;
}

.practice-prompt-dialog .el-dialog__header {
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
  padding: 0.35rem 1.15rem 1rem;
  max-height: min(72vh, 40rem);
  overflow-y: auto;
}

.practice-prompt-dialog .el-dialog__footer {
  padding: 0.7rem 1.15rem 1rem;
}

@media (max-width: 640px) {
  .practice-prompt-dialog.el-dialog {
    border-radius: 0;
  }

  .practice-prompt-dialog .el-dialog__header {
    padding: 0.85rem 1rem 0.5rem;
  }

  .practice-prompt-dialog .el-dialog__body {
    padding: 0.25rem 0.85rem 0.85rem;
    max-height: none;
  }
}
</style>
