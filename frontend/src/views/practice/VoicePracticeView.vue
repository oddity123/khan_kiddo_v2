<script setup lang="ts">
import {Microphone, Mute, SwitchButton, VideoPause, VideoPlay} from '@element-plus/icons-vue'
import {computed, nextTick, onBeforeUnmount, onMounted, ref, watch} from 'vue'
import {useRouter} from 'vue-router'

import {trackFunnelEvent} from '@/api/funnel'
import type {VoicePracticeSession} from '@/api/voicePractice'
import VoiceLearningPanel from '@/components/practice/VoiceLearningPanel.vue'
import VoiceMaterialPicker from '@/components/practice/VoiceMaterialPicker.vue'
import VoicePracticeHistory from '@/components/practice/VoicePracticeHistory.vue'
import {useVoiceLearningPanel} from '@/composables/useVoiceLearningPanel'
import {useVoicePracticeRecord} from '@/composables/useVoicePracticeRecord'
import {useVoiceRealtime} from '@/composables/useVoiceRealtime'
import {captionSavePresentation, completedCaptionLines, handoffBlockReason, parseVoiceTranscript, writeVoiceHandoff} from '@/utils/voicePracticeTranscript'

/** 分析页「去练口语」可能写入；本页不再展示编辑框，仅静默消费一次。 */
const INSTRUCTIONS_KEY = 'kk_voice_instructions'

const STATUS_LABEL: Record<string, string> = {
  idle: '待开始',
  checking: '检查中',
  connecting: '连接中',
  live: '通话中',
  ended: '已结束',
  error: '出错',
}

const router = useRouter()
const {
  sessions,
  activeId,
  saveState,
  saveError,
  review,
  quotaBlocked,
  quotaMessage,
  beginError,
  quotaHit,
  activeSaved,
  refresh,
  begin,
  markLive,
  armClientCap,
  abandonIfUnused,
  saveLines,
  noteQuota,
  complete,
  openReview,
  closeReview,
} = useVoicePracticeRecord()
const {
  status,
  statusDetail,
  config,
  captions,
  userPartial,
  assistantPartial,
  muted,
  loadConfig,
  start,
  stop,
  interrupt,
  toggleMute,
} = useVoiceRealtime()
const learningPanel = useVoiceLearningPanel()

const seededInstructions = ref('')
const selectedCardIds = ref<string[]>([])
const selectedAnalysisIds = ref<string[]>([])
const captionsListRef = ref<HTMLElement | null>(null)
const materialDialogOpen = ref(false)
const historyDialogOpen = ref(false)
/** 窄屏：字幕与学习面板二选一（桌面双栏同时可见） */
const mobileTab = ref<'call' | 'learning'>('call')
/** 面板跟随当前回看的场次，否则是正在/刚结束的这一场；刷新后从历史回看即可恢复 */
const panelSessionId = computed(() => review.value?.id ?? activeId.value)

const isBusy = computed(() => status.value === 'live' || status.value === 'connecting')
const isPrep = computed(() => !isBusy.value && !review.value && captions.value.length === 0 && status.value !== 'ended')
const statusLabel = computed(() => STATUS_LABEL[status.value] ?? status.value)
const showConfigError = computed(() => Boolean(config.value && !config.value.configured))
const materialSummary = computed(() => {
  const cards = selectedCardIds.value.length
  const analyses = selectedAnalysisIds.value.length
  if (!cards && !analyses) {
    return '未选'
  }
  const parts: string[] = []
  if (cards) {
    parts.push(`${cards} 卡`)
  }
  if (analyses) {
    parts.push(`${analyses} 分析`)
  }
  return parts.join(' · ')
})
const dockAlert = computed(() => {
  if (showConfigError.value) {
    return '口语陪练暂未开通，请稍后再试或联系管理员。'
  }
  if (quotaMessage.value) {
    return quotaMessage.value
  }
  if (beginError.value) {
    return beginError.value
  }
  if (saveState.value === 'failed') {
    const base = captionSavePresentation('failed').text
    return saveError.value ? `${base}（${saveError.value}）` : base
  }
  return ''
})
const dockHint = computed(() => {
  if (dockAlert.value) {
    return ''
  }
  if (saveState.value === 'saved') {
    return captionSavePresentation('saved').text
  }
  if (statusDetail.value && status.value !== 'idle') {
    return statusDetail.value
  }
  return ''
})
const shownCaptions = computed(() => {
  if (review.value) {
    return parseVoiceTranscript(review.value.transcript)
  }
  return captions.value
})
const handoffSession = computed(() => review.value ?? activeSaved.value)
const handoffReason = computed(() => {
  if (saveState.value === 'failed') {
    return '先保存这场字幕，才能送去分析'
  }
  if (!handoffSession.value) {
    return ''
  }
  return handoffBlockReason(handoffSession.value.transcript, handoffSession.value.transcriptSaved)
})
const showHandoff = computed(() =>
  saveState.value !== 'idle' || review.value != null)

