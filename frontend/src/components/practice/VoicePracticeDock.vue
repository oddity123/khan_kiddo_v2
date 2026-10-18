<script setup lang="ts">
import {ChatDotRound, Microphone, Mute, SwitchButton, VideoPause, VideoPlay} from '@element-plus/icons-vue'

import {useReviewChat} from '@/composables/useReviewChat'

defineProps<{
  instructions: string
  statusText: string
  statusTone: 'muted' | 'error'
  startDisabled: boolean
  stopDisabled: boolean
  interruptDisabled: boolean
  muteDisabled: boolean
  muted: boolean
  promptDisabled: boolean
  showHandoff: boolean
  handoffDisabled: boolean
  handoffReason: string
  showRetry: boolean
}>()

const emit = defineEmits<{
  'update:instructions': [value: string]
  start: []
  stop: []
  interrupt: []
  toggleMute: []
  handoff: []
  retry: []
}>()

const {open: reviewOpen, toggleChat} = useReviewChat()
</script>

<template>
  <footer class="voice-dock">
    <div class="voice-dock__lead">
      <el-button
        type="primary"
        size="large"
        :icon="VideoPlay"
        :disabled="startDisabled"
        @click="emit('start')"
      >
        开始对话
      </el-button>
      <span
        v-if="statusText"
        class="voice-dock__note"
        :data-tone="statusTone"
        aria-live="polite"
      >{{ statusText }}</span>
      <button
        v-if="showRetry"
        type="button"
        class="voice-dock__retry"
        @click="emit('retry')"
      >
        再试一次保存
      </button>
    </div>

    <div class="voice-dock__secondary">
      <el-button
        size="large"
        :icon="VideoPause"
        :disabled="stopDisabled"
        @click="emit('stop')"
      >
        结束
      </el-button>
      <el-button
        size="large"
        :icon="SwitchButton"
        :disabled="interruptDisabled"
        @click="emit('interrupt')"
      >
        打断播报
      </el-button>
      <el-button
        size="large"
        :icon="muted ? Mute : Microphone"
        :disabled="muteDisabled"
        @click="emit('toggleMute')"
      >
        {{ muted ? '取消静音' : '静音' }}
      </el-button>
    </div>

    <div v-if="showHandoff" class="voice-dock__handoff">
      <el-button
        size="large"
        plain
        :disabled="handoffDisabled"
        @click="emit('handoff')"
      >
        送去分析
      </el-button>
      <span v-if="handoffReason" class="voice-dock__note" data-tone="error">{{ handoffReason }}</span>
    </div>

    <el-button
      size="large"
      :icon="ChatDotRound"
      :plain="reviewOpen"
      :aria-pressed="reviewOpen"
      @click="toggleChat"
    >
      复盘
    </el-button>

    <el-input
      id="voice-instructions"
      class="voice-dock__prompt"
      :model-value="instructions"
      placeholder="可粘贴复练提示词；留空则使用默认口语教练指令"
      aria-label="可粘贴复练提示词；留空则使用默认口语教练指令"
      :disabled="promptDisabled"
      @update:model-value="emit('update:instructions', $event)"
    />
  </footer>
</template>

<style scoped>
.voice-dock {
  flex-shrink: 0;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.55rem 0.75rem;
  padding-bottom: 0.15rem;
}

.voice-dock__lead,
.voice-dock__secondary,
.voice-dock__handoff {
  display: inline-flex;
  flex-wrap: nowrap;
  align-items: center;
  gap: 0.45rem 0.55rem;
  max-width: 100%;
}

.voice-dock__note {
  flex: 0 1 auto;
  max-width: 18rem;
  font-size: 0.82rem;
  line-height: 1.35;
  color: var(--kk-color-text-muted);
}

.voice-dock__note[data-tone='error'] {
  color: var(--el-color-danger);
}

.voice-dock__retry {
  border: none;
  background: none;
  padding: 0;
  color: var(--kk-color-primary);
  font-family: var(--kk-font-body);
  font-size: 0.82rem;
  font-weight: 600;
  cursor: pointer;
}

.voice-dock__prompt {
  flex: 1 1 14rem;
  min-width: min(100%, 12rem);
}

.voice-dock__prompt :deep(.el-input__wrapper),
.voice-dock__prompt :deep(.el-input__wrapper:hover),
.voice-dock__prompt :deep(.el-input__wrapper.is-focus) {
  box-shadow: none;
  background: var(--kk-glass-inner-bg);
  border: 1px solid var(--kk-glass-inner-border);
}

.voice-dock__prompt :deep(.el-input__wrapper.is-focus) {
  border-color: color-mix(in srgb, var(--kk-color-primary) 45%, var(--kk-glass-inner-border));
}

@media (max-width: 720px) {
  .voice-dock__lead,
  .voice-dock__prompt {
    flex-basis: 100%;
  }

  .voice-dock__note {
    flex: 1 1 auto;
    min-width: 0;
  }
}
</style>
