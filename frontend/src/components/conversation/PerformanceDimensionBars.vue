<script setup lang="ts">
import {computed, withDefaults} from 'vue'

import type {PerformanceDimensionScores} from '@/types/conversation'
import {listPerformanceDimensions, scoreBarPercent} from '@/utils/analysisDisplay'

const props = withDefaults(
    defineProps<{
      scores?: PerformanceDimensionScores
      /** 继承父级 .summary-panel 的 0.84rem 正文字号 */
      dense?: boolean
    }>(),
    {dense: false},
)

const items = computed(() => listPerformanceDimensions(props.scores))
</script>

<template>
  <div
      v-if="items.length"
      class="dim-bars"
      :class="{ 'dim-bars--dense': dense }"
      role="list"
      aria-label="口语表现分项得分"
  >
    <div
        v-for="item in items"
        :key="item.key"
        class="dim-row"
        :class="{ 'dim-row--emphasis': item.emphasis }"
        role="listitem"
    >
      <span class="dim-label">{{ item.label }}</span>
      <span class="dim-track" aria-hidden="true">
        <span
            class="dim-fill"
            :style="{ width: `${scoreBarPercent(item.value)}%` }"
        />
      </span>
      <span class="dim-value">{{ item.value }}</span>
    </div>
  </div>
</template>

<style scoped>
.dim-bars {
  display: flex;
  flex-direction: column;
  gap: 0.38rem;
}

.dim-row {
  display: grid;
  grid-template-columns: 3.75rem 1fr 1.85rem;
  align-items: center;
  gap: 0.4rem;
}

.dim-row--emphasis .dim-label {
  color: var(--kk-color-accent-text);
  font-weight: 700;
}

.dim-row--emphasis .dim-fill {
  background: linear-gradient(
      90deg,
      var(--kk-color-accent),
      color-mix(in srgb, var(--kk-color-accent) 65%, var(--kk-color-primary))
  );
}

.dim-bars--dense {
  gap: calc(0.38rem * 1.1);
}

.dim-bars--dense .dim-row {
  gap: calc(0.4rem * 1.1);
}

.dim-bars--dense .dim-label {
  padding-block: calc(0.15em * 1.1);
  padding-right: calc(0.35rem * 1.1);
}

.dim-bars--dense .dim-label,
.dim-bars--dense .dim-value {
  font-size: 0.84rem;
}

.dim-label {
  font-size: 0.75rem;
  font-weight: 600;
  line-height: 1.2;
  color: var(--kk-color-text-subtle);
  white-space: nowrap;
}

.dim-track {
  height: 0.42rem;
  border-radius: var(--kk-radius-pill);
  background: color-mix(in srgb, var(--kk-color-primary) 8%, var(--kk-glass-inner-bg));
  overflow: hidden;
}

.dim-fill {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(
      90deg,
      var(--kk-color-primary-soft),
      var(--kk-color-primary)
  );
  transition: width 0.55s var(--kk-ease-out);
}

.dim-value {
  font-family: var(--kk-font-mono);
  font-size: 0.8rem;
  font-weight: 600;
  text-align: right;
  color: var(--kk-color-primary);
  font-variant-numeric: tabular-nums;
}

@media (prefers-reduced-motion: reduce) {
  .dim-fill {
    transition: none;
  }
}
</style>
