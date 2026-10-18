import assert from 'node:assert/strict'
import {test} from 'node:test'

import {
  captionSavePresentation,
  completedCaptionLines,
  formatWallClock,
  handoffBlockReason,
  parseVoiceTranscript,
} from './voicePracticeTranscript.ts'

test('completedCaptionLines keeps finished user and assistant lines only', () => {
  const lines = completedCaptionLines([
    {role: 'user', text: ' I go yesterday '},
    {role: 'assistant', text: 'What did you do?'},
    {role: 'system', text: 'ignore'},
    {role: 'user', text: '   '},
  ])
  assert.deepEqual(lines, [
    {role: 'user', text: 'I go yesterday'},
    {role: 'assistant', text: 'What did you do?'},
  ])
})

test('parseVoiceTranscript reads saved User and Assistant rows', () => {
  const lines = parseVoiceTranscript('User: I go yesterday\nAssistant: What did you do?\nnoise')
  assert.deepEqual(lines, [
    {role: 'user', text: 'I go yesterday'},
    {role: 'assistant', text: 'What did you do?'},
  ])
})

test('handoffBlockReason explains unsaved, empty, and short transcripts', () => {
  assert.match(handoffBlockReason('User: hello there', false), /先保存/)
  assert.match(handoffBlockReason('', true), /没有完成的字幕/)
  assert.match(handoffBlockReason('User: hi', true), /太短/)
  assert.equal(handoffBlockReason('User: I went to the park yesterday', true), '')
})

test('failed save is not presented as success', () => {
  assert.equal(captionSavePresentation('failed').success, false)
  assert.match(captionSavePresentation('failed').text, /没存上/)
  assert.equal(captionSavePresentation('saved').success, true)
  assert.equal(captionSavePresentation('saving').success, false)
  assert.equal(captionSavePresentation('idle').success, false)
})

test('formatWallClock renders minutes and seconds', () => {
  assert.equal(formatWallClock(75), '1 分 15 秒')
  assert.equal(formatWallClock(0), '0 分 0 秒')
})
