<script setup lang="ts">
import {computed} from 'vue'

import ShadowingPractice from '@/components/conversation/ShadowingPractice.vue'
import type {ActionCardExample} from '@/types/conversation'
import type {ShadowingStatus} from '@/types/shadowing'

const props = defineProps<{
  example: ActionCardExample
  analysisId?: string
  /** 传入即表示本句可跟读；为空时不显示跟读区 */
  shadowing?: ShadowingStatus | null
}>()

const suggestion = computed(() => props.example.suggestion?.trim() || '')
const showSuggestion = computed(
    () => Boolean(suggestion.value && suggestion.value !== props.example.originalSentence?.trim()),
)
const showShadowing = computed(() => Boolean(
    showSuggestion.value
    && props.analysisId
    && props.shadowing?.scoringEnabled
    && props.example.sentenceId != null
    && props.example.sentenceId !== '',
))
</script>

<template>
  <article class="ev-mini" :class="{ 'ev-mini--pair': showSuggestion }">
    <div class="ev-mini-orig-wrap">
      <p class="ev-mini-orig">{{ example.originalSentence }}</p>
    </div>
    <p v-if="showSuggestion" class="ev-mini-suggest" aria-label="AI 建议">
      {{ suggestion }}
    </p>
    <ShadowingPractice
        v-if="showShadowing && analysisId && example.sentenceId != null"
        :analysis-id="analysisId"
        :sentence-id="example.sentenceId"
        :text="suggestion"
        :tts-enabled="Boolean(shadowing?.ttsEnabled)"
    />
  </article>
</template>

<style scoped>
/* 精炼证据条：不强制省略，完整展示两条例句 */
.ev-mini {
  display: flex;
  flex-direction: column;
  gap: 0.45rem;
  padding: 0.65rem 0.75rem;
  border-radius: var(--kk-radius-md);
  background: color-mix(in srgb, var(--kk-color-surface-muted) 72%, transparent);
  border: 1px solid var(--kk-color-border-subtle);
  border-left: 2px solid color-mix(in srgb, var(--kk-color-accent) 55%, transparent);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.55);
}

.ev-mini-orig-wrap {
  min-width: 0;
}

.ev-mini--pair .ev-mini-orig-wrap {
  padding-bottom: 0.4rem;
  border-bottom: 1px solid var(--kk-color-border-subtle);
}

.ev-mini-orig {
  margin: 0;
  font-family: var(--kk-font-mono);
  font-size: 0.8rem;
  line-height: 1.55;
  letter-spacing: 0.01em;
  color: var(--kk-color-text-subtle);
  font-style: italic;
  overflow-wrap: anywhere;
  word-break: break-word;
}

.ev-mini-suggest {
  margin: 0;
  font-family: var(--kk-font-mono);
  font-size: 0.84rem;
  line-height: 1.55;
  font-weight: 600;
  color: var(--kk-color-primary);
  overflow-wrap: anywhere;
  word-break: break-word;
}

@media (max-width: 640px) {
  .ev-mini {
    padding: 0.55rem 0.6rem;
  }

  .ev-mini-orig,
  .ev-mini-suggest {
    font-size: 0.8rem;
  }
}
</style>
