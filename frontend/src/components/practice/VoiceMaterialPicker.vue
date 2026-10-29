<script setup lang="ts">
import {onMounted, ref, watch} from 'vue'

import {listConversationAnalyses} from '@/api/conversationAnalysis'
import {fetchTodayGrowthCards} from '@/api/growthCard'
import type {AnalysisSummaryRow} from '@/types/conversation'
import type {GrowthCard} from '@/types/growthCard'
import {getErrorMessage} from '@/utils/error'

const props = defineProps<{
  disabled?: boolean
}>()

const cardIds = defineModel<string[]>('cardIds', {default: () => []})
const analysisIds = defineModel<string[]>('analysisIds', {default: () => []})

const loading = ref(false)
const loadError = ref('')
const cards = ref<GrowthCard[]>([])
const analyses = ref<AnalysisSummaryRow[]>([])

async function load() {
  loading.value = true
  loadError.value = ''
  try {
    const [cardRes, analysisRes] = await Promise.all([
      fetchTodayGrowthCards(),
      listConversationAnalyses({page: 1, size: 8}),
    ])
    cards.value = cardRes.data ?? []
    analyses.value = analysisRes.data?.records ?? []
  } catch (error) {
    cards.value = []
    analyses.value = []
    loadError.value = getErrorMessage(error, '加载可选材料失败')
  } finally {
    loading.value = false
  }
}

function toggleCard(id: string) {
  if (props.disabled) {
    return
  }
  const set = new Set(cardIds.value)
  if (set.has(id)) {
    set.delete(id)
  } else if (set.size < 10) {
    set.add(id)
  }
  cardIds.value = [...set]
}

function toggleAnalysis(id: string) {
  if (props.disabled) {
    return
  }
  const set = new Set(analysisIds.value)
  if (set.has(id)) {
    set.delete(id)
  } else if (set.size < 5) {
    set.add(id)
  }
  analysisIds.value = [...set]
}

watch(
  () => props.disabled,
  () => {
    // no-op; model owned by parent
  },
)

onMounted(() => {
  void load()
})
</script>

<template>
  <section class="material-picker kk-glass kk-glass--panel" aria-label="本场练习材料">
    <div class="material-picker__head">
      <h2 class="voice-section-title">本场材料</h2>
      <button type="button" class="material-picker__refresh" :disabled="disabled || loading" @click="load">
        刷新
      </button>
    </div>
    <p class="material-picker__hint">
      勾选今日到期成长卡与近期分析（可空）。通话中 AI 只会在所选范围内找题。
    </p>
    <p v-if="loadError" class="material-picker__error">{{ loadError }}</p>
    <div v-loading="loading" class="material-picker__body">
      <div class="material-picker__group">
        <h3 class="material-picker__label">成长卡（最多 10）</h3>
        <ul v-if="cards.length" class="material-picker__list">
          <li v-for="card in cards" :key="card.cardId">
            <label class="material-picker__item">
              <input
                type="checkbox"
                :checked="cardIds.includes(card.cardId)"
                :disabled="disabled || (!cardIds.includes(card.cardId) && cardIds.length >= 10)"
                @change="toggleCard(card.cardId)"
              >
              <span class="material-picker__meta">{{ card.type }} · {{ card.status }}</span>
              <span class="material-picker__front">{{ card.front }}</span>
            </label>
          </li>
        </ul>
        <p v-else class="material-picker__empty">今日暂无到期成长卡，可稍后刷新或空着手练。</p>
      </div>
      <div class="material-picker__group">
        <h3 class="material-picker__label">近期分析（最多 5）</h3>
        <ul v-if="analyses.length" class="material-picker__list">
          <li v-for="row in analyses" :key="row.analysisId">
            <label class="material-picker__item">
              <input
                type="checkbox"
                :checked="analysisIds.includes(row.analysisId)"
                :disabled="disabled || (!analysisIds.includes(row.analysisId) && analysisIds.length >= 5)"
                @change="toggleAnalysis(row.analysisId)"
              >
              <span class="material-picker__meta">{{ row.status }}</span>
              <span class="material-picker__front">{{ row.preview || row.analysisId }}</span>
            </label>
          </li>
        </ul>
        <p v-else class="material-picker__empty">暂无分析记录。</p>
      </div>
    </div>
  </section>
</template>

<style scoped>
.material-picker {
  flex-shrink: 0;
  padding: 0.85rem 1.1rem;
}

.material-picker__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
}

.material-picker__head .voice-section-title {
  margin-bottom: 0;
}

.material-picker__refresh {
  border: none;
  background: none;
  padding: 0;
  color: var(--kk-color-primary);
  font-family: var(--kk-font-body);
  font-weight: 600;
  cursor: pointer;
}

.material-picker__refresh:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.material-picker__hint,
.material-picker__empty,
.material-picker__error {
  margin: 0.45rem 0 0.65rem;
  font-size: 0.85rem;
  color: var(--kk-color-text-muted);
  line-height: 1.45;
}

.material-picker__error {
  color: var(--kk-color-danger);
}

.material-picker__body {
  display: grid;
  gap: 0.85rem;
}

@media (min-width: 900px) {
  .material-picker__body {
    grid-template-columns: 1fr 1fr;
  }
}

.material-picker__label {
  margin: 0 0 0.4rem;
  font-family: var(--kk-font-body);
  font-size: 0.82rem;
  font-weight: 700;
  color: var(--kk-color-text-secondary);
}

.material-picker__list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 0.4rem;
  max-height: min(40vh, 16rem);
  overflow-y: auto;
}

.material-picker__item {
  display: grid;
  grid-template-columns: auto 1fr;
  grid-template-rows: auto auto;
  column-gap: 0.5rem;
  row-gap: 0.15rem;
  align-items: start;
  padding: 0.45rem 0.55rem;
  border-radius: var(--kk-radius-sm);
  background: var(--kk-glass-inner-bg);
  border: 1px solid var(--kk-glass-inner-border);
  cursor: pointer;
}

.material-picker__item input {
  grid-row: 1 / span 2;
  margin-top: 0.2rem;
}

.material-picker__meta {
  font-family: var(--kk-font-mono);
  font-size: 0.72rem;
  color: var(--kk-color-text-subtle);
}

.material-picker__front {
  font-size: 0.88rem;
  color: var(--kk-color-text);
  line-height: 1.35;
  word-break: break-word;
}
</style>
