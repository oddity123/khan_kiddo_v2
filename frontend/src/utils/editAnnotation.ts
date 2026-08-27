import type {SentenceEdit} from '@/types/conversation'

export type TextSegmentKind = 'plain' | 'del' | 'ins' | 'gap'

export interface TextSegment {
  kind: TextSegmentKind
  text: string
  /** 可选：供 title / a11y */
  op?: SentenceEdit['op']
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
  const oLen = originalTokens?.length ?? 0
  const cLen = correctedTokens?.length ?? 0
  for (const edit of edits) {
    if (edit.oStart < 0 || edit.oEnd < edit.oStart || edit.oEnd > oLen) {
      return false
    }
    if (edit.cStart < 0 || edit.cEnd < edit.cStart || edit.cEnd > cLen) {
      return false
    }
  }
  return true
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
      text: tokens[i],
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
      text: tokens[i],
      op,
    })
  }
  return segments
}
