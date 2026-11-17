import {CONNECT_PENDING_STORAGE_KEY, CREDENTIAL_STORAGE_KEY} from './constants'

export interface StoredCredential {
  token: string
  username: string
  expiresAt: string
  connectedAt: string
}

export interface PendingConnect {
  sessionId: string
  codeVerifier: string
  expiresAt: string
  startedAt: string
}

export async function loadCredential(): Promise<StoredCredential | null> {
  const stored = await chrome.storage.local.get(CREDENTIAL_STORAGE_KEY)
  const value = stored[CREDENTIAL_STORAGE_KEY]
  if (!value || typeof value !== 'object') {
    return null
  }
  const cred = value as StoredCredential
  if (typeof cred.token !== 'string' || !cred.token.startsWith('kk_ext_')) {
    return null
  }
  return cred
}

export async function saveCredential(cred: StoredCredential): Promise<void> {
  await chrome.storage.local.set({[CREDENTIAL_STORAGE_KEY]: cred})
}

export async function clearCredential(): Promise<void> {
  await chrome.storage.local.remove(CREDENTIAL_STORAGE_KEY)
}

export async function loadPendingConnect(): Promise<PendingConnect | null> {
  const stored = await chrome.storage.local.get(CONNECT_PENDING_STORAGE_KEY)
  const value = stored[CONNECT_PENDING_STORAGE_KEY]
  if (!value || typeof value !== 'object') {
    return null
  }
  const pending = value as PendingConnect
  if (typeof pending.sessionId !== 'string' || typeof pending.codeVerifier !== 'string') {
    return null
  }
  return pending
}

export async function savePendingConnect(pending: PendingConnect): Promise<void> {
  await chrome.storage.local.set({[CONNECT_PENDING_STORAGE_KEY]: pending})
}

export async function clearPendingConnect(): Promise<void> {
  await chrome.storage.local.remove(CONNECT_PENDING_STORAGE_KEY)
}
