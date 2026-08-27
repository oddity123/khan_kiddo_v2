<script setup lang="ts">
import type {Component} from 'vue'
import {computed} from 'vue'
import {
  ArrowRight,
  ChatLineSquare,
  CircleCheck,
  Clock,
  Connection,
  Document,
  EditPen,
  List,
  MagicStick,
  Promotion,
  Rank,
  Reading,
} from '@element-plus/icons-vue'

import type {AnalysisError, AnalysisItem} from '@/types/conversation'
import {displayTypeLabel, errorPointText, sortErrors} from '@/utils/analysisDisplay'
import EditAnnotatedText from '@/components/conversation/EditAnnotatedText.vue'

const props = defineProps<{
  item: AnalysisItem
  index?: number
}>()

const sortedErrors = computed(() => sortErrors(props.item.errors ?? []))
const errorCount = computed(() => sortedErrors.value.length)

/** 卡片顶部短标签按家族去重，与详情页筛选一致 */
const visibleChips = computed(() => {
  const seen = new Set<string>()
  const chips: AnalysisError[] = []
  for (const err of sortedErrors.value) {
    const key = err.familyId?.trim() || err.type?.trim() || String(chips.length)
    if (seen.has(key)) {
      continue
    }
    seen.add(key)
    chips.push(err)
    if (chips.length >= 3) {
      break
    }
  }
  return chips
})
const hiddenChipCount = computed(() => {
  const unique = new Set(
      sortedErrors.value.map((err) => err.familyId?.trim() || err.type?.trim() || ''),
  )
  unique.delete('')
  return Math.max(0, unique.size - visibleChips.value.length)
})

function errorBadgeClass(level?: string) {
  if (level === 'FATAL' || level === 'BASIC') {
    return 'chip--fatal'
  }
  if (level === 'NATURAL') {
    return 'chip--warn'
  }
  return 'chip--soft'
}

function chipIcon(type?: string): Component {
  const label = displayTypeLabel(type)
  if (label.includes('时态')) {
    return Clock
  }
  if (label.includes('句式') || label.includes('结构') || label.includes('从句') || label.includes('句子')) {
    return Rank
  }
  if (
      label.includes('用词') ||
      label.includes('词汇') ||
      label.includes('搭配') ||
      label.includes('词性') ||
      label.includes('词形')
  ) {
    return EditPen
  }
  if (label.includes('语法') || label.includes('冠词') || label.includes('介词') || label.includes('名词') || label.includes('一致')) {
    return Document
  }
  if (label.includes('语气') || label.includes('表达') || label.includes('口语') || label.includes('流畅')) {
    return ChatLineSquare
  }
  if (label.includes('中式') || label.includes('冗余') || label.includes('中文')) {
    return Connection
  }
  if (label.includes('自然')) {
    return Promotion
  }
  return Reading
}

function chipLabel(err: AnalysisError): string {
  return displayTypeLabel(err.familyTitleZh || err.type)
}
</script>

<template>
  <article
      class="sentence-card kk-glass"
      :style="{ '--card-delay': `${(index ?? 0) * 70}ms` }"
  >
    <section class="sentence-pane sentence-pane--before">
      <header class="pane-head">
        <span class="pane-head-icon pane-head-icon--orig" aria-hidden="true">
          <el-icon><ChatLineSquare/></el-icon>
        </span>
        <span class="pane-tag">原句</span>
      </header>
      <EditAnnotatedText
          side="original"
          :text="item.originalSentence"
          :tokens="item.originalTokens"
          :edits="item.edits"
      />
      <span v-if="!errorCount" class="ok-badge">
        <el-icon class="ok-badge-icon" aria-hidden="true"><CircleCheck/></el-icon>
        表达到位
      </span>
    </section>

    <section v-if="item.suggestion" class="sentence-pane sentence-pane--after">
      <header class="pane-head">
        <span class="pane-head-icon pane-head-icon--ai" aria-hidden="true">
          <el-icon><MagicStick/></el-icon>
        </span>
        <span class="pane-tag pane-tag--ai">优化表达</span>
      </header>
      <EditAnnotatedText
          side="corrected"
          :text="item.suggestion"
          :tokens="item.correctedTokens"
          :edits="item.edits"
      />
    </section>

    <div v-if="errorCount" class="chip-row">
      <span
          v-for="(err, i) in visibleChips"
          :key="i"
          class="chip"
          :class="errorBadgeClass(err.errorLevel)"
      >
        <el-icon class="chip-icon">
          <component :is="chipIcon(chipLabel(err))"/>
        </el-icon>
        {{ chipLabel(err) }}
      </span>
      <span v-if="hiddenChipCount > 0" class="chip chip--more">+{{ hiddenChipCount }}</span>
    </div>

    <details v-if="errorCount" class="error-fold">
      <summary class="error-fold-summary">
        <el-icon class="chevron"><ArrowRight/></el-icon>
        <span class="fold-icon-wrap" aria-hidden="true">
          <el-icon class="fold-icon"><List/></el-icon>
        </span>
        <span>优化点（{{ errorCount }}）</span>
      </summary>
      <div class="error-fold-body">
        <div
            v-for="(err, i) in sortedErrors"
            :key="i"
            class="error-point-card"
        >
          <span class="error-point-tag" :class="errorBadgeClass(err.errorLevel)">
            <el-icon class="error-point-tag-icon">
              <component :is="chipIcon(chipLabel(err))"/>
            </el-icon>
            {{ chipLabel(err) }}
          </span>
          <p v-if="err.type && err.type !== chipLabel(err)" class="error-point-leaf">
            {{ displayTypeLabel(err.type) }}
          </p>
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
  box-shadow: inset 0 0 0 1px color-mix(in srgb, var(--kk-color-primary) 6%, transparent);
}

