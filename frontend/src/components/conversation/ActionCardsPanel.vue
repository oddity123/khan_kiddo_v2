<script setup lang="ts">
import {ArrowRight, CircleCheck, MagicStick} from '@element-plus/icons-vue'
import {ElMessage} from 'element-plus'
import {computed, onBeforeUnmount, onMounted, ref} from 'vue'

import {mintHabitGrowthCard} from '@/api/growthCard'
import HabitEvidencePreview from '@/components/conversation/HabitEvidencePreview.vue'
import SentenceAnalysisCard from '@/components/conversation/SentenceAnalysisCard.vue'
import type {
  ActionCard,
  ActionCardExample,
  AnalysisItem,
  PointChannel,
} from '@/types/conversation'
import type {GrowthCard} from '@/types/growthCard'
import {getErrorMessage} from '@/utils/error'

const PREVIEW_LIMIT = 2

const props = withDefaults(
    defineProps<{
      cards: ActionCard[]
      analysisId?: string
      /** 本场已生成成长卡，用于置灰「已生成」 */
      growthCards?: GrowthCard[]
      /** 句子级检查完整列表，用于证据卡展示该句全部优化点 */
      analysisItems?: AnalysisItem[]
    }>(),
    {cards: () => [], growthCards: () => [], analysisItems: () => []},
)

const emit = defineEmits<{
  generated: [card: ActionCard]
  openCards: []
}>()

const CHANNEL_LABEL: Record<PointChannel, string> = {
  rule: '语法规则',
  fluency: '表达流畅',
  lexical: '词汇选择',
  chinese: '中式思维',
}

const mintingKey = ref<string | null>(null)
const evidenceOpen = ref(false)
const evidenceTitle = ref('')
const evidenceItems = ref<AnalysisItem[]>([])
const viewportWidth = ref(typeof window !== 'undefined' ? window.innerWidth : 1024)

function syncViewportWidth() {
  viewportWidth.value = window.innerWidth
}

onMounted(() => {
  syncViewportWidth()
  window.addEventListener('resize', syncViewportWidth, {passive: true})
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', syncViewportWidth)
})

const evidenceFullscreen = computed(() => viewportWidth.value <= 640)
const evidenceDialogWidth = computed(() =>
    evidenceFullscreen.value ? '100%' : 'min(880px, 96vw)',
)

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

function cardKey(card: ActionCard): string {
  return card.habitKey || card.pointId
}

function allExamples(card: ActionCard): ActionCardExample[] {
  return card.examples ?? []
}

function previewExamples(card: ActionCard): ActionCardExample[] {
  return allExamples(card).slice(0, PREVIEW_LIMIT)
}

function evidenceCount(card: ActionCard): number {
  const resolved = resolveItems(allExamples(card), card).length
  return resolved > 0 ? resolved : card.errorCount || 0
}

const itemBySentenceId = computed(() => {
  const map = new Map<string, AnalysisItem>()
  for (const item of props.analysisItems) {
    if (item.sentenceId != null) {
      map.set(String(item.sentenceId), item)
    }
  }
  return map
})

/** 优先复用句子级检查的完整条目（含该句全部优化点） */
function resolveAnalysisItem(example: ActionCardExample, card: ActionCard): AnalysisItem {
  if (example.sentenceId != null && example.sentenceId !== '') {
    const byId = itemBySentenceId.value.get(String(example.sentenceId))
    if (byId) {
      return byId
    }
  }
  const needle = example.originalSentence?.trim()
  if (needle) {
    const byText = props.analysisItems.find((item) => item.originalSentence?.trim() === needle)
    if (byText) {
      return byText
    }
  }
  return fallbackAnalysisItem(example, card)
}

function fallbackAnalysisItem(example: ActionCardExample, card: ActionCard): AnalysisItem {
  const rawId = example.sentenceId
  const parsed = rawId == null || rawId === '' ? NaN : Number(rawId)
  const point = example.errorPoint?.trim()
  return {
    sentenceId: Number.isFinite(parsed) ? parsed : undefined,
    originalSentence: example.originalSentence,
    suggestion: example.suggestion,
    errors: point
        ? [{
          type: channelLabel(card.channel),
          point,
          errorLevel: 'BASIC',
          channel: card.channel,
          pointId: card.pointId,
        }]
        : [],
  }
}

