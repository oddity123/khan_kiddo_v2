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

test('follows has_next_page and advances past stale current_node to newest descendant', async () => {
  const message = (id, parent_id, role, text, create_time) => ({
    id, create_time, metadata: {parent_id}, author: {role}, content: {text: '', parts: [text]},
  })
  const first = {
    current_node: 'm2',
    messages: [
      message('m1', null, 'user', 'Turn 1', 1),
      message('m2', 'm1', 'assistant', 'Reply 1', 2),
    ],
    page_info: {has_previous_page: false, has_next_page: true, end_cursor: 'after-m2'},
  }
  const newer = {
    messages: [
      message('m3', 'm2', 'user', 'Turn 2', 3),
      message('m4', 'm3', 'assistant', 'Reply 2', 4),
    ],
    page_info: {has_previous_page: true, has_next_page: false},
  }
  const requests = []
  const window = {fetch: async (input, options = {}) => {
    const url = String(input)
    requests.push({url, options})
    if (url.includes('/api/auth/session')) return Response.json({accessToken: 'tok-123'})
    if (url.includes('after=after-m2')) return Response.json(newer)
    return Response.json(first)
  }}
  runInNewContext(`(${installResponseCapture.toString()})()`, {
    window, location: new URL('https://chatgpt.com/c/test'), URL, Request, Headers, AbortSignal, setTimeout: () => {},
  })
  window.__kkStartHistoryCapture('test')
  for (let i = 0; i < 30 && window.__kkCapture.history.status !== 'finished'; i++) {
    await new Promise(resolve => setTimeout(resolve, 10))
  }
  const result = window.__kkCapture.history
  assert.equal(result.pages, 2)
  assert.deepEqual(Array.from(result.messages, item => item.text), ['Turn 1', 'Reply 1', 'Turn 2', 'Reply 2'])
  assert.ok(requests.every(req => req.options.cache === 'no-store'))
})

test('supplements unpersisted tail turns from visible ChatGPT DOM without duplicating API messages', async () => {
  const message = (id, parent_id, role, text) => ({id, metadata: {parent_id}, author: {role}, content: {parts: [text]}})
  const first = {
    current_node: 'm2',
    messages: [
      message('m1', null, 'user', 'Let us practice.'),
      message('m2', 'm1', 'assistant', 'Next tiny situation: say that in one sentence.'),
    ],
    page_info: {has_previous_page: false, has_next_page: false},
  }
  const makeEl = (role, id, text) => ({
    getAttribute(name) {
      if (name === 'data-message-author-role') return role
      if (name === 'data-message-id') return id
      return null
    },
    querySelectorAll() { return [{innerText: text}] },
    innerText: text,
  })
  const domNodes = [
    makeEl('user', 'm1', 'Let us practice.'),
    makeEl('assistant', 'm2', 'Next tiny situation: say that in one sentence.'),
    makeEl('user', 'm3', 'I tasted the food and felt embarrassed'),
    makeEl('assistant', 'm4', 'Yep, clear and smooth.'),
  ]
  const document = {querySelectorAll: () => domNodes}
  const window = {fetch: async (input) => {
    if (String(input).includes('/api/auth/session')) return Response.json({accessToken: 'tok-123'})
    return Response.json(first)
  }}
  runInNewContext(`(${installResponseCapture.toString()})()`, {
    window, document, location: new URL('https://chatgpt.com/c/test'), URL, Request, Headers, AbortSignal, setTimeout: () => {},
  })
  window.__kkStartHistoryCapture('test')
  for (let i = 0; i < 30 && window.__kkCapture.history.status !== 'finished'; i++) {
    await new Promise(resolve => setTimeout(resolve, 10))
  }
  const result = window.__kkCapture.history
  assert.deepEqual(Array.from(result.messages, item => `${item.role}: ${item.text}`), [
    'user: Let us practice.',
    'assistant: Next tiny situation: say that in one sentence.',
    'user: I tasted the food and felt embarrassed',
    'assistant: Yep, clear and smooth.',
  ])
})
