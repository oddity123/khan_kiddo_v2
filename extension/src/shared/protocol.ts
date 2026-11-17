export type ExtensionMessageType =
    | 'IMPORT_SHARE'
    | 'IMPORT_SHARE_RESULT'
    | 'GET_SETTINGS'
    | 'SAVE_SETTINGS'
    | 'START_CONNECT'
    | 'GET_CONNECTION'
    | 'REVOKE_CONNECTION'

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

export type ExtensionRequest =
    | ImportShareRequest
    | GetSettingsRequest
    | SaveSettingsRequest
    | StartConnectRequest
    | GetConnectionRequest
    | RevokeConnectionRequest
