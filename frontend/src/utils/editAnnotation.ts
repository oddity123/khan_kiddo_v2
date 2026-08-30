import type {SentenceEdit} from '@/types/conversation'

export type TextSegmentKind = 'plain' | 'del' | 'ins' | 'gap'

export interface TextSegment {
  kind: TextSegmentKind
  text: string
  /** 可选：供 title / a11y */
  op?: SentenceEdit['op']
}

/** Jackson / 旧库可能写出 ostart；与 SentenceEdit 并存 */
interface RawSentenceEdit {
  op?: string
  oStart?: number
  oEnd?: number
  oStr?: string
  cStart?: number
  cEnd?: number
  cStr?: string
  ostart?: number
  oend?: number
  ostr?: string
  cstart?: number
  cend?: number
  cstr?: string
}

function readOffset(...values: unknown[]): number {
  for (const value of values) {
    if (typeof value === 'number' && Number.isInteger(value)) {
      return value
    }
  }
  return Number.NaN
}

function readOptionalStr(...values: unknown[]): string | undefined {
  for (const value of values) {
    if (typeof value === 'string') {
      return value
    }
  }
  return undefined
}

/**
 * 把 API / 旧 JSON 的 edits 收成 camelCase，供分段与校验使用。
 */
export function normalizeSentenceEdits(edits: unknown): SentenceEdit[] {
  if (!Array.isArray(edits)) {
    return []
  }
  const out: SentenceEdit[] = []
  for (const raw of edits) {
    if (!raw || typeof raw !== 'object') {
      continue
    }
    const edit = raw as RawSentenceEdit
    const op = edit.op
    if (op !== 'R' && op !== 'M' && op !== 'U') {
      continue
    }
    out.push({
      op,
      oStart: readOffset(edit.oStart, edit.ostart),
      oEnd: readOffset(edit.oEnd, edit.oend),
      oStr: readOptionalStr(edit.oStr, edit.ostr),
      cStart: readOffset(edit.cStart, edit.cstart),
      cEnd: readOffset(edit.cEnd, edit.cend),
      cStr: readOptionalStr(edit.cStr, edit.cstr),
    })
  }
  return out
}

function isEndExclusiveRange(start: number, end: number, len: number): boolean {
  return Number.isInteger(start) && Number.isInteger(end) && start >= 0 && end >= start && end <= len
}

export function editOffsetsValidForSide(
  side: 'original' | 'corrected',
  edits: SentenceEdit[],
  tokens: string[],
): boolean {
  const len = tokens.length
  for (const edit of edits) {
    if (side === 'original') {
      if (!isEndExclusiveRange(edit.oStart, edit.oEnd, len)) {
        return false
      }
    } else if (!isEndExclusiveRange(edit.cStart, edit.cEnd, len)) {
      return false
    }
  }
  return true
}

/**
 * 校验 edits 下标是否落在 token 数组内；不一致时整句应降级纯文本。
 */
export function editsOffsetsValid(
  edits: SentenceEdit[] | undefined,
  originalTokens: string[] | undefined,
  correctedTokens: string[] | undefined,
): boolean {
  if (!edits?.length) {
    return true
  }
  return (
    editOffsetsValidForSide('original', edits, originalTokens ?? []) &&
    editOffsetsValidForSide('corrected', edits, correctedTokens ?? [])
  )
}

function tokenText(index: number, token: string): string {
  return index === 0 ? token : ` ${token}`
}

export function buildOriginalSegments(
  tokens: string[],
  edits: SentenceEdit[],
): TextSegment[] {
  const marks = new Map<number, 'del'>()
  const gaps = new Set<number>()
  for (const edit of edits) {
    if (edit.op === 'R' || edit.op === 'U') {
      for (let i = edit.oStart; i < edit.oEnd; i++) {
        marks.set(i, 'del')
      }
    } else if (edit.op === 'M') {
      gaps.add(edit.oStart)
    }
  }
  const segments: TextSegment[] = []
  for (let i = 0; i < tokens.length; i++) {
    if (gaps.has(i)) {
      segments.push({kind: 'gap', text: '', op: 'M'})
    }
    const kind = marks.get(i) ?? 'plain'
    segments.push({
      kind,
      text: tokenText(i, tokens[i]),
      op: kind === 'del' ? (edits.find((e) => e.oStart <= i && i < e.oEnd)?.op ?? 'R') : undefined,
    })
  }
  if (gaps.has(tokens.length)) {
    segments.push({kind: 'gap', text: '', op: 'M'})
  }
  return segments
}

export function buildCorrectedSegments(
  tokens: string[],
  edits: SentenceEdit[],
): TextSegment[] {
  const marks = new Map<number, SentenceEdit['op']>()
  for (const edit of edits) {
    if (edit.op === 'R' || edit.op === 'M') {
      for (let i = edit.cStart; i < edit.cEnd; i++) {
        marks.set(i, edit.op)
      }
    }
  }
  const segments: TextSegment[] = []
  for (let i = 0; i < tokens.length; i++) {
    const op = marks.get(i)
    segments.push({
      kind: op ? 'ins' : 'plain',
      text: tokenText(i, tokens[i]),
      op,
    })
  }
  return segments
}

export function segmentsPlainText(segments: TextSegment[]): string {
  return segments.map((segment) => segment.text).join('')
}
