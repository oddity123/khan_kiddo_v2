<script setup lang="ts">
import {ArrowRight, ChatLineRound, MagicStick, WarningFilled} from '@element-plus/icons-vue'
import {computed} from 'vue'

import type {AnalysisItem} from '@/types/conversation'
import {displayTypeLabel, errorPointText, sortErrors} from '@/utils/analysisDisplay'

const props = defineProps<{
  item: AnalysisItem
  index?: number
}>()

const CHIP_ICONS = [MagicStick, ChatLineRound, WarningFilled] as const

const sortedErrors = computed(() => sortErrors(props.item.errors ?? []))
const errorCount = computed(() => sortedErrors.value.length)
const visibleChips = computed(() => sortedErrors.value.slice(0, 3))
const hiddenChipCount = computed(() => Math.max(0, errorCount.value - 3))

function errorBadgeClass(level?: string) {
  if (level === 'FATAL' || level === 'BASIC') {
    return 'chip--fatal'
  }
  if (level === 'NATURAL') {
    return 'chip--warn'
  }
  return 'chip--soft'
}
</script>

<template>
  <article
      class="sentence-card kk-glass"
      :style="{ '--card-delay': `${(index ?? 0) * 70}ms` }"
  >
    <section class="sentence-pane sentence-pane--before">
      <span class="pane-tag">原句</span>
      <p class="pane-quote">{{ item.originalSentence }}</p>
      <span v-if="!errorCount" class="ok-badge">表达到位</span>
    </section>

    <section v-if="item.suggestion" class="sentence-pane sentence-pane--after">
      <span class="pane-tag pane-tag--ai">AI 建议</span>
      <p class="pane-improved">{{ item.suggestion }}</p>
    </section>

    <div v-if="errorCount" class="chip-row">
      <span
          v-for="(err, i) in visibleChips"
          :key="i"
          class="chip"
          :class="errorBadgeClass(err.errorLevel)"
      >
        <el-icon class="chip-icon">
          <component :is="CHIP_ICONS[i % CHIP_ICONS.length]"/>
        </el-icon>
        {{ displayTypeLabel(err.type) }}
      </span>
      <span v-if="hiddenChipCount > 0" class="chip chip--more">+{{ hiddenChipCount }}</span>
    </div>

    <details v-if="errorCount" class="error-fold">
      <summary class="error-fold-summary">
        <el-icon class="chevron"><ArrowRight/></el-icon>
        <el-icon class="fold-icon"><MagicStick/></el-icon>
        <span>优化点（{{ errorCount }}）</span>
      </summary>
      <div class="error-fold-body">
        <div
            v-for="(err, i) in sortedErrors"
            :key="i"
            class="error-point-card"
        >
          <span class="error-point-tag" :class="errorBadgeClass(err.errorLevel)">
            {{ displayTypeLabel(err.type) }}
          </span>
          <p class="error-point-text">{{ errorPointText(err) }}</p>
        </div>
      </div>
    </details>
  </article>
</template>

<style scoped>
.sentence-card {
  position: relative;
  padding: 1.1rem 1.15rem;
  margin-bottom: 1rem;
  border-radius: var(--kk-radius-lg);
  box-shadow: var(--kk-glass-shadow),
  inset 0 1px 0 var(--kk-glass-highlight),
  0 16px 36px rgba(11, 26, 125, 0.1);
  animation: card-enter 0.55s var(--kk-ease-out) both;
  animation-delay: var(--card-delay, 0ms);
}

.sentence-card::after {
  content: '';
  position: absolute;
  inset: 0;
  border-radius: inherit;
  background: linear-gradient(
      125deg,
      rgba(255, 255, 255, 0.38) 0%,
      transparent 42%,
      transparent 68%,
      rgba(11, 26, 125, 0.03) 100%
  );
  pointer-events: none;
}

