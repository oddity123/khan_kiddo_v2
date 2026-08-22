import type {LocationQuery, LocationQueryRaw, RouteLocationNormalizedLoaded, Router} from 'vue-router'

export function parseAdminListPage(query: LocationQuery): number {
  const raw = query.page
  const value = Array.isArray(raw) ? raw[0] : raw
  const page = Number(value)
  return Number.isFinite(page) && page > 0 ? Math.floor(page) : 1
}

export function parseAdminListKeyword(query: LocationQuery): string {
  const raw = query.keyword
  const value = Array.isArray(raw) ? raw[0] : raw
  return typeof value === 'string' ? value : ''
}

export function parseAdminListUsername(query: LocationQuery): string {
  const raw = query.username
  const value = Array.isArray(raw) ? raw[0] : raw
  return typeof value === 'string' ? value : ''
}

export function buildAdminAnalysesListQuery(
  baseQuery: LocationQuery,
  page: number,
  keyword: string,
  username: string,
): LocationQueryRaw {
  const query: LocationQueryRaw = {}
  for (const [key, val] of Object.entries(baseQuery)) {
    if (key === 'page' || key === 'keyword' || key === 'username') {
      continue
    }
    if (val != null) {
      query[key] = val
    }
  }
  if (page > 1) {
    query.page = String(page)
  }
  const trimmedKeyword = keyword.trim()
  if (trimmedKeyword) {
    query.keyword = trimmedKeyword
  }
  const trimmedUsername = username.trim()
  if (trimmedUsername) {
    query.username = trimmedUsername
  }
  return query
}

export function syncAdminAnalysesListRouteQuery(
  router: Router,
  route: RouteLocationNormalizedLoaded,
  page: number,
  keyword: string,
  username: string,
) {
  router.replace({
    path: route.path,
    query: buildAdminAnalysesListQuery(route.query, page, keyword, username),
  })
}

export function adminAnalysesListReturnTo(
  router: Router,
  route: RouteLocationNormalizedLoaded,
  page: number,
  keyword: string,
  username: string,
) {
  return router.resolve({
    path: route.path,
    query: buildAdminAnalysesListQuery(route.query, page, keyword, username),
  }).fullPath
}

export function buildAdminListQuery(
  baseQuery: LocationQuery,
  page: number,
  keyword: string,
): LocationQueryRaw {
  const query: LocationQueryRaw = {}
  for (const [key, val] of Object.entries(baseQuery)) {
    if (key === 'page' || key === 'keyword') {
      continue
    }
    if (val != null) {
      query[key] = val
    }
  }
  if (page > 1) {
    query.page = String(page)
  }
  const trimmed = keyword.trim()
  if (trimmed) {
    query.keyword = trimmed
  }
  return query
}

export function syncAdminListRouteQuery(
  router: Router,
  route: RouteLocationNormalizedLoaded,
  page: number,
  keyword: string,
) {
  router.replace({
    path: route.path,
    query: buildAdminListQuery(route.query, page, keyword),
  })
}

export function adminListReturnTo(
  router: Router,
  route: RouteLocationNormalizedLoaded,
  page: number,
  keyword: string,
) {
  return router.resolve({
    path: route.path,
    query: buildAdminListQuery(route.query, page, keyword),
  }).fullPath
}
