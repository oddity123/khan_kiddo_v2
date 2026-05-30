import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useAppStore = defineStore('app', () => {
  const backendStatus = ref<string | null>(null)
  const backendLoading = ref(false)

  function setBackendStatus(status: string | null) {
    backendStatus.value = status
  }

  function setBackendLoading(loading: boolean) {
    backendLoading.value = loading
  }

  return {
    backendStatus,
    backendLoading,
    setBackendStatus,
    setBackendLoading,
  }
})
