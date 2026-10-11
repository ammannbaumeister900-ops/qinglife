const { test } = require('node:test'), assert = require('node:assert/strict')
const { fixture, tick } = require('./helpers/identity-fixture')
for (const environment of ['staging', 'production']) {
  test(environment + ': settings, direct auth and auth.next cannot run local fake association', async () => {
    for (const next of ['', 'register', 'daily', 'records', 'experiences', 'experience', 'archive', 'https://invalid.example']) {
      const f = fixture({ runtime: { environment } }), p = f.page('mine-flow', 'auth', { next, id: 'exact-session' })
      assert.equal(p.data.view, 'account'); assert.equal(p.data.demoMode, false)
      const before = JSON.stringify([...f.storage]); p.setData({ phoneInput: '19900000009', codeInput: '123456', agreement: true })
      await p.sendCode(); await p.login(); await tick()
      assert.equal(p.data.codeSent, false); assert.equal(JSON.stringify([...f.storage]), before)
      assert.equal(f.modals.length, 0); assert.equal(f.toasts.some(t => /关联成功/.test(t)), false)
      assert.equal(f.nav.length, 0); assert.equal(p.data.account.nickname, 'A')
      const privacy = f.page('mine-flow', 'privacy'); privacy.refresh(); await privacy.openAccount()
      assert.equal(privacy.data.view, 'account'); assert.equal(privacy.data.account.nickname, 'A')
    }
  })
  test(environment + ': old experience routes preserve the precise ID and contain no Demo private content', () => {
    for (const view of ['experiences', 'experience', 'records']) {
      const f = fixture({ runtime: { environment } }), p = f.page('mine-flow', view, { id: 'session /精确' })
      assert.equal(p.data.currentExperience, null); assert.equal(p.data.experienceTabs.length, 0)
      assert.equal(f.nav[0], '/pages/personal/index?view=' + view + '&id=' + encodeURIComponent('session /精确'))
    }
    const f = fixture({ runtime: { environment } }), p = f.page('mine-flow', 'unknown')
    assert.equal(p.data.view, 'unavailable'); assert.equal(p.data.currentExperience, null)
  })
}
test('formal environments with missing configuration fail closed instead of redirecting to a Demo fallback', async () => {
  for (const environment of ['staging', 'production', 'invalid']) {
    const f = fixture({ runtime: { environment, businessBaseUrl: '' }, cold: true })
    const legacy = f.page('mine-flow', 'experience', { id: 'exact' })
    assert.equal(legacy.data.view, 'unavailable'); assert.equal(f.nav.length, 0)
    const p = f.page('mine-flow', 'auth'); await p.onShow(); assert.equal(p.data.account, null)
    await p.sendCode(); await p.login(); assert.equal(f.modals.length, 0)
    const daily = f.page(); await daily.onShow(); assert.equal(daily.data.dailyReady, false)
    assert.equal(daily.data.dailyNote, ''); assert.equal(f.requests.length, 0)
  }
})
test('account phone is read from the server without inferring linkage from a local flag or customer ID', async () => {
  const f = fixture(), p = f.page('mine-flow', 'auth'); await p.onShow()
  assert.equal(p.data.account.phone, '19900000001'); f.switchActor('B')
  assert.equal(p.data.account, null); await p.onShow(); assert.equal(p.data.account.phone, '')
  assert.equal(p.data.account.nickname, 'B'); assert.equal(f.requests.every(r => r.method === 'GET'), true)
})
test('late account read and expired credentials never resurrect the previous account', async () => {
  const f = fixture(), p = f.page('mine-flow', 'auth'); let delayed
  f.intercept((o, actor, route) => { if (actor === 'A' && route === '/me/overview') { delayed = o; return false } })
  const pending = p.onShow(); await tick(); f.switchActor('B'); await p.onShow()
  f.response(delayed, { customerId: 'A', profile: { phone: '19900000001' } }); await pending
  assert.equal(p.data.account.nickname, 'B'); p.onHide(); assert.equal(p.data.account, null)
})
test('explicit Demo retains the local workflow with truthful labels and no server request', async () => {
  const f = fixture({ runtime: { environment: 'demo', businessBaseUrl: '' }, cold: true }), p = f.page('mine-flow', 'auth', { next: 'daily' })
  p.refresh(); assert.equal(p.data.demoMode, true); assert.equal(p.data.view, 'auth')
  p.setData({ phoneInput: '19900000009', codeInput: '123456', agreement: true }); p.sendCode(); p.login()
  assert.match(f.modals[0].content, /演示.*不会发送短信/); assert.equal(f.toasts.at(-1), '本机演示状态已更新')
  assert.equal(f.store.getState().phone, '19900000009'); assert.equal(f.nav.at(-1), '/pages/friend-flow/index?view=daily')
  assert.equal(f.requests.length, 0)
  const daily = f.page(); daily.onShow(); daily.setData({ dailyChoice: 'rest', dailyNote: 'DEMO_ONLY' }); await daily.saveDaily()
  assert.equal(Object.values(f.store.getState().dailyRecords)[0].note, 'DEMO_ONLY')
})

