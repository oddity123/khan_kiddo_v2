<script setup lang="ts">
import {MagicStick, TrendCharts} from '@element-plus/icons-vue'
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
  <section class="hero kk-glass kk-glass--panel" aria-label="本次最该改的说话习惯">
    <div class="hero-top">
      <span class="hero-icon-wrap" aria-hidden="true">
        <el-icon class="hero-icon"><TrendCharts/></el-icon>
      </span>

      <div class="hero-body">
        <p class="hero-eyebrow">本场习惯诊断</p>
        <h2 class="hero-headline">{{ headline }}</h2>
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
  </section>
</template>

<style scoped>
.hero {
  display: flex;
  flex-direction: column;
  gap: 0.85rem;
  padding: 1.15rem 1.3rem;
  margin-bottom: 1.25rem;
  border-top: 2px solid var(--kk-color-accent);
}

.hero-top {
  display: flex;
  align-items: center;
  gap: 1rem;
}

.hero-icon-wrap {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 3rem;
  height: 3rem;
  flex-shrink: 0;
  border-radius: 50%;
  background: linear-gradient(
      145deg,
      color-mix(in srgb, var(--kk-color-accent) 22%, white),
      color-mix(in srgb, var(--kk-color-primary) 10%, white)
  );
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.8);
}

.hero-icon {
  font-size: 1.3rem;
  color: var(--kk-color-primary);
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
  font-size: clamp(1.05rem, 2vw, 1.3rem);
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
  border-top: 1px solid var(--kk-glass-divider);
}

.hero-example {
  padding: 0.65rem 0.75rem;
  border-radius: var(--kk-radius-sm);
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