.pane-head {
  display: flex;
  align-items: center;
  gap: 0.45rem;
  margin-bottom: 0.5rem;
}

.pane-head-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 1.55rem;
  height: 1.55rem;
  border-radius: var(--kk-radius-sm);
  flex-shrink: 0;
  font-size: 0.9rem;
}

.pane-head-icon--orig {
  background: color-mix(in srgb, var(--kk-color-accent) 18%, white);
  color: var(--kk-color-accent-text);
  border: 1px solid color-mix(in srgb, var(--kk-color-accent) 28%, transparent);
}

.pane-head-icon--ai {
  background: linear-gradient(
      145deg,
      color-mix(in srgb, var(--kk-color-primary) 14%, white),
      color-mix(in srgb, var(--kk-color-link) 10%, white)
  );
  color: var(--kk-color-primary);
  border: 1px solid color-mix(in srgb, var(--kk-color-primary) 16%, transparent);
}

.pane-tag {
  font-size: 0.68rem;
  font-weight: 700;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  color: rgba(11, 26, 125, 0.55);
}

.pane-tag--ai {
  color: var(--kk-color-primary-soft);
}

.pane-quote {
  margin: 0;
  font-family: var(--kk-font-mono);
  font-size: 0.9rem;
  line-height: 1.65;
  color: var(--kk-color-text-muted);
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

.sentence-pane--before :deep(.edit-text) {
  color: var(--kk-color-text-muted);
  font-style: italic;
}

.sentence-pane--after :deep(.edit-text) {
  color: var(--kk-color-link);
  font-weight: 500;
}

.ok-badge {
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
  margin-top: 0.55rem;
  padding: 0.15rem 0.55rem 0.15rem 0.4rem;
  border-radius: var(--kk-radius-pill);
  background: var(--kk-color-accent-bg);
  color: var(--kk-color-accent-text);
  font-size: 0.72rem;
  font-weight: 700;
}

.ok-badge-icon {
  font-size: 0.85rem;
  color: var(--kk-color-success);
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
  background: color-mix(in srgb, var(--kk-color-link) 10%, white);
  color: var(--kk-color-link);
  border: 1px solid color-mix(in srgb, var(--kk-color-link) 18%, transparent);
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

.fold-icon-wrap {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 1.35rem;
  height: 1.35rem;
  border-radius: var(--kk-radius-sm);
  background: color-mix(in srgb, var(--kk-color-primary) 10%, white);
  color: var(--kk-color-primary);
}

.fold-icon {
  font-size: 0.8rem;
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
  display: inline-flex;
  align-items: center;
  gap: 0.25rem;
  margin-bottom: 0.35rem;
  padding: 0.12rem 0.45rem;
  border-radius: var(--kk-radius-sm);
  font-size: 0.68rem;
  font-weight: 700;
}

.error-point-tag-icon {
  font-size: 0.75rem;
}

.error-point-leaf {
  margin: 0.35rem 0 0;
  font-size: 0.78rem;
  font-weight: 600;
  line-height: 1.4;
  color: var(--kk-color-text-secondary);
}

.error-point-text {
  margin: 0.35rem 0 0;
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