function resolveItems(examples: ActionCardExample[], card: ActionCard): AnalysisItem[] {
  const seen = new Set<string>()
  const result: AnalysisItem[] = []
  for (const example of examples) {
    const item = resolveAnalysisItem(example, card)
    const key = item.sentenceId != null
        ? `id:${item.sentenceId}`
        : `txt:${item.originalSentence}`
    if (seen.has(key)) {
      continue
    }
    seen.add(key)
    result.push(item)
  }
  return result
}

function openEvidence(card: ActionCard, event?: Event) {
  event?.stopPropagation()
  event?.preventDefault()
  const items = resolveItems(allExamples(card), card)
  if (!items.length) {
    return
  }
  evidenceTitle.value = displayTitle(card)
  evidenceItems.value = items
  evidenceOpen.value = true
}

function habitSourceRef(card: ActionCard): string | null {
  const key = cardKey(card)
  return key ? `habit:${key}` : null
}

function isGenerated(card: ActionCard): boolean {
  const ref = habitSourceRef(card)
  return Boolean(ref && generatedHabitRefs.value.has(ref))
}

function isMintDone(card: ActionCard): boolean {
  return isGenerated(card)
}

function isMintPending(card: ActionCard): boolean {
  return mintingKey.value === cardKey(card)
}

function displayTitle(card: ActionCard): string {
  return (card.headlineZh || card.titleZh || '').replace(/^本次最该改：/, '').trim()
}

function displayDiagnosis(card: ActionCard): string {
  return (card.diagnosisZh || card.whyZh || '').trim()
}

function onMintClick(card: ActionCard, event?: Event) {
  if (isMintDone(card)) {
    onOpenCards(event)
    return
  }
  void onGenerate(card, event)
}

async function onGenerate(card: ActionCard, event?: Event) {
  event?.stopPropagation()
  event?.preventDefault()
  if (!props.analysisId || mintingKey.value || isMintDone(card)) {
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

function onOpenCards(event?: Event) {
  event?.stopPropagation()
  event?.preventDefault()
  emit('openCards')
}
</script>

<template>
  <div v-if="props.cards.length" class="ac-panel">
    <details
        v-for="card in props.cards"
        :key="cardKey(card)"
        class="ac-card"
        :class="{ 'ac-card--top1': card.rank === 1 }"
        :open="card.rank <= 2"
    >
      <summary class="ac-summary">
        <el-icon class="ac-chevron"><ArrowRight/></el-icon>
        <span class="rank-mark" :aria-label="`Top ${card.rank}`">
          <span class="rank-mark-label">TOP</span>
          <span class="rank-mark-num">{{ card.rank }}</span>
        </span>
        <div class="ac-summary-main">
          <h3 class="ac-title">{{ displayTitle(card) }}</h3>
          <div class="ac-meta-row">
            <span class="ac-channel-tag">{{ channelLabel(card.channel) }}</span>
            <button
                v-if="evidenceCount(card)"
                type="button"
                class="ac-evidence-btn"
                @click="openEvidence(card, $event)"
            >
              查看证据: {{ evidenceCount(card) }}句
            </button>
            <button
                type="button"
                class="ac-cta"
                :class="{
                  'ac-cta--done': isMintDone(card),
                  'ac-cta--pending': isMintPending(card),
                }"
                :disabled="!isMintDone(card) && (!analysisId || isMintPending(card))"
                @click="onMintClick(card, $event)"
            >
              <el-icon v-if="isMintDone(card)"><CircleCheck/></el-icon>
              <el-icon v-else-if="!isMintPending(card)"><MagicStick/></el-icon>
              <template v-if="isMintDone(card)">已生成</template>
              <template v-else-if="isMintPending(card)">制卡中…</template>
              <template v-else>制卡</template>
            </button>
          </div>
        </div>
      </summary>

      <div class="ac-body">
        <p v-if="displayDiagnosis(card)" class="ac-why">{{ displayDiagnosis(card) }}</p>

        <div v-if="previewExamples(card).length" class="ac-preview">
          <HabitEvidencePreview
              v-for="(example, i) in previewExamples(card)"
              :key="example.sentenceId ?? `${cardKey(card)}-pv-${i}`"
              :example="example"
          />
        </div>
      </div>
    </details>

    <el-dialog
        v-model="evidenceOpen"
        class="ac-evidence-dialog"
        :title="evidenceTitle ? `证据 · ${evidenceTitle}` : '证据'"
        :width="evidenceDialogWidth"
        append-to-body
        destroy-on-close
        align-center
        :fullscreen="evidenceFullscreen"
    >
      <div class="ac-evidence-list">
        <SentenceAnalysisCard
            v-for="(item, i) in evidenceItems"
            :key="item.sentenceId ?? `ev-${i}`"
            :item="item"
            :index="i"
        />
      </div>
    </el-dialog>
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

.ac-card--top1 {
  background: linear-gradient(
      155deg,
      color-mix(in srgb, var(--kk-color-accent) 14%, white) 0%,
      color-mix(in srgb, var(--kk-color-primary) 4%, white) 55%,
      rgba(255, 255, 255, 0.5) 100%
  );
  border: 1px solid color-mix(in srgb, var(--kk-color-accent) 28%, var(--kk-glass-inner-border));
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.75);
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
  align-items: flex-start;
  gap: 0.55rem;
}

