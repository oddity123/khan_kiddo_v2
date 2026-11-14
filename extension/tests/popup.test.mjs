import {test} from 'node:test'
import assert from 'node:assert/strict'
import {mountConnectionProbe} from '../src/connection/popup.ts'

function setup() {
  const nodes = Object.fromEntries(['historyStatus', 'historyText', 'copyHistory', 'readHistory'].map(id => [id, {
    textContent: '', disabled: false,
    addEventListener(_name, callback) { this.click = callback },
  }]))
  const timers = new Map()
  let nextTimer = 0
  let result
  let failure
  let tab = {id: 7, url: 'https://chatgpt.com/c/example'}
  const calls = []
  globalThis.document = {getElementById: () => ({classList: {remove() {}}, querySelector: selector => nodes[selector.slice(1)]})}
  globalThis.window = {
    setTimeout(callback) { timers.set(++nextTimer, callback); return nextTimer },
    clearTimeout(id) { timers.delete(id) }, addEventListener() {},
  }
  globalThis.chrome = {
    tabs: {query: async () => [tab], reload: async id => { calls.push(['reload', id]) }},
    scripting: {executeScript: async ({func}) => {
      if (func.toString().includes('sessionStorage')) { calls.push(['marker']); return [{result: true}] }
      if (failure) throw new Error(failure)
      return [{result}]
    }},
  }
  return {
    nodes, timers, calls,
    setResult(value) { result = value },
    setFailure(value) { failure = value },
    switchConversation() { tab = {...tab, url: 'https://chatgpt.com/c/other'} },
    async tick() {
      const [id, callback] = timers.entries().next().value
      timers.delete(id)
      callback()
      await new Promise(resolve => setImmediate(resolve))
    },
  }
}

test('click resumes polling through navigation, then shows progress and result without reopening', async () => {
  const ui = setup()
  mountConnectionProbe()
  await new Promise(resolve => setImmediate(resolve))
  await ui.nodes.readHistory.click()
  assert.deepEqual(ui.calls, [['marker'], ['reload', 7]])
  assert.equal(ui.nodes.readHistory.disabled, true)
  assert.equal(ui.timers.size, 1)
  ui.setFailure('Frame was removed')
  await ui.tick()
  assert.equal(ui.timers.size, 1)
  assert.equal(ui.nodes.readHistory.disabled, true)
  ui.setFailure(undefined)
  await ui.tick() // Capture not installed yet after navigation.
  assert.equal(ui.timers.size, 1)
  ui.setResult({conversationId: 'example', status: 'reading', pages: 2})
  await ui.tick()
  assert.equal(ui.nodes.historyStatus.textContent, '已读取 2 页…')
  ui.setResult({conversationId: 'example', status: 'finished', messageCount: 1, messages: [{role:'user', text:'Hello'}]})
  await ui.tick()
  assert.equal(ui.nodes.historyText.textContent, 'User: Hello')
  assert.equal(ui.nodes.copyHistory.disabled, false)
  assert.equal(ui.nodes.readHistory.disabled, false)
  assert.equal(ui.timers.size, 0)
})

test('navigation to another conversation stops polling and permits retry', async () => {
  const ui = setup()
  mountConnectionProbe()
  await new Promise(resolve => setImmediate(resolve))
  await ui.nodes.readHistory.click()
  ui.switchConversation()
  await ui.tick()
  assert.match(ui.nodes.historyStatus.textContent, /对话已切换/)
  assert.equal(ui.nodes.readHistory.disabled, false)
  assert.equal(ui.timers.size, 0)
})

test('capture startup timeout stops polling and permits retry', async () => {
  const ui = setup()
  const originalNow = Date.now
  try {
    Date.now = () => 1000
    mountConnectionProbe()
    await new Promise(resolve => setImmediate(resolve))
    await ui.nodes.readHistory.click()
    Date.now = () => 17000
    await ui.tick()
    assert.match(ui.nodes.historyStatus.textContent, /未能启动读取/)
    assert.equal(ui.nodes.readHistory.disabled, false)
    assert.equal(ui.timers.size, 0)
  } finally { Date.now = originalNow }
})
