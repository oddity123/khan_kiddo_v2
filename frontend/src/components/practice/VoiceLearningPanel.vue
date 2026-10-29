<script setup lang="ts">
import {computed} from 'vue'

import {growthCardStatusLabel, growthCardTypeLabel} from '@/utils/growthCardLabels'
import {attemptOutcomeLabel, type VoiceLearningPanelState} from '@/utils/voiceLearningPanel'

const props = defineProps<{
  state: VoiceLearningPanelState
  loading?: boolean
  error?: string
  /** 通话中且尚无内容时的空态文案走「教练会挑一张卡」 */
  live?: boolean
}>()

const focus = computed(() => props.state.focus)
// 最新的尝试排在最上面，通话中一眼看到刚发生的
const attemptsNewestFirst = computed(() => [...props.state.attempts].reverse())
const notesNewestFirst = computed(() => [...props.state.notes].reverse())
const isEmpty = computed(() =>
  !props.state.focus && props.state.attempts.length === 0 && props.state.notes.length === 0)

function upgradeText(before?: string | null, after?: string | null): string {
  if (!after) {
    return ''
  }
  return `${growthCardStatusLabel(before ?? '')} → ${growthCardStatusLabel(after)}`
}
</script>

<template>
  <aside class="voice-learning kk-glass kk-glass--panel" aria-label="本场学习">
    <header class="voice-learning__head">
      <h2 class="voice-learning__title">本场学习</h2>
      <span v-if="loading" class="voice-learning__hint">读取中…</span>
    </header>

    <p v-if="error" class="voice-learning__error">{{ error }}</p>

    <div class="voice-learning__scroll">
      <section class="voice-learning__block" aria-label="当前焦点卡">
        <h3 class="voice-learning__label">当前焦点</h3>
        <div v-if="focus" class="voice-focus">
          <div class="voice-focus__meta">
            <span class="voice-chip">{{ growthCardTypeLabel(focus.type) }}</span>
            <span class="voice-chip voice-chip--status" :data-status="focus.status">
              {{ growthCardStatusLabel(focus.status) }}
            </span>
          </div>
          <p class="voice-focus__front">{{ focus.front }}</p>
          <p v-if="focus.promptHint" class="voice-focus__hint">{{ focus.promptHint }}</p>
          <details class="voice-focus__back">
            <summary>参考答案</summary>
            <p>{{ focus.back }}</p>
          </details>
        </div>
        <p v-else class="voice-learning__empty">
          {{ live ? '教练选好要练的卡后，会显示在这里。' : '通话中教练选中的卡会显示在这里。' }}
        </p>
      </section>

      <section class="voice-learning__block" aria-label="本场证据">
        <h3 class="voice-learning__label">本场证据</h3>
        <ul v-if="attemptsNewestFirst.length" class="voice-learning__list">
          <li v-for="attempt in attemptsNewestFirst" :key="attempt.attemptId" class="voice-attempt">
            <div class="voice-attempt__top">
              <span class="voice-chip voice-chip--outcome" :data-outcome="attempt.outcome">
                {{ attemptOutcomeLabel(attempt.outcome) }}
              </span>
              <span v-if="attempt.statusAfter" class="voice-attempt__upgrade">
                {{ upgradeText(attempt.statusBefore, attempt.statusAfter) }}
              </span>
            </div>
            <p class="voice-attempt__card">{{ attempt.cardFront }}</p>
            <p v-if="attempt.userUtterance" class="voice-attempt__said">“{{ attempt.userUtterance }}”</p>
            <p v-if="attempt.reason" class="voice-attempt__reason">{{ attempt.reason }}</p>
          </li>
        </ul>
        <p v-else class="voice-learning__empty">还没有记录。每次你答完，教练会记下是否独立说出。</p>
      </section>

      <section v-if="notesNewestFirst.length" class="voice-learning__block" aria-label="教练备注">
        <h3 class="voice-learning__label">教练备注</h3>
        <ul class="voice-learning__list">
          <li v-for="note in notesNewestFirst" :key="note.noteId" class="voice-note">
            <p class="voice-note__text">{{ note.text }}</p>
            <p v-if="note.cardFront" class="voice-note__ref">{{ note.cardFront }}</p>
          </li>
        </ul>
      </section>

      <p v-if="isEmpty && !live && !loading" class="voice-learning__empty voice-learning__empty--tail">
        这场没有学习记录。
      </p>
    </div>
  </aside>
</template>

<style scoped>
.voice-learning {
  display: flex;
  flex-direction: column;
  min-height: 0;
  padding: 1rem 1.05rem;
  /* 本页 overflow:hidden 会把外阴影裁出竖边鬼影，只保留顶高光 */
  box-shadow: inset 0 1px 0 var(--kk-glass-highlight);
}

