import {ref} from 'vue'

/** 复盘浮层与口语页坞共用同一开合状态。 */
const open = ref(false)

export function useReviewChat() {
  function openChat() {
    open.value = true
  }

  function closeChat() {
    open.value = false
  }

  function toggleChat() {
    open.value = !open.value
  }

  return {open, openChat, closeChat, toggleChat}
}
