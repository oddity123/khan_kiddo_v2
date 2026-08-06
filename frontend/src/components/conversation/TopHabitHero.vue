<script setup lang="ts">
import {CircleCheck} from '@element-plus/icons-vue'
import {computed} from 'vue'

import type {ActionCard, ActionCardExample} from '@/types/conversation'

const props = withDefaults(
    defineProps<{
      card: ActionCard
      /** Top1 习惯卡铸卡状态 */
      mintStatus?: 'pending' | 'ready' | 'failed' | 'none'
    }>(),
    {mintStatus: 'none'},
)

const emit = defineEmits<{
  locate: [sentenceId: string | number]
  openCards: []
}>()

const headline = computed(() =>
    (props.card.headlineZh || props.card.titleZh || '').replace(/^本次最该改：/, '').trim(),
)
const diagnosis = computed(() => props.card.diagnosisZh || props.card.whyZh)

const previewExamples = computed((): ActionCardExample[] =>
    (props.card.examples ?? []).slice(0, 2),
)

const mintHint = computed(() => {
  if (props.mintStatus === 'ready') {
    return 'khankiddo已帮您自动生成卡片'
  }
  if (props.mintStatus === 'pending') {
    return '成长卡生成中…'
  }
  return ''
})

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
        <p v-if="diagnosis" class="hero-why">{{ diagnosis }}</p>
        <div class="hero-meta">
          <span v-if="card.errorCount" class="hero-count">命中 {{ card.errorCount }} 句</span>
          <p
              v-if="mintHint"
              class="hero-mint-hint"
              :class="{
                'hero-mint-hint--ready': mintStatus === 'ready',
                'hero-mint-hint--pending': mintStatus === 'pending',
              }"
          >
            <el-icon v-if="mintStatus === 'ready'" aria-hidden="true"><CircleCheck/></el-icon>
            <span>{{ mintHint }}</span>
            <button
                v-if="mintStatus === 'ready'"
                type="button"
                class="hero-mint-link"
                @click="emit('openCards')"
            >
              查看
            </button>
          </p>
        </div>
      </div>
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

.hero-meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.45rem 0.65rem;
  margin-top: 0.1rem;
}

.hero-mint-hint {
  display: inline-flex;
  align-items: center;
  gap: 0.28rem;
  margin: 0;
  padding: 0.14rem 0.55rem;
  border-radius: var(--kk-radius-pill);
  font-size: 0.72rem;
  font-weight: 600;
  line-height: 1.3;
}

.hero-mint-hint--ready {
  color: var(--kk-color-success);
  background: var(--kk-color-success-bg);
  border: 1px solid color-mix(in srgb, var(--kk-color-success) 20%, transparent);
}

.hero-mint-hint--pending {
  color: var(--kk-color-text-muted);
  background: var(--kk-glass-inner-bg);
  border: 1px solid var(--kk-glass-inner-border);
}

.hero-mint-link {
  margin: 0;
  padding: 0;
  border: none;
  background: none;
  font: inherit;
  font-weight: 700;
  color: inherit;
  text-decoration: underline;
  text-underline-offset: 0.12em;
  cursor: pointer;
}

.hero-mint-link:hover {
  opacity: 0.85;
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
</style>
