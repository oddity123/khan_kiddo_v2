import http from './http'

export interface SiteInfo {
  icpNumber: string | null
  icpUrl: string
  psbNumber: string | null
  psbUrl: string
}

export function fetchSiteInfo() {
  return http.get<SiteInfo>('/api/site')
}
