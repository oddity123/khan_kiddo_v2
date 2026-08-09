<script setup lang="ts">
import {ChatDotRound, Close} from '@element-plus/icons-vue'
import {storeToRefs} from 'pinia'
import {onMounted, ref, watch} from 'vue'
import {useRoute, useRouter} from 'vue-router'

import GrammarRagChatPanel from '@/components/review/GrammarRagChatPanel.vue'
import {useAuthStore} from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const {isAuthenticated} = storeToRefs(auth)

const open = ref(false)

function openChat() {
  open.value = true
}

function closeChat() {
  open.value = false
}

function toggleChat() {
  open.value = !open.value
}

async function consumeChatQuery() {
  if (route.query.chat !== '1') {
    return
  }
  open.value = true
  const nextQuery = {...route.query}
  delete nextQuery.chat
  await router.replace({path: route.path, query: nextQuery})
}

onMounted(() => {
  void consumeChatQuery()
})

watch(
    () => route.fullPath,
    () => {
      void consumeChatQuery()
    },
)

watch(isAuthenticated, (authed) => {
  if (!authed) {
    open.value = false
  }
})

defineExpose({openChat, closeChat, toggleChat})
</script>

<template>
  <div v-if="isAuthenticated" class="rag-float">
    <div
        class="rag-panel kk-glass kk-glass--panel"
        :class="{ 'rag-panel--open': open }"
        role="dialog"
        aria-label="复盘助手对话"
        :aria-hidden="!open"
    >
      <header class="rag-panel-head">
        <div class="rag-panel-title">
          <el-icon><ChatDotRound/></el-icon>
          <div>
            <p class="rag-panel-kicker">Review Chat</p>
            <h2>复盘助手</h2>
          </div>
        </div>
        <button type="button" class="rag-panel-close" aria-label="收起对话" @click="closeChat">
          <el-icon><Close/></el-icon>
        </button>
      </header>
      <GrammarRagChatPanel compact class="rag-panel-body"/>
    </div>

    <button
        type="button"
        class="rag-bubble"
        :class="{ 'rag-bubble--open': open }"
        :aria-label="open ? '收起复盘助手' : '打开复盘助手'"
        @click="toggleChat"
    >
      <el-icon v-if="!open" :size="22"><ChatDotRound/></el-icon>
      <el-icon v-else :size="20"><Close/></el-icon>
      <span v-if="!open" class="rag-bubble-label">复盘</span>
    </button>
  </div>
</template>

<style scoped>
.rag-float {
  position: fixed;
  right: max(1rem, calc((100vw - min(80vw, 72rem)) / 2 + 0.25rem));
  bottom: 1.25rem;
  z-index: 80;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 0.75rem;
  pointer-events: none;
}

.rag-panel,
.rag-bubble {
  pointer-events: auto;
}

.rag-panel {
  width: min(26.5rem, calc(100vw - 1.5rem));
  height: min(34rem, calc(100dvh - 6.5rem));
  display: flex;
  flex-direction: column;
  overflow: hidden;
  border-radius: var(--kk-radius-lg);
  box-shadow:
      0 22px 48px rgba(11, 26, 125, 0.22),
      0 8px 18px rgba(20, 24, 36, 0.1);
  opacity: 0;
  visibility: hidden;
  pointer-events: none;
  transform: translateY(12px) scale(0.98);
  transition:
      opacity 0.22s var(--kk-ease-out),
      transform 0.22s var(--kk-ease-out),
      visibility 0.22s var(--kk-ease-out);
}

.rag-panel--open {
  opacity: 1;
  visibility: visible;
  pointer-events: auto;
  transform: translateY(0) scale(1);
}

.rag-panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
  flex-shrink: 0;
  padding: 0.85rem 0.95rem 0.7rem;
  border-bottom: 1px solid var(--kk-glass-divider);
}

.rag-panel-title {
  display: flex;
  align-items: center;
  gap: 0.55rem;
  min-width: 0;
  color: var(--kk-color-primary);
}

.rag-panel-title h2 {
  margin: 0;
  font-family: var(--kk-font-display);
  font-size: 1.05rem;
  font-weight: 800;
  line-height: 1.2;
}

.rag-panel-kicker {
  margin: 0 0 0.08rem;
  font-family: var(--kk-font-mono);
  font-size: 0.62rem;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  color: var(--kk-color-text-subtle);
}

.rag-panel-close {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 2rem;
  height: 2rem;
  border: none;
  border-radius: 999px;
  background: var(--kk-glass-subtle-bg-strong);
  color: var(--kk-color-text-secondary);
  cursor: pointer;
}

.rag-panel-close:hover {
  color: var(--kk-color-primary);
  background: var(--kk-glass-hover-bg);
}

.rag-panel-body {
  flex: 1 1 0;
  min-height: 0;
}

.rag-bubble {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 0.35rem;
  min-width: 3.4rem;
  height: 3.4rem;
  padding: 0 1rem;
  border: none;
  border-radius: 999px;
  cursor: pointer;
  color: #fff;
  background: linear-gradient(135deg, var(--kk-color-primary) 0%, var(--kk-color-primary-soft) 100%);
  box-shadow:
      0 14px 28px rgba(11, 26, 125, 0.32),
      inset 0 1px 0 rgba(255, 255, 255, 0.22);
  transition:
      transform var(--kk-duration-normal) var(--kk-ease-out),
      box-shadow var(--kk-duration-normal) var(--kk-ease-out);
}

.rag-bubble:hover {
  transform: translateY(-2px);
  box-shadow:
      0 18px 34px rgba(11, 26, 125, 0.36),
      inset 0 1px 0 rgba(255, 255, 255, 0.25);
}

.rag-bubble--open {
  width: 3.4rem;
  padding: 0;
}

.rag-bubble-label {
  font-size: 0.88rem;
  font-weight: 700;
  letter-spacing: 0.02em;
}

@media (max-width: 720px) {
  .rag-float {
    right: max(0.75rem, env(safe-area-inset-right, 0px));
    bottom: max(0.75rem, env(safe-area-inset-bottom, 0px));
  }

  .rag-panel {
    width: min(100vw - 1rem, 26.5rem);
    height: min(72dvh, calc(100dvh - 5.5rem));
    border-radius: var(--kk-radius-lg);
  }

  /* 移动端：圆形图标钮，减轻遮挡与视觉重量 */
  .rag-bubble {
    width: 3.15rem;
    min-width: 3.15rem;
    height: 3.15rem;
    padding: 0;
    box-shadow:
        0 8px 20px rgba(11, 26, 125, 0.28),
        inset 0 1px 0 rgba(255, 255, 255, 0.2);
  }

  .rag-bubble:hover,
  .rag-bubble:active {
    transform: scale(0.97);
  }

  .rag-bubble-label {
    display: none;
  }

  .rag-bubble--open {
    width: 3.15rem;
    min-width: 3.15rem;
  }
}

@media (prefers-reduced-motion: reduce) {
  .rag-panel,
  .rag-bubble {
    transition: none;
  }
}
</style>
