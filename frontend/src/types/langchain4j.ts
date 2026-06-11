export const LANGCHAIN4J_STREAM_STATUS = {
  TOKEN: 'TOKEN',
  DONE: 'DONE',
  ERROR: 'ERROR',
} as const

export type Langchain4jStreamStatus =
  (typeof LANGCHAIN4J_STREAM_STATUS)[keyof typeof LANGCHAIN4J_STREAM_STATUS]

export interface Langchain4jStreamEvent {
  status: Langchain4jStreamStatus
  token?: string
  message?: string
}

export interface ChatMessage {
  role: 'user' | 'assistant'
  content: string
}