.voice-learning__head {
  flex-shrink: 0;
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 0.75rem;
  margin-bottom: 0.65rem;
}

.voice-learning__title {
  margin: 0;
  font-family: var(--kk-font-display);
  font-size: 1.05rem;
  font-weight: 700;
  color: var(--kk-color-primary);
}

.voice-learning__hint {
  font-size: 0.82rem;
  color: var(--kk-color-text-muted);
}

.voice-learning__error {
  margin: 0 0 0.6rem;
  font-size: 0.88rem;
  color: var(--kk-color-danger);
}

.voice-learning__scroll {
  flex: 1 1 0;
  min-height: 0;
  overflow-y: auto;
  overscroll-behavior: contain;
  display: flex;
  flex-direction: column;
  gap: 1rem;
  scrollbar-width: thin;
  scrollbar-color: color-mix(in srgb, var(--kk-color-primary) 22%, transparent) transparent;
}

.voice-learning__label {
  margin: 0 0 0.45rem;
  font-family: var(--kk-font-body);
  font-size: 0.78rem;
  font-weight: 700;
  letter-spacing: 0.02em;
  color: var(--kk-color-text-muted);
}

.voice-learning__empty {
  margin: 0;
  font-size: 0.88rem;
  line-height: 1.55;
  color: var(--kk-color-text-muted);
}

.voice-learning__list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 0.55rem;
}

.voice-focus,
.voice-attempt,
.voice-note {
  padding: 0.7rem 0.8rem;
  border-radius: var(--kk-radius-md);
  background: var(--kk-glass-inner-bg);
  border: 1px solid var(--kk-glass-inner-border);
}

.voice-focus__meta,
.voice-attempt__top {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.4rem;
}

.voice-chip {
  display: inline-block;
  padding: 0.15rem 0.6rem;
  border-radius: var(--kk-radius-pill);
  font-size: 0.74rem;
  font-weight: 700;
  letter-spacing: 0.02em;
  background: var(--kk-color-accent-bg);
  color: var(--kk-color-accent-text);
}

.voice-chip--status[data-status='fuzzy'] {
  background: var(--kk-color-warn-bg);
  color: var(--kk-color-warn);
}

.voice-chip--status[data-status='mastered'],
.voice-chip--outcome[data-outcome='independent_success'] {
  background: var(--kk-color-success-bg);
  color: var(--kk-color-success);
}

.voice-chip--status[data-status='unfamiliar'],
.voice-chip--outcome[data-outcome='miss'] {
  background: var(--kk-color-danger-bg);
  color: var(--kk-color-danger);
}

.voice-chip--outcome[data-outcome='prompted'],
.voice-chip--outcome[data-outcome='uncertain'] {
  background: var(--kk-color-warn-bg);
  color: var(--kk-color-warn);
}

.voice-focus__front {
  margin: 0.55rem 0 0;
  font-family: var(--kk-font-mono);
  font-size: 0.95rem;
  line-height: 1.55;
  color: var(--kk-color-text);
  word-break: break-word;
}

.voice-focus__hint {
  margin: 0.4rem 0 0;
  font-size: 0.84rem;
  color: var(--kk-color-text-muted);
}

.voice-focus__back {
  margin-top: 0.55rem;
  font-size: 0.86rem;
  color: var(--kk-color-text-secondary);
}

.voice-focus__back summary {
  cursor: pointer;
  font-weight: 600;
  color: var(--kk-color-primary);
}

.voice-focus__back p {
  margin: 0.4rem 0 0;
  font-family: var(--kk-font-mono);
  line-height: 1.55;
  word-break: break-word;
}

.voice-attempt__upgrade {
  font-size: 0.8rem;
  font-weight: 600;
  color: var(--kk-color-success);
}

.voice-attempt__card {
  margin: 0.45rem 0 0;
  font-family: var(--kk-font-mono);
  font-size: 0.86rem;
  color: var(--kk-color-text-secondary);
  word-break: break-word;
}

.voice-attempt__said {
  margin: 0.3rem 0 0;
  font-family: var(--kk-font-mono);
  font-size: 0.88rem;
  color: var(--kk-color-text);
  word-break: break-word;
}

.voice-attempt__reason,
.voice-note__ref {
  margin: 0.3rem 0 0;
  font-size: 0.82rem;
  color: var(--kk-color-text-muted);
  line-height: 1.45;
}

.voice-note__text {
  margin: 0;
  font-size: 0.9rem;
  line-height: 1.55;
  color: var(--kk-color-text);
  word-break: break-word;
}
</style>
