import assert from 'node:assert/strict'
import {test} from 'node:test'

import {forgetSignupFrom, readSignupFrom, rememberSignupFrom, type SignupFromStore} from './signupFrom.ts'

function memoryStore(): SignupFromStore & { data: Map<string, string> } {
  const data = new Map<string, string>()
  return {
    data,
    getItem: (key) => data.get(key) ?? null,
    setItem: (key, value) => {
      data.set(key, value)
    },
    removeItem: (key) => {
      data.delete(key)
    },
  }
}

test('rememberSignupFrom keeps a short source mark', () => {
  const store = memoryStore()
  rememberSignupFrom('xhs-ielts', store)
  assert.equal(readSignupFrom(store), 'xhs-ielts')
  rememberSignupFrom('reactivate', store)
  assert.equal(readSignupFrom(store), 'reactivate')
})

test('rememberSignupFrom drops blank and unsafe values', () => {
  const store = memoryStore()
  rememberSignupFrom('reactivate', store)
  rememberSignupFrom('has space', store)
  rememberSignupFrom('../etc', store)
  rememberSignupFrom('', store)
  assert.equal(readSignupFrom(store), 'reactivate')
  forgetSignupFrom(store)
  assert.equal(readSignupFrom(store), undefined)
})
