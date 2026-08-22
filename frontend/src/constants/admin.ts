/** 管理后台入口路径（非公开，勿写入导航栏） */
export const ADMIN_BASE_PATH = '/admin/0113'

export const ADMIN_USERS_PATH = `${ADMIN_BASE_PATH}/users`

export const ADMIN_ANALYSES_PATH = `${ADMIN_BASE_PATH}/analyses`

export const ADMIN_KNOWLEDGE_PATH = `${ADMIN_BASE_PATH}/knowledge/points`

/** 管理端对话详情来源：全站对话列表 / 某用户对话列表 */
export const ADMIN_ANALYSIS_FROM_ANALYSES = 'analyses'
export const ADMIN_ANALYSIS_FROM_USER = 'user'

export function adminUserAnalysesPath(userId: number | string) {
  return `${ADMIN_BASE_PATH}/users/${userId}/analyses`
}

export function adminAnalysisDetailPath(userId: number | string, analysisId: string) {
  return `${ADMIN_BASE_PATH}/users/${userId}/analyses/${analysisId}`
}

export function isAdminDetailFromGlobalAnalyses(query: Record<string, unknown>) {
  return query.from === ADMIN_ANALYSIS_FROM_ANALYSES
}

export function parseAdminReturnTo(query: Record<string, unknown>): string | null {
  const raw = query.returnTo
  const value = Array.isArray(raw) ? raw[0] : raw
  if (typeof value !== 'string' || !value.startsWith(ADMIN_BASE_PATH)) {
    return null
  }
  return value
}
