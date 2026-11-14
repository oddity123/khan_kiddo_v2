import {test} from 'node:test'
import assert from 'node:assert/strict'
import {injectComposerText} from '../src/connection/inject.ts'

function setup({draft = '', textarea = false, path = '/', generating = false, acceptsInput = true} = {}) {
  const events = []
  class Textarea {
    disabled = false
    readOnly = false
    _value = draft
    get value() { return this._value }
    set value(value) { this._value = value }
    getClientRects() { return [1] }
    closest() { return null }
    focus() { events.push('focus') }
    dispatchEvent(event) { events.push(event.type) }
  }
  const editor = textarea ? new Textarea() : {
    innerText: draft, isContentEditable: true,
    getClientRects: () => [1], getAttribute: () => null, closest: () => null,
    focus() { events.push('focus') },
  }
  globalThis.getComputedStyle = () => ({visibility: 'visible'})
  globalThis.HTMLTextAreaElement = Textarea
  globalThis.location = new URL(`https://chatgpt.com${path}`)
  globalThis.window = {getSelection: () => ({removeAllRanges() {}, addRange() {}})}
  globalThis.document = {
    querySelector(selector) {
      if (selector === '[data-testid="stop-button"]') return generating ? {} : null
      throw new Error(`Unexpected selector: ${selector}`)
    },
    querySelectorAll: () => [editor],
    createRange: () => ({selectNodeContents() {}}),
    execCommand(command, _ui, text) {
      assert.equal(command, 'insertText')
      if (acceptsInput) { editor.innerText = text; events.push('native-input') }
      return acceptsInput
    },
  }
  return {editor, events}
}

test('fills an empty rich text composer through native editing without sending', () => {
  const ui = setup()
  assert.deepEqual(injectComposerText('Hello, let us practice English.'), {ok: true})
  assert.equal(ui.editor.innerText, 'Hello, let us practice English.')
  assert.deepEqual(ui.events, ['focus', 'native-input'])
})
test('preserves an existing draft', () => {
  const ui = setup({draft: 'My unfinished message'})
  assert.match(injectComposerText('Example').error, /已有草稿/)
  assert.equal(ui.editor.innerText, 'My unfinished message')
  assert.deepEqual(ui.events, [])
})
test('supports textarea and dispatches input for the page state', () => {
  const ui = setup({textarea:true})
  assert.equal(injectComposerText('Example').ok, true)
  assert.equal(ui.editor.value, 'Example')
  assert.deepEqual(ui.events, ['focus', 'input'])
})
test('rejects share pages, active generation, and an editor refusing native input', () => {
  setup({path:'/share/example'})
  assert.equal(injectComposerText('Example').ok, false)
  setup({generating:true})
  assert.match(injectComposerText('Example').error, /回复结束/)
  setup({acceptsInput:false})
  assert.equal(injectComposerText('Example').ok, false)
})


test('new ChatGPT shell: skips hidden textbox and fills the visible textbox without an ID', () => {
  const ui = setup()
  const hidden = {...ui.editor, innerText: 'Hidden draft', getClientRects: () => []}
  document.querySelectorAll = selector => {
    assert.ok(selector.includes('[role="textbox"][contenteditable="true"]'))
    return [hidden, ui.editor]
  }
  assert.equal(injectComposerText('Example').ok, true)
  assert.equal(ui.editor.innerText, 'Example')
  assert.equal(hidden.innerText, 'Hidden draft')
})

test('multiple visible editors are rejected without modifying either draft', () => {
  const ui = setup()
  const other = {...ui.editor}
  document.querySelectorAll = () => [ui.editor, other]
  assert.match(injectComposerText('Example').error, /多个可见输入框/)
  assert.equal(ui.editor.innerText, '')
  assert.equal(other.innerText, '')
  assert.deepEqual(ui.events, [])
})

test('no visible editor or a hidden ancestor is rejected', () => {
  const ui = setup()
  ui.editor.closest = () => ({})
  assert.match(injectComposerText('Example').error, /未找到输入框/)
  ui.editor.closest = () => null
  globalThis.getComputedStyle = () => ({visibility:'hidden'})
  assert.match(injectComposerText('Example').error, /未找到输入框/)
  assert.deepEqual(ui.events, [])
})
