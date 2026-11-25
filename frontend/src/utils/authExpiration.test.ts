import assert from 'node:assert/strict'
import {test} from 'node:test'

import {LOGIN_REQUIRED_MESSAGE, SESSION_EXPIRED_MESSAGE} from '../constants/auth.ts'
import {
  buildLoginRedirectLocation,
  computeServerClockOffsetMs,
  isCredentialAuthEndpoint,
  isTokenExpired,
  parseJwtExpMs,
  resolveUnauthorizedMessage,
  shouldHandleUnauthorizedResponse,
} from './authExpiration.ts'

function encodeBase64Url(obj: Record<string, unknown>): string {
  const bytes = new TextEncoder().encode(JSON.stringify(obj))
  let binary = ''
  for (const byte of bytes) {
    binary += String.fromCharCode(byte)
  }
  return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/g, '')
}

function makeFakeJwt(payload: Record<string, unknown>): string {
  return `${encodeBase64Url({ alg: 'HS256', typ: 'JWT' })}.${encodeBase64Url(payload)}.sig`
}

test('isCredentialAuthEndpoint matches login and register regardless of origin or query', () => {
  assert.equal(isCredentialAuthEndpoint('/api/auth/login'), true)
  assert.equal(isCredentialAuthEndpoint('https://example.com/api/auth/login?next=1'), true)
  assert.equal(isCredentialAuthEndpoint('/api/auth/register'), true)
  assert.equal(isCredentialAuthEndpoint('/api/auth/me'), false)
  assert.equal(isCredentialAuthEndpoint('/api/conversation/analyses'), false)
  assert.equal(isCredentialAuthEndpoint(undefined), false)
})

test('shouldHandleUnauthorizedResponse filters non-401, login endpoints, and skipped requests', () => {
  assert.equal(shouldHandleUnauthorizedResponse({ status: 401, url: '/api/conversation/analyses' }), true)
  assert.equal(shouldHandleUnauthorizedResponse({ status: 401, url: '/api/auth/login' }), false)
  assert.equal(shouldHandleUnauthorizedResponse({ status: 401, url: '/api/auth/register' }), false)
  assert.equal(
    shouldHandleUnauthorizedResponse({ status: 401, url: '/api/auth/me', skipAuthExpiredHandler: true }),
    false,
  )
  assert.equal(shouldHandleUnauthorizedResponse({ status: 403, url: '/api/conversation/analyses' }), false)
  assert.equal(shouldHandleUnauthorizedResponse({ status: 500, url: '/api/conversation/analyses' }), false)
})

test('resolveUnauthorizedMessage distinguishes expired session, guest, and specific backend errors', () => {
  assert.equal(resolveUnauthorizedMessage('未登录', true), SESSION_EXPIRED_MESSAGE)
  assert.equal(resolveUnauthorizedMessage(undefined, true), SESSION_EXPIRED_MESSAGE)
  assert.equal(resolveUnauthorizedMessage('未登录', false), LOGIN_REQUIRED_MESSAGE)
  assert.equal(resolveUnauthorizedMessage('账号已禁用', true), '账号已禁用')
})

test('buildLoginRedirectLocation preserves full path and avoids redirecting on auth pages', () => {
  assert.deepEqual(
    buildLoginRedirectLocation('/conversation/analyses', '/conversation/analyses?page=2'),
    { path: '/login', query: { redirect: '/conversation/analyses?page=2' } },
  )
  assert.equal(buildLoginRedirectLocation('/login', '/login?redirect=/review'), null)
  assert.equal(buildLoginRedirectLocation('/register', '/register'), null)
  assert.deepEqual(buildLoginRedirectLocation('/review', '//evil.example'), {
    path: '/login',
    query: { redirect: '/' },
  })
})

test('parseJwtExpMs decodes standard JWT payload including non-ASCII claims', () => {
  const expSec = 1_800_000_000
  const token = makeFakeJwt({ sub: '1', username: '测试用户', exp: expSec })
  assert.equal(parseJwtExpMs(token), expSec * 1000)
  assert.equal(parseJwtExpMs('invalid.token'), null)
  assert.equal(parseJwtExpMs(makeFakeJwt({ sub: '1' })), null)
  assert.equal(parseJwtExpMs(null), null)
})

test('computeServerClockOffsetMs calibrates skew with Date header and guards fast local clocks', () => {
  const verifiedAtMs = Date.parse('2026-11-25T08:00:00Z')
  const serverDate = 'Mon, 20 Apr 2026 08:00:00 GMT'
  const serverMs = Date.parse(serverDate)
  const expMs = serverMs + 7 * 24 * 3600 * 1000

  // With Date header, skewed local clock is calibrated accurately
  const calibrated = computeServerClockOffsetMs({
    expMs,
    verifiedAtMs,
    serverDateHeader: serverDate,
  })
  assert.equal(calibrated, serverMs - verifiedAtMs)

  const token = makeFakeJwt({ sub: '1', exp: Math.floor(expMs / 1000) })
  assert.equal(isTokenExpired(token, verifiedAtMs, calibrated), false)
  assert.equal(isTokenExpired(token, verifiedAtMs + 7 * 24 * 3600 * 1000, calibrated), true)

  // Without Date header when local clock is already past exp, disable client-side cutoff
  const uncalibratedFastClock = computeServerClockOffsetMs({
    expMs,
    verifiedAtMs,
    serverDateHeader: null,
  })
  assert.equal(uncalibratedFastClock, null)
  assert.equal(isTokenExpired(token, verifiedAtMs + 1000, uncalibratedFastClock), false)

  // Without Date header when local clock is behind exp, normal local expiry works
  const normalOffset = computeServerClockOffsetMs({
    expMs: verifiedAtMs + 60_000,
    verifiedAtMs,
    serverDateHeader: null,
  })
  assert.equal(normalOffset, 0)
  const shortToken = makeFakeJwt({ sub: '1', exp: Math.floor((verifiedAtMs + 60_000) / 1000) })
  assert.equal(isTokenExpired(shortToken, verifiedAtMs + 59_000, normalOffset), false)
  assert.equal(isTokenExpired(shortToken, verifiedAtMs + 60_000, normalOffset), true)
})
