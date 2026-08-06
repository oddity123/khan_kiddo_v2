import {ElMessage} from 'element-plus'
import {computed, nextTick, onUnmounted, ref, watch} from 'vue'

import {chatGrammarRagStream} from '@/api/grammarRag'
import type {ChatMessage} from '@/types/grammarRag'
import {RAG_STREAM_STATUS} from '@/types/grammarRag'
import {getErrorMessage} from '@/utils/error'

const SAMPLE_QUESTIONS = [
  '我最近常犯哪些语法错误？',
  '我的时态错误有哪些典型例子？',
  '冠词错误一般出现在什么句式里？',
] as const

export function useGrammarRagChat() {
  const chatInput = ref('')
  const chatMessages = ref<ChatMessage[]>([])
  const chatStreaming = ref(false)
  const chatStreamingText = ref('')
  const messagesEndRef = ref<HTMLElement | null>(null)

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
      void onSendChat()
    }
  }

  return {
    sampleQuestions: SAMPLE_QUESTIONS,
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
  }
}
