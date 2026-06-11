<script setup lang="ts">
import {ChatLineRound, Promotion} from '@element-plus/icons-vue'
import {ElMessage} from 'element-plus'
import {computed, onUnmounted, ref} from 'vue'

import {chatLangchain4jLearningStream} from '@/api/langchain4jLearning'
import type {ChatMessage} from '@/types/langchain4j'
import {LANGCHAIN4J_STREAM_STATUS} from '@/types/langchain4j'
import {getErrorMessage} from '@/utils/error'

const SAMPLE_QUESTIONS = [
  '根据自我介绍，我的技术背景是什么？',
  '我目前负责哪些工作？',
  'Khan Kiddo 项目里我做了什么？',
  '本周工作周报里写了什么？',
]

const input = ref('')
const messages = ref<ChatMessage[]>([])
const streaming = ref(false)
const streamingText = ref('')

let abortController: AbortController | null = null

const canSend = computed(() => !streaming.value && input.value.trim().length > 0)

const showStreamingBubble = computed(() => streaming.value || streamingText.value.length > 0)

onUnmounted(() => {
  abortController?.abort()
})

function useSampleQuestion(question: string) {
  input.value = question
}

async function onSend() {
  const question = input.value.trim()
  if (!question || streaming.value) {
    return
  }

  messages.value.push({role: 'user', content: question})
  input.value = ''
  streaming.value = true
  streamingText.value = ''

  abortController?.abort()
  abortController = new AbortController()

  try {
    await chatLangchain4jLearningStream(
        question,
        (event) => {
          if (event.status === LANGCHAIN4J_STREAM_STATUS.TOKEN && event.token) {
            streamingText.value += event.token
          }
        },
        abortController.signal,
    )
    if (streamingText.value.trim()) {
      messages.value.push({role: 'assistant', content: streamingText.value})
    }
  } catch (err) {
    if (abortController.signal.aborted) {
      if (streamingText.value.trim()) {
        messages.value.push({role: 'assistant', content: streamingText.value})
      }
      return
    }
    ElMessage.error(getErrorMessage(err, '回答失败'))
  } finally {
    streaming.value = false
    streamingText.value = ''
    abortController = null
  }
}

function onKeydown(event: KeyboardEvent) {
  if (event.key === 'Enter' && !event.shiftKey) {
    event.preventDefault()
    void onSend()
  }
}
</script>

<template>
  <div class="learning-page">
    <header class="page-header kk-glass kk-glass--panel">
      <h1 class="page-title">LangChain for Java 学习</h1>
      <p class="page-desc">
        基于 <strong>Easy RAG</strong> 的个人文档问答练习。知识库为<strong>自我介绍、工作背景、项目说明、学习笔记、工作周报</strong>等
        <code class="inline-code">.txt</code> 文件（可替换为你自己的内容），由<strong>通义千问（Qwen Plus）</strong>流式回答。
      </p>
    </header>

    <section class="chat-panel kk-glass kk-glass--panel">
      <div class="chat-messages" role="log" aria-live="polite">
        <p v-if="messages.length === 0 && !showStreamingBubble" class="chat-empty">
          根据你的个人文档提问，例如技术背景、工作职责、项目进展、学习笔记等。答案仅来自知识库，不会凭空编造。
        </p>

        <div
            v-for="(msg, idx) in messages"
            :key="idx"
            class="chat-bubble"
            :class="msg.role === 'user' ? 'chat-bubble--user' : 'chat-bubble--assistant'"
        >
          <span class="chat-role">{{ msg.role === 'user' ? '你' : '助手' }}</span>
          <p class="chat-content">{{ msg.content }}</p>
        </div>

        <div v-if="showStreamingBubble" class="chat-bubble chat-bubble--assistant chat-bubble--live">
          <span class="chat-role">助手</span>
          <p class="chat-content">
            <template v-if="streamingText">{{ streamingText }}</template>
            <span v-else class="chat-typing">思考中…</span>
          </p>
        </div>
      </div>

      <div class="sample-row">
        <span class="sample-label">试试：</span>
        <button
            v-for="q in SAMPLE_QUESTIONS"
            :key="q"
            type="button"
            class="sample-chip"
            :disabled="streaming"
            @click="useSampleQuestion(q)"
        >
          {{ q }}
        </button>
      </div>

      <div class="composer">
        <el-input
            v-model="input"
            type="textarea"
            :rows="2"
            placeholder="就文档内容提问，例如「我的岗位职责是什么？」（Enter 发送）"
            :disabled="streaming"
            resize="none"
            @keydown="onKeydown"
        />
        <el-button
            type="primary"
            :icon="Promotion"
            :loading="streaming"
            :disabled="!canSend"
            @click="onSend"
        >
          发送
        </el-button>
      </div>
    </section>

    <aside class="tips kk-glass kk-glass--panel">
      <h2 class="tips-title">
        <el-icon><ChatLineRound /></el-icon>
        知识库说明
      </h2>
      <ul class="tips-list">
        <li>框架：<strong>LangChain for Java</strong>（LangChain4j）</li>
        <li>文档目录：<code>rag/langchain4j-learning/</code></li>
        <li>文档类型：自我介绍、工作 SQL/DDL、学习笔记等</li>
        <li>替换方式：直接编辑目录下 <code>.txt</code>，重启后端生效</li>
        <li>嵌入：<code>text-embedding-v3</code>（千问，中文检索）</li>
        <li>技术：<code>Easy RAG</code> + <code>minScore</code> + SSE</li>
      </ul>
    </aside>
  </div>
