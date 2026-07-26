/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_API_BASE_URL: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}

import 'vue-router'

declare module 'vue-router' {
  interface RouteMeta {
    title?: string
    description?: string
    robots?: string
    guestOnly?: boolean
    requiresAuth?: boolean
    immersive?: boolean
  }
}