onMounted(async () => {
  const seeded = sessionStorage.getItem(INSTRUCTIONS_KEY)
  if (seeded) {
    seededInstructions.value = seeded
    sessionStorage.removeItem(INSTRUCTIONS_KEY)
  }
  await loadConfig()
  await refresh()
})

function readLines() {
  return captions.value
}

onBeforeUnmount(() => {
  if (activeId.value == null) {
    void stop()
    return
  }
  void complete(readLines, quotaHit.value, () => stop())
})

async function scrollCaptionsToBottom() {
  await nextTick()
  const el = captionsListRef.value
  if (!el) {
    return
  }
  el.scrollTo({top: el.scrollHeight, behavior: 'smooth'})
}

watch(panelSessionId, (id) => {
  learningPanel.reset()
  if (id != null) {
    void learningPanel.hydrate(id)
  }
})

watch([shownCaptions, userPartial, assistantPartial], () => {
  void scrollCaptionsToBottom()
}, {deep: true})

async function onStart() {
  materialDialogOpen.value = false
  historyDialogOpen.value = false
  const opened = await begin({
    cardIds: selectedCardIds.value,
    analysisIds: selectedAnalysisIds.value,
  })
  if (!opened || activeId.value == null) {
    return
  }
  const sessionId = activeId.value
  const outcome = await start({
    instructions: seededInstructions.value || undefined,
    practiceSessionId: sessionId,
    onLive: () => markLive(),
    onLearningEvent: learningPanel.applyEvent,
    onQuotaExhausted: () => {
      noteQuota()
      void complete(readLines, true, () => stop())
    },
    onClosed: (unexpected) => {
      if (unexpected) {
        void complete(readLines, quotaHit.value, () => stop())
      }
    },
  })
  if (outcome !== 'started') {
    await abandonIfUnused()
    return
  }
  armClientCap(() => {
    noteQuota()
    void complete(readLines, true, () => stop())
  })
}

async function onStop() {
  await complete(readLines, false, () => stop())
}

async function onRetrySave() {
  await saveLines(completedCaptionLines(captions.value), quotaHit.value)
}

function onHistoryOpen(session: VoicePracticeSession) {
  openReview(session)
  historyDialogOpen.value = false
  mobileTab.value = 'call'
}

function onHandoff(session: VoicePracticeSession | null) {
  if (!session?.transcript) {
    return
  }
  if (handoffBlockReason(session.transcript, session.transcriptSaved)) {
    return
  }
  writeVoiceHandoff({voiceSessionId: session.id, transcript: session.transcript})
  trackFunnelEvent({name: 'voice_to_analyze', voiceSessionId: session.id})
  void router.push({
    name: 'conversation-analyze',
    query: {voiceSessionId: String(session.id)},
  })
}

</script>

