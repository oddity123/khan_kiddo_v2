<script setup lang="ts">
import {ArrowRight, CircleCheck, MagicStick} from '@element-plus/icons-vue'
import {ElMessage} from 'element-plus'
import {computed, ref} from 'vue'

import {mintHabitGrowthCard} from '@/api/growthCard'
import type {ActionCard, ActionCardExample, PointChannel} from '@/types/conversation'
import type {GrowthCard} from '@/types/growthCard'
import {getErrorMessage} from '@/utils/error'

const props = withDefaults(
    defineProps<{
      cards: ActionCard[]
      analysisId?: string
      /** 本场已生成成长卡，用于置灰「已生成」 */
      growthCards?: GrowthCard[]
    }>(),
    {cards: () => [], growthCards: () => []},
)

const emit = defineEmits<{
  locate: [sentenceId: string | number]
  generated: [card: ActionCard]
}>()

const CHANNEL_LABEL: Record<PointChannel, string> = {
  rule: '语法规则',
  fluency: '表达流畅',
  lexical: '词汇选择',
  chinese: '中式思维',
}

const mintingKey = ref<string | null>(null)

const generatedHabitRefs = computed(() => {
  const refs = new Set<string>()
  for (const card of props.growthCards) {
    if (card.type === 'habit' && card.sourceRef) {
      refs.add(card.sourceRef)
    }
  }
  return refs
})

function channelLabel(channel: PointChannel): string {
  return CHANNEL_LABEL[channel] ?? '其它'
}

function previewExamples(card: ActionCard): ActionCardExample[] {
  return (card.examples ?? []).slice(0, 2)
}

function onLocate(example: ActionCardExample) {
  if (example.sentenceId != null) {
    emit('locate', example.sentenceId)
  }
}

function cardKey(card: ActionCard): string {
  return card.habitKey || card.pointId
}

function habitSourceRef(card: ActionCard): string | null {
  const key = cardKey(card)
  return key ? `habit:${key}` : null
}

function isGenerated(card: ActionCard): boolean {
  const ref = habitSourceRef(card)
  return Boolean(ref && generatedHabitRefs.value.has(ref))
}

function displayTitle(card: ActionCard): string {
  return (card.headlineZh || card.titleZh || '').replace(/^本次最该改：/, '').trim()
}

function displayDiagnosis(card: ActionCard): string {
  return (card.diagnosisZh || card.whyZh || '').trim()
}

async function onGenerate(card: ActionCard) {
  if (!props.analysisId || mintingKey.value || isGenerated(card)) {
    return
  }
  const key = cardKey(card)
  if (!key) {
    ElMessage.warning('暂无可生成的卡片内容')
    return
  }
  mintingKey.value = key
  try {
    await mintHabitGrowthCard(props.analysisId, key)
    ElMessage.success('已生成成长卡')
    emit('generated', card)
  } catch (error) {
    ElMessage.error(getErrorMessage(error, '生成卡片失败'))
  } finally {
    mintingKey.value = null
  }
}
</script>

<template>
  <div v-if="props.cards.length" class="ac-panel">
    <details
        v-for="card in props.cards"
        :key="cardKey(card)"
        class="ac-card"
        :open="card.rank < 3"
    >
      <summary class="ac-summary">
        <el-icon class="ac-chevron"><ArrowRight/></el-icon>
        <span class="rank-mark" :aria-label="`Top ${card.rank}`">
          <span class="rank-mark-label">TOP</span>
          <span class="rank-mark-num">{{ card.rank }}</span>
        </span>
        <span class="ac-title">{{ displayTitle(card) }}</span>
        <span class="ac-channel-tag">{{ channelLabel(card.channel) }}</span>
        <span class="ac-count">{{ card.errorCount }} 句</span>
      </summary>

      <div class="ac-body">
        <p v-if="displayDiagnosis(card)" class="ac-why">{{ displayDiagnosis(card) }}</p>

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
          <button
              type="button"
              class="ac-cta"
              :class="{ 'ac-cta--done': isGenerated(card) }"
              :disabled="!analysisId || mintingKey === cardKey(card) || isGenerated(card)"
              @click="onGenerate(card)"
          >
            <el-icon v-if="isGenerated(card)"><CircleCheck/></el-icon>
            <el-icon v-else><MagicStick/></el-icon>
            <template v-if="isGenerated(card)">已生成</template>
            <template v-else-if="mintingKey === cardKey(card)">制卡中…</template>
            <template v-else>让khankiddo制卡</template>
          </button>
        </div>
      </div>
    </details>
  </div>
</template>

<style scoped>
.ac-panel {
  display: flex;
  flex-direction: column;
  gap: 0.65rem;
}

.ac-card {
  padding: 0.85rem 0.95rem;
  border-radius: var(--kk-radius-lg);
  background: var(--kk-glass-inner-bg);
  border: 1px solid var(--kk-glass-inner-border);
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
  gap: 0.65rem;
}

.ac-chevron {
  flex-shrink: 0;
  color: var(--kk-color-text-subtle);
  transition: transform 0.2s var(--kk-ease-out);
}

.ac-card[open] .ac-chevron {
  transform: rotate(90deg);
}

.rank-mark {
  display: inline-flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  width: 2.35rem;
  padding: 0.22rem 0.1rem 0.28rem;
  border-radius: var(--kk-radius-md);
  background: color-mix(in srgb, var(--kk-color-primary) 10%, white);
  color: var(--kk-color-primary);
  line-height: 1;
}

.rank-mark-label {
  font-family: var(--kk-font-mono);
  font-size: 0.5rem;
  font-weight: 700;
  letter-spacing: 0.12em;
  opacity: 0.75;
}

.rank-mark-num {
  margin-top: 0.08rem;
  font-family: var(--kk-font-display);
  font-size: 1.05rem;
  font-weight: 800;
  font-variant-numeric: tabular-nums;
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
  background: rgba(255, 255, 255, 0.55);
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
  border-radius: var(--kk-radius-md);
  background: rgba(255, 255, 255, 0.45);
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

.ac-cta {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  padding: 0.45rem 0.95rem;
  border-radius: var(--kk-radius-pill);
  border: none;
  background: linear-gradient(145deg, var(--kk-color-primary), var(--kk-color-primary-soft));
  color: #fff;
  font-size: 0.84rem;
  font-weight: 700;
  cursor: pointer;
  box-shadow: var(--kk-shadow-btn);
  transition: transform 0.18s ease, box-shadow 0.18s ease, opacity 0.18s ease;
}

.ac-cta:hover:not(:disabled) {
  transform: translateY(-1px);
  box-shadow: var(--kk-shadow-btn-hover);
}

.ac-cta:active:not(:disabled) {
  transform: translateY(0);
}

.ac-cta:disabled {
  opacity: 0.65;
  cursor: not-allowed;
}

.ac-cta--done,
.ac-cta--done:disabled {
  opacity: 1;
  color: var(--kk-color-success);
  background: var(--kk-color-success-bg);
  border: 1px solid color-mix(in srgb, var(--kk-color-success) 22%, transparent);
  box-shadow: none;
  cursor: default;
}

@media (max-width: 640px) {
  .ac-channel-tag {
    display: none;
  }

  .ac-cta {
    width: 100%;
    justify-content: center;
  }
}

@media (prefers-reduced-motion: reduce) {
  .ac-chevron,
  .ac-cta {
    transition: none;
  }
}
</style>
