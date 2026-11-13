import {test} from 'node:test'
import assert from 'node:assert/strict'
import {inspectConversationPage} from '../src/connection/pageAdapter.ts'

function page({path = '/c/example', messages = [], generating = false, editor = null} = {}) {
  globalThis.location = new URL(`https://chatgpt.com${path}`)
  globalThis.window = {top: null}
  globalThis.getComputedStyle = () => ({visibility: 'visible'})
  globalThis.document = {
    querySelectorAll: selector => selector.includes('contenteditable') || selector === 'textarea' ? editor ? [editor] : [] : messages,
    querySelector: selector => selector === '#prompt-textarea' ? editor
      : selector === '[data-message-author-role]' ? messages[0] ?? null
      : selector === '[data-testid="stop-button"]' && generating ? {} : null,
  }
}
function message(role, text, hidden = false) {
  return {
    dataset: {messageAuthorRole: role, messageId: text},
    closest: () => hidden ? {} : null,
    getClientRects: () => [1], querySelector: () => null, innerText: text,
  }
}
test('preserves rendered role/order and never claims completeness, including transcript text', () => {
  page({messages: [message('user', 'Voice transcript'), message('assistant', 'Reply'), message('tool', 'excluded')]})
  const result = inspectConversationPage()
  assert.deepEqual(result.messages.map(m => m.role), ['user', 'assistant'])
  assert.equal(result.completeness, 'unverified')
  assert.equal(result.conversationId, 'example')
})
test('hidden alternate branches excluded; live generation reported', () => {
  page({messages: [message('assistant', 'old branch', true), message('assistant', 'new branch')], generating: true})
  assert.deepEqual(inspectConversationPage().messages.map(m => m.text), ['new branch'])
  assert.equal(inspectConversationPage().generating, true)
})
test('empty/partially rendered page is not complete', () => {
  page()
  assert.equal(inspectConversationPage().completeness, 'unverified')
  assert.equal(inspectConversationPage().messages.length, 0)
})
test('SPA navigation reads current identity; share route is not a conversation', () => {
  page({path: '/c/first'})
  assert.equal(inspectConversationPage().conversationId, 'first')
  page({path: '/c/second'})
  assert.equal(inspectConversationPage().conversationId, 'second')
  page({path: '/share/second'})
  assert.equal(inspectConversationPage().conversationId, null)
})
test('new shell extracts user bubbles and assistant bodies in DOM order, including nested response text', () => {
  page({path: '/g/project/c/conversation'})
  const nodes = [
    {role: 'user', text: 'Yesterday I went swimming.'},
    {role: 'assistant', text: 'Great!\nTell me more.'},
    {role: 'user', text: 'Voice transcript'},
  ].map(({role, text}) => ({
    dataset: {}, innerText: text,
    hasAttribute: name => name === 'data-user-message-bubble' && role === 'user',
    closest: () => null, getClientRects: () => [1],
  }))
  document.querySelectorAll = selector => selector.startsWith('[data-chatgpt-conversation-selection-target]') ? nodes : []
  const result = inspectConversationPage()
  assert.equal(result.adapter, 'conversation-selection')
  assert.deepEqual(result.messages.map(m => m.role), ['user', 'assistant', 'user'])
  assert.equal(result.messages[1].text, 'Great!\nTell me more.')
  assert.equal(result.conversationId, 'conversation')
  assert.equal(result.completeness, 'unverified')
})

test('turn keys keep identical voice acknowledgements distinct when message IDs are absent', () => {
  page({path: '/c/voice'})
  const nodes = ['turn-1', 'turn-2'].map(key => ({
    dataset: {}, innerText: 'Mm-hmm.', hasAttribute: () => false,
    closest: selector => selector === '[data-turn-key]' ? {getAttribute: () => key} : null,
    getClientRects: () => [1],
  }))
  document.querySelectorAll = selector => selector.startsWith('[data-chatgpt-conversation-selection-target]') ? nodes : []
  assert.deepEqual(inspectConversationPage().messages.map(m => m.messageId), ['turn-1:assistant', 'turn-2:assistant'])
})
