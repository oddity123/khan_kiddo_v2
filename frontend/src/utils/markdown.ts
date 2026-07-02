import DOMPurify from 'dompurify'
import {marked} from 'marked'

marked.setOptions({
  breaks: true,
  gfm: true,
})

function escapeHtml(text: string) {
  return text
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
}

export function renderMarkdown(raw: string): string {
  const trimmed = raw.trim()
  if (!trimmed) {
    return ''
  }
  try {
    const html = marked.parse(trimmed) as string
    return DOMPurify.sanitize(html)
  } catch {
    return DOMPurify.sanitize(`<pre>${escapeHtml(trimmed)}</pre>`)
  }
}
