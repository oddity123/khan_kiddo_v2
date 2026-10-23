import assert from 'node:assert/strict'
import test from 'node:test'
import {expressionStatusLabel, sortErrors, sortItemsByPriority} from './analysisDisplay.ts'

test('status labels distinguish error, risk and naturalness with legacy fallback', () => {
  assert.equal(expressionStatusLabel('ERROR'), '错误')
  assert.equal(expressionStatusLabel('RISK'), '注意')
  assert.equal(expressionStatusLabel('UNNATURAL'), '更自然')
  assert.equal(expressionStatusLabel(undefined), '错误')
})

test('optional polish is hidden without removing mixed sentences or legacy issues', () => {
  const optional = {type: '词汇', status: 'OPTIONAL' as const, errorLevel: 'FATAL'}
  const issue = {type: '时态', status: 'ERROR' as const, errorLevel: 'BASIC'}
  const legacy = {type: '冠词', errorLevel: 'BASIC'}
  assert.deepEqual(sortErrors([optional, issue, legacy]), [issue, legacy])
  const mixed = {originalSentence: 'Yesterday I go.', errors: [optional, issue]}
  const old = {originalSentence: 'a apple', errors: [legacy]}
  assert.deepEqual(sortItemsByPriority([
    {originalSentence: 'Sounds good.', errors: [optional]}, mixed, old,
  ]), [mixed, old])
})
