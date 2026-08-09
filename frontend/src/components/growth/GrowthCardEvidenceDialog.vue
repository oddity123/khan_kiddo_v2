<script setup lang="ts">
import {computed, onBeforeUnmount, onMounted, ref} from 'vue'

import HabitEvidencePreview from '@/components/conversation/HabitEvidencePreview.vue'
import type {GrowthCardEvidence} from '@/types/growthCard'

const open = defineModel<boolean>({default: false})

const props = withDefaults(
    defineProps<{
      title?: string
      items?: GrowthCardEvidence[]
    }>(),
    {
      title: '',
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
