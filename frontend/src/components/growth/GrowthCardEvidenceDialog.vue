<script setup lang="ts">
import {ChatDotRound} from '@element-plus/icons-vue'
import {computed, onBeforeUnmount, onMounted, ref} from 'vue'

import HabitEvidencePreview from '@/components/conversation/HabitEvidencePreview.vue'
import type {GrowthCardEvidence} from '@/types/growthCard'

const open = defineModel<boolean>({default: false})

const props = withDefaults(
    defineProps<{
      title?: string
      reason?: string | null
      items?: GrowthCardEvidence[]
    }>(),
    {
      title: '',
      reason: null,
      items: () => [],
    },
)

const viewportWidth = ref(typeof window !== 'undefined' ? window.innerWidth : 1024)

function onResize() {
  viewportWidth.value = window.innerWidth
}

onMounted(() => {
  window.addEventListener('resize', onResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
})

const fullscreen = computed(() => viewportWidth.value <= 640)
const dialogWidth = computed(() => (fullscreen.value ? '100%' : 'min(880px, 96vw)'))

const dialogTitle = computed(() =>
    props.title ? `证据 · ${props.title}` : '证据',
)

const reasonText = computed(() => {
  const raw = props.reason
  return typeof raw === 'string' && raw.trim() ? raw.trim() : ''
})

const previewItems = computed(() =>
    (props.items ?? []).map((row) => ({
      sentenceId: row.sentenceId ?? undefined,
      originalSentence: row.originalSentence,
      suggestion: row.suggestion ?? undefined,
    })),
)
</script>

<template>
  <el-dialog
      v-model="open"
      class="gc-evidence-dialog"
      :title="dialogTitle"
      :width="dialogWidth"
      append-to-body
      destroy-on-close
      align-center
      :fullscreen="fullscreen"
  >
    <div
        v-if="reasonText"
        class="gc-evidence-reason"
        role="note"
        aria-label="说明"
    >
      <el-icon class="gc-evidence-reason__icon" :size="18">
        <ChatDotRound/>
      </el-icon>
      <div class="gc-evidence-reason__body">
        <span class="gc-evidence-reason__label">说明</span>
        <p class="gc-evidence-reason__text">{{ reasonText }}</p>
      </div>
    </div>
    <div v-if="previewItems.length" class="gc-evidence-list">
      <HabitEvidencePreview
          v-for="(item, i) in previewItems"
          :key="item.sentenceId ?? `gc-ev-${i}`"
          :example="item"
      />
    </div>
    <p v-else class="gc-evidence-empty">暂无关联原句</p>
  </el-dialog>
</template>

<style scoped>
.gc-evidence-reason {
  display: flex;
  align-items: flex-start;
  gap: 0.65rem;
  margin: 0 0 0.75rem;
  padding: 0.7rem 0.85rem;
  border-radius: var(--kk-radius-md);
  background: color-mix(in srgb, var(--kk-color-primary) 7%, white);
  border: 1px solid color-mix(in srgb, var(--kk-color-primary) 16%, var(--kk-glass-inner-border));
}

.gc-evidence-reason__icon {
  flex-shrink: 0;
  margin-top: 0.12rem;
  color: var(--kk-color-primary);
}

.gc-evidence-reason__body {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 0.2rem;
}

.gc-evidence-reason__label {
  font-size: 0.72rem;
  font-weight: 700;
  letter-spacing: 0.04em;
  text-transform: uppercase;
  color: var(--kk-color-primary);
  font-family: var(--kk-font-body);
}

.gc-evidence-reason__text {
  margin: 0;
  font-size: 0.92rem;
  line-height: 1.55;
  color: var(--kk-color-text);
  font-family: var(--kk-font-body);
  word-break: break-word;
}

.gc-evidence-list {
  display: flex;
  flex-direction: column;
  gap: 0.65rem;
  max-height: min(70vh, 32rem);
  overflow: auto;
  padding: 0.15rem 0.1rem 0.35rem;
}

.gc-evidence-empty {
  margin: 0;
  padding: 1.25rem 0.5rem;
  text-align: center;
  color: var(--kk-color-text-subtle);
  font-size: 0.9rem;
}
</style>

<style>
.gc-evidence-dialog.el-dialog {
  border-radius: var(--kk-radius-lg);
  overflow: hidden;
}

.gc-evidence-dialog .el-dialog__header {
  padding: 1rem 1.15rem 0.65rem;
  margin-right: 0;
}

.gc-evidence-dialog .el-dialog__title {
  font-size: 1.05rem;
  font-weight: 700;
  color: var(--kk-color-text);
}

.gc-evidence-dialog .el-dialog__body {
  padding: 0.35rem 1.15rem 1.15rem;
}

@media (max-width: 640px) {
  .gc-evidence-dialog.el-dialog {
    border-radius: 0;
  }

  .gc-evidence-dialog .el-dialog__header {
    padding: 0.85rem 1rem 0.5rem;
  }

  .gc-evidence-dialog .el-dialog__body {
    padding: 0.25rem 0.85rem 1rem;
  }
}
</style>
