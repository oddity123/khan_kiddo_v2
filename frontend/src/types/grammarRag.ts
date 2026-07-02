export const RAG_STREAM_STATUS = {
  TOKEN: 'token',
  DONE: 'done',
  ERROR: 'error',
} as const

export type RagStreamStatus = (typeof RAG_STREAM_STATUS)[keyof typeof RAG_STREAM_STATUS]

export interface RagStreamEvent {
  status: RagStreamStatus
  token?: string
  message?: string
}

export interface ChatMessage {
  role: 'user' | 'assistant'
  content: string
}
