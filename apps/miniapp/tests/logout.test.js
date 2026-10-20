const { test } = require('node:test')
const assert = require('node:assert/strict')
const { fixture, tick } = require('./helpers/identity-fixture')

test('logout revokes the current token, notifies identity consumers and preserves other storage', async () => {
  const f = fixture(), old = f.api.identityScope()
  f.storage.set('unrelated-preference', 'keep')
  f.storage.set('qinglife_business_token:https://other.invalid', 'other-token')
  const local = JSON.stringify(f.store.getState()); let changed = 0
  f.api.watchIdentity(() => changed++)
  f.intercept((o, actor, route) => {
    if (route === '/auth/logout') { assert.equal(actor, 'A'); assert.equal(o.method, 'POST'); f.response(o, null); return false }
    if (route === '/sessions') { f.response(o, []); return false }
  })
  await f.api.logout()
  assert.equal(f.storage.has(f.key), false)
  assert.equal(changed, 1); assert.throws(() => f.api.assertIdentity(old), /身份已变化/)
  assert.equal(f.storage.get('unrelated-preference'), 'keep')
  assert.equal(f.storage.get('qinglife_business_token:https://other.invalid'), 'other-token')
  assert.equal(JSON.stringify(f.store.getState()), local)
  assert.equal((await f.api.context()).state.loggedIn, false)
  assert.equal(f.loginCalls(), 0)
})

test('offline logout clears local identity but reports that remote revocation failed', async () => {
  const f = fixture()
  f.intercept((o, actor, route) => { if (route === '/auth/logout') { o.fail({ errMsg: 'offline' }); return false } })
  await assert.rejects(f.api.logout(), /offline/)
  assert.equal(f.storage.has(f.key), false); assert.equal(f.api.identityScope().token, '')
  assert.equal(f.loginCalls(), 0)
})

test('an already expired logout remains logged out without a new automatic login', async () => {
  const f = fixture()
  f.intercept((o, actor, route) => { if (route === '/auth/logout') { f.response(o, null, 401); return false } })
  await f.api.logout()
  assert.equal(f.storage.has(f.key), false); assert.equal(f.loginCalls(), 0)
})

test('logout cancels an outstanding native login even when no token exists yet', async () => {
  const f = fixture({ cold: true }); let nativeLogin
  f.wx.login = o => { nativeLogin = o }
  const pending = f.api.login(), rejected = assert.rejects(pending, /身份已变化/)
  await f.api.logout(); nativeLogin.success({ code: 'synthetic-delayed' }); await rejected
  assert.equal(f.storage.has(f.key), false); assert.equal(f.requests.length, 0)
  f.wx.login = o => o.success({ code: 'synthetic-explicit' })
  await f.api.login(); assert.equal(f.storage.get(f.key), 'synthetic-A')
})

test('private reads started before logout cannot return the old owner afterwards', async () => {
  const f = fixture(); let delayed
  f.intercept((o, actor, route) => {
    if (route === '/me/overview') { delayed = o; return false }
    if (route === '/auth/logout') { f.response(o, null); return false }
  })
  const pending = f.api.privateOverview(), rejected = assert.rejects(pending, /身份已变化/)
  await tick(); await f.api.logout()
  f.response(delayed, { customerId: 'A', profile: { nickname: 'OLD_PRIVATE_NAME' } }); await rejected
  assert.equal(f.storage.has(f.key), false)
})

test('duplicate logout shares one request and an old logout cannot clear a replacement identity', async () => {
  const f = fixture(); let delayed
  f.intercept((o, actor, route) => { if (route === '/auth/logout') { delayed = o; return false } })
  const first = f.api.logout(); assert.equal(f.api.logout(), first)
  const rejected = assert.rejects(first, /身份已变化/); await tick()
  f.switchActor('B'); f.response(delayed, null); await rejected
  assert.equal(f.storage.get(f.key), 'synthetic-B')
  assert.equal(f.requests.filter(r => r.route === '/auth/logout').length, 1)
})
