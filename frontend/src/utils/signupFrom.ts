import {SIGNUP_FROM_STORAGE_KEY} from '../constants/voicePractice.ts'

const FROM_PATTERN = /^[A-Za-z0-9_-]{1,64}$/

export interface SignupFromStore {
  getItem(key: string): string | null
  setItem(key: string, value: string): void
  removeItem(key: string): void
}

function browserStore(): SignupFromStore | null {
  try {
    if (typeof sessionStorage === 'undefined') {
      return null
    }
    return sessionStorage
  } catch {
    return null
  }
}

export function rememberSignupFrom(raw: unknown, store: SignupFromStore | null = browserStore()): void {
  if (!store || typeof raw !== 'string' || !FROM_PATTERN.test(raw)) {
    return
  }
  store.setItem(SIGNUP_FROM_STORAGE_KEY, raw)
}

export function readSignupFrom(store: SignupFromStore | null = browserStore()): string | undefined {
  if (!store) {
    return undefined
  }
  const value = store.getItem(SIGNUP_FROM_STORAGE_KEY)
  if (!value || !FROM_PATTERN.test(value)) {
    return undefined
  }
  return value
}

export function forgetSignupFrom(store: SignupFromStore | null = browserStore()): void {
  store?.removeItem(SIGNUP_FROM_STORAGE_KEY)
}
