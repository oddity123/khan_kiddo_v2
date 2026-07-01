<script setup lang="ts">
import {Promotion, Search, View} from '@element-plus/icons-vue'
import {ElMessage} from 'element-plus'
import {computed, onUnmounted, ref} from 'vue'
import {useRouter} from 'vue-router'

import {chatGrammarRagStream, searchGrammarErrors} from '@/api/grammarRag'
import type {ChatMessage, GrammarErrorSearchItem} from '@/types/grammarRag'
import {PROBLEM_TYPE_OPTIONS, RAG_STREAM_STATUS} from '@/types/grammarRag'
import {getErrorMessage} from '@/utils/error'

const router = useRouter()

const activeTab = ref<'search' | 'chat'>('search')
const searchQuery = ref('')
const selectedProblemTypes = ref<string[]>([])
const searchLoading = ref(false)
const searchResults = ref<GrammarErrorSearchItem[]>([])

const chatInput = ref('')
const chatMessages = ref<ChatMessage[]>([])
const chatStreaming = ref(false)
const chatStreamingText = ref('')

const sampleQuestions = [
  '我最近常犯哪些语法错误？',
  '我的时态错误有哪些典型例子？',
  '冠词错误一般出现在什么句式里？',
]

let abortController: AbortController | null = null

const canSendChat = computed(() => !chatStreaming.value && chatInput.value.trim().length > 0)
const showStreamingBubble = computed(() => chatStreaming.value || chatStreamingText.value.length > 0)

onUnmounted(() => {
  abortController?.abort()
})

function formatScore(score?: number) {
  if (score == null) {
    return '—'
  }
  return `${(score * 100).toFixed(1)}%`
}

function formatTime(value?: string) {
  if (!value) {
    return '—'
  }
  return value.replace('T', ' ').slice(0, 19)
}

function problemTypeLabel(value: string) {
  const option = PROBLEM_TYPE_OPTIONS.find((item) => item.value === value)
  return option?.label ?? value
}

async function onSearch() {
  const query = searchQuery.value.trim()
  if (!query) {
    ElMessage.warning('请输入搜索内容')
    return
  }
  searchLoading.value = true
  try {
    const {data} = await searchGrammarErrors({
      query,
      problemTypes: selectedProblemTypes.value.length ? selectedProblemTypes.value : undefined,
    })
    searchResults.value = data.items ?? []
    if (!searchResults.value.length) {
      ElMessage.info('未找到相关历史错句，可先完成对话分析以积累数据')
    }
  } catch (error) {
    searchResults.value = []
    ElMessage.error(getErrorMessage(error, '搜索失败'))
  } finally {
    searchLoading.value = false
  }
}

function goDetail(analysisId: string) {
  router.push(`/conversation/analyses/${analysisId}`)
}

function useSampleQuestion(question: string) {
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
      <div>
        <h1 class="page-title">语法复盘</h1>
        <p class="page-desc">语义检索历史语法问题，或用 AI 归纳你的常见错误模式。</p>
      </div>
    </header>

    <section class="main-panel kk-glass kk-glass--panel">
      <el-tabs v-model="activeTab" class="rag-tabs">
        <el-tab-pane label="语义搜索" name="search">
          <div class="search-toolbar">
            <el-input
                v-model="searchQuery"
                placeholder="例如：主谓一致、people is、冠词错误"
                clearable
                class="search-input"
                @keyup.enter="onSearch"
            >
              <template #prefix>
                <el-icon><Search/></el-icon>
              </template>
            </el-input>
            <el-button type="primary" :icon="Search" :loading="searchLoading" @click="onSearch">
              搜索
            </el-button>
          </div>

          <div class="filter-row">
            <span class="filter-label">问题类型</span>
            <el-select
                v-model="selectedProblemTypes"
                multiple
                collapse-tags
                collapse-tags-tooltip
                clearable
                placeholder="全部类型"
                class="type-select"
            >
              <el-option
                  v-for="option in PROBLEM_TYPE_OPTIONS"
                  :key="option.value"
                  :label="option.label"
                  :value="option.value"
              />
            </el-select>
          </div>

          <div v-loading="searchLoading" class="results-wrap">
            <el-empty
                v-if="!searchLoading && !searchResults.length"
                description="输入关键词搜索历史错句；完成对话分析后会自动建立索引"
            />
            <article v-for="(item, index) in searchResults" :key="`${item.analysisId}-${item.sentenceId}-${index}`" class="result-card">
              <div class="result-head">
                <span class="score-badge">相似度 {{ formatScore(item.score) }}</span>
                <span class="result-time">{{ formatTime(item.createdAt) }}</span>
              </div>
              <p class="original-sentence">{{ item.originalSentence }}</p>
              <div v-if="item.problemTypes?.length" class="tag-row">
                <span v-for="tag in item.problemTypes" :key="tag" class="problem-tag">
                  {{ problemTypeLabel(tag) }}
                </span>
              </div>
              <ul v-if="item.errorPoints?.length" class="error-points">
                <li v-for="(point, pointIndex) in item.errorPoints" :key="pointIndex">{{ point }}</li>
              </ul>
              <p v-if="item.suggestion" class="suggestion">
                <span class="label">建议</span>
                {{ item.suggestion }}
              </p>
              <el-button text type="primary" :icon="View" @click="goDetail(item.analysisId)">
                查看分析详情
              </el-button>
            </article>
          </div>
        </el-tab-pane>

        <el-tab-pane label="AI 复盘问答" name="chat">
          <div class="sample-row">
            <span class="sample-label">示例问题</span>
            <button
                v-for="question in sampleQuestions"
                :key="question"
                type="button"
                class="sample-chip"
                @click="useSampleQuestion(question)"
            >
              {{ question }}
            </button>
          </div>

          <div class="chat-panel">
            <el-empty v-if="!chatMessages.length && !showStreamingBubble" description="向 AI 提问你的语法错误模式" />
            <div v-else class="chat-messages">
              <div
                  v-for="(message, index) in chatMessages"
                  :key="index"
                  class="chat-bubble"
                  :class="message.role === 'user' ? 'chat-bubble--user' : 'chat-bubble--assistant'"
              >
                {{ message.content }}
              </div>
              <div v-if="showStreamingBubble" class="chat-bubble chat-bubble--assistant chat-bubble--streaming">
                {{ chatStreamingText || '思考中…' }}
              </div>
            </div>

            <div class="chat-input-row">
              <el-input
                  v-model="chatInput"
                  type="textarea"
                  :rows="3"
                  resize="none"
                  placeholder="例如：我过去一个月最常犯哪几类语法错误？"
                  @keydown="onChatKeydown"
              />
              <el-button
                  type="primary"
                  :icon="Promotion"
                  :disabled="!canSendChat"
                  :loading="chatStreaming"
                  @click="onSendChat"
              >
                发送
              </el-button>
            </div>
          </div>
        </el-tab-pane>
      </el-tabs>
    </section>
  </div>
</template>

<style scoped>
.grammar-rag-page {
  font-family: var(--kk-font-body);
  color: var(--kk-color-text);
}

.page-head {
  margin-bottom: 1.25rem;
}

.page-title {
  margin: 0 0 0.35rem;
  font-family: var(--kk-font-display);
  font-size: clamp(1.5rem, 3vw, 2rem);
  font-weight: 800;
  color: var(--kk-color-primary);
}

.page-desc {
  margin: 0;
  color: var(--kk-color-text-muted);
  line-height: 1.6;
}

.main-panel {
  padding: 1.25rem 1.35rem;
}

.search-toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 0.65rem;
  margin-bottom: 0.85rem;
}

