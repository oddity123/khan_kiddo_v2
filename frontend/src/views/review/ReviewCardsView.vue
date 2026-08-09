<script setup lang="ts">
import {Refresh, Tickets} from '@element-plus/icons-vue'
import {ElMessage} from 'element-plus'
import {computed, onMounted, ref} from 'vue'

import {fetchRandomGrowthCards, fetchTodayGrowthCards} from '@/api/growthCard'
import GrowthFlashcardDeck from '@/components/growth/GrowthFlashcardDeck.vue'
import type {GrowthCard} from '@/types/growthCard'
import {getErrorMessage} from '@/utils/error'

type ReviewMode = 'today' | 'random'

const loading = ref(false)
const mode = ref<ReviewMode>('today')
const cards = ref<GrowthCard[]>([])

const dueCount = computed(() => (mode.value === 'today' ? cards.value.length : null))

const modeLabel = computed(() => (mode.value === 'today' ? '今日待复习' : '随机 5 张'))

async function loadToday() {
  loading.value = true
  mode.value = 'today'
  try {
    const {data} = await fetchTodayGrowthCards()
    cards.value = data ?? []
  } catch (error) {
    cards.value = []
    ElMessage.error(getErrorMessage(error, '加载今日成长卡失败'))
  } finally {
    loading.value = false
  }
}

async function loadRandom() {
  loading.value = true
  mode.value = 'random'
  try {
    const {data} = await fetchRandomGrowthCards(5)
    cards.value = data ?? []
    if (!cards.value.length) {
      ElMessage.info('暂时没有可抽的成长卡')
    }
  } catch (error) {
    cards.value = []
    ElMessage.error(getErrorMessage(error, '随机抽卡失败'))
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  void loadToday()
})

function onCardDeleted(cardId: string) {
  cards.value = cards.value.filter((card) => card.cardId !== cardId)
}
</script>

<template>
  <div class="review-cards-page" v-loading="loading">
    <header class="page-head">
      <div class="head-copy">
        <p class="page-eyebrow">Card Review</p>
        <h1 class="page-title">成长卡复习</h1>
        <p class="page-desc">
          默认练习今日到期卡片；也可随机抽 5 张巩固。翻面后选择评分档位。
        </p>
      </div>
      <div class="head-actions">
        <button
            type="button"
            class="mode-btn"
            :class="{ 'mode-btn--active': mode === 'today' }"
            :disabled="loading"
            @click="loadToday"
        >
          <el-icon><Tickets/></el-icon>
          今日待复习
          <span v-if="mode === 'today' && dueCount != null" class="mode-count">{{ dueCount }}</span>
        </button>
        <button
            type="button"
            class="mode-btn mode-btn--accent"
            :disabled="loading"
            @click="loadRandom"
        >
          <el-icon><Refresh/></el-icon>
          随机 5 张
        </button>
      </div>
    </header>

    <section class="review-stage kk-glass kk-glass--panel">
      <div class="stage-meta">
        <span class="stage-kicker">{{ modeLabel }}</span>
        <span class="stage-hint">先点卡片看答案，再选择评分</span>
      </div>
      <GrowthFlashcardDeck
          page
          deletable
          :cards="cards"
          :empty-today-text="mode === 'random' ? '没有抽到成长卡，先去分析生成一些吧' : '今天没有待复习的成长卡'"
          :empty-done-text="mode === 'random' ? '这 5 张已经练完' : '今日成长卡已练完'"
          @deleted="onCardDeleted"
      />
    </section>
  </div>
</template>

<style scoped>
.review-cards-page {
  font-family: var(--kk-font-body);
  color: var(--kk-color-text);
  display: flex;
  flex-direction: column;
  gap: 1rem;
  min-height: calc(100dvh - var(--kk-navbar-offset) - 3rem);
  padding-bottom: 1rem;
}

.page-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 1rem;
  flex-wrap: wrap;
}

.head-copy {
  min-width: 0;
  flex: 1 1 16rem;
}

.page-eyebrow {
  margin: 0 0 0.2rem;
  font-family: var(--kk-font-mono);
  font-size: 0.72rem;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: var(--kk-color-text-subtle);
}

.page-title {
  margin: 0 0 0.25rem;
  font-family: var(--kk-font-display);
  font-size: clamp(1.35rem, 2.6vw, 1.85rem);
  font-weight: 800;
  color: var(--kk-color-primary);
  line-height: 1.2;
}

.page-desc {
  margin: 0;
  max-width: 36rem;
  font-size: 0.9rem;
  color: var(--kk-color-text-muted);
  line-height: 1.55;
}

.head-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
  align-items: center;
}

.mode-btn {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  min-height: 2.45rem;
  padding: 0.45rem 0.85rem;
  border-radius: var(--kk-radius-sm);
  border: 1px solid rgba(11, 26, 125, 0.12);
  background: rgba(255, 255, 255, 0.78);
  color: var(--kk-color-primary);
  font-family: inherit;
  font-size: 0.86rem;
  font-weight: 700;
  cursor: pointer;
  transition:
      background var(--kk-duration-normal) var(--kk-ease-out),
      border-color var(--kk-duration-normal) var(--kk-ease-out),
      transform var(--kk-duration-normal) var(--kk-ease-out);
}

.mode-btn:hover:not(:disabled) {
  transform: translateY(-1px);
  border-color: rgba(11, 26, 125, 0.2);
}

.mode-btn:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.mode-btn--active {
  color: #fff;
  background: linear-gradient(135deg, var(--kk-color-primary), var(--kk-color-primary-soft));
  border-color: transparent;
  box-shadow: 0 8px 18px rgba(11, 26, 125, 0.22);
}

.mode-btn--accent {
  color: var(--kk-color-accent-text);
  background: var(--kk-color-accent-bg);
  border-color: rgba(184, 148, 31, 0.22);
}

.mode-count {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 1.35rem;
  padding: 0.08rem 0.35rem;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.22);
  font-family: var(--kk-font-mono);
  font-size: 0.72rem;
}

.review-stage {
  flex: 0 0 auto;
  display: flex;
  flex-direction: column;
  gap: 1rem;
  min-height: 0;
  padding: 1.15rem 1.1rem 1.25rem;
}

.stage-meta {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 0.75rem;
  flex-wrap: wrap;
}

.stage-kicker {
  font-family: var(--kk-font-mono);
  font-size: 0.72rem;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--kk-color-primary);
}

.stage-hint {
  font-size: 0.78rem;
  color: var(--kk-color-text-subtle);
}

@media (max-width: 720px) {
  .review-stage {
    padding: 0.9rem 0.8rem 1rem;
  }
}
</style>
