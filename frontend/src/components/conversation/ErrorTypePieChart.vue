<script setup lang="ts">
import {computed, ref, watch} from 'vue'

import type {ErrorTypeDistribution} from '@/types/conversation'
import {displayTypeLabel} from '@/utils/analysisDisplay'

const props = withDefaults(
    defineProps<{
      items: ErrorTypeDistribution[]
      colors?: string[]
      animate?: boolean
      compact?: boolean
      centerValue?: string | number
      centerLabel?: string
      size?: number
    }>(),
    {
      colors: () => ['#0b1a7d', '#283593', '#b8941f', '#5a6175', '#2d6a4f', '#1f4da9'],
      animate: true,
      centerLabel: '综合得分',
      compact: false,
    },
)

const chartSize = computed(() => props.size ?? (props.compact ? 148 : 192))

const cx = computed(() => chartSize.value / 2)
const cy = computed(() => chartSize.value / 2)
const outerR = computed(() => chartSize.value * 0.42)
const innerR = computed(() => chartSize.value * 0.26)

const showCompactCenterTotal = computed(
    () => props.compact && props.centerValue == null,
)

const sortedDistribution = computed(() =>
    [...props.items].sort((a, b) => b.count - a.count),
)

const total = computed(() =>
    sortedDistribution.value.reduce((sum, item) => sum + item.count, 0),
)

interface PieSegment {
  type: string
  count: number
  label: string
  color: string
  path: string
  percent: number
}

const segments = computed((): PieSegment[] => {
  const t = total.value
  if (t <= 0) {
    return []
  }
  let cursor = 0
  return sortedDistribution.value.map((item, index) => {
    const sweep = (item.count / t) * 360
    const start = cursor
    const end = cursor + sweep
    cursor = end
    return {
      type: item.type,
      count: item.count,
      label: displayTypeLabel(item.type),
      color: props.colors[index % props.colors.length],
      path: describeDonutSlice(cx.value, cy.value, outerR.value, innerR.value, start, end),
      percent: Math.round((item.count / t) * 100),
    }
  })
})

const COMPACT_LEGEND_ROWS = 3
const COMPACT_LEGEND_COLS = 2
const COMPACT_LEGEND_VISIBLE = COMPACT_LEGEND_ROWS * COMPACT_LEGEND_COLS

const activeIndex = ref<number | null>(null)
const legendExpanded = ref(false)

const activeSegment = computed(() =>
    activeIndex.value != null ? segments.value[activeIndex.value] : null,
)

const compactLegendLimit = computed(() =>
    props.compact ? COMPACT_LEGEND_VISIBLE : segments.value.length,
)

const hasCollapsedLegend = computed(
    () => props.compact && segments.value.length > compactLegendLimit.value,
)

const legendSegments = computed(() => {
  if (!props.compact || legendExpanded.value) {
    return segments.value
  }
  return segments.value.slice(0, compactLegendLimit.value)
})

const collapsedLegendCount = computed(() =>
    Math.max(0, segments.value.length - compactLegendLimit.value),
)

function toggleLegendExpanded() {
  legendExpanded.value = !legendExpanded.value
}

function toRad(deg: number) {
  return ((deg - 90) * Math.PI) / 180
}

function describeDonutSlice(
    centerX: number,
    centerY: number,
    rOut: number,
    rIn: number,
    startDeg: number,
    endDeg: number,
) {
  if (endDeg - startDeg >= 359.99) {
    endDeg = startDeg + 359.99
  }
  const sa = toRad(startDeg)
  const ea = toRad(endDeg)
  const x1 = centerX + rOut * Math.cos(sa)
  const y1 = centerY + rOut * Math.sin(sa)
  const x2 = centerX + rOut * Math.cos(ea)
  const y2 = centerY + rOut * Math.sin(ea)
  const x3 = centerX + rIn * Math.cos(ea)
  const y3 = centerY + rIn * Math.sin(ea)
  const x4 = centerX + rIn * Math.cos(sa)
  const y4 = centerY + rIn * Math.sin(sa)
  const large = endDeg - startDeg > 180 ? 1 : 0
  return [
    `M ${x1} ${y1}`,
    `A ${rOut} ${rOut} 0 ${large} 1 ${x2} ${y2}`,
    `L ${x3} ${y3}`,
    `A ${rIn} ${rIn} 0 ${large} 0 ${x4} ${y4}`,
    'Z',
  ].join(' ')
}

