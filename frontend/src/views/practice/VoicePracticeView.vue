<script setup lang="ts">
import {ArrowLeft, Microphone, Mute, SwitchButton, VideoPause, VideoPlay} from '@element-plus/icons-vue'
import {computed, nextTick, onBeforeUnmount, onMounted, ref, watch} from 'vue'
import {useRouter} from 'vue-router'

import {useVoiceRealtime} from '@/composables/useVoiceRealtime'

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

const statusLabel = computed(() => STATUS_LABEL[status.value] ?? status.value)
const showConfigError = computed(() => Boolean(config.value && !config.value.configured))
const showStatusDetail = computed(() => {
  if (showConfigError.value) {
    return false
  }
  return Boolean(statusDetail.value) && status.value !== 'idle'
})
const configErrorText = computed(() => {
  if (!showConfigError.value) {
    return ''
  }
  // 不向终端用户暴露密钥名 / .env 等部署说明
  return '口语陪练暂未开通，请稍后再试或联系管理员。'
})

onMounted(async () => {
  const seeded = sessionStorage.getItem(INSTRUCTIONS_KEY)
  if (seeded) {
    customInstructions.value = seeded
    sessionStorage.removeItem(INSTRUCTIONS_KEY)
  }
  await loadConfig()
})

onBeforeUnmount(() => {
  void stop()
})

async function scrollCaptionsToBottom() {
  await nextTick()
  const el = captionsListRef.value
  if (!el) {
    return
  }
  el.scrollTo({top: el.scrollHeight, behavior: 'smooth'})
}

watch([captions, userPartial, assistantPartial], () => {
  void scrollCaptionsToBottom()
}, {deep: true})

async function onStart() {
  await start(customInstructions.value || undefined)
}

async function onStop() {
  await stop()
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

    <section class="voice-status kk-glass kk-glass--panel" aria-live="polite">
      <div class="voice-status__row">
        <span class="voice-status__dot" :data-state="status" />
        <strong>{{ statusLabel }}</strong>
        <span v-if="showStatusDetail" class="voice-status__detail">{{ statusDetail }}</span>
      </div>
      <p v-if="showConfigError" class="voice-status__error">
        {{ configErrorText }}
      </p>
    </section>

    <section class="voice-captions kk-glass kk-glass--panel" aria-label="实时字幕">
      <h2 class="voice-section-title">字幕</h2>
      <div class="voice-captions__body">
        <ul ref="captionsListRef" class="voice-captions__lines">
          <li
            v-for="(line, idx) in captions"
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
          <li v-if="userPartial" class="caption-row caption-row--user is-partial">
            <span class="caption-meta">你</span>
            <div class="caption-bubble caption-bubble--user">{{ userPartial }}…</div>
          </li>
          <li v-if="assistantPartial" class="caption-row caption-row--assistant is-partial">
            <span class="caption-meta">陪练</span>
            <div class="caption-bubble caption-bubble--assistant">{{ assistantPartial }}…</div>
          </li>
          <li
            v-if="!captions.length && !userPartial && !assistantPartial"
            class="voice-captions__empty"
          >
            开始后，这里会显示识别与回复文本。
          </li>
        </ul>
      </div>
    </section>

    <footer class="voice-dock">
      <section class="voice-prompt kk-glass kk-glass--panel">
        <label class="voice-section-title" for="voice-instructions">本场提示词（可选）</label>
        <el-input
          id="voice-instructions"
          v-model="customInstructions"
          type="textarea"
          :autosize="{ minRows: 2, maxRows: 4 }"
          placeholder="可粘贴复练提示词；留空则使用默认口语教练指令"
          :disabled="status === 'live' || status === 'connecting'"
        />
      </section>
      <section class="voice-controls">
        <el-button
          type="primary"
          size="large"
          :icon="VideoPlay"
          :disabled="status === 'live' || status === 'connecting' || (config !== null && !config.configured)"
          @click="onStart"
        >
          开始对话
        </el-button>
        <el-button
          size="large"
          :icon="VideoPause"
          :disabled="status !== 'live' && status !== 'connecting'"
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

.voice-status,
.voice-captions,
.voice-prompt {
  padding: 0.85rem 1.1rem;
}

.voice-status {
  flex-shrink: 0;
}

.voice-status__row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.5rem 0.75rem;
  font-family: var(--kk-font-body);
}

.voice-status__dot {
  width: 0.65rem;
  height: 0.65rem;
  border-radius: 50%;
  background: var(--kk-color-text-muted);
}

.voice-status__dot[data-state='live'] {
  background: var(--kk-color-accent);
  box-shadow: 0 0 0 4px color-mix(in srgb, var(--kk-color-accent) 25%, transparent);
}

.voice-status__dot[data-state='error'] {
  background: var(--el-color-danger);
}

.voice-status__dot[data-state='connecting'],
.voice-status__dot[data-state='checking'] {
  background: var(--kk-color-primary);
  animation: pulse 1.2s ease-in-out infinite;
}

.voice-status__detail {
  color: var(--kk-color-text-muted);
  font-size: 0.92rem;
}

.voice-status__error {
  margin: 0.65rem 0 0;
  font-size: 0.9rem;
  color: var(--el-color-danger);
  line-height: 1.5;
}

.voice-captions {
  flex: 1 1 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  /* 满屏 flex + 父级 overflow:hidden 时，大模糊外阴影会被裁成脏边/白雾带 */
  box-shadow:
    var(--kk-shadow-card),
    inset 0 1px 0 var(--kk-glass-highlight);
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

.voice-captions__body {
  flex: 1 1 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
  border-radius: 14px;
  background: var(--kk-glass-inner-bg);
  border: 1px solid var(--kk-glass-inner-border);
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

.voice-dock {
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  gap: 0.65rem;
  padding-bottom: 0.25rem;
}

.voice-controls {
  display: flex;
  flex-wrap: wrap;
  gap: 0.65rem;
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

@media (max-width: 640px) {
  .topbar-spacer {
    width: 4.25rem;
  }

  .voice-controls .el-button {
    flex: 1 1 calc(50% - 0.65rem);
  }
}
</style>
