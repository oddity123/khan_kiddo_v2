import {defineStore} from 'pinia'

import type {ConversationAnalysisDetail} from '@/types/conversation'

/**
 * 游客分析结果仅存内存；刷新或关标签即丢失。
 */
export const useEphemeralAnalysisStore = defineStore('ephemeralAnalysis', {
  state: () => ({
    detail: null as ConversationAnalysisDetail | null,
  }),
  getters: {
    hasDetail: (state) => state.detail != null,
  },
  actions: {
    setDetail(detail: ConversationAnalysisDetail | null) {
      this.detail = detail
    },
    clear() {
      this.detail = null
    },
  },
})