function onSliceEnter(index: number) {
  activeIndex.value = index
}

function onSliceLeave() {
  activeIndex.value = null
}

function segmentIndex(seg: PieSegment) {
  return segments.value.findIndex((s) => s.type === seg.type)
}

watch(() => props.items, () => {
  legendExpanded.value = false
})
</script>

<template>
  <div
      v-if="segments.length"
      class="pie-chart"
      :class="{
        'pie-chart--focus': activeIndex != null,
        'pie-chart--compact': compact,
      }"
      @mouseleave="onSliceLeave"
  >
    <div
        class="pie-chart__viz"
        :style="{ width: `${chartSize}px`, height: `${chartSize}px` }"
    >
      <svg
          :width="chartSize"
          :height="chartSize"
          :viewBox="`0 0 ${chartSize} ${chartSize}`"
          class="pie-svg"
          role="img"
          aria-label="优化类型分布饼图"
      >
        <circle :cx="cx" :cy="cy" :r="outerR" class="pie-ring-bg"/>
        <path
            v-for="(seg, index) in segments"
            :key="seg.type"
            :d="seg.path"
            :fill="seg.color"
            class="pie-slice"
            :class="{
              'pie-slice--active': activeIndex === index,
              'pie-slice--dimmed': activeIndex != null && activeIndex !== index,
            }"
            @mouseenter="onSliceEnter(index)"
            @focusin="onSliceEnter(index)"
        />
      </svg>
      <div v-if="centerValue != null && activeIndex == null" class="pie-center">
        <span class="pie-center-value">{{ centerValue }}</span>
        <span class="pie-center-label">{{ centerLabel }}</span>
      </div>
      <div v-else-if="showCompactCenterTotal && activeIndex == null" class="pie-center">
        <span class="pie-center-value pie-center-value--compact">{{ total }}</span>
        <span class="pie-center-label">优化点</span>
      </div>
      <div v-else-if="activeSegment" class="pie-center pie-center--hover">
        <span class="pie-center-value pie-center-value--sm">{{ activeSegment.percent }}%</span>
        <span class="pie-center-label">{{ activeSegment.label }}</span>
      </div>
    </div>

    <div class="pie-legend-wrap">
      <ul class="pie-legend">
        <li
            v-for="seg in legendSegments"
            :key="seg.type"
            class="pie-legend-item"
            :class="{
              'pie-legend-item--active': activeIndex === segmentIndex(seg),
              'pie-legend-item--blur': activeIndex != null && activeIndex !== segmentIndex(seg),
            }"
            @mouseenter="onSliceEnter(segmentIndex(seg))"
        >
          <span class="pie-legend-dot" :style="{ background: seg.color }"/>
          <span class="pie-legend-label">{{ seg.label }}</span>
          <span class="pie-legend-meta">{{ seg.count }} · {{ seg.percent }}%</span>
        </li>
      </ul>
      <button
          v-if="hasCollapsedLegend"
          type="button"
          class="pie-legend-toggle"
          @click="toggleLegendExpanded"
      >
        {{ legendExpanded ? '收起' : `展开其余 ${collapsedLegendCount} 项` }}
      </button>
    </div>
  </div>
</template>

<style scoped>
.pie-chart {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 1.5rem 2rem;
}

.pie-chart__viz {
  position: relative;
  flex-shrink: 0;
}

.pie-svg {
  display: block;
}

.pie-ring-bg {
  fill: var(--kk-glass-inner-bg);
  stroke: var(--kk-glass-inner-border);
  stroke-width: 1;
}