.search-input {
  flex: 1;
  min-width: 14rem;
}

.filter-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.65rem;
  margin-bottom: 1rem;
}

.filter-label,
.sample-label {
  color: var(--kk-color-text-muted);
  font-size: 0.9rem;
}

.type-select {
  min-width: 16rem;
  flex: 1;
}

.results-wrap {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  min-height: 8rem;
}

.result-card {
  padding: 1rem 1.05rem;
  border-radius: 14px;
  background: var(--kk-glass-inner-bg);
  box-shadow: inset 0 0 0 1px var(--kk-glass-inner-border);
}

.result-head {
  display: flex;
  justify-content: space-between;
  gap: 0.5rem;
  margin-bottom: 0.55rem;
  font-size: 0.82rem;
  color: var(--kk-color-text-muted);
}

.score-badge {
  color: var(--kk-color-primary);
  font-weight: 600;
}

.original-sentence {
  margin: 0 0 0.65rem;
  font-family: var(--kk-font-mono);
  line-height: 1.65;
}

.tag-row {
  display: flex;
  flex-wrap: wrap;
  gap: 0.4rem;
  margin-bottom: 0.55rem;
}

.problem-tag {
  padding: 0.15rem 0.55rem;
  border-radius: 999px;
  font-size: 0.78rem;
  background: rgba(11, 26, 125, 0.08);
  color: var(--kk-color-primary);
}

.error-points {
  margin: 0 0 0.55rem;
  padding-left: 1.1rem;
  color: var(--kk-color-text-secondary);
}

.suggestion {
  margin: 0 0 0.65rem;
  line-height: 1.6;
}

.suggestion .label {
  color: var(--kk-color-text-muted);
  margin-right: 0.35rem;
}

.sample-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.5rem;
  margin-bottom: 1rem;
}

.sample-chip {
  border: none;
  cursor: pointer;
  padding: 0.35rem 0.7rem;
  border-radius: 999px;
  background: var(--kk-glass-subtle-bg-strong);
  color: var(--kk-color-text-secondary);
  font-size: 0.82rem;
}

.sample-chip:hover {
  color: var(--kk-color-primary);
  background: var(--kk-glass-hover-bg);
}

.chat-panel {
  display: flex;
  flex-direction: column;
  gap: 0.85rem;
  min-height: 22rem;
}

.chat-messages {
  display: flex;
  flex-direction: column;
  gap: 0.65rem;
  max-height: 28rem;
  overflow-y: auto;
  padding-right: 0.25rem;
}

.chat-bubble {
  max-width: min(100%, 44rem);
  padding: 0.75rem 0.9rem;
  border-radius: 14px;
  line-height: 1.65;
  white-space: pre-wrap;
}

.chat-bubble--user {
  align-self: flex-end;
  background: linear-gradient(135deg, var(--kk-color-primary) 0%, var(--kk-color-primary-soft) 100%);
  color: #fff;
}

.chat-bubble--assistant {
  align-self: flex-start;
  background: var(--kk-glass-inner-bg);
  box-shadow: inset 0 0 0 1px var(--kk-glass-inner-border);
}

.chat-bubble--streaming {
  color: var(--kk-color-text-secondary);
}

.chat-input-row {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 0.65rem;
  align-items: end;
}

@media (max-width: 768px) {
  .chat-input-row {
    grid-template-columns: 1fr;
  }
}
</style>
