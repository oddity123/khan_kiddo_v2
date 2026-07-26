const SITE_ORIGIN = 'https://khankiddo.top'
const SITE_NAME = 'Khan Kiddo'
const DEFAULT_DESCRIPTION =
  'Khan Kiddo 是 AI 口语英语学习助手：分析对话语法与表达，生成诊断报告，帮你更自然地说英语。'

export interface SeoPayload {
  title: string
  description?: string
  path?: string
  robots?: string
}

function upsertMeta(attr: 'name' | 'property', key: string, content: string): void {
  const selector = `meta[${attr}="${key}"]`
  let el = document.head.querySelector<HTMLMetaElement>(selector)
  if (!el) {
    el = document.createElement('meta')
    el.setAttribute(attr, key)
    document.head.appendChild(el)
  }
  el.setAttribute('content', content)
}

function upsertLink(rel: string, href: string): void {
  let el = document.head.querySelector<HTMLLinkElement>(`link[rel="${rel}"]`)
  if (!el) {
    el = document.createElement('link')
    el.setAttribute('rel', rel)
    document.head.appendChild(el)
  }
  el.setAttribute('href', href)
}

export function applySeo(payload: SeoPayload): void {
  const title = payload.title.includes(SITE_NAME)
    ? payload.title
    : `${payload.title} · ${SITE_NAME}`
  const description = payload.description || DEFAULT_DESCRIPTION
  const path = payload.path || '/'
  const canonical = `${SITE_ORIGIN}${path === '/' ? '/' : path}`
  const robots = payload.robots || 'index, follow'

  document.title = title
  upsertMeta('name', 'description', description)
  upsertMeta('name', 'robots', robots)
  upsertMeta('property', 'og:type', 'website')
  upsertMeta('property', 'og:site_name', SITE_NAME)
  upsertMeta('property', 'og:title', title)
  upsertMeta('property', 'og:description', description)
  upsertMeta('property', 'og:url', canonical)
  upsertMeta('property', 'og:locale', 'zh_CN')
  upsertMeta('name', 'twitter:card', 'summary')
  upsertMeta('name', 'twitter:title', title)
  upsertMeta('name', 'twitter:description', description)
  upsertLink('canonical', canonical)
}

export {DEFAULT_DESCRIPTION, SITE_NAME, SITE_ORIGIN}
