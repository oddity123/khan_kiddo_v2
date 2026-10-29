/**
 * 右侧「本场学习」面板的纯状态逻辑：事件契约解析、按 id 去重合并。
 * 形状与后端 VoiceLearning*Dto 一致（REST hydrate 与 proxy.learning.* 共用）。
 */

export type VoiceAttemptOutcome = 'independent_success' | 'prompted' | 'uncertain' | 'miss'

export interface VoiceLearningFocus {
  eventId: number
  cardId: string
  type: string
  front: string
  back: string
  status: string
  promptHint?: string | null
  createdAt?: string | null
}

export interface VoiceLearningAttempt {
  attemptId: number
  cardId: string
  cardFront: string
  outcome: string
  userUtterance?: string | null
  reason?: string | null
  statusBefore?: string | null
  statusAfter?: string | null
  createdAt?: string | null
}

export interface VoiceLearningNote {
  noteId: number
  text: string
  cardId?: string | null
  cardFront?: string | null
  analysisId?: string | null
  createdAt?: string | null
}

export interface VoiceLearningPanelState {
  focus: VoiceLearningFocus | null
  attempts: VoiceLearningAttempt[]
  notes: VoiceLearningNote[]
}

export type VoiceLearningPanelEvent =
  | {kind: 'focus'; payload: VoiceLearningFocus}
  | {kind: 'attempt'; payload: VoiceLearningAttempt}
  | {kind: 'note'; payload: VoiceLearningNote}

export const LEARNING_EVENT_FOCUS = 'proxy.learning.focus'
export const LEARNING_EVENT_ATTEMPT = 'proxy.learning.attempt'
export const LEARNING_EVENT_NOTE = 'proxy.learning.note'

export function emptyLearningPanel(): VoiceLearningPanelState {
  return {focus: null, attempts: [], notes: []}
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return value !== null && typeof value === 'object' && !Array.isArray(value)
}

/** 只认三类面板事件（`proxy.learning.tool` 生命周期帧返回 null）。 */
export function parseLearningPanelEvent(event: Record<string, unknown>): VoiceLearningPanelEvent | null {
  const type = String(event.type ?? '')
  const payload = event.payload
  if (!isRecord(payload)) {
    return null
  }
  if (type === LEARNING_EVENT_FOCUS && typeof payload.cardId === 'string') {
    return {kind: 'focus', payload: payload as unknown as VoiceLearningFocus}
  }
  if (type === LEARNING_EVENT_ATTEMPT && typeof payload.cardId === 'string') {
    return {kind: 'attempt', payload: payload as unknown as VoiceLearningAttempt}
  }
  if (type === LEARNING_EVENT_NOTE && typeof payload.text === 'string') {
    return {kind: 'note', payload: payload as unknown as VoiceLearningNote}
  }
  return null
}

function upsertById<T>(list: T[], item: T, idOf: (row: T) => number): T[] {
  const id = idOf(item)
  if (list.some((row) => idOf(row) === id)) {
    return list.map((row) => (idOf(row) === id ? item : row))
  }
  return [...list, item]
}

/** 应用一条实时事件（不可变）。attempt 上调进度时，同卡焦点状态同步。 */
export function applyLearningPanelEvent(
  state: VoiceLearningPanelState,
  event: VoiceLearningPanelEvent,
): VoiceLearningPanelState {
  if (event.kind === 'focus') {
    return {...state, focus: event.payload}
  }
  if (event.kind === 'attempt') {
    const attempt = event.payload
    const focus =
      state.focus && state.focus.cardId === attempt.cardId && attempt.statusAfter
        ? {...state.focus, status: attempt.statusAfter}
        : state.focus
    return {
      ...state,
      focus,
      attempts: upsertById(state.attempts, attempt, (row) => row.attemptId),
    }
  }
  return {...state, notes: upsertById(state.notes, event.payload, (row) => row.noteId)}
}

/**
 * 合并 REST 快照与已到达的实时事件：按 id 去重，焦点取较新的一条。
 * 避免 hydrate 晚于首批事件时把实时数据冲掉。
 */
export function mergeLearningPanel(
  current: VoiceLearningPanelState,
  remote: Partial<VoiceLearningPanelState> | null | undefined,
): VoiceLearningPanelState {
  let next = current
  const remoteFocus = remote?.focus ?? null
  if (remoteFocus && (!next.focus || remoteFocus.eventId >= next.focus.eventId)) {
    next = {...next, focus: remoteFocus}
  }
  for (const attempt of remote?.attempts ?? []) {
    next = {...next, attempts: upsertById(next.attempts, attempt, (row) => row.attemptId)}
  }
  for (const note of remote?.notes ?? []) {
    next = {...next, notes: upsertById(next.notes, note, (row) => row.noteId)}
  }
  return {
    ...next,
    attempts: [...next.attempts].sort((a, b) => a.attemptId - b.attemptId),
    notes: [...next.notes].sort((a, b) => a.noteId - b.noteId),
  }
}

export function attemptOutcomeLabel(outcome: string): string {
  if (outcome === 'independent_success') return '独立说出'
  if (outcome === 'prompted') return '有提示'
  if (outcome === 'uncertain') return '不确定'
  if (outcome === 'miss') return '没说出'
  return outcome
}
