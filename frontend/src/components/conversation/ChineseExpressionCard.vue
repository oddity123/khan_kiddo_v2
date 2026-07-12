<script setup lang="ts">
import {ChatLineSquare, MagicStick, QuestionFilled} from '@element-plus/icons-vue'

import type {ChineseExpressionItem} from '@/types/conversation'

defineProps<{
  item: ChineseExpressionItem
  index?: number
}>()
</script>

<template>
  <article
      class="cn-card kk-glass"
      :style="{ '--card-delay': `${(index ?? 0) * 70}ms` }"
  >
    <header class="cn-card-head">
      <span class="cn-badge">
        <el-icon aria-hidden="true"><QuestionFilled/></el-icon>
        表达缺口
      </span>
    </header>

    <section class="cn-pane cn-pane--orig">
      <header class="pane-head">
        <span class="pane-head-icon" aria-hidden="true">
          <el-icon><ChatLineSquare/></el-icon>
        </span>
        <span class="pane-tag">{{ item.focusPhrase ? '目标词' : '原句' }}</span>
      </header>
      <p class="pane-quote">{{ item.focusPhrase || item.originalSentence }}</p>
    </section>

    <section v-if="item.suggestion" class="cn-pane cn-pane--suggest">
      <header class="pane-head">
        <span class="pane-head-icon pane-head-icon--ai" aria-hidden="true">
          <el-icon><MagicStick/></el-icon>
        </span>
        <span class="pane-tag pane-tag--ai">{{ item.focusPhrase ? '英文' : '英文建议' }}</span>
      </header>
      <p class="pane-improved">{{ item.suggestion }}</p>
    </section>

    <p v-if="item.focusPhrase" class="cn-empty-hint">原句：{{ item.originalSentence }}</p>
    <p v-else-if="!item.suggestion" class="cn-empty-hint">暂未生成英文建议，可稍后重试分析。</p>
  </article>
</template>

<style scoped>
.cn-card {
  padding: 1rem 1.1rem;
  margin-bottom: 0.85rem;
  border-radius: var(--kk-radius-md);
  border: 1px solid rgba(184, 148, 31, 0.22);
  background: linear-gradient(
      135deg,
      rgba(243, 236, 212, 0.45) 0%,
      var(--kk-glass-inner-bg, rgba(255, 255, 255, 0.55)) 100%
  );
  animation: cn-card-in 0.45s var(--kk-ease-out) both;
  animation-delay: var(--card-delay, 0ms);
}

@keyframes cn-card-in {
  from {
    opacity: 0;
    transform: translateY(8px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.cn-card-head {
  margin-bottom: 0.65rem;
}

.cn-badge {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  padding: 0.2rem 0.65rem;
  border-radius: var(--kk-radius-pill);
  font-size: 0.78rem;
  font-weight: 600;
  color: var(--kk-color-accent-text);
  background: var(--kk-color-accent-bg);
}

.cn-pane {
  margin-top: 0.5rem;
}

.pane-head {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  margin-bottom: 0.35rem;
}

.pane-head-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 1.35rem;
  height: 1.35rem;
  border-radius: var(--kk-radius-sm);
  color: var(--kk-color-text-muted);
  background: rgba(11, 26, 125, 0.06);
}

.pane-head-icon--ai {
  color: var(--kk-color-accent-text);
  background: rgba(184, 148, 31, 0.15);
}

.pane-tag {
  font-size: 0.72rem;
  font-weight: 700;
  letter-spacing: 0.04em;
  text-transform: uppercase;
  color: var(--kk-color-text-subtle);
}

.pane-tag--ai {
  color: var(--kk-color-accent-text);
}

.pane-quote {
  margin: 0;
  font-family: var(--kk-font-body);
  font-size: 0.95rem;
  line-height: 1.55;
  color: var(--kk-color-text);
}

.pane-improved {
  margin: 0;
  font-family: var(--kk-font-mono);
  font-size: 0.92rem;
  line-height: 1.55;
  color: var(--kk-color-primary);
}

.cn-empty-hint {
  margin: 0.5rem 0 0;
  font-size: 0.82rem;
  color: var(--kk-color-text-muted);
}
</style>
