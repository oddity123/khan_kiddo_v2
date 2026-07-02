<script setup lang="ts">
import {ChatDotRound, Promotion} from '@element-plus/icons-vue'
import {ElMessage} from 'element-plus'
import {computed, nextTick, onUnmounted, ref, watch} from 'vue'

import {chatGrammarRagStream} from '@/api/grammarRag'
import MarkdownContent from '@/components/common/MarkdownContent.vue'
import type {ChatMessage} from '@/types/grammarRag'
import {RAG_STREAM_STATUS} from '@/types/grammarRag'
import {getErrorMessage} from '@/utils/error'

const chatInput = ref('')
const chatMessages = ref<ChatMessage[]>([])
const chatStreaming = ref(false)
const chatStreamingText = ref('')
const messagesEndRef = ref<HTMLElement | null>(null)

const sampleQuestions = [
  '我最近常犯哪些语法错误？',
  '我的时态错误有哪些典型例子？',
  '冠词错误一般出现在什么句式里？',
]

let abortController: AbortController | null = null

const canSendChat = computed(() => !chatStreaming.value && chatInput.value.trim().length > 0)
const showStreamingBubble = computed(() => chatStreaming.value || chatStreamingText.value.length > 0)
const hasMessages = computed(() => chatMessages.value.length > 0 || showStreamingBubble.value)

onUnmounted(() => {
  abortController?.abort()
})

watch(
    [chatMessages, chatStreamingText, chatStreaming],
    async () => {
      await nextTick()
      messagesEndRef.value?.scrollIntoView({behavior: 'smooth', block: 'end'})
    },
    {deep: true},
)

function useSampleQuestion(question: string) {
  if (chatStreaming.value) {
    return
  }
  chatInput.value = question
}

async function onSendChat() {
  const question = chatInput.value.trim()
  if (!question || chatStreaming.value) {
    return
  }

  chatMessages.value.push({role: 'user', content: question})
  chatInput.value = ''
  chatStreaming.value = true
  chatStreamingText.value = ''

  abortController?.abort()
  abortController = new AbortController()

  try {
    await chatGrammarRagStream(
        question,
        (event) => {
          if (event.status === RAG_STREAM_STATUS.TOKEN && event.token) {
            chatStreamingText.value += event.token
          }
        },
        abortController.signal,
    )
    if (chatStreamingText.value.trim()) {
      chatMessages.value.push({role: 'assistant', content: chatStreamingText.value})
    }
  } catch (err) {
    if (abortController.signal.aborted) {
      if (chatStreamingText.value.trim()) {
        chatMessages.value.push({role: 'assistant', content: chatStreamingText.value})
      }
      return
    }
    ElMessage.error(getErrorMessage(err, '问答失败'))
  } finally {
    chatStreaming.value = false
    chatStreamingText.value = ''
    abortController = null
  }
}

function onChatKeydown(event: KeyboardEvent) {
  if (event.key === 'Enter' && !event.shiftKey) {
    event.preventDefault()
    onSendChat()
  }
}
</script>

<template>
  <div class="grammar-rag-page">
    <header class="page-head">
      <div class="head-copy">
        <p class="page-eyebrow">Grammar Review</p>
        <h1 class="page-title">语法复盘</h1>
        <p class="page-desc">基于历史语法错误，AI 帮你归纳薄弱点与改进方向。</p>
      </div>
      <div class="head-badge">
        <el-icon><ChatDotRound/></el-icon>
        <span>RAG 问答</span>
      </div>
    </header>

    <section class="chat-shell kk-glass kk-glass--panel">
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
          <p class="empty-title">开始你的语法复盘</p>
          <p class="empty-desc">
            完成对话分析后，可在此询问常见错误类型、典型例句与改进建议。AI 回答支持 Markdown 排版。
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
            :rows="2"
            resize="none"
            :disabled="chatStreaming"
            placeholder="例如：我过去一个月最常犯哪几类语法错误？（Enter 发送，Shift+Enter 换行）"
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
  </div>
</template>

<style scoped>
.grammar-rag-page {
  flex: 1 1 0;
  min-height: 0;
  height: 100%;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  gap: 0.9rem;
  font-family: var(--kk-font-body);
  color: var(--kk-color-text);
}

.page-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 1rem;
  flex-shrink: 0;
}

.head-copy {
  min-width: 0;
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
  font-size: 0.9rem;
  color: var(--kk-color-text-muted);
  line-height: 1.55;
}

.head-badge {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  flex-shrink: 0;
  padding: 0.4rem 0.7rem;
  border-radius: 999px;
  font-size: 0.8rem;
  font-weight: 600;
  color: var(--kk-color-primary);
  background: rgba(11, 26, 125, 0.08);
  box-shadow: inset 0 0 0 1px rgba(11, 26, 125, 0.1);
}

.chat-shell {
  flex: 1 1 0;
  min-height: 0;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  padding: 0.95rem 1rem 0.85rem;
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
  padding: 1.5rem;
  text-align: center;
}

.empty-title {
  margin: 0 0 0.45rem;
  font-family: var(--kk-font-display);
  font-size: 1.1rem;
  font-weight: 700;
  color: var(--kk-color-primary);
}

.empty-desc {
  margin: 0;
  max-width: 28rem;
  font-size: 0.9rem;
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

.message-bubble--streaming {
  position: relative;
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

@media (max-width: 768px) {
  .page-head {
    flex-direction: column;
    gap: 0.55rem;
  }

  .head-badge {
    align-self: flex-start;
  }

  .chat-composer {
    grid-template-columns: 1fr;
  }

  .send-btn {
    width: 100%;
  }
}
</style>