<template>
  <div class="voice-page">
    <header class="page-head">
      <div>
        <h1 class="page-title">口语陪练</h1>
        <p class="page-desc">
          勾选材料后开始对话；也可空着手练。通话中会显示字幕与本场学习。
          <template v-if="materialSummary !== '未选'">
            当前已选 {{ materialSummary }}。
          </template>
        </p>
      </div>
      <div class="page-actions">
        <el-button @click="historyDialogOpen = true">练习历史</el-button>
        <el-button type="primary" :disabled="isBusy" @click="materialDialogOpen = true">
          选择材料
        </el-button>
      </div>
    </header>

    <nav
      v-if="!isPrep"
      class="voice-tabs"
      aria-label="切换视图"
    >
      <button
        type="button"
        class="voice-tabs__btn"
        :class="{'is-active': mobileTab === 'call'}"
        @click="mobileTab = 'call'"
      >
        通话
      </button>
      <button
        type="button"
        class="voice-tabs__btn"
        :class="{'is-active': mobileTab === 'learning'}"
        @click="mobileTab = 'learning'"
      >
        本场学习<span v-if="learningPanel.itemCount.value" class="voice-tabs__count">{{ learningPanel.itemCount.value }}</span>
      </button>
    </nav>

    <div class="voice-body" :class="{'voice-body--prep': isPrep}">
      <div
        v-if="isPrep"
        class="voice-prep"
      >
        <p class="voice-prep__title">勾选材料后开始对话</p>
        <p class="voice-prep__hint">
          用右上角「选择材料」勾选成长卡 / 分析，也可先空着手练。通话中字幕与学习面板会出现在这里。
        </p>
      </div>

      <template v-else>
        <div class="voice-main" :class="{'is-tab-hidden': mobileTab !== 'call'}">
          <section class="voice-captions kk-glass kk-glass--panel" aria-label="实时字幕">
            <div class="voice-captions__head">
              <h2 class="voice-section-title">字幕</h2>
              <button v-if="review" type="button" class="voice-review-back" @click="closeReview">
                返回当前字幕
              </button>
            </div>
            <p v-if="review" class="voice-review-note">正在查看已保存的练习</p>
            <div class="voice-captions__body">
              <ul ref="captionsListRef" class="voice-captions__lines">
                <li
                  v-for="(line, idx) in shownCaptions"
                  :key="idx"
                  class="caption-row"
                  :class="{
                    'caption-row--user': line.role === 'user',
                    'caption-row--assistant': line.role === 'assistant',
                    'caption-row--system': line.role === 'system',
                  }"
                >
                  <span class="caption-meta">
                    {{ line.role === 'user' ? '你' : line.role === 'system' ? '系统' : '陪练' }}
                  </span>
                  <div
                    class="caption-bubble"
                    :class="{
                      'caption-bubble--user': line.role === 'user',
                      'caption-bubble--assistant': line.role === 'assistant',
                      'caption-bubble--system': line.role === 'system',
                    }"
                  >
                    {{ line.text }}
                  </div>
                </li>
                <li v-if="!review && userPartial" class="caption-row caption-row--user is-partial">
                  <span class="caption-meta">你</span>
                  <div class="caption-bubble caption-bubble--user">{{ userPartial }}…</div>
                </li>
                <li v-if="!review && assistantPartial" class="caption-row caption-row--assistant is-partial">
                  <span class="caption-meta">陪练</span>
                  <div class="caption-bubble caption-bubble--assistant">{{ assistantPartial }}…</div>
                </li>
                <li
                  v-if="!shownCaptions.length && (review || (!userPartial && !assistantPartial))"
                  class="voice-captions__empty"
                >
                  {{ review ? '这场没有完成的字幕。' : '开始后，这里会显示识别与回复文本。' }}
                </li>
              </ul>
            </div>
          </section>
        </div>

        <VoiceLearningPanel
          class="voice-aside"
          :class="{'is-tab-hidden': mobileTab !== 'learning'}"
          :state="learningPanel.state.value"
          :loading="learningPanel.loading.value"
          :error="learningPanel.error.value"
          :live="isBusy"
        />
      </template>
    </div>

    <footer class="voice-dock">
      <p v-if="dockAlert" class="voice-dock__alert" role="alert">
        {{ dockAlert }}
        <button
          v-if="saveState === 'failed'"
          type="button"
          class="voice-retry"
          @click="onRetrySave"
        >
          再试一次保存
        </button>
      </p>
      <p v-else-if="dockHint" class="voice-dock__hint">{{ dockHint }}</p>

      <section v-if="showHandoff" class="voice-handoff">
        <el-button
          type="primary"
          plain
          :disabled="Boolean(handoffReason)"
          @click="onHandoff(handoffSession)"
        >
          送去分析
        </el-button>
        <p v-if="handoffReason" class="voice-handoff__reason">{{ handoffReason }}</p>
      </section>

      <section class="voice-controls">
        <button
          type="button"
          class="voice-status-chip"
          :data-state="status"
          :title="statusDetail || statusLabel"
          aria-live="polite"
        >
          <span class="voice-status-chip__dot" :data-state="status" />
          <span class="voice-status-chip__label">{{ statusLabel }}</span>
        </button>
        <el-button
          type="primary"
          size="large"
          :icon="VideoPlay"
          :disabled="isBusy || quotaBlocked || saveState === 'failed' || (config !== null && !config.configured)"
          @click="onStart"
        >
          开始对话
        </el-button>
        <el-button
          size="large"
          :icon="VideoPause"
          :disabled="!isBusy"
          @click="onStop"
        >
          结束
        </el-button>
        <el-button
          size="large"
          :icon="SwitchButton"
          :disabled="status !== 'live'"
          @click="interrupt"
        >
          打断播报
        </el-button>
        <el-button
          size="large"
          :icon="muted ? Mute : Microphone"
          :disabled="status !== 'live'"
          @click="toggleMute"
        >
          {{ muted ? '取消静音' : '静音' }}
        </el-button>
      </section>
    </footer>

    <el-dialog
      v-model="materialDialogOpen"
      title="选择材料"
      width="min(720px, 94vw)"
      class="voice-dialog"
      destroy-on-close
      append-to-body
    >
      <VoiceMaterialPicker
        v-model:card-ids="selectedCardIds"
        v-model:analysis-ids="selectedAnalysisIds"
        :disabled="isBusy"
        class="voice-dialog__picker"
      />
      <template #footer>
        <el-button type="primary" @click="materialDialogOpen = false">完成</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="historyDialogOpen"
      title="练习历史"
      width="min(560px, 94vw)"
      class="voice-dialog"
      destroy-on-close
      append-to-body
    >
      <VoicePracticeHistory
        class="voice-dialog__history"
        :sessions="sessions"
        :disabled="saveState === 'failed'"
        @open="onHistoryOpen"
        @handoff="onHandoff"
      />
    </el-dialog>
  </div>
