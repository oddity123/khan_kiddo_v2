import assert from 'node:assert/strict'
import {test} from 'node:test'

import {encodeWavPcm16, isSilent} from './wavEncoder.ts'

function ascii(view: DataView, offset: number, length: number): string {
  return String.fromCharCode(...Array.from({length}, (_, i) => view.getUint8(offset + i)))
}

test('encodeWavPcm16 writes a 16kHz mono PCM16 header', () => {
  const view = new DataView(encodeWavPcm16(new Float32Array(10)))

  assert.equal(view.byteLength, 44 + 20)
  assert.equal(ascii(view, 0, 4), 'RIFF')
  assert.equal(view.getUint32(4, true), 36 + 20)
  assert.equal(ascii(view, 8, 4), 'WAVE')
  assert.equal(view.getUint16(20, true), 1)
  assert.equal(view.getUint16(22, true), 1)
  assert.equal(view.getUint32(24, true), 16000)
  assert.equal(view.getUint16(34, true), 16)
  assert.equal(ascii(view, 36, 4), 'data')
  assert.equal(view.getUint32(40, true), 20)
})

test('encodeWavPcm16 clamps and scales samples', () => {
  const view = new DataView(encodeWavPcm16(Float32Array.from([1, -1, 0, 2, -3, 0.5])))

  assert.equal(view.getInt16(44, true), 32767)
  assert.equal(view.getInt16(46, true), -32768)
  assert.equal(view.getInt16(48, true), 0)
  assert.equal(view.getInt16(50, true), 32767)
  assert.equal(view.getInt16(52, true), -32768)
  assert.equal(view.getInt16(54, true), Math.trunc(0.5 * 0x7fff))
})

test('isSilent detects near-zero recordings', () => {
  assert.equal(isSilent(new Float32Array(100)), true)
  assert.equal(isSilent(Float32Array.from([0, 0.001, -0.005])), true)
  assert.equal(isSilent(Float32Array.from([0, 0.2, 0])), false)
})