.pie-slice {
  cursor: pointer;
  outline: none;
  transition: filter 0.15s ease, opacity 0.15s ease;
}

.pie-slice--active {
  filter: brightness(1.12) drop-shadow(0 3px 10px rgba(11, 26, 125, 0.32));
}

.pie-slice--dimmed {
  opacity: 0.35;
  filter: saturate(0.6);
}

.pie-center {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  pointer-events: none;
  text-align: center;
}

.pie-center-value {
  font-family: var(--kk-font-display);
  font-size: 1.85rem;
  font-weight: 900;
  line-height: 1;
  color: var(--kk-color-primary);
}

.pie-center-value--sm {
  font-size: 1.5rem;
}

.pie-center-value--compact {
  font-size: 1.45rem;
}

.pie-chart--compact {
  flex-direction: column;
  align-items: stretch;
  gap: 0.75rem;
}

.pie-chart--compact .pie-chart__viz {
  align-self: center;
}

.pie-legend-wrap {
  flex: 1;
  min-width: 10rem;
}

.pie-chart--compact .pie-legend-wrap {
  flex: none;
  width: 100%;
  min-width: 0;
}

.pie-chart--compact .pie-legend {
  flex: none;
  width: 100%;
  min-width: 0;
  grid-template-columns: 1fr 1fr;
  gap: 0.35rem 0.5rem;
}

.pie-legend-toggle {
  display: block;
  width: 100%;
  margin-top: 0.45rem;
  padding: 0.35rem 0.5rem;
  border: none;
  border-radius: var(--kk-radius-sm);
  background: transparent;
  font-family: var(--kk-font-body);
  font-size: 0.72rem;
  font-weight: 600;
  color: var(--kk-color-primary);
  cursor: pointer;
  text-align: center;
  transition: background 0.15s ease, color 0.15s ease;
}

.pie-legend-toggle:hover {
  background: var(--kk-glass-inner-bg);
  color: var(--kk-color-accent-text);
}

.pie-chart--compact .pie-legend-item {
  padding: 0.35rem 0.45rem;
}

.pie-chart--compact .pie-legend-label {
  font-size: 0.78rem;
}

.pie-chart--compact .pie-legend-meta {
  font-size: 0.68rem;
}

.pie-center-label {
  margin-top: 0.2rem;
  font-size: 0.68rem;
  font-weight: 600;
  color: var(--kk-color-text-subtle);
  max-width: 5.5rem;
  line-height: 1.3;
}

.pie-center--hover .pie-center-label {
  color: var(--kk-color-primary);
}

.pie-legend {
  list-style: none;
  margin: 0;
  padding: 0;
  flex: 1;
  min-width: 10rem;
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(9.5rem, 1fr));
  gap: 0.55rem 1rem;
}

.pie-legend-item {
  display: grid;
  grid-template-columns: auto 1fr;
  grid-template-rows: auto auto;
  column-gap: 0.5rem;
  row-gap: 0.1rem;
  padding: 0.45rem 0.55rem;
  border-radius: var(--kk-radius-md);
  cursor: default;
  transition: background 0.15s ease, opacity 0.15s ease;
}

.pie-legend-item--active {
  background: var(--kk-glass-inner-bg);
  opacity: 1;
}

.pie-legend-item--blur {
  opacity: 0.4;
}

.pie-legend-dot {
  grid-row: 1 / span 2;
  align-self: center;
  width: 0.65rem;
  height: 0.65rem;
  border-radius: 50%;
  box-shadow: 0 0 0 2px rgba(255, 255, 255, 0.8);
}

.pie-legend-label {
  font-size: 0.82rem;
  font-weight: 600;
  color: var(--kk-color-text-secondary);
}

.pie-legend-meta {
  grid-column: 2;
  font-size: 0.72rem;
  font-weight: 700;
  color: var(--kk-color-primary);
  font-family: var(--kk-font-display);
}

</style>
