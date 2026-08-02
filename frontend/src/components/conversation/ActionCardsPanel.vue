<script setup lang="ts">
import {
  ArrowRight,
  ChatLineSquare,
  Connection,
  Document,
  Promotion,
} from '@element-plus/icons-vue'
import type {Component} from 'vue'

import type {ActionCard, ActionCardExample, PointChannel} from '@/types/conversation'

const props = withDefaults(
    defineProps<{
      cards: ActionCard[]
    }>(),
    {cards: () => []},
)

const emit = defineEmits<{
  locate: [sentenceId: string | number]
  practice: [card: ActionCard]
}>()

const CHANNEL_LABEL: Record<PointChannel, string> = {
  rule: '语法规则',
  fluency: '表达流畅',
  lexical: '词汇选择',
  chinese: '中式思维',
}

const CHANNEL_ICON: Record<PointChannel, Component> = {
  rule: Document,
  fluency: Promotion,
  lexical: ChatLineSquare,
  chinese: Connection,
}

function channelLabel(channel: PointChannel): string {
  return CHANNEL_LABEL[channel] ?? '其它'
}

function channelIcon(channel: PointChannel): Component {
  return CHANNEL_ICON[channel] ?? Document
}

function previewExamples(card: ActionCard): ActionCardExample[] {
  return (card.examples ?? []).slice(0, 2)
}

function onLocate(example: ActionCardExample) {
  if (example.sentenceId != null) {
    emit('locate', example.sentenceId)
  }
}

function onPractice(card: ActionCard) {
  emit('practice', card)
}

function cardKey(card: ActionCard): string {
  return card.habitKey || card.pointId
}
</script>

<template>
  <div v-if="props.cards.length" class="ac-panel">
    <details
        v-for="card in props.cards"
        :key="cardKey(card)"
        class="ac-card kk-glass"
        :open="card.rank < 2"
    >
      <summary class="ac-summary">
        <el-icon class="ac-chevron"><ArrowRight/></el-icon>
        <span class="ac-channel-icon" :class="`ac-channel-icon--${card.channel}`" aria-hidden="true">
          <el-icon>
            <component :is="channelIcon(card.channel)"/>
          </el-icon>
        </span>
        <span class="ac-title">{{ card.titleZh }}</span>
        <span class="ac-channel-tag">{{ channelLabel(card.channel) }}</span>
        <span class="ac-count">{{ card.errorCount }} 句</span>
      </summary>

      <div class="ac-body">
        <p v-if="card.whyZh" class="ac-why">{{ card.whyZh }}</p>

        <div v-if="previewExamples(card).length" class="ac-examples">
          <div
              v-for="(example, i) in previewExamples(card)"
              :key="example.sentenceId ?? `${cardKey(card)}-${i}`"
              class="ac-example"
          >
            <p class="ac-example-orig">{{ example.originalSentence }}</p>
            <p v-if="example.suggestion" class="ac-example-suggest">{{ example.suggestion }}</p>
            <button
                v-if="example.sentenceId != null"
                type="button"
                class="ac-link-btn"
                @click="onLocate(example)"
            >
              查看原句
            </button>
          </div>
        </div>

        <div class="ac-actions">
          <el-button size="small" type="primary" plain @click="onPractice(card)">
            重说
          </el-button>
        </div>
      </div>
    </details>
  </div>
</template>

<style scoped>
.ac-panel {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.ac-card {
  padding: 0.9rem 1rem;
  border-radius: var(--kk-radius-md);
}

.ac-card > summary {
  list-style: none;
  cursor: pointer;
  user-select: none;
}

.ac-card > summary::-webkit-details-marker {
  display: none;
}

.ac-summary {
  display: flex;
  align-items: center;
  gap: 0.55rem;
}

.ac-chevron {
  flex-shrink: 0;
  color: var(--kk-color-text-subtle);
  transition: transform 0.2s var(--kk-ease-out);
}

.ac-card[open] .ac-chevron {
  transform: rotate(90deg);
}

.ac-channel-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 1.55rem;
  height: 1.55rem;
  flex-shrink: 0;
  border-radius: var(--kk-radius-sm);
  font-size: 0.85rem;
  background: color-mix(in srgb, var(--kk-color-primary) 10%, white);
  color: var(--kk-color-primary-soft);
  border: 1px solid color-mix(in srgb, var(--kk-color-primary) 16%, transparent);
}

.ac-channel-icon--chinese {
  background: color-mix(in srgb, var(--kk-color-accent) 20%, white);
  color: var(--kk-color-accent-text);
  border-color: color-mix(in srgb, var(--kk-color-accent) 30%, transparent);
}

.ac-title {
  flex: 1;
  min-width: 0;
  font-weight: 700;
  color: var(--kk-color-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ac-channel-tag {
  flex-shrink: 0;
  padding: 0.15rem 0.55rem;
  border-radius: var(--kk-radius-pill);
  background: var(--kk-glass-inner-bg);
  border: 1px solid var(--kk-glass-inner-border);
  font-size: 0.7rem;
  font-weight: 600;
  color: var(--kk-color-text-subtle);
}

.ac-count {
  flex-shrink: 0;
  font-family: var(--kk-font-mono);
  font-size: 0.78rem;
  font-weight: 700;
  color: var(--kk-color-primary);
  font-variant-numeric: tabular-nums;
}

.ac-body {
  margin-top: 0.75rem;
  padding-top: 0.75rem;
  border-top: 1px solid var(--kk-glass-divider);
  display: flex;
  flex-direction: column;
  gap: 0.65rem;
}

.ac-why {
  margin: 0;
  font-size: 0.85rem;
  line-height: 1.55;
  color: var(--kk-color-text-muted);
}

.ac-examples {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.ac-example {
  padding: 0.65rem 0.75rem;
  border-radius: var(--kk-radius-sm);
  background: var(--kk-glass-inner-bg);
  border-left: 3px solid color-mix(in srgb, var(--kk-color-primary) 35%, transparent);
}

.ac-example-orig {
  margin: 0;
  font-family: var(--kk-font-mono);
  font-size: 0.85rem;
  line-height: 1.5;
  color: var(--kk-color-text-muted);
  font-style: italic;
}

.ac-example-suggest {
  margin: 0.25rem 0 0;
  font-family: var(--kk-font-mono);
  font-size: 0.85rem;
  line-height: 1.5;
  font-weight: 600;
  color: var(--kk-color-primary);
}

.ac-link-btn {
  margin-top: 0.4rem;
  padding: 0;
  border: none;
  background: none;
  font-size: 0.76rem;
  font-weight: 600;
  color: var(--kk-color-link);
  cursor: pointer;
}

.ac-link-btn:hover {
  text-decoration: underline;
}

.ac-actions {
  display: flex;
  justify-content: flex-end;
}

@media (prefers-reduced-motion: reduce) {
  .ac-chevron {
    transition: none;
  }
}
</style>
