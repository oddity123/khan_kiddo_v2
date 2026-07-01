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

export interface GrammarErrorSearchRequest {
  query: string
  problemTypes?: string[]
  limit?: number
}

export interface GrammarErrorSearchItem {
  analysisId: string
  sentenceId?: number
  originalSentence: string
  problemTypes: string[]
  errorPoints: string[]
  suggestion: string
  score?: number
  createdAt?: string
}

export interface GrammarErrorSearchResponse {
  items: GrammarErrorSearchItem[]
}

export interface ChatMessage {
  role: 'user' | 'assistant'
  content: string
}

export const PROBLEM_TYPE_OPTIONS = [
  { value: 'Tense', label: '时态错误' },
  { value: 'Agreement', label: '主谓一致' },
  { value: 'Plural', label: '单复数错误' },
  { value: 'Article', label: '冠词错误' },
  { value: 'Preposition', label: '介词错误' },
  { value: 'Pronoun', label: '代词错误' },
  { value: 'Structure', label: '句式结构' },
  { value: 'Clause', label: '从句错误' },
  { value: 'Word Form', label: '词性错误' },
  { value: 'Comparison', label: '比较级错误' },
  { value: 'Word Choice', label: '用词不当' },
  { value: 'Collocation', label: '搭配错误' },
  { value: 'Chinglish', label: '中式英语' },
  { value: 'Redundancy', label: '表达冗余' },
  { value: 'Tone', label: '语气不当' },
  { value: 'Unnatural', label: '表达生硬' },
  { value: 'Vocabulary', label: '词汇贫乏' },
  { value: 'Formal', label: '口语化不足' },
  { value: 'Incomplete', label: '句子未完成' },
  { value: 'Chinese', label: '中文表达' },
] as const
