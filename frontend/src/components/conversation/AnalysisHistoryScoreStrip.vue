<script setup lang="ts">
import {computed} from 'vue'

import type {PerformanceDimensionScores} from '@/types/conversation'
import {listPerformanceDimensions} from '@/utils/analysisDisplay'

const props = defineProps<{
  performanceScore?: number
  dimensionScores?: PerformanceDimensionScores
}>()

const overall = computed(() => {
  if (props.performanceScore != null) {
    return props.performanceScore
  }
  return null
})

const dimensions = computed(() => listPerformanceDimensions(props.dimensionScores))

const hasScores = computed(() => overall.value != null || dimensions.value.length > 0)
</script>

<template>
  <div v-if="hasScores" class="score-strip" aria-label="口语表现得分">
    <div v-if="overall != null" class="score-overall">
      <span class="score-overall-num">{{ overall }}</span>
      <span class="score-overall-lbl">综合</span>
    </div>
    <ul v-if="dimensions.length" class="score-dims">
      <li
          v-for="dim in dimensions"
          :key="dim.key"
          class="score-dim"
          :class="{ 'score-dim--emphasis': dim.emphasis }"
      >
        <span class="score-dim-label">{{ dim.label }}</span>
        <span class="score-dim-value">{{ dim.value }}</span>
      </li>
    </ul>
  </div>
</template>

<style scoped>
.score-strip {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.65rem 1rem;
  margin-top: 0.55rem;
  padding-top: 0.55rem;
  border-top: 1px solid var(--kk-glass-divider);
}

.score-overall {
  display: flex;
  align-items: baseline;
  gap: 0.3rem;
  padding: 0.2rem 0.55rem;
  border-radius: var(--kk-radius-sm);
  background: color-mix(in srgb, var(--kk-color-primary) 9%, white);
  flex-shrink: 0;
}

.score-overall-num {
  font-family: var(--kk-font-display);
  font-size: 1.35rem;
  font-weight: 900;
  line-height: 1;
  color: var(--kk-color-primary);
  font-variant-numeric: tabular-nums;
}

.score-overall-lbl {
  font-size: 0.62rem;
  font-weight: 700;
  letter-spacing: 0.04em;
  color: var(--kk-color-text-subtle);
}

.score-dims {
  display: flex;
  flex-wrap: wrap;
  gap: 0.35rem;
  margin: 0;
  padding: 0;
  list-style: none;
}

.score-dim {
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
  padding: 0.18rem 0.5rem;
  border-radius: 999px;
  background: var(--kk-glass-inner-bg);
  border: 1px solid var(--kk-glass-inner-border);
  font-size: 0.72rem;
}

.score-dim--emphasis {
  border-color: color-mix(in srgb, var(--kk-color-accent) 45%, transparent);
  background: color-mix(in srgb, var(--kk-color-accent-bg) 70%, white);
}

.score-dim-label {
  font-weight: 600;
  color: var(--kk-color-text-subtle);
}

.score-dim--emphasis .score-dim-label {
  color: var(--kk-color-accent-text);
}

.score-dim-value {
  font-family: var(--kk-font-mono);
  font-weight: 700;
  color: var(--kk-color-primary);
  font-variant-numeric: tabular-nums;
}
</style>
