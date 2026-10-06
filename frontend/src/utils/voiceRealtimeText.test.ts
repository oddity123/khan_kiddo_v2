import assert from 'node:assert/strict'
import {test} from 'node:test'

import {
  foldAsrStreamingPreviews,
  mergeStreamingText,
  pickAsrStreamingPreview,
} from './voiceRealtimeText.ts'

test('mergeStreamingText replaces with cumulative snapshot', () => {
  assert.equal(mergeStreamingText('', 'and'), 'and')
  assert.equal(mergeStreamingText('and', 'and this'), 'and this')
  assert.equal(mergeStreamingText('and this', 'and this is fine'), 'and this is fine')
})

test('mergeStreamingText keeps current when delta is shorter prefix snapshot', () => {
  assert.equal(mergeStreamingText('and this is fine', 'and this'), 'and this is fine')
})

test('mergeStreamingText appends true incremental tokens', () => {
  assert.equal(mergeStreamingText('Hel', 'lo'), 'Hello')
  assert.equal(mergeStreamingText('Hello', ' world'), 'Hello world')
})

test('mergeStreamingText ignores empty delta', () => {
  assert.equal(mergeStreamingText('keep', ''), 'keep')
})

test('pickAsrStreamingPreview prefers transcript then text then delta', () => {
  assert.equal(
    pickAsrStreamingPreview({transcript: 'final-ish', text: 'mid', delta: 'tok'}),
    'final-ish',
  )
  assert.equal(pickAsrStreamingPreview({text: 'mid', delta: 'tok'}), 'mid')
  assert.equal(pickAsrStreamingPreview({delta: 'tok'}), 'tok')
  assert.equal(pickAsrStreamingPreview({}), '')
})

test('foldAsrStreamingPreviews cover-assigns cumulative frames (no andand this)', () => {
  const frames = [{delta: 'and'}, {delta: 'and this'}, {delta: 'and this is fine'}]
  assert.equal(foldAsrStreamingPreviews(frames), 'and this is fine')
  // 对比：盲目 merge/append 会叠成 andand…
  let naive = ''
  for (const frame of frames) {
    naive += String(frame.delta ?? '')
  }
  assert.equal(naive, 'andand thisand this is fine')
  assert.notEqual(foldAsrStreamingPreviews(frames), naive)
})

test('foldAsrStreamingPreviews survives non-prefix ASR revisions', () => {
  // 前缀启发式会对这类改写落到 +=；覆盖赋值仍正确
  const frames = [{delta: 'a'}, {delta: 'I'}, {delta: 'I am'}, {text: 'I am fine'}]
  assert.equal(foldAsrStreamingPreviews(frames), 'I am fine')
  assert.notEqual(mergeStreamingText(mergeStreamingText('a', 'I'), 'I am'), 'I am')
})