.ac-chevron {
  flex-shrink: 0;
  margin-top: 0.55rem;
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

.ac-card--top1 .rank-mark {
  width: 2.85rem;
  padding: 0.3rem 0.12rem 0.35rem;
  background: linear-gradient(160deg, var(--kk-color-primary), var(--kk-color-primary-soft));
  color: #fff;
  box-shadow: 0 6px 16px color-mix(in srgb, var(--kk-color-primary) 28%, transparent);
}

.rank-mark-label {
  font-family: var(--kk-font-mono);
  font-size: 0.5rem;
  font-weight: 700;
  letter-spacing: 0.12em;
  opacity: 0.75;
}

.ac-card--top1 .rank-mark-label {
  opacity: 0.85;
}

.rank-mark-num {
  margin-top: 0.08rem;
  font-family: var(--kk-font-display);
  font-size: 1.05rem;
  font-weight: 800;
  font-variant-numeric: tabular-nums;
}

.ac-card--top1 .rank-mark-num {
  font-size: 1.35rem;
}

.ac-summary-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
  padding-top: 0.1rem;
}

.ac-title {
  margin: 0;
  font-family: var(--kk-font-display);
  font-size: 0.98rem;
  font-weight: 800;
  line-height: 1.35;
  color: var(--kk-color-text);
  overflow-wrap: anywhere;
  word-break: break-word;
}

.ac-card--top1 .ac-title {
  font-size: clamp(1.02rem, 2vw, 1.18rem);
  color: var(--kk-color-primary);
}

.ac-meta-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.4rem;
}

