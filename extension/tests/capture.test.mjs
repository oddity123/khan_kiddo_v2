import {test} from 'node:test'
import assert from 'node:assert/strict'
import {runInNewContext} from 'node:vm'
import {installResponseCapture} from '../src/connection/capture.ts'

test('automatic history follows the observed cursor and reconstructs the active branch across pages', async () => {
  const message = (id, parent_id, role, text) => ({id, metadata: {parent_id}, author: {role}, content: {parts: [text]}})
  const newest = {current_node: 'answer', messages: [message('answer', 'hidden', 'assistant', 'Hi')],
    page_info: {start_cursor: 'older', has_previous_page: true}}
  const older = {messages: [message('root', null, 'user', 'Hello'), message('hidden', 'root', 'tool', ''),
    message('alternate', 'root', 'assistant', 'Other branch')], page_info: {has_previous_page: false}}
  const requests = []
  let releaseOlder
  const olderResponse = new Promise(resolve => { releaseOlder = resolve })
  const window = {fetch: async (url, options) => {
    requests.push({url, options})
    return requests.length === 1 ? Response.json(newest) : olderResponse
  }}
  runInNewContext(`(${installResponseCapture.toString()})()`, {
    window, location: new URL('https://chatgpt.com/c/test'), URL, Request, AbortSignal,
    sessionStorage: {getItem: key => key.endsWith('.auto') ? 'true' : 'test', removeItem: () => {}}, setTimeout: () => {},
  })
  await window.fetch(new Request('https://chatgpt.com/backend-api/conversations/test?num_turns=10&include_has_versions=true', {
    credentials: 'include', headers: {'x-page-context': 'fixture-context'},
  }))
  for (let i = 0; i < 20 && requests.length < 2; i++) await new Promise(resolve => setTimeout(resolve, 10))
  assert.equal(window.__kkCapture.history.status, 'reading')
  assert.equal(window.__kkCapture.history.pages, 1)
  assert.equal(window.__kkCapture.history.messageCount, undefined)
  assert.equal(new URL(requests[1].url).searchParams.get('before'), 'older')
  assert.equal(requests[1].options.credentials, 'include')
  assert.equal(requests[1].options.headers.get('x-page-context'), 'fixture-context')
  releaseOlder(Response.json(older))
  for (let i = 0; i < 20 && window.__kkCapture.history.status !== 'finished'; i++) await new Promise(resolve => setTimeout(resolve, 10))
  assert.equal(JSON.stringify(window.__kkCapture).includes('fixture-context'), false)
  const result = window.__kkCapture.history
  assert.equal(result.pages, 2)
  assert.equal(result.stopReason, 'no-previous-page')
  assert.equal(result.branchStatus, 'root-reached')
  assert.deepEqual(Array.from(result.messages, item => item.text), ['Hello', 'Hi'])
})