</template>

<style scoped>
.voice-page {
  flex: 1 1 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  overflow: hidden;
  font-family: var(--kk-font-body);
  color: var(--kk-color-text);
}

.voice-body {
  flex: 1 1 0;
  min-height: 0;
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(18rem, 24rem);
  grid-template-rows: minmax(0, 1fr);
  gap: 0.75rem;
}

.voice-body--prep {
  grid-template-columns: minmax(0, 1fr);
}

.voice-main {
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.voice-aside {
  min-height: 0;
}

.voice-prep {
  align-self: center;
  justify-self: center;
  max-width: 28rem;
  text-align: center;
  padding: 1.5rem 1rem;
}

.voice-prep__title {
  margin: 0;
  font-family: var(--kk-font-display);
  font-size: 1.25rem;
  font-weight: 700;
  color: var(--kk-color-primary);
}

.voice-prep__hint {
  margin: 0.65rem 0 0;
  color: var(--kk-color-text-muted);
  font-size: 0.92rem;
  line-height: 1.55;
}

.voice-tabs {
  display: none;
  flex-shrink: 0;
  gap: 0.4rem;
}

.voice-tabs__btn {
  flex: 1 1 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 0.4rem;
  padding: 0.55rem 0.75rem;
  border: 1px solid var(--kk-glass-inner-border);
  border-radius: var(--kk-radius-pill);
  background: var(--kk-glass-inner-bg);
  color: var(--kk-color-text-secondary);
  font-family: var(--kk-font-body);
  font-size: 0.92rem;
  font-weight: 600;
  cursor: pointer;
}

.voice-tabs__btn.is-active {
  background: var(--kk-color-primary);
  border-color: var(--kk-color-primary);
  color: #fff;
}

.voice-tabs__count {
  min-width: 1.25rem;
  padding: 0 0.35rem;
  border-radius: var(--kk-radius-pill);
  background: var(--kk-color-accent-bg);
  color: var(--kk-color-accent-text);
  font-size: 0.74rem;
  line-height: 1.25rem;
}

@media (max-width: 991px) {
  .voice-body:not(.voice-body--prep) {
    grid-template-columns: minmax(0, 1fr);
  }

  .voice-tabs {
    display: flex;
  }

  .is-tab-hidden {
    display: none;
  }
}

/* 字幕区玻璃块：overflow:hidden 下只保留顶高光，避免外阴影竖边鬼影 */
.voice-page .voice-captions {
  box-shadow: inset 0 1px 0 var(--kk-glass-highlight);
}

/* 与「分析历史」AnalysisListView.page-head 对齐：标题+说明 | el-button 操作 */
.page-head {
  flex-shrink: 0;
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  justify-content: space-between;
  gap: 1rem;
  margin-bottom: 0.5rem;
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
  max-width: 36rem;
}

.page-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
}

