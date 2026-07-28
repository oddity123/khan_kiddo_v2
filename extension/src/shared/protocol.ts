export type ExtensionMessageType =
    | 'IMPORT_SHARE'
    | 'IMPORT_SHARE_RESULT'
    | 'GET_SETTINGS'
    | 'SAVE_SETTINGS'

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

export type ExtensionRequest =
    | ImportShareRequest
    | GetSettingsRequest
    | SaveSettingsRequest