.ac-meta-row .ac-cta {
  margin-left: auto;
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

.ac-evidence-btn {
  flex-shrink: 0;
  margin: 0;
  padding: 0.15rem 0.55rem;
  border-radius: var(--kk-radius-pill);
  border: 1px solid color-mix(in srgb, var(--kk-color-primary) 18%, var(--kk-glass-inner-border));
  background: color-mix(in srgb, var(--kk-color-primary) 6%, white);
  font-family: var(--kk-font-mono);
  font-size: 0.7rem;
  font-weight: 700;
  color: var(--kk-color-primary);
  font-variant-numeric: tabular-nums;
  cursor: pointer;
  transition: background 0.15s ease, border-color 0.15s ease;
}

.ac-evidence-btn:hover {
  background: color-mix(in srgb, var(--kk-color-primary) 12%, white);
  border-color: color-mix(in srgb, var(--kk-color-primary) 28%, transparent);
}

.ac-body {
  margin-top: 0.85rem;
  padding-top: 0.85rem;
  border-top: 1px solid var(--kk-glass-divider);
  display: flex;
  flex-direction: column;
  gap: 0.85rem;
}

.ac-card--top1 .ac-body {
  border-top-color: color-mix(in srgb, var(--kk-color-accent) 18%, var(--kk-glass-divider));
}

.ac-why {
  margin: 0;
  font-size: 0.88rem;
  line-height: 1.65;
  color: var(--kk-color-text-muted);
}

.ac-preview {
  display: flex;
  flex-direction: column;
  gap: 0.55rem;
  /* 抵消卡片左右内边距，让例句占满可读宽度 */
  margin-inline: -0.35rem;
}

.ac-cta {
  display: inline-flex;
  align-items: center;
  gap: 0.28rem;
  padding: 0.28rem 0.7rem;
  border-radius: var(--kk-radius-pill);
  border: none;
  background: linear-gradient(145deg, var(--kk-color-primary), var(--kk-color-primary-soft));
  color: #fff;
  font-size: 0.74rem;
  font-weight: 700;
  cursor: pointer;
  box-shadow: var(--kk-shadow-btn);
  transition: transform 0.18s ease, box-shadow 0.18s ease, opacity 0.18s ease;
  white-space: nowrap;
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
  cursor: pointer;
}

.ac-cta--pending,
.ac-cta--pending:disabled {
  opacity: 1;
  color: var(--kk-color-text-muted);
  background: var(--kk-glass-inner-bg);
  border: 1px solid var(--kk-glass-inner-border);
  box-shadow: none;
  cursor: wait;
}

@media (max-width: 640px) {
  .ac-card {
    padding: 0.75rem 0.8rem;
  }

  .ac-body {
    margin-top: 0.7rem;
    padding-top: 0.7rem;
    gap: 0.7rem;
  }

  .ac-preview {
    margin-inline: -0.25rem;
  }

  .ac-cta {
    box-shadow: none;
  }

  .ac-evidence-btn {
    font-size: 0.66rem;
    padding: 0.12rem 0.45rem;
  }
}

@media (prefers-reduced-motion: reduce) {
  .ac-chevron,
  .ac-cta,
  .ac-evidence-btn {
    transition: none;
  }
}
</style>

<!-- teleported dialog：圆角与列表对齐句子卡 -->
<style>
.ac-evidence-dialog.el-dialog {
  border-radius: var(--kk-radius-lg);
  overflow: hidden;
  background: var(--kk-color-bg-base);
}

.ac-evidence-dialog .el-dialog__header {
  padding: 1rem 1.15rem 0.65rem;
  margin-right: 0;
}

.ac-evidence-dialog .el-dialog__title {
  font-family: var(--kk-font-display);
  font-weight: 700;
  color: var(--kk-color-primary);
  font-size: 1.05rem;
  line-height: 1.35;
  padding-right: 1.5rem;
}

.ac-evidence-dialog .el-dialog__body {
  padding: 0.35rem 1.15rem 1.15rem;
}

.ac-evidence-list {
  display: flex;
  flex-direction: column;
  gap: 1rem;
  max-height: min(78vh, 42rem);
  overflow-y: auto;
  padding: 0.15rem 0.25rem 0.35rem 0.1rem;
}

/* 弹窗内叠卡：收掉大模糊外阴影，避免缝隙处叠影发脏 */
.ac-evidence-list .sentence-card {
  margin-bottom: 0;
  box-shadow:
    inset 0 1px 0 var(--kk-glass-highlight),
    0 1px 2px rgba(11, 26, 125, 0.04),
    0 6px 16px rgba(11, 26, 125, 0.06);
}

.ac-evidence-list .sentence-card:last-child {
  margin-bottom: 0;
}

@media (max-width: 640px) {
  .ac-evidence-dialog.el-dialog {
    border-radius: 0;
    margin: 0;
    display: flex;
    flex-direction: column;
    height: 100%;
    max-height: 100%;
    overflow: hidden;
  }

  .ac-evidence-dialog .el-dialog__header {
    flex-shrink: 0;
    padding: 0.85rem 0.9rem 0.5rem;
  }

  .ac-evidence-dialog .el-dialog__title {
    font-size: 0.98rem;
  }

  .ac-evidence-dialog .el-dialog__body {
    flex: 1 1 auto;
    min-height: 0;
    overflow: hidden;
    display: flex;
    flex-direction: column;
    padding: 0.25rem 0.65rem max(1rem, env(safe-area-inset-bottom, 0px));
  }

  .ac-evidence-list {
    flex: 1 1 auto;
    min-height: 0;
    gap: 0.75rem;
    max-height: none;
    overflow-x: hidden;
    overflow-y: auto;
    -webkit-overflow-scrolling: touch;
    overscroll-behavior: contain;
    padding: 0 0 0.5rem;
  }

  /* 收紧嵌套内边距，加宽英文可读行宽 */
  .ac-evidence-list .sentence-card {
    padding: 0.75rem 0.7rem;
    border-radius: var(--kk-radius-md);
  }

  .ac-evidence-list .sentence-pane {
    padding: 0.7rem 0.75rem;
    border-radius: var(--kk-radius-sm);
  }

  .ac-evidence-list .sentence-pane--after {
    margin-top: 0.55rem;
  }

  .ac-evidence-list .pane-quote,
  .ac-evidence-list .pane-improved {
    font-size: 0.86rem;
    line-height: 1.6;
  }
}
</style>
