<script setup lang="ts">
import {Microphone, Mute, SwitchButton, VideoPause, VideoPlay} from '@element-plus/icons-vue'
import {onBeforeUnmount, onMounted, ref} from 'vue'
import {useRouter} from 'vue-router'

import {useVoiceRealtime} from '@/composables/useVoiceRealtime'

const INSTRUCTIONS_KEY = 'kk_voice_instructions'

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

async function onStart() {
  await start(customInstructions.value || undefined)
}

async function onStop() {
  await stop()
}

function goBack() {
  void router.back()
}
</script>

<template>
  <div class="voice-room kk-page-shell">
    <header class="voice-room__header">
      <button type="button" class="voice-room__back" @click="goBack">← 返回</button>
      <div class="voice-room__titles">
        <h1 class="voice-room__brand">Khan Kiddo</h1>
        <p class="voice-room__subtitle">站内口语陪练 · 豆包全双工实时语音</p>
      </div>
    </header>

    <section class="voice-room__status kk-glass kk-glass--panel" aria-live="polite">
      <div class="voice-room__status-row">
        <span class="voice-room__dot" :data-state="status" />
        <strong>{{ status }}</strong>
        <span class="voice-room__detail">{{ statusDetail }}</span>
      </div>
      <p v-if="config && !config.configured" class="voice-room__hint">
        {{ config.message }}
      </p>
      <p v-else class="voice-room__hint">
        浏览器采集麦克风，经本站后端代理连到火山 openspeech；密钥不会下发到前端。
        需要麦克风权限。关闭麦克风时会发送静音保活事件。
      </p>
    </section>

    <section class="voice-room__controls">
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

    <section class="voice-room__captions kk-glass kk-glass--panel" aria-label="实时字幕">
      <h2 class="voice-room__section-title">字幕</h2>
      <ul class="voice-room__lines">
        <li v-for="(line, idx) in captions" :key="idx" :data-role="line.role">
          <span class="voice-room__role">{{ line.role === 'user' ? '你' : '陪练' }}</span>
          <span class="voice-room__text">{{ line.text }}</span>
        </li>
        <li v-if="userPartial" data-role="user" class="is-partial">
          <span class="voice-room__role">你</span>
          <span class="voice-room__text">{{ userPartial }}…</span>
        </li>
        <li v-if="assistantPartial" data-role="assistant" class="is-partial">
          <span class="voice-room__role">陪练</span>
          <span class="voice-room__text">{{ assistantPartial }}…</span>
        </li>
        <li v-if="!captions.length && !userPartial && !assistantPartial" class="voice-room__empty">
          开始后，这里会显示识别与回复文本。
        </li>
      </ul>
    </section>

    <section class="voice-room__prompt kk-glass kk-glass--panel">
      <label class="voice-room__section-title" for="voice-instructions">本场提示词（可选）</label>
      <el-input
        id="voice-instructions"
        v-model="customInstructions"
        type="textarea"
        :autosize="{ minRows: 3, maxRows: 8 }"
        placeholder="可粘贴复练提示词；留空则使用默认口语教练指令"
        :disabled="status === 'live' || status === 'connecting'"
      />
    </section>
  </div>
</template>

<style scoped>
.voice-room {
  display: flex;
  flex-direction: column;
  gap: 1rem;
  padding: 1.25rem 0 2.5rem;
  min-height: calc(100vh - 2rem);
}

.voice-room__header {
  display: flex;
  align-items: flex-start;
  gap: 1rem;
}

.voice-room__back {
  border: 0;
  background: transparent;
  color: var(--kk-color-text-muted);
  font-family: var(--kk-font-body);
  cursor: pointer;
  padding: 0.35rem 0;
}

.voice-room__brand {
  margin: 0;
  font-family: var(--kk-font-display);
  font-size: clamp(1.75rem, 4vw, 2.4rem);
  color: var(--kk-color-primary);
  letter-spacing: -0.02em;
}

.voice-room__subtitle {
  margin: 0.25rem 0 0;
  color: var(--kk-color-text-muted);
  font-family: var(--kk-font-body);
}

.voice-room__status,
.voice-room__captions,
.voice-room__prompt {
  padding: 1rem 1.1rem;
}

.voice-room__status-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.5rem 0.75rem;
  font-family: var(--kk-font-body);
}

.voice-room__dot {
  width: 0.65rem;
  height: 0.65rem;
  border-radius: 50%;
  background: var(--kk-color-text-muted);
}

.voice-room__dot[data-state='live'] {
  background: var(--kk-color-accent);
  box-shadow: 0 0 0 4px color-mix(in srgb, var(--kk-color-accent) 25%, transparent);
}

.voice-room__dot[data-state='error'] {
  background: var(--el-color-danger);
}

.voice-room__dot[data-state='connecting'],
.voice-room__dot[data-state='checking'] {
  background: var(--kk-color-primary);
  animation: pulse 1.2s ease-in-out infinite;
}

.voice-room__detail {
  color: var(--kk-color-text-muted);
}

.voice-room__hint {
  margin: 0.65rem 0 0;
  font-size: 0.9rem;
  color: var(--kk-color-text-muted);
  line-height: 1.5;
}

.voice-room__controls {
  display: flex;
  flex-wrap: wrap;
  gap: 0.65rem;
}

.voice-room__section-title {
  display: block;
  margin: 0 0 0.65rem;
  font-family: var(--kk-font-display);
  font-size: 1.05rem;
  color: var(--kk-color-primary);
}

.voice-room__lines {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  min-height: 8rem;
  max-height: min(40vh, 28rem);
  overflow: auto;
}

.voice-room__lines li {
  display: grid;
  grid-template-columns: 3rem 1fr;
  gap: 0.65rem;
  font-family: var(--kk-font-body);
}

.voice-room__lines li[data-role='assistant'] .voice-room__text {
  font-family: var(--kk-font-mono);
}

.voice-room__role {
  color: var(--kk-color-text-muted);
  font-size: 0.85rem;
}

.voice-room__empty {
  color: var(--kk-color-text-muted);
  display: block !important;
}

.is-partial {
  opacity: 0.75;
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
  .voice-room__controls .el-button {
    flex: 1 1 calc(50% - 0.65rem);
  }
}
</style>