.voice-captions {
  flex: 1 1 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  padding: 0.85rem 1.1rem;
}

.voice-captions__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
  flex-shrink: 0;
}

.voice-captions__head .voice-section-title {
  margin-bottom: 0;
}

.voice-section-title {
  display: block;
  margin: 0 0 0.55rem;
  font-family: var(--kk-font-display);
  font-size: 1.05rem;
  font-weight: 700;
  color: var(--kk-color-primary);
}

.voice-review-note {
  margin: 0.45rem 0 0;
  font-size: 0.9rem;
  line-height: 1.5;
  color: var(--kk-color-text-muted);
  flex-shrink: 0;
}

.voice-review-back,
.voice-retry {
  border: none;
  background: none;
  padding: 0;
  color: var(--kk-color-primary);
  font-family: var(--kk-font-body);
  font-weight: 600;
  cursor: pointer;
}

.voice-review-back {
  margin: 0;
}

.voice-retry {
  margin-left: 0.65rem;
}

.voice-captions__body {
  flex: 1 1 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  isolation: isolate;
}

.voice-captions__lines {
  list-style: none;
  margin: 0;
  padding: 0.9rem 0.7rem 0.55rem 0.85rem;
  display: flex;
  flex-direction: column;
  gap: 0.85rem;
  flex: 1 1 0;
  min-height: 0;
  overflow-y: auto;
  overscroll-behavior: contain;
  scroll-behavior: smooth;
  scrollbar-width: thin;
  scrollbar-color: color-mix(in srgb, var(--kk-color-primary) 22%, transparent) transparent;
}

.voice-captions__lines::-webkit-scrollbar {
  width: 8px;
}

.voice-captions__lines::-webkit-scrollbar-track {
  background: transparent;
}

.voice-captions__lines::-webkit-scrollbar-thumb {
  border-radius: 999px;
  background: color-mix(in srgb, var(--kk-color-primary) 22%, transparent);
  border: 2px solid transparent;
  background-clip: padding-box;
}

.caption-row {
  display: flex;
  flex-direction: column;
  gap: 0.3rem;
  max-width: min(100%, 36rem);
}

.caption-row--user {
  align-self: flex-end;
  align-items: flex-end;
}

.caption-row--assistant,
.caption-row--system {
  align-self: flex-start;
  align-items: flex-start;
}

.caption-meta {
  font-size: 0.74rem;
  color: var(--kk-color-text-subtle, var(--kk-color-text-muted));
  padding: 0 0.2rem;
}

