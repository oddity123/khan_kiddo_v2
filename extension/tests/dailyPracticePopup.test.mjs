import {test} from 'node:test'
import assert from 'node:assert/strict'
import {mountDailyPractice} from '../src/connection/dailyPracticePopup.ts'

function setupDom() {
  const summary = {textContent: ''}
  const status = {textContent: '', className: 'status'}
  const injectBtn = {
    disabled: true,
    click: null,
    addEventListener(_name, cb) { this.click = cb },
  }
  const refreshBtn = {
    disabled: false,
    click: null,
    addEventListener(_name, cb) { this.click = cb },
  }
  const section = {
    classList: {
      hidden: true,
      add(name) { if (name === 'hidden') this.hidden = true },
      remove(name) { if (name === 'hidden') this.hidden = false },
    },
    querySelector(sel) {
      if (sel === '#dailyPracticeSummary') return summary
      if (sel === '#dailyPracticeStatus') return status
      if (sel === '#dailyPracticeInject') return injectBtn
      if (sel === '#dailyPracticeRefresh') return refreshBtn
      return null
    },
  }
  globalThis.document = {
    getElementById(id) {
      return id === 'dailyPracticeSection' ? section : null
    },
  }
  return {section, summary, status, injectBtn, refreshBtn}
}

test('loads daily practice when shown and rotates on refresh', async () => {
  const ui = setupDom()
  const messages = []
  let call = 0
  globalThis.chrome = {
    runtime: {
      sendMessage: async (msg) => {
        messages.push(msg)
        if (msg?.type === 'GET_DAILY_PRACTICE') {
          call += 1
          if (call === 1) {
            return {
              type: 'DAILY_PRACTICE',
              ok: true,
              empty: false,
              summary: '练：spend 漏 -ing · 1 个表达',
              prompt: 'PROMPT-1',
              goals: [{memoryId: 11, title: 'spend 漏 -ing'}],
              vocabulary: [{cardId: 'c1', front: 'a', back: 'b'}],
            }
          }
          assert.deepEqual(msg.excludeMemoryIds, [11])
          assert.deepEqual(msg.excludeCardIds, ['c1'])
          return {
            type: 'DAILY_PRACTICE',
            ok: true,
            empty: false,
            summary: '练：article · 0 个表达',
            prompt: 'PROMPT-2',
            goals: [{memoryId: 22, title: 'article'}],
            vocabulary: [],
          }
        }
        return {type: 'MARK_DAILY_PRACTICED_RESULT', ok: true}
      },
    },
    scripting: {
      executeScript: async () => [{result: {ok: true}}],
    },
  }

  const api = mountDailyPractice()
  api.show()
  await new Promise((r) => setImmediate(r))
  assert.equal(ui.section.classList.hidden, false)
  assert.equal(ui.summary.textContent, '练：spend 漏 -ing · 1 个表达')
  assert.equal(ui.injectBtn.disabled, false)

  await ui.refreshBtn.click()
  await new Promise((r) => setImmediate(r))
  assert.equal(ui.summary.textContent, '练：article · 0 个表达')
  assert.ok(messages.some((m) => m?.type === 'GET_DAILY_PRACTICE' && m.excludeMemoryIds?.[0] === 11))
})

test('inject marks practiced after successful fill', async () => {
  const ui = setupDom()
  const messages = []
  globalThis.chrome = {
    runtime: {
      sendMessage: async (msg) => {
        messages.push(msg)
        if (msg?.type === 'GET_DAILY_PRACTICE') {
          return {
            type: 'DAILY_PRACTICE',
            ok: true,
            summary: '练：x',
            prompt: 'PROMPT',
            goals: [{memoryId: 7}],
            vocabulary: [],
          }
        }
        if (msg?.type === 'MARK_DAILY_PRACTICED') {
          return {type: 'MARK_DAILY_PRACTICED_RESULT', ok: true}
        }
        return {ok: true}
      },
    },
    tabs: {query: async () => [{id: 3, url: 'https://chatgpt.com/'}]},
    scripting: {
      executeScript: async () => [{result: {ok: true}}],
    },
  }

  // requireActiveChatGptTab uses tabs.query
  const api = mountDailyPractice()
  api.show()
  await new Promise((r) => setImmediate(r))
  await ui.injectBtn.click()
  await new Promise((r) => setImmediate(r))
  assert.ok(messages.some((m) => m?.type === 'MARK_DAILY_PRACTICED' && m.memoryIds?.[0] === 7))
  assert.match(ui.status.textContent, /已填入/)
})
