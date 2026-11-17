import {test} from 'node:test'
import assert from 'node:assert/strict'
import {runInNewContext} from 'node:vm'
import {installResponseCapture} from '../src/connection/capture.ts'

test('active history capture uses session token then pages the conversation', async () => {
  const message = (id, parent_id, role, text) => ({id, metadata: {parent_id}, author: {role}, content: {parts: [text]}})
  const newest = {current_node: 'answer', messages: [message('answer', 'hidden', 'assistant', 'Hi')],
    page_info: {start_cursor: 'older', has_previous_page: true}}
  const older = {messages: [message('root', null, 'user', 'Hello'), message('hidden', 'root', 'tool', ''),
    message('alternate', 'root', 'assistant', 'Other branch')], page_info: {has_previous_page: false}}
  const requests = []
  let releaseOlder
  const olderResponse = new Promise(resolve => { releaseOlder = resolve })
  const window = {fetch: async (input, options = {}) => {
    const url = typeof input === 'string' || input instanceof URL ? String(input) : input.url
    const headers = options.headers instanceof Headers
      ? options.headers
      : new Headers(options.headers || (input instanceof Request ? input.headers : undefined))
    requests.push({url, options: {...options, headers}})
    if (String(url).includes('/api/auth/session')) {
      return Response.json({accessToken: 'tok-123'})
    }
    return requests.filter(item => String(item.url).includes('/backend-api/')).length === 1
      ? Response.json(newest)
      : olderResponse
  }}
  runInNewContext(`(${installResponseCapture.toString()})()`, {
    window, location: new URL('https://chatgpt.com/c/test'), URL, Request, Headers, AbortSignal, setTimeout: () => {},
  })
  assert.equal(typeof window.__kkStartHistoryCapture, 'function')
  window.__kkStartHistoryCapture('test')
  for (let i = 0; i < 30 && requests.filter(item => String(item.url).includes('/backend-api/')).length < 2; i++) {
    await new Promise(resolve => setTimeout(resolve, 10))
  }
  const apiRequests = requests.filter(item => String(item.url).includes('/backend-api/'))
  assert.equal(window.__kkCapture.history.status, 'reading')
  assert.equal(window.__kkCapture.history.pages, 1)
  assert.equal(apiRequests[0].options.headers.get('Authorization'), 'Bearer tok-123')
  assert.equal(new URL(apiRequests[1].url).searchParams.get('before'), 'older')
  releaseOlder(Response.json(older))
  for (let i = 0; i < 30 && window.__kkCapture.history.status !== 'finished'; i++) {
    await new Promise(resolve => setTimeout(resolve, 10))
  }
  const result = window.__kkCapture.history
  assert.equal(result.pages, 2)
  assert.equal(result.stopReason, 'no-previous-page')
  assert.equal(result.branchStatus, 'root-reached')
  assert.deepEqual(Array.from(result.messages, item => item.text), ['Hello', 'Hi'])
})

test('missing access token finishes with an explicit stop reason', async () => {
  const window = {fetch: async () => Response.json({})}
  runInNewContext(`(${installResponseCapture.toString()})()`, {
    window, location: new URL('https://chatgpt.com/c/test'), URL, Request, Headers, AbortSignal, setTimeout: () => {},
  })
  window.__kkStartHistoryCapture('test')
  for (let i = 0; i < 20 && window.__kkCapture.history.status !== 'finished'; i++) {
    await new Promise(resolve => setTimeout(resolve, 10))
  }
  assert.equal(window.__kkCapture.history.stopReason, 'missing-access-token')
  assert.equal(window.__kkCapture.history.messageCount, 0)
})