</template>

<style scoped>
.learning-page {
  display: grid;
  gap: 1.25rem;
  grid-template-columns: 1fr 280px;
  align-items: start;
}

.page-header {
  grid-column: 1 / -1;
  padding: 1.25rem 1.5rem;
}

.page-title {
  margin: 0 0 0.35rem;
  font-family: var(--kk-font-display);
  font-size: 1.65rem;
  font-weight: 600;
  color: var(--kk-color-primary);
  letter-spacing: -0.02em;
}

.page-desc {
  margin: 0;
  font-size: 0.92rem;
  color: var(--kk-color-text-muted);
  line-height: 1.55;
}

.page-desc .inline-code {
  font-family: var(--kk-font-mono);
  font-size: 0.85em;
  padding: 0.1em 0.35em;
  border-radius: 4px;
  background: var(--kk-glass-inner-bg);
}

.chat-panel {
  display: flex;
  flex-direction: column;
  gap: 0.85rem;
  padding: 1rem 1.25rem 1.25rem;
  min-height: 420px;
}

.chat-messages {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  max-height: 52vh;
  overflow-y: auto;
  padding: 0.25rem 0.15rem;
}

.chat-empty {
  margin: 2rem 0;
  text-align: center;
  color: var(--kk-color-text-muted);
  font-size: 0.9rem;
}

.chat-bubble {
  max-width: 88%;
  padding: 0.65rem 0.85rem;
  border-radius: 12px;
  background: var(--kk-glass-inner-bg);
  border: 1px solid var(--kk-glass-inner-border);
}

.chat-bubble--user {
  align-self: flex-end;
  background: color-mix(in srgb, var(--kk-color-primary) 8%, var(--kk-glass-inner-bg));
}

.chat-bubble--assistant {
  align-self: flex-start;
}

.chat-bubble--live {
  border-style: dashed;
}

.chat-role {
  display: block;
  font-size: 0.72rem;
  font-weight: 600;
  color: var(--kk-color-text-muted);
  margin-bottom: 0.25rem;
  text-transform: uppercase;
  letter-spacing: 0.04em;
}

.chat-content {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
  font-size: 0.92rem;
  line-height: 1.55;
  color: var(--kk-color-text);
}

.chat-typing {
  color: var(--kk-color-text-muted);
  font-style: italic;
}

.sample-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.45rem;
}

.sample-label {
  font-size: 0.8rem;
  color: var(--kk-color-text-muted);
}

.sample-chip {
  border: 1px solid var(--kk-glass-inner-border);
  background: var(--kk-glass-inner-bg);
  color: var(--kk-color-primary);
  font-size: 0.78rem;
  padding: 0.25rem 0.55rem;
  border-radius: 999px;
  cursor: pointer;
  transition: background var(--kk-duration-fast) ease;
}

.sample-chip:hover:not(:disabled) {
  background: var(--kk-glass-hover-bg);
}

.sample-chip:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.composer {
  display: flex;
  gap: 0.65rem;
  align-items: flex-end;
}

.composer :deep(.el-textarea) {
  flex: 1;
}

.tips {
  padding: 1rem 1.15rem;
}

.tips-title {
  display: flex;
  align-items: center;
  gap: 0.35rem;
  margin: 0 0 0.65rem;
  font-size: 0.95rem;
  font-weight: 600;
  color: var(--kk-color-primary);
}

.tips-list {
  margin: 0;
  padding-left: 1.1rem;
  font-size: 0.82rem;
  line-height: 1.65;
  color: var(--kk-color-text-muted);
}

.tips-list code {
  font-family: var(--kk-font-mono);
  font-size: 0.78rem;
  color: var(--kk-color-text);
}

@media (max-width: 900px) {
  .learning-page {
    grid-template-columns: 1fr;
  }

  .tips {
    order: -1;
  }
}
</style>
