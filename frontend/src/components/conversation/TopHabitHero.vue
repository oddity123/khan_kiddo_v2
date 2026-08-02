<script setup lang="ts">
import {MagicStick} from '@element-plus/icons-vue'
import {computed} from 'vue'

import type {ActionCard, ActionCardExample} from '@/types/conversation'

const props = defineProps<{
  card: ActionCard
}>()

const emit = defineEmits<{
  practice: [card: ActionCard]
  locate: [sentenceId: string | number]
}>()

const headline = computed(() => props.card.headlineZh || `本次最该改：${props.card.titleZh}`)
const buttonLabel = computed(() => props.card.actionHintZh || '重说一句')

const previewExamples = computed((): ActionCardExample[] =>
    (props.card.examples ?? []).slice(0, 2),
)

function onPractice() {
  emit('practice', props.card)
}

function onLocate(example: ActionCardExample) {
  if (example.sentenceId != null) {
    emit('locate', example.sentenceId)
  }
}
</script>

<template>
  <article class="hero" aria-label="Top 1 最该改的说话习惯">
    <div class="hero-top">
      <div class="rank-mark" aria-hidden="true">
        <span class="rank-mark-label">TOP</span>
        <span class="rank-mark-num">1</span>
      </div>

      <div class="hero-body">
        <p class="hero-eyebrow">本场最该先改</p>
        <h3 class="hero-headline">{{ headline }}</h3>
        <p v-if="card.whyZh" class="hero-why">{{ card.whyZh }}</p>
        <span v-if="card.errorCount" class="hero-count">命中 {{ card.errorCount }} 句</span>
      </div>

      <button type="button" class="hero-cta" @click="onPractice">
        <el-icon><MagicStick/></el-icon>
        {{ buttonLabel }}
      </button>
    </div>

    <div v-if="previewExamples.length" class="hero-examples">
      <div
          v-for="(example, i) in previewExamples"
          :key="example.sentenceId ?? `hero-ex-${i}`"
          class="hero-example"
      >
        <p class="hero-example-orig">{{ example.originalSentence }}</p>
        <p v-if="example.suggestion" class="hero-example-suggest">{{ example.suggestion }}</p>
        <button
            v-if="example.sentenceId != null"
            type="button"
            class="hero-link-btn"
            @click="onLocate(example)"
        >
          查看原句
        </button>
      </div>
    </div>
  </article>
</template>

<style scoped>
.hero {
  display: flex;
  flex-direction: column;
  gap: 0.85rem;
  padding: 1.05rem 1.15rem 1.15rem;
  border-radius: var(--kk-radius-lg);
  background: linear-gradient(
      155deg,
      color-mix(in srgb, var(--kk-color-accent) 14%, white) 0%,
      color-mix(in srgb, var(--kk-color-primary) 4%, white) 55%,
      rgba(255, 255, 255, 0.5) 100%
  );
  border: 1px solid color-mix(in srgb, var(--kk-color-accent) 28%, var(--kk-glass-inner-border));
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.75);
}

.hero-top {
  display: flex;
  align-items: flex-start;
  gap: 0.95rem;
}

.rank-mark {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  width: 3.1rem;
  padding: 0.35rem 0.2rem 0.4rem;
  border-radius: var(--kk-radius-md);
  background: linear-gradient(160deg, var(--kk-color-primary), var(--kk-color-primary-soft));
  color: #fff;
  box-shadow: 0 6px 16px color-mix(in srgb, var(--kk-color-primary) 28%, transparent);
  line-height: 1;
}

.rank-mark-label {
  font-family: var(--kk-font-mono);
  font-size: 0.58rem;
  font-weight: 700;
  letter-spacing: 0.14em;
  opacity: 0.85;
}

.rank-mark-num {
  margin-top: 0.12rem;
  font-family: var(--kk-font-display);
  font-size: 1.55rem;
  font-weight: 800;
  font-variant-numeric: tabular-nums;
}

.hero-body {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 0.3rem;
}

.hero-eyebrow {
  margin: 0;
  font-size: 0.7rem;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--kk-color-accent-text);
}

.hero-headline {
  margin: 0;
  font-family: var(--kk-font-display);
  font-size: clamp(1.05rem, 2vw, 1.28rem);
  font-weight: 800;
  line-height: 1.35;
  color: var(--kk-color-primary);
}

.hero-why {
  margin: 0;
  font-size: 0.88rem;
  line-height: 1.5;
  color: var(--kk-color-text-muted);
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  overflow: hidden;
}

.hero-count {
  align-self: flex-start;
  margin-top: 0.1rem;
  padding: 0.12rem 0.55rem;
  border-radius: var(--kk-radius-pill);
  background: var(--kk-glass-inner-bg);
  border: 1px solid var(--kk-glass-inner-border);
  font-family: var(--kk-font-mono);
  font-size: 0.72rem;
  font-weight: 600;
  color: var(--kk-color-text-subtle);
}

.hero-cta {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  flex-shrink: 0;
  align-self: center;
  padding: 0.6rem 1.15rem;
  border-radius: var(--kk-radius-pill);
  border: none;
  background: linear-gradient(145deg, var(--kk-color-primary), var(--kk-color-primary-soft));
  color: #fff;
  font-size: 0.92rem;
  font-weight: 700;
  cursor: pointer;
  box-shadow: var(--kk-shadow-btn);
  transition: transform 0.18s ease, box-shadow 0.18s ease;
}

.hero-cta:hover {
  transform: translateY(-1px);
  box-shadow: var(--kk-shadow-btn-hover);
}

.hero-cta:active {
  transform: translateY(0);
}

.hero-examples {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  padding-top: 0.75rem;
  border-top: 1px solid color-mix(in srgb, var(--kk-color-accent) 18%, var(--kk-glass-divider));
}

.hero-example {
  padding: 0.65rem 0.75rem;
  border-radius: var(--kk-radius-md);
  background: var(--kk-glass-inner-bg);
  border-left: 3px solid color-mix(in srgb, var(--kk-color-primary) 35%, transparent);
}

.hero-example-orig {
  margin: 0;
  font-family: var(--kk-font-mono);
  font-size: 0.85rem;
  line-height: 1.5;
  color: var(--kk-color-text-muted);
  font-style: italic;
}

.hero-example-suggest {
  margin: 0.25rem 0 0;
  font-family: var(--kk-font-mono);
  font-size: 0.85rem;
  line-height: 1.5;
  font-weight: 600;
  color: var(--kk-color-primary);
}

.hero-link-btn {
  margin-top: 0.4rem;
  padding: 0;
  border: none;
  background: none;
  font-size: 0.76rem;
  font-weight: 600;
  color: var(--kk-color-link);
  cursor: pointer;
}

.hero-link-btn:hover {
  text-decoration: underline;
}

@media (max-width: 640px) {
  .hero-top {
    flex-wrap: wrap;
  }

  .hero-cta {
    width: 100%;
    justify-content: center;
  }
}

@media (prefers-reduced-motion: reduce) {
  .hero-cta {
    transition: none;
  }
}
</style>