@keyframes card-enter {
  from {
    opacity: 0;
    transform: translateY(12px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.sentence-pane {
  position: relative;
  z-index: 1;
  border-radius: var(--kk-radius-md);
  padding: 1rem 1.05rem;
}

.sentence-pane--before {
  background: var(--kk-glass-inner-bg);
  border-left: 3px solid var(--kk-color-accent);
}

.sentence-pane--after {
  margin-top: 0.85rem;
  background: var(--kk-glass-inner-bg-muted);
  border: 1px solid var(--kk-glass-inner-border);
}

.pane-tag {
  display: block;
  font-size: 0.68rem;
  font-weight: 700;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  color: rgba(11, 26, 125, 0.55);
  margin-bottom: 0.4rem;
}

.pane-tag--ai {
  color: var(--kk-color-primary-soft);
}

.pane-quote {
  margin: 0;
  font-family: var(--kk-font-mono);
  font-size: 0.9rem;
  line-height: 1.65;
  color: #4a5068;
  font-style: italic;
}

.pane-improved {
  margin: 0;
  font-family: var(--kk-font-mono);
  font-size: 0.9rem;
  line-height: 1.65;
  color: var(--kk-color-link);
  font-weight: 500;
}

.ok-badge {
  display: inline-block;
  margin-top: 0.55rem;
  padding: 0.15rem 0.55rem;
  border-radius: var(--kk-radius-pill);
  background: var(--kk-color-accent-bg);
  color: var(--kk-color-accent-text);
  font-size: 0.72rem;
  font-weight: 700;
}

.chip-row {
  position: relative;
  z-index: 1;
  display: flex;
  flex-wrap: wrap;
  gap: 0.4rem;
  margin-top: 0.85rem;
  padding-top: 0.15rem;
}

.chip {
  display: inline-flex;
  align-items: center;
  gap: 0.25rem;
  padding: 0.22rem 0.65rem;
  border-radius: var(--kk-radius-pill);
  font-size: 0.72rem;
  font-weight: 600;
}

.chip-icon {
  font-size: 0.85rem;
}

.chip--fatal {
  background: var(--kk-color-danger-bg);
  color: var(--kk-color-danger);
  border: 1px solid rgba(160, 24, 24, 0.2);
}

.chip--warn {
  background: var(--kk-color-warn-bg);
  color: var(--kk-color-warn);
  border: 1px solid rgba(122, 98, 0, 0.22);
}

.chip--soft {
  background: #e8f2ff;
  color: #0e5080;
  border: 1px solid rgba(14, 80, 128, 0.18);
}

.chip--more {
  background: var(--kk-glass-inner-bg);
  color: var(--kk-color-text-subtle);
  border: 1px solid var(--kk-glass-inner-border);
}

.error-fold {
  position: relative;
  z-index: 1;
  margin-top: 0.65rem;
  padding-top: 0.65rem;
  border-top: 1px solid var(--kk-glass-inner-border);
}

.error-fold > summary {
  list-style: none;
  cursor: pointer;
  user-select: none;
}

.error-fold > summary::-webkit-details-marker {
  display: none;
}

.error-fold-summary {
  display: flex;
  align-items: center;
  gap: 0.35rem;
  font-size: 0.72rem;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--kk-color-text-subtle);
}

.chevron {
  transition: transform 0.2s var(--kk-ease-out);
}

.error-fold[open] .chevron {
  transform: rotate(90deg);
}

.fold-icon {
  color: var(--kk-color-primary);
}

.error-fold-body {
  margin-top: 0.65rem;
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.error-point-card {
  padding: 0.75rem 0.85rem;
  border-radius: var(--kk-radius-md);
  background: var(--kk-glass-inner-bg);
  border-left: 3px solid color-mix(in srgb, var(--kk-color-primary) 35%, transparent);
}

.error-point-tag {
  display: inline-block;
  margin-bottom: 0.35rem;
  padding: 0.12rem 0.45rem;
  border-radius: var(--kk-radius-sm);
  font-size: 0.68rem;
  font-weight: 700;
}

.error-point-text {
  margin: 0;
  font-size: 0.82rem;
  line-height: 1.55;
  color: var(--kk-color-text-muted);
}

@media (prefers-reduced-motion: reduce) {
  .sentence-card {
    animation: none;
  }
}
</style>
