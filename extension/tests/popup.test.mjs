import {test} from 'node:test'
import assert from 'node:assert/strict'
import {mountConnectionProbe} from '../src/connection/popup.ts'

function setup() {
  const nodes = Object.fromEntries(
    ['historyStatus', 'historyTaskBadge', 'historyText', 'copyHistory', 'analyzeHistory'].map(id => [id, {
      textContent: id === 'analyzeHistory' ? '一键分析' : '',
      disabled: id === 'copyHistory',
      addEventListener(_name, callback) { this.click = callback },
      classList: {add() {}, remove() {}, toggle() {}},
    }]),
  )
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
    tabs: {query: async () => [tab]},
    runtime: {sendMessage: async () => ({type: 'ANALYSIS_TASK_STATUS', running: false})},
    scripting: {executeScript: async ({files, func, args}) => {
      if (files) { calls.push(['inject-capture']); return [{}] }
      if (func && func.toString().includes('__kkStartHistoryCapture')) {
        calls.push(['start', args?.[0]])
        return [{result: true}]
      }
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

test('one-click analyze starts capture without reload, then shows progress and result', async () => {
  const ui = setup()
  mountConnectionProbe()
  await new Promise(resolve => setImmediate(resolve))
  await ui.nodes.analyzeHistory.click()
  assert.deepEqual(ui.calls, [['inject-capture'], ['start', 'example']])
  assert.equal(ui.nodes.analyzeHistory.disabled, true)
  assert.equal(ui.timers.size, 1)
  ui.setFailure('Frame was removed')
  await ui.tick()
  assert.match(ui.nodes.historyStatus.textContent, /Frame was removed|失败|Error/i)
  assert.equal(ui.nodes.analyzeHistory.disabled, false)
})

test('successful poll shows reading progress then finished transcript and submits', async () => {
  const ui = setup()
  const messages = []
  globalThis.chrome.runtime.sendMessage = async (msg) => {
    messages.push(msg)
    if (msg?.type === 'SUBMIT_ANALYSIS') {
      return {type: 'SUBMIT_ANALYSIS_RESULT', ok: true, status: 'QUEUED', taskId: 't1'}
    }
    return {type: 'ANALYSIS_TASK_STATUS', running: false}
  }
  mountConnectionProbe()
  await new Promise(resolve => setImmediate(resolve))
  await ui.nodes.analyzeHistory.click()
  ui.setResult({conversationId: 'example', status: 'reading', pages: 2})
  await ui.tick()
  assert.equal(ui.nodes.historyStatus.textContent, '已读取 2 页…')
  ui.setResult({conversationId: 'example', status: 'finished', messageCount: 1, messages: [{role:'user', text:'Hello'}]})
  await ui.tick()
  assert.equal(ui.nodes.historyText.textContent, 'User: Hello')
  assert.equal(ui.nodes.copyHistory.disabled, false)
  await new Promise(resolve => setImmediate(resolve))
  assert.ok(messages.some(m => m?.type === 'SUBMIT_ANALYSIS'))
  assert.match(ui.nodes.historyStatus.textContent, /正在分析中/)
  assert.equal(ui.nodes.historyTaskBadge.textContent, '分析中')
})

test('navigation to another conversation stops polling and permits retry', async () => {
  const ui = setup()
  mountConnectionProbe()
  await new Promise(resolve => setImmediate(resolve))
  await ui.nodes.analyzeHistory.click()
  ui.switchConversation()
  await ui.tick()
  assert.match(ui.nodes.historyStatus.textContent, /对话已切换/)
  assert.equal(ui.nodes.analyzeHistory.disabled, false)
  assert.equal(ui.timers.size, 0)
})