.caption-bubble {
  padding: 0.75rem 0.9rem;
  border-radius: 14px;
  font-family: var(--kk-font-body);
  font-size: 0.95rem;
  line-height: 1.55;
  word-break: break-word;
}

.caption-bubble--user {
  background: linear-gradient(135deg, var(--kk-color-primary) 0%, var(--kk-color-primary-soft) 100%);
  color: #fff;
  border-bottom-right-radius: 4px;
}

.caption-bubble--assistant {
  background: var(--kk-glass-inner-bg);
  color: var(--kk-color-text);
  border: 1px solid var(--kk-glass-inner-border);
  border-bottom-left-radius: 4px;
  font-family: var(--kk-font-mono);
  font-size: 0.9rem;
}

.caption-bubble--system {
  background: color-mix(in srgb, var(--kk-color-accent, #b7791f) 12%, transparent);
  color: var(--kk-color-text);
  border: 1px dashed color-mix(in srgb, var(--kk-color-accent, #b7791f) 45%, transparent);
  border-bottom-left-radius: 4px;
  font-family: var(--kk-font-mono);
  font-size: 0.82rem;
}

.voice-captions__empty {
  color: var(--kk-color-text-muted);
  font-size: 0.9rem;
  text-align: center;
  padding: 1.5rem 0.5rem;
  align-self: stretch;
}

.voice-dock {
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  gap: 0.55rem;
  padding-bottom: 0.25rem;
}

.voice-dock__alert {
  margin: 0;
  font-size: 0.9rem;
  line-height: 1.45;
  color: var(--el-color-danger);
}

.voice-dock__hint {
  margin: 0;
  font-size: 0.88rem;
  line-height: 1.45;
  color: var(--kk-color-text-muted);
}

.voice-handoff {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.5rem 0.75rem;
}

.voice-handoff__reason {
  margin: 0;
  font-size: 0.9rem;
  color: var(--el-color-danger);
}

.voice-controls {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.65rem;
}

.voice-status-chip {
  display: inline-flex;
  align-items: center;
  gap: 0.45rem;
  height: 40px;
  padding: 0 0.9rem;
  border-radius: var(--kk-radius-pill);
  border: 1px solid var(--kk-glass-inner-border);
  background: var(--kk-glass-inner-bg);
  color: var(--kk-color-text-secondary);
  font-family: var(--kk-font-body);
  font-size: 0.88rem;
  font-weight: 600;
  cursor: default;
}

.voice-status-chip__dot {
  width: 0.55rem;
  height: 0.55rem;
  border-radius: 50%;
  background: var(--kk-color-text-muted);
}

.voice-status-chip__dot[data-state='live'] {
  background: var(--kk-color-accent);
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--kk-color-accent) 25%, transparent);
}

.voice-status-chip__dot[data-state='error'] {
  background: var(--el-color-danger);
}

.voice-status-chip__dot[data-state='connecting'],
.voice-status-chip__dot[data-state='checking'] {
  background: var(--kk-color-primary);
  animation: pulse 1.2s ease-in-out infinite;
}

.is-partial {
  opacity: 0.78;
}

@keyframes pulse {
  0%,
  100% {
    opacity: 1;
  }
  50% {
    opacity: 0.45;
  }
}

/* 弹窗内去掉嵌套玻璃与矮滚动区 */
.voice-dialog__picker :deep(.material-picker) {
  box-shadow: none;
  border: none;
  background: transparent;
  padding: 0;
}

.voice-dialog__picker :deep(.material-picker__list) {
  max-height: min(40vh, 16rem);
}

.voice-dialog__history :deep(.voice-history__list) {
  max-height: min(50vh, 22rem);
}

.voice-dialog__history :deep(.voice-history__title) {
  display: none;
}

@media (max-width: 640px) {
  .voice-controls .el-button {
    flex: 1 1 calc(50% - 0.65rem);
  }

  .voice-status-chip {
    flex: 1 1 100%;
    justify-content: center;
  }
}
</style>
