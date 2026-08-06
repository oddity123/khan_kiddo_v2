<script setup lang="ts">
import {Promotion} from '@element-plus/icons-vue'

import MarkdownContent from '@/components/common/MarkdownContent.vue'
import {useGrammarRagChat} from '@/composables/useGrammarRagChat'

defineProps<{
  compact?: boolean
}>()

const {
  sampleQuestions,
  chatInput,
  chatMessages,
  chatStreaming,
  chatStreamingText,
  messagesEndRef,
  canSendChat,
  showStreamingBubble,
  hasMessages,
  useSampleQuestion,
  onSendChat,
  onChatKeydown,
} = useGrammarRagChat()
</script>

<template>
  <section class="chat-shell" :class="{ 'chat-shell--compact': compact }">
    <div class="sample-row">
      <span class="sample-label">试试问</span>
      <div class="sample-scroll">
        <button
            v-for="question in sampleQuestions"
            :key="question"
            type="button"
            class="sample-chip"
            :disabled="chatStreaming"
            @click="useSampleQuestion(question)"
        >
          {{ question }}
        </button>
      </div>
    </div>

    <div class="chat-body">
      <div v-if="!hasMessages" class="chat-empty">
        <p class="empty-title">开始你的复盘</p>
        <p class="empty-desc">
          完成对话分析后，可在此询问常见错误类型、典型例句与改进建议。
        </p>
      </div>

      <div v-else class="chat-messages">
        <article
            v-for="(message, index) in chatMessages"
            :key="`${message.role}-${index}`"
            class="message-row"
            :class="message.role === 'user' ? 'message-row--user' : 'message-row--assistant'"
        >
          <div class="message-meta">
            {{ message.role === 'user' ? '你' : '复盘助手' }}
          </div>
          <div
              class="message-bubble"
              :class="message.role === 'user' ? 'message-bubble--user' : 'message-bubble--assistant'"
          >
            <p v-if="message.role === 'user'" class="user-text">{{ message.content }}</p>
            <MarkdownContent v-else :content="message.content"/>
          </div>
        </article>

        <article v-if="showStreamingBubble" class="message-row message-row--assistant">
          <div class="message-meta">复盘助手</div>
          <div class="message-bubble message-bubble--assistant message-bubble--streaming">
            <MarkdownContent
                v-if="chatStreamingText"
                :content="chatStreamingText"
            />
            <p v-else class="thinking-text">正在检索历史错句并归纳…</p>
          </div>
        </article>

        <div ref="messagesEndRef" class="messages-anchor"/>
      </div>
    </div>

    <footer class="chat-composer">
      <el-input
          v-model="chatInput"
          type="textarea"
          :rows="compact ? 2 : 2"
          resize="none"
          :disabled="chatStreaming"
          placeholder="例如：我最常犯哪几类语法错误？（Enter 发送）"
          @keydown="onChatKeydown"
      />
      <el-button
          type="primary"
          class="send-btn"
          :icon="Promotion"
          :disabled="!canSendChat"
          :loading="chatStreaming"
          @click="onSendChat"
      >
        发送
      </el-button>
    </footer>
  </section>
</template>

<style scoped>
.chat-shell {
  flex: 1 1 0;
  min-height: 0;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  padding: 0.95rem 1rem 0.85rem;
}

.chat-shell--compact {
  padding: 0.7rem 0.8rem 0.75rem;
}

.sample-row {
  display: flex;
  align-items: center;
  gap: 0.55rem;
  flex-shrink: 0;
  margin-bottom: 0.75rem;
}

.sample-label {
  flex-shrink: 0;
  font-size: 0.82rem;
  color: var(--kk-color-text-muted);
}

.sample-scroll {
  display: flex;
  gap: 0.45rem;
  overflow-x: auto;
  padding-bottom: 0.15rem;
  scrollbar-width: thin;
}

.sample-chip {
  flex-shrink: 0;
  border: none;
  cursor: pointer;
  padding: 0.32rem 0.72rem;
  border-radius: 999px;
  background: var(--kk-glass-subtle-bg-strong);
  color: var(--kk-color-text-secondary);
  font-size: 0.8rem;
  transition:
      color var(--kk-duration-normal) ease,
      background var(--kk-duration-normal) ease,
      transform var(--kk-duration-normal) ease;
}

.sample-chip:hover:not(:disabled) {
  color: var(--kk-color-primary);
  background: var(--kk-glass-hover-bg);
  transform: translateY(-1px);
}

.sample-chip:disabled {
  cursor: not-allowed;
  opacity: 0.55;
}

.chat-body {
  flex: 1 1 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
  border-radius: 14px;
  background: var(--kk-glass-inner-bg);
  box-shadow: inset 0 0 0 1px var(--kk-glass-inner-border);
  overflow: hidden;
}

.chat-empty {
  flex: 1 1 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 1.25rem;
  text-align: center;
}

.empty-title {
  margin: 0 0 0.45rem;
  font-family: var(--kk-font-display);
  font-size: 1.05rem;
  font-weight: 700;
  color: var(--kk-color-primary);
}

.empty-desc {
  margin: 0;
  max-width: 22rem;
  font-size: 0.86rem;
  line-height: 1.65;
  color: var(--kk-color-text-muted);
}

.chat-messages {
  flex: 1 1 0;
  min-height: 0;
  overflow-y: auto;
  overscroll-behavior: contain;
  padding: 0.9rem 0.85rem 0.4rem;
  display: flex;
  flex-direction: column;
  gap: 0.85rem;
  scroll-behavior: smooth;
}

.message-row {
  display: flex;
  flex-direction: column;
  gap: 0.3rem;
  max-width: min(100%, 46rem);
}

.message-row--user {
  align-self: flex-end;
  align-items: flex-end;
}

.message-row--assistant {
  align-self: flex-start;
  align-items: flex-start;
}

.message-meta {
  font-size: 0.74rem;
  color: var(--kk-color-text-subtle);
  padding: 0 0.2rem;
}

.message-bubble {
  width: 100%;
  padding: 0.75rem 0.9rem;
  border-radius: 14px;
}

.message-bubble--user {
  background: linear-gradient(135deg, var(--kk-color-primary) 0%, var(--kk-color-primary-soft) 100%);
  color: #fff;
  border-bottom-right-radius: 4px;
}

.message-bubble--assistant {
  background: rgba(255, 255, 255, 0.72);
  box-shadow: inset 0 0 0 1px var(--kk-glass-inner-border);
  border-bottom-left-radius: 4px;
}

.user-text {
  margin: 0;
  line-height: 1.65;
  white-space: pre-wrap;
}

.thinking-text {
  margin: 0;
  font-size: 0.9rem;
  color: var(--kk-color-text-muted);
}

.messages-anchor {
  height: 1px;
  flex-shrink: 0;
}

.chat-composer {
  flex-shrink: 0;
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 0.65rem;
  align-items: end;
  margin-top: 0.75rem;
  padding-top: 0.75rem;
  border-top: 1px solid var(--kk-glass-divider);
}

.send-btn {
  min-width: 5.5rem;
}

@media (max-width: 560px) {
  .chat-composer {
    grid-template-columns: 1fr;
  }

  .send-btn {
    width: 100%;
  }
}
</style>
