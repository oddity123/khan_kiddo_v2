import assert from 'node:assert/strict'
import {test} from 'node:test'

import {mergeStreamingText} from './voiceRealtimeText.ts'

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
