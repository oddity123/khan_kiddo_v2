<script setup lang="ts">
import {ArrowLeft} from '@element-plus/icons-vue'
import {computed, nextTick, onBeforeUnmount, onMounted, ref, watch} from 'vue'
import {useRouter} from 'vue-router'

import {trackFunnelEvent} from '@/api/funnel'
import type {VoicePracticeSession} from '@/api/voicePractice'
import VoicePracticeDock from '@/components/practice/VoicePracticeDock.vue'
import VoicePracticeHistory from '@/components/practice/VoicePracticeHistory.vue'
import {useVoicePracticeRecord} from '@/composables/useVoicePracticeRecord'
import {useVoiceRealtime} from '@/composables/useVoiceRealtime'
import {captionSavePresentation, completedCaptionLines, handoffBlockReason, parseVoiceTranscript, writeVoiceHandoff} from '@/utils/voicePracticeTranscript'

const INSTRUCTIONS_KEY = 'kk_voice_instructions'

const STATUS_LABEL: Record<string, string> = {
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

const customInstructions = ref('')
const captionsListRef = ref<HTMLElement | null>(null)

const showConfigError = computed(() => Boolean(config.value && !config.value.configured))
const showStatusDetail = computed(() => {
  if (showConfigError.value || quotaMessage.value) {
    return false
  }
  return Boolean(statusDetail.value) && status.value !== 'idle'
})
const savePresentation = computed(() => captionSavePresentation(saveState.value))
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
const configErrorText = computed(() => {
  if (!showConfigError.value) {
    return ''
  }
  // 不向终端用户暴露密钥名 / .env 等部署说明
  return '口语陪练暂未开通，请稍后再试或联系管理员。'
})
const dockStatus = computed(() => {
  if (quotaMessage.value) {
    return {text: quotaMessage.value, tone: 'error' as const}
  }
  if (configErrorText.value) {
    return {text: configErrorText.value, tone: 'error' as const}
  }
  if (beginError.value) {
    return {text: beginError.value, tone: 'error' as const}
  }
  if (saveState.value === 'failed') {
    const extra = saveError.value ? `（${saveError.value}）` : ''
    return {text: `${savePresentation.value.text}${extra}`, tone: 'error' as const}
  }
  if (saveState.value === 'saving' || savePresentation.value.success) {
    return {text: savePresentation.value.text, tone: 'muted' as const}
  }
  if (status.value === 'idle') {
    return {text: '', tone: 'muted' as const}
  }
  const text = showStatusDetail.value && statusDetail.value
    ? statusDetail.value
    : (STATUS_LABEL[status.value] ?? status.value)
  return {text, tone: status.value === 'error' ? 'error' as const : 'muted' as const}
})

onMounted(async () => {
  const seeded = sessionStorage.getItem(INSTRUCTIONS_KEY)
  if (seeded) {
    customInstructions.value = seeded
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

watch([shownCaptions, userPartial, assistantPartial], () => {
  void scrollCaptionsToBottom()
}, {deep: true})

async function onStart() {
  const opened = await begin()
  if (!opened || activeId.value == null) {
    return
  }
  const sessionId = activeId.value
  const outcome = await start({
    instructions: customInstructions.value || undefined,
    practiceSessionId: sessionId,
    onLive: () => markLive(),
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

function onBackClick() {
  void router.push('/conversation/analyze')
}
</script>

<template>
  <div class="voice-page">
    <header class="detail-topbar kk-glass">
      <button type="button" class="back-link" @click="onBackClick">
        <el-icon><ArrowLeft /></el-icon>
        返回分析
      </button>
      <h1 class="topbar-title">口语陪练</h1>
      <span class="topbar-spacer" aria-hidden="true" />
    </header>

    <VoicePracticeHistory
      class="kk-glass kk-glass--panel voice-history-panel"
      :sessions="sessions"
      :disabled="saveState === 'failed'"
      @open="openReview"
      @handoff="onHandoff"
    />

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
            :class="line.role === 'user' ? 'caption-row--user' : 'caption-row--assistant'"
          >
            <span class="caption-meta">{{ line.role === 'user' ? '你' : '陪练' }}</span>
            <div
              class="caption-bubble"
              :class="line.role === 'user' ? 'caption-bubble--user' : 'caption-bubble--assistant'"
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

    <VoicePracticeDock
      v-model:instructions="customInstructions"
      :status-text="dockStatus.text"
      :status-tone="dockStatus.tone"
      :start-disabled="status === 'live' || status === 'connecting' || quotaBlocked || saveState === 'failed' || (config !== null && !config.configured)"
      :stop-disabled="status !== 'live' && status !== 'connecting'"
      :interrupt-disabled="status !== 'live'"
      :mute-disabled="status !== 'live'"
      :muted="muted"
      :prompt-disabled="status === 'live' || status === 'connecting'"
      :show-handoff="showHandoff"
      :handoff-disabled="Boolean(handoffReason)"
      :handoff-reason="handoffReason"
      :show-retry="saveState === 'failed'"
      @start="onStart"
      @stop="onStop"
      @interrupt="interrupt"
      @toggle-mute="toggleMute"
      @handoff="onHandoff(handoffSession)"
      @retry="onRetrySave"
    />
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
}

/*
 * 本页 overflow:hidden（字幕区占满剩余视口）会把 kk-glass 外阴影裁成左右竖边鬼影。
 * 全页玻璃块只保留顶高光，边线靠 border，与分析详情侧栏「干净边」一致。
 */
.voice-page .detail-topbar,
.voice-page .voice-history-panel,
.voice-page .voice-captions {
  box-shadow: inset 0 1px 0 var(--kk-glass-highlight);
}

.detail-topbar {
  flex-shrink: 0;
  display: grid;
  grid-template-columns: auto 1fr auto;
  align-items: center;
  gap: 1rem;
  padding: 0.75rem 1.1rem;
  border-radius: var(--kk-radius-lg);
  z-index: 20;
}

.back-link {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  color: var(--kk-color-primary);
  font-weight: 600;
  text-decoration: none;
  border: none;
  background: none;
  padding: 0;
  cursor: pointer;
  font-family: inherit;
  font-size: inherit;
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

.topbar-spacer {
  width: 5.5rem;
}

.voice-history-panel,
.voice-captions {
  padding: 0.85rem 1.1rem;
}

.voice-review-note {
  margin: 0.65rem 0 0;
  font-size: 0.9rem;
  line-height: 1.5;
  color: var(--kk-color-text-muted);
}

.voice-review-back {
  margin-top: 0.45rem;
  border: none;
  background: none;
  padding: 0;
  color: var(--kk-color-primary);
  font-family: var(--kk-font-body);
  font-weight: 600;
  cursor: pointer;
}

.voice-captions__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
}

.voice-captions__head .voice-section-title {
  margin-bottom: 0;
}

.voice-history-panel {
  flex-shrink: 0;
}

.voice-captions {
  flex: 1 1 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.voice-section-title {
  display: block;
  flex-shrink: 0;
  margin: 0 0 0.55rem;
  font-family: var(--kk-font-display);
  font-size: 1.05rem;
  font-weight: 700;
  color: var(--kk-color-primary);
}

/* 内层只做滚动容器，不再套第二层圆角玻璃（避免与外层 radius 错位叠影） */
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

.caption-row--assistant {
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

.voice-captions__empty {
  color: var(--kk-color-text-muted);
  font-size: 0.9rem;
  text-align: center;
  padding: 1.5rem 0.5rem;
  align-self: stretch;
}

.is-partial {
  opacity: 0.78;
}

@media (max-width: 640px) {
  .topbar-spacer {
    width: 4.25rem;
  }
}
</style>
