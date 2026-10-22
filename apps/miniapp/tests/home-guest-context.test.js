const { test } = require('node:test')
const assert = require('node:assert/strict')
const { fixture, plain } = require('./helpers/identity-fixture')
function publicApi(f, privateCode) {
  f.intercept((o, actor, route) => {
    if (route === '/sessions') { f.response(o, [{ id: 's', startDate: '2099-01-01', endDate: '2099-01-03', status: 'open', capacity: 20 }]); return false }
    if (route === '/readings/home') { f.response(o, { featured: [{ id: 'article' }], stories: [{ id: 'story' }] }); return false }
    if (route === '/camp-voices/s') { f.response(o, { contractVersion: 1, sessionId: 's', list: [{ id: 'voice', sessionId: 's', personKey: 'public-person', note: '公开心声。', excerpt: '公开心声。', author: '轻友' }], hasMore: false, nextCursor: null }); return false }
    if (route === '/me/overview' && privateCode) { f.response(o, null, privateCode); return false }
  })
}
test('cold guest reads public home context with no login or private request', async () => {
  const f = fixture({ cold: true }); publicApi(f)
  const context = await f.api.context(undefined, { allowGuest: true })
  assert.equal(context.state.loggedIn, false)
  assert.equal(context.activities[0].id, 's')
  assert.deepEqual(plain(context.state.registrations), {})
  assert.equal(f.loginCalls(), 0)
  assert(!f.requests.some(r => r.route === '/me/overview'))
})
test('expired login returns clean guest context on public home; private callers still reject', async () => {
  const f = fixture(); publicApi(f, 401)
  const context = await f.api.context(undefined, { allowGuest: true })
  assert.equal(context.state.loggedIn, false)
  assert.equal(context.state.customerId, '')
  assert.deepEqual(plain(context.state.registrations), {})
  assert.equal(context.state.phone, '')
  assert.equal(f.storage.has(f.key), false)
  assert.equal(f.loginCalls(), 0)
  f.switchActor('A')
  await assert.rejects(f.api.context(), error => error.code === 401)
})
test('real homepage renders public camps/readings after expired login with no login error', async () => {
  const f = fixture(); publicApi(f, 401)
  const p = f.page('home')
  await p.onShow()
  assert.equal(p.data.loadError, '')
  assert.equal(p.data.state.loggedIn, false)
  assert.equal(p.data.featured.id, 's')
  assert.equal(p.data.featuredReadings[0].id, 'article')
  assert.equal(p.data.stories[0].id, 'story')
  assert.equal(p.data.voiceError, '')
  assert.equal(p.data.voiceCards[0].id, 'voice')
  assert.equal(p.data.showHomeTask, false)
  assert.equal(f.toasts.length, 0)
  assert.equal(f.loginCalls(), 0)
  p.onHide()
})
test('public context does not disguise server errors as a successful guest response', async () => {
  const f = fixture(); publicApi(f, 500)
  await assert.rejects(f.api.context(undefined, { allowGuest: true }), error => error.code === 500)
  assert.equal(f.storage.has(f.key), true)
})
