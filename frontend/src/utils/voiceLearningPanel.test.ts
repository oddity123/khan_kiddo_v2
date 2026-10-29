import assert from 'node:assert/strict'
import {test} from 'node:test'

import {
  applyLearningPanelEvent,
  attemptOutcomeLabel,
  emptyLearningPanel,
  mergeLearningPanel,
  parseLearningPanelEvent,
  type VoiceLearningAttempt,
  type VoiceLearningFocus,
} from './voiceLearningPanel.ts'

const focus = (over: Partial<VoiceLearningFocus> = {}): VoiceLearningFocus => ({
  eventId: 1,
  cardId: 'c1',
  type: 'habit',
  front: 'I go yesterday',
  back: 'I went yesterday',
  status: 'unfamiliar',
  ...over,
})

const attempt = (over: Partial<VoiceLearningAttempt> = {}): VoiceLearningAttempt => ({
  attemptId: 1,
  cardId: 'c1',
  cardFront: 'I go yesterday',
  outcome: 'prompted',
  ...over,
})

test('parseLearningPanelEvent recognises focus / attempt / note and ignores lifecycle frames', () => {
  assert.equal(
    parseLearningPanelEvent({type: 'proxy.learning.focus', callId: 'x', payload: focus()})?.kind,
    'focus',
  )
  assert.equal(
    parseLearningPanelEvent({type: 'proxy.learning.attempt', payload: attempt()})?.kind,
    'attempt',
  )
  assert.equal(
    parseLearningPanelEvent({type: 'proxy.learning.note', payload: {noteId: 1, text: 'hi'}})?.kind,
    'note',
  )
  assert.equal(parseLearningPanelEvent({type: 'proxy.learning.tool', phase: 'replied'}), null)
  assert.equal(parseLearningPanelEvent({type: 'proxy.learning.focus'}), null)
  assert.equal(parseLearningPanelEvent({type: 'proxy.learning.note', payload: {noteId: 1}}), null)
})

test('applyLearningPanelEvent: focus replaces, attempt upgrade syncs focused card status', () => {
  let state = applyLearningPanelEvent(emptyLearningPanel(), {kind: 'focus', payload: focus()})
  assert.equal(state.focus?.status, 'unfamiliar')
  state = applyLearningPanelEvent(state, {
    kind: 'attempt',
    payload: attempt({outcome: 'independent_success', statusBefore: 'unfamiliar', statusAfter: 'fuzzy'}),
  })
  assert.equal(state.focus?.status, 'fuzzy')
  assert.equal(state.attempts.length, 1)

  // 其它卡的尝试不改焦点；同 id 事件重放不重复
  state = applyLearningPanelEvent(state, {
    kind: 'attempt',
    payload: attempt({attemptId: 2, cardId: 'other', statusAfter: 'mastered'}),
  })
  assert.equal(state.focus?.status, 'fuzzy')
  state = applyLearningPanelEvent(state, {kind: 'attempt', payload: attempt({attemptId: 2, cardId: 'other'})})
  assert.equal(state.attempts.length, 2)
})

test('applyLearningPanelEvent appends notes once per id', () => {
  let state = emptyLearningPanel()
  state = applyLearningPanelEvent(state, {kind: 'note', payload: {noteId: 5, text: 'a'}})
  state = applyLearningPanelEvent(state, {kind: 'note', payload: {noteId: 5, text: 'a'}})
  assert.equal(state.notes.length, 1)
})

test('mergeLearningPanel unions by id, keeps newer focus, sorts by id', () => {
  const live = applyLearningPanelEvent(emptyLearningPanel(), {
    kind: 'focus',
    payload: focus({eventId: 9, cardId: 'c9'}),
  })
  const merged = mergeLearningPanel(live, {
    focus: focus({eventId: 3, cardId: 'old'}),
    attempts: [attempt({attemptId: 2}), attempt({attemptId: 1})],
    notes: [{noteId: 4, text: 'n'}],
  })
  assert.equal(merged.focus?.cardId, 'c9')
  assert.deepEqual(merged.attempts.map((a) => a.attemptId), [1, 2])
  assert.equal(merged.notes.length, 1)

  const fromEmpty = mergeLearningPanel(emptyLearningPanel(), {focus: focus({eventId: 3}), attempts: [], notes: []})
  assert.equal(fromEmpty.focus?.eventId, 3)
  assert.deepEqual(mergeLearningPanel(emptyLearningPanel(), null), emptyLearningPanel())
})

test('attemptOutcomeLabel maps wire values', () => {
  assert.equal(attemptOutcomeLabel('independent_success'), '独立说出')
  assert.equal(attemptOutcomeLabel('miss'), '没说出')
  assert.equal(attemptOutcomeLabel('weird'), 'weird')
})
