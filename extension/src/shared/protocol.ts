export type ExtensionMessageType =
    | 'IMPORT_SHARE'
    | 'IMPORT_SHARE_RESULT'
    | 'GET_SETTINGS'
    | 'SAVE_SETTINGS'
    | 'START_CONNECT'
    | 'GET_CONNECTION'
    | 'REVOKE_CONNECTION'
    | 'SUBMIT_ANALYSIS'
    | 'GET_ANALYSIS_TASK'
    | 'GET_DAILY_PRACTICE'
    | 'MARK_DAILY_PRACTICED'

export interface ImportConversationPayload {
  source: 'chatgpt_share'
  shareId: string
  shareUrl: string
  title: string | null
  conversationContent: string
  messageCount: number
  importedAt: string
}

export interface ImportShareRequest {
  type: 'IMPORT_SHARE'
  shareUrl: string
}

export interface ImportShareResult {
  type: 'IMPORT_SHARE_RESULT'
  ok: boolean
  error?: string
  payload?: ImportConversationPayload
}

export interface GetSettingsRequest {
  type: 'GET_SETTINGS'
}

export interface SaveSettingsRequest {
  type: 'SAVE_SETTINGS'
  webOrigin: string
}

export interface SettingsResponse {
  type: 'SETTINGS'
  webOrigin: string
  allowOriginOverride: boolean
}

export interface StartConnectRequest {
  type: 'START_CONNECT'
}

export interface GetConnectionRequest {
  type: 'GET_CONNECTION'
}

export interface RevokeConnectionRequest {
  type: 'REVOKE_CONNECTION'
}

export interface ConnectionStatusResponse {
  type: 'CONNECTION_STATUS'
  connected: boolean
  connecting?: boolean
  username?: string | null
  expiresAt?: string | null
  error?: string | null
}

export interface SubmitAnalysisRequest {
  type: 'SUBMIT_ANALYSIS'
  conversationContent: string
  sourceType: 'chatgpt_history' | 'chatgpt_share'
  sourceConversationId?: string | null
  tabId?: number
}

export interface SubmitAnalysisResult {
  type: 'SUBMIT_ANALYSIS_RESULT'
  ok: boolean
  error?: string
  taskId?: string
  status?: string
  analysisId?: string
}

export interface GetAnalysisTaskRequest {
  type: 'GET_ANALYSIS_TASK'
  sourceType?: 'chatgpt_history' | 'chatgpt_share'
  sourceConversationId?: string | null
}

export interface AnalysisTaskStatusResponse {
  type: 'ANALYSIS_TASK_STATUS'
  running: boolean
  found?: boolean
  taskId?: string
  status?: string
  analysisId?: string
  error?: string | null
  sourceConversationId?: string | null
}

export interface GetDailyPracticeRequest {
  type: 'GET_DAILY_PRACTICE'
  excludeMemoryIds?: number[]
  excludeCardIds?: string[]
}

export interface DailyPracticeGoal {
  memoryId?: number | null
  title?: string
  pointId?: string
  familyId?: string
  memoryType?: string
  status?: string
  masteryLevel?: number
  priorityScore?: number
  evidenceOriginal?: string
  evidenceRewrite?: string
}

export interface DailyPracticeVocab {
  cardId?: string
  type?: string
  front?: string
  back?: string
}

export interface DailyPracticeResponse {
  type: 'DAILY_PRACTICE'
  ok: boolean
  error?: string
  empty?: boolean
  summary?: string
  prompt?: string
  goals?: DailyPracticeGoal[]
  vocabulary?: DailyPracticeVocab[]
}

export interface MarkDailyPracticedRequest {
  type: 'MARK_DAILY_PRACTICED'
  memoryIds: number[]
  sourceConversationId?: string | null
  tabId?: number
  practiceType?: string
}

export interface MarkDailyPracticedResponse {
  type: 'MARK_DAILY_PRACTICED_RESULT'
  ok: boolean
  error?: string
  sessionIds?: number[]
}

export type ExtensionRequest =
    | ImportShareRequest
    | GetSettingsRequest
    | SaveSettingsRequest
    | StartConnectRequest
    | GetConnectionRequest
    | RevokeConnectionRequest
    | SubmitAnalysisRequest
    | GetAnalysisTaskRequest
    | GetDailyPracticeRequest
    | MarkDailyPracticedRequest