test('default formal route is account information and failed compatibility redirects stay unavailable', async () => {
  const f = fixture(), p = f.page('mine-flow', undefined)
  // The helper's default is daily; explicitly exercise the route without a view.
  p.onLoad({ next: 'daily' }); assert.equal(p.data.view, 'account'); await p.onShow()
  assert.equal(p.data.account.nickname, 'A')
  f.wx.redirectTo = o => o.fail({ errMsg: 'route failure' })
  const legacy = f.page('mine-flow', 'experience', { id: 'exact' })
  assert.equal(legacy.data.view, 'unavailable'); assert.equal(legacy.data.currentExperience, null)
})
test('expired account identity clears display and explicit retry uses the existing WeChat login', async () => {
  const f = fixture(), p = f.page('mine-flow', 'auth'); await p.onShow()
  f.intercept((o, actor, route) => { if (route === '/me/overview') { f.response(o, null, 401); return false } })
  await p.loadAccount(); assert.equal(p.data.account, null); assert.equal(f.storage.has(f.key), false)
  f.intercept(null); await p.readAccount(); assert.equal(p.data.account.nickname, 'A'); assert.equal(f.loginCalls(), 1)
})

test('formal settings and compatibility state never carry hidden Demo private text', () => {
  const f = fixture()
  f.store.updateState(s => { s.dailyRecords.old = { note: 'UNOWNED_DAILY' }; s.experienceReviews = { old: { note: 'UNOWNED_EXPERIENCE' } }; return s })
  for (const view of ['privacy', 'collections', 'unavailable']) {
    const p = f.page('mine-flow', view); p.refresh()
    assert.equal(JSON.stringify(p.data).includes('UNOWNED_DAILY'), false)
    assert.equal(JSON.stringify(p.data).includes('UNOWNED_EXPERIENCE'), false)
  }
  assert.equal(f.store.getState().dailyRecords.old.note, 'UNOWNED_DAILY', 'legacy local text is preserved, not assigned to a formal account')
})

test('account login completion cannot revive the page after navigation away', async () => {
  const f = fixture({ cold: true }), p = f.page('mine-flow', 'auth'); await p.onShow(); let login
  f.wx.login = o => { login = o }
  const retry = p.readAccount(); p.onHide(); login.success({ code: 'synthetic-delayed' }); await retry
  assert.equal(p.data.account, null); assert.equal(f.requests.filter(r => r.route === '/me/overview').length, 0)
})

test('direct real replacement pages fail closed when formal API configuration is missing', async () => {
  const f = fixture({ runtime: { environment: 'staging', businessBaseUrl: '' }, cold: true })
  f.store.updateState(s => { s.dailyRecords.old = { note: 'UNOWNED_SECRET' }; return s })
  assert.throws(() => f.api.enabled(), /业务接口尚未配置/)
  for (const view of ['records', 'experiences', 'experience']) {
    const p = f.page('personal', view); await p.onShow()
    assert.equal(p.data.records.length, 0); assert.equal(p.data.rows.length, 0)
    assert.match(p.data.error, /业务接口尚未配置/)
  }
  await assert.rejects(f.api.login(), /业务接口尚未配置/)
  assert.equal(f.loginCalls(), 0); assert.equal(f.requests.length, 0)
  const demo = fixture({ runtime: { environment: 'demo', businessBaseUrl: '' }, cold: true })
  assert.equal(demo.api.enabled(), false)
})
