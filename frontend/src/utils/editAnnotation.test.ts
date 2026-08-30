import assert from 'node:assert/strict'
import {test} from 'node:test'

import {
  buildCorrectedSegments,
  buildOriginalSegments,
  editsOffsetsValid,
  normalizeSentenceEdits,
  segmentsPlainText,
} from './editAnnotation.ts'
import type {SentenceEdit} from '@/types/conversation'

const insertWeAre: SentenceEdit = {
  op: 'M',
  oStart: 2,
  oEnd: 2,
  oStr: '',
  cStart: 2,
  cEnd: 4,
  cStr: 'we are',
}

test('normalizeSentenceEdits maps Jackson ostart keys to oStart', () => {
  const normalized = normalizeSentenceEdits([
    {
      op: 'M',
      ostart: 2,
      oend: 2,
      ostr: '',
      cstart: 2,
      cend: 4,
      cstr: 'we are',
    },
  ])
  assert.deepEqual(normalized, [insertWeAre])
})

test('editsOffsetsValid rejects missing numeric offsets', () => {
  assert.equal(
    editsOffsetsValid(
      [{op: 'M'} as SentenceEdit],
      ['Yeah,', 'wherever', 'in', 'the', 'conversation.'],
      ['Yeah,', 'wherever', 'we', 'are', 'in', 'the', 'conversation.'],
    ),
    false,
  )
})

test('M edit highlights inserted tokens on the corrected side', () => {
  const tokens = ['Yeah,', 'wherever', 'we', 'are', 'in', 'the', 'conversation.']
  const segs = buildCorrectedSegments(tokens, [insertWeAre])
  assert.equal(segs[2]?.kind, 'ins')
  assert.equal(segs[2]?.text.trim(), 'we')
  assert.equal(segs[3]?.kind, 'ins')
  assert.equal(segs[3]?.text.trim(), 'are')
  assert.equal(segs[4]?.kind, 'plain')
})

test('M edit places a gap between original tokens', () => {
  const tokens = ['Yeah,', 'wherever', 'in', 'the', 'conversation.']
  const segs = buildOriginalSegments(tokens, [insertWeAre])
  const kinds = segs.map((s) => s.kind)
  assert.deepEqual(kinds, ['plain', 'plain', 'gap', 'plain', 'plain', 'plain'])
})

test('segment text keeps spaces between tokens', () => {
  const tokens = ['Yeah,', 'wherever', 'in', 'the', 'conversation.']
  const text = segmentsPlainText(buildOriginalSegments(tokens, [insertWeAre]))
  assert.equal(text, 'Yeah, wherever in the conversation.')
})
