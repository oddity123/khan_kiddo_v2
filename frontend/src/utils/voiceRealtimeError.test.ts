import assert from 'node:assert/strict'
import {test} from 'node:test'

import {formatRealtimeError} from './voiceRealtimeError.ts'

test('formatRealtimeError prefers flat message+code', () => {
  assert.equal(
    formatRealtimeError({type: 'error', message: 'upstream down', code: 503}),
    'upstream down（code=503）',
  )
})

test('formatRealtimeError reads nested openspeech error', () => {
  assert.equal(
    formatRealtimeError({type: 'error', error: {message: 'bad session', code: 'invalid'}}),
    'bad session（code=invalid）',
  )
})

test('formatRealtimeError falls back to code-only', () => {
  assert.equal(formatRealtimeError({type: 'error', code: 42}), '实时语音错误 code=42')
})

test('formatRealtimeError falls back to JSON snippet', () => {
  const text = formatRealtimeError({type: 'error', weird: true})
  assert.match(text, /^实时语音服务返回错误：/)
  assert.match(text, /weird/)
})
