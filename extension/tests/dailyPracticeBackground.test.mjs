import {test} from 'node:test'
import assert from 'node:assert/strict'
import {
  PENDING_PRACTICE_SESSIONS_KEY,
  bindPendingPracticeSession,
  clearPendingPracticeSessions,
  markDailyPracticed,
} from '../src/background/dailyPractice.ts'

function setupMockEnv() {
  const store = {
    'kk.extension.credential': {
      token: 'kk_ext_testtoken',
      username: 'tester',
      expiresAt: '2026-12-31T00:00:00Z',
      connectedAt: '2026-11-23T00:00:00Z',
    },
  }
  const requests = []

  globalThis.chrome = {
    storage: {
      local: {
        get: async (key) => {
          if (typeof key === 'string') {
            return key in store ? {[key]: store[key]} : {}
          }
          return {...store}
        },
        set: async (obj) => {
          Object.assign(store, obj)
        },
        remove: async (key) => {
          delete store[key]
        },
      },
    },
  }

  globalThis.fetch = async (url, init = {}) => {
    requests.push({url: String(url), init})
    if (String(url).endsWith('/api/practice/daily/mark-practiced')) {
      return {
        ok: true,
        status: 200,
        text: async () => JSON.stringify({sessionIds: [101, 102]}),
      }
    }
    if (String(url).endsWith('/api/practice/sessions/bind-conversation')) {
      return {
        ok: true,
        status: 200,
        text: async () => JSON.stringify({boundCount: 2}),
      }
    }
    return {ok: true, status: 200, text: async () => '{}'}
  }

  return {store, requests}
}

test('saves pending practice sessions when injected on new chat tab and binds before analysis', async () => {
  const {store, requests} = setupMockEnv()

  const markRes = await markDailyPracticed('http://localhost:8080', {
    memoryIds: [11, 22],
    sourceConversationId: null,
    tabId: 42,
    practiceType: 'SCENARIO',
  })

  assert.equal(markRes.ok, true)
  assert.deepEqual(markRes.sessionIds, [101, 102])
  assert.ok(store[PENDING_PRACTICE_SESSIONS_KEY]['42'])
  assert.deepEqual(store[PENDING_PRACTICE_SESSIONS_KEY]['42'].sessionIds, [101, 102])

  await bindPendingPracticeSession('http://localhost:8080', 42, 'conv-new-99')

  const bindReq = requests.find((r) => r.url.endsWith('/api/practice/sessions/bind-conversation'))
  assert.ok(bindReq)
  assert.deepEqual(JSON.parse(String(bindReq.init.body)), {
    sessionIds: [101, 102],
    sourceConversationId: 'conv-new-99',
  })
  assert.equal(store[PENDING_PRACTICE_SESSIONS_KEY]['42'], undefined)
})

test('drops expired pending sessions and clears on revoke', async () => {
  const {store, requests} = setupMockEnv()
  store[PENDING_PRACTICE_SESSIONS_KEY] = {
    '8': {
      sessionIds: [300],
      injectedAt: new Date(Date.now() - 13 * 60 * 60 * 1000).toISOString(),
    },
    '9': {
      sessionIds: [301],
      injectedAt: new Date().toISOString(),
    },
  }

  await bindPendingPracticeSession('http://localhost:8080', 8, 'conv-old')
  assert.equal(requests.length, 0)

  await clearPendingPracticeSessions()
  assert.equal(store[PENDING_PRACTICE_SESSIONS_KEY], undefined)
})
