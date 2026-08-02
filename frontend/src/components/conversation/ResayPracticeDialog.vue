<script setup lang="ts">
import {ChatLineSquare, CircleCheck, MagicStick} from '@element-plus/icons-vue'
import {computed} from 'vue'

import type {PracticePrompt} from '@/types/conversation'

const props = defineProps<{
  modelValue: boolean
  prompt: PracticePrompt | null
  actionHint?: string
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  done: []
}>()

const visible = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit('update:modelValue', value),
})

const hasOriginal = computed(() => !!props.prompt?.originalSentence)
const hasTarget = computed(() => !!props.prompt?.targetSentence)
const hasCoaching = computed(() => !!props.prompt?.coachingZh)
const hasContent = computed(() => hasOriginal.value || hasTarget.value || hasCoaching.value)

function onConfirm() {
  emit('done')
  visible.value = false
}
</script>

<template>
  <el-dialog
      v-model="visible"
      class="resay-dialog"
      width="min(92vw, 30rem)"
      align-center
      append-to-body
      destroy-on-close
  >
    <template #header>
      <div class="resay-head">
        <span class="resay-head-icon" aria-hidden="true">
          <el-icon><ChatLineSquare/></el-icon>
        </span>
        <span class="resay-head-title">{{ actionHint || '重说一句' }}</span>
      </div>
    </template>

    <div class="resay-body">
      <section v-if="hasOriginal" class="resay-pane resay-pane--orig">
        <header class="pane-head">
          <span class="pane-tag">原句</span>
        </header>
        <p class="pane-quote">{{ prompt?.originalSentence }}</p>
      </section>

      <section v-if="hasTarget" class="resay-pane resay-pane--target">
        <header class="pane-head">
          <span class="pane-head-icon" aria-hidden="true">
            <el-icon><MagicStick/></el-icon>
          </span>
          <span class="pane-tag pane-tag--ai">改说成</span>
        </header>
        <p class="pane-improved">{{ prompt?.targetSentence }}</p>
      </section>

      <p v-if="hasCoaching" class="resay-coaching">{{ prompt?.coachingZh }}</p>

      <p v-if="!hasContent" class="resay-empty">
        暂无可练习的例句，可先返回查看具体证据句。
      </p>
    </div>

    <template #footer>
      <div class="resay-footer">
        <el-button @click="visible = false">先不练</el-button>
        <el-button type="primary" :icon="CircleCheck" @click="onConfirm">我说过了</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<style scoped>
/*
 * el-dialog 的根节点会继承本组件的 scope 属性（与 el-form 等 EP 组件一致，
 * 参见 LoginView 中 .login-form :deep(...) 的用法），故可直接用 :deep() 定制。
 * 不在此处重复 backdrop-filter，统一走 .kk-glass* token。
 */
.resay-dialog {
  --el-dialog-border-radius: var(--kk-radius-lg);
}

.resay-dialog:deep(.el-dialog) {
  padding: 0;
  overflow: hidden;
  background: var(--kk-glass-panel-bg);
  border: 1px solid var(--kk-glass-panel-border);
  box-shadow: var(--kk-glass-panel-shadow), inset 0 1px 0 var(--kk-glass-highlight);
}

.resay-dialog:deep(.el-dialog__header) {
  margin: 0;
  padding: 1.1rem 1.3rem 0.85rem;
  border-bottom: 1px solid var(--kk-glass-divider);
}

.resay-dialog:deep(.el-dialog__body) {
  padding: 1.1rem 1.3rem;
}

.resay-dialog:deep(.el-dialog__footer) {
  padding: 0.75rem 1.3rem 1.15rem;
  border-top: 1px solid var(--kk-glass-divider);
}

.resay-head {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.resay-head-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 1.9rem;
  height: 1.9rem;
  flex-shrink: 0;
  border-radius: var(--kk-radius-sm);
  background: color-mix(in srgb, var(--kk-color-accent) 22%, white);
  color: var(--kk-color-accent-text);
  border: 1px solid color-mix(in srgb, var(--kk-color-accent) 30%, transparent);
}

.resay-head-title {
  font-family: var(--kk-font-display);
  font-size: 1.1rem;
  font-weight: 700;
  color: var(--kk-color-primary);
}

.resay-body {
  display: flex;
  flex-direction: column;
  gap: 0.85rem;
}

.resay-pane {
  padding: 0.85rem 0.95rem;
  border-radius: var(--kk-radius-md);
}

.resay-pane--orig {
  background: var(--kk-glass-inner-bg);
  border-left: 3px solid var(--kk-color-accent);
}

.resay-pane--target {
  background: var(--kk-glass-inner-bg-muted);
  border: 1px solid var(--kk-glass-inner-border);
  box-shadow: inset 0 0 0 1px color-mix(in srgb, var(--kk-color-primary) 6%, transparent);
}

.pane-head {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  margin-bottom: 0.4rem;
}

.pane-head-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 1.35rem;
  height: 1.35rem;
  border-radius: var(--kk-radius-sm);
  color: var(--kk-color-primary);
  background: color-mix(in srgb, var(--kk-color-primary) 12%, white);
}

.pane-tag {
  font-size: 0.72rem;
  font-weight: 700;
  letter-spacing: 0.04em;
  text-transform: uppercase;
  color: var(--kk-color-text-subtle);
}

.pane-tag--ai {
  color: var(--kk-color-primary-soft);
}

.pane-quote {
  margin: 0;
  font-family: var(--kk-font-mono);
  font-size: 0.95rem;
  line-height: 1.6;
  color: var(--kk-color-text-muted);
  font-style: italic;
}

.pane-improved {
  margin: 0;
  font-family: var(--kk-font-mono);
  font-size: 0.98rem;
  line-height: 1.6;
  font-weight: 600;
  color: var(--kk-color-primary);
}

.resay-coaching {
  margin: 0;
  padding: 0.7rem 0.85rem;
  border-radius: var(--kk-radius-md);
  background: var(--kk-color-accent-bg);
  color: var(--kk-color-accent-text);
  font-size: 0.88rem;
  line-height: 1.55;
}

.resay-empty {
  margin: 0;
  font-size: 0.88rem;
  color: var(--kk-color-text-muted);
}

.resay-footer {
  display: flex;
  justify-content: flex-end;
  gap: 0.6rem;
}
</style>
