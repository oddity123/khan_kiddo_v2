<script setup lang="ts">
import type {VoicePracticeSession} from '@/api/voicePractice'
import {formatListTime} from '@/utils/analysisListDisplay'
import {formatWallClock, handoffBlockReason} from '@/utils/voicePracticeTranscript'

defineProps<{
  sessions: VoicePracticeSession[]
  disabled?: boolean
}>()

const emit = defineEmits<{
  open: [session: VoicePracticeSession]
  handoff: [session: VoicePracticeSession]
}>()
</script>

<template>
  <section class="voice-history" aria-label="最近练习">
    <h2 class="voice-history__title">最近练习</h2>
    <p v-if="!sessions.length" class="voice-history__empty">
      保存后的练习会出现在这里，刷新仍可打开。
    </p>
    <ul v-else class="voice-history__list">
      <li v-for="session in sessions" :key="session.id" class="voice-history__item">
        <button
          type="button"
          class="voice-history__open"
          :disabled="disabled"
          @click="emit('open', session)"
        >
          <span>{{ formatListTime(session.startedAt) }}</span>
          <span>{{ formatWallClock(session.wallDurationSeconds) }}</span>
          <span v-if="session.stoppedByCap" class="voice-history__cap">额度已停</span>
        </button>
        <span
          v-if="handoffBlockReason(session.transcript, session.transcriptSaved)"
          class="voice-history__reason"
        >
          {{ handoffBlockReason(session.transcript, session.transcriptSaved) }}
        </span>
        <button
          v-else
          type="button"
          class="voice-history__handoff"
          :disabled="disabled"
          @click="emit('handoff', session)"
        >
          送去分析
        </button>
      </li>
    </ul>
  </section>
</template>

<style scoped>
.voice-history__title {
  margin: 0 0 0.4rem;
  font-family: var(--kk-font-display);
  font-size: 1.05rem;
  font-weight: 700;
  color: var(--kk-color-primary);
}

.voice-history__empty {
  margin: 0;
  color: var(--kk-color-text-muted);
  font-size: 0.88rem;
}

.voice-history__list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
  max-height: 8.5rem;
  overflow-y: auto;
}

.voice-history__item {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.voice-history__open,
.voice-history__handoff {
  border: none;
  background: none;
  font-family: var(--kk-font-body);
  cursor: pointer;
  color: var(--kk-color-primary);
}

.voice-history__open {
  flex: 1;
  display: flex;
  flex-wrap: wrap;
  gap: 0.35rem 0.75rem;
  text-align: left;
  padding: 0.2rem 0;
  font-size: 0.88rem;
}

.voice-history__open:disabled,
.voice-history__handoff:disabled {
  cursor: not-allowed;
  opacity: 0.45;
}

.voice-history__cap {
  color: var(--el-color-danger);
}

.voice-history__handoff {
  flex-shrink: 0;
  font-size: 0.85rem;
  font-weight: 600;
}

.voice-history__reason {
  flex-shrink: 1;
  font-size: 0.78rem;
  color: var(--kk-color-text-muted);
  text-align: right;
}
</style>
