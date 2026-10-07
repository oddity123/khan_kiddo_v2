import assert from 'node:assert/strict'
import {test} from 'node:test'

import {bytesToBase64, downsampleTo16k, floatTo16BitPcm} from './voiceRealtimeAudio.ts'

test('floatTo16BitPcm clamps and scales', () => {
  const pcm = floatTo16BitPcm(new Float32Array([0, 1, -1, 2, -2]))
  assert.equal(pcm[0], 0)
  assert.equal(pcm[1], 0x7fff)
  assert.equal(pcm[2], -0x8000)
  assert.equal(pcm[3], 0x7fff)
  assert.equal(pcm[4], -0x8000)
})

test('downsampleTo16k identity when rates match', () => {
  const input = new Float32Array([0.1, 0.2, 0.3])
  assert.equal(downsampleTo16k(input, 16_000), input)
})

test('downsampleTo16k halves 32k to 16k', () => {
  const input = new Float32Array([1, 2, 3, 4, 5, 6])
  const out = downsampleTo16k(input, 32_000, 16_000)
  assert.deepEqual([...out], [1, 3, 5])
})

test('bytesToBase64 round-trips ASCII bytes', () => {
  const bytes = new Uint8Array([72, 105]) // Hi
  assert.equal(atob(bytesToBase64(bytes)), 'Hi')
})
