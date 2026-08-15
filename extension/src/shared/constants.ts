/** Must stay in sync with frontend AnalyzeView import key. */
export const ANALYZE_IMPORT_SESSION_KEY = 'kk.extension.import.conversation'

export const WEB_ORIGIN_STORAGE_KEY = 'kk.extension.webOrigin'

/** Bump the suffix whenever the disclosed data practices materially change. */
export const PRIVACY_CONSENT_STORAGE_KEY = 'kk.extension.privacyConsent.v1'

export const PRIVACY_POLICY_URL = 'https://khankiddo.top/privacy/extension'

const envOrigin = (import.meta.env.VITE_KK_WEB_ORIGIN as string | undefined)?.trim()

/** Build-time default: localhost in development, production site in release builds. */
export const DEFAULT_WEB_ORIGIN = (envOrigin || 'http://localhost:5173').replace(/\/+$/, '')

/** Only development builds expose / honor a user-editable origin override. */
export const ALLOW_WEB_ORIGIN_OVERRIDE =
    import.meta.env.VITE_KK_ALLOW_ORIGIN_OVERRIDE === 'true' || import.meta.env.DEV
