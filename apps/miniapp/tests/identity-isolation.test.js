const { test } = require('node:test'), assert = require('node:assert/strict')
const { fixture, tick } = require('./helpers/identity-fixture')
const edit = (p, note = 'SYNTHETIC_PRIVATE_A') => { p.chooseDaily({ currentTarget: { dataset: { value: 'rest' } } }); p.onDailyNote({ detail: { value: note } }) }
test('A → B → A restores server records without sharing drafts, lists or Demo storage', async () => {
  const f = fixture(), p = f.page()
  f.store.updateState(s => { s.dailyRecords.unowned = { choice: 'done', note: 'UNOWNED_LEGACY' }; return s })
  await p.onShow(); edit(p); await p.saveDaily()
  assert.equal(p.data.savedRecord.note, 'SYNTHETIC_PRIVATE_A')
  assert.equal(f.store.getState().dailyRecords.unowned.note, 'UNOWNED_LEGACY')
  assert.equal(JSON.stringify([...f.storage]).includes('SYNTHETIC_PRIVATE_A'), false, 'formal private text must never persist locally')
  edit(p, 'UNSAVED_A'); f.switchActor('B')
  assert.equal(p.data.dailyNote, '', 'known identity change clears immediately, before a new read')
  assert.equal(p.data.dailyReady, false)
  await p.onShow(); assert.equal(p.data.state.habit.id, 'habit-B'); assert.equal(p.data.dailyNote, '')
  const old = f.page('friend-flow', 'records'); await old.onShow(); assert.equal(old.data.records.length, 0)
  f.switchActor('A'); await p.onShow(); assert.equal(p.data.dailyNote, 'SYNTHETIC_PRIVATE_A')
  await old.onShow(); assert.equal(old.data.records[0].note, 'SYNTHETIC_PRIVATE_A')
})
test('cold start, absent identity and old direct records route never trust the Demo owner', async () => {
  const f = fixture({ cold: true }), p = f.page('friend-flow', 'records')
  f.store.updateState(s => { s.dailyRecords.old = { note: 'UNOWNED' }; return s })
  await p.onShow(); assert.equal(p.data.records.length, 0); assert.equal(p.data.dailyReady, false)
  assert.match(p.data.dailyError, /微信登录/); assert.equal(f.requests.length, 0)
  await p.retryPrivate(); assert.equal(f.loginCalls(), 1); assert.equal(p.data.dailyReady, true)
  assert.equal(p.data.records.length, 0)
})
test('existing legal records restore after module restart with only credentials retained', async () => {
  const records = { A: [{ id: 'legal-A', sessionId: 'session-A', recordDate: '2026-10-10', recordStage: 'habit', planDay: 1, choiceValue: 'light', note: 'EXISTING_A', updatedAt: '2026-10-10 11:00:00' }] }
  const f = fixture({ records }), p = f.page(); await p.onShow(); assert.equal(p.data.dailyNote, 'EXISTING_A')
  const second = fixture({ records, cold: true, storage: new Map([[f.key, 'synthetic-A']]) }), q = second.page()
  await q.onShow(); assert.equal(q.data.savedRecord.note, 'EXISTING_A')
  assert.equal(q.data.records[0].savedAt, '2026-10-10 11:00:00')
})
test('401 invalidates visible private content, operations and current credentials', async () => {
  const f = fixture(), p = f.page(); await p.onShow(); edit(p)
  f.intercept((o, actor, route) => { if (route === '/me/overview') { f.response(o, null, 401); return false } })
  await p.saveDaily(); assert.equal(p.data.dailyNote, ''); assert.equal(p.data.records.length, 0)
  assert.equal(p.data.dailyReady, false); assert.equal(f.storage.has(f.key), false)
  assert.equal(f.requests.filter(r => r.method === 'PUT').length, 0)
})
test('an old delayed read cannot repopulate B; an old 401 cannot delete B credentials', async () => {
  for (const code of [200, 401]) {
    const f = fixture(), p = f.page(); let delayed
    f.intercept((o, actor, route) => { if (actor === 'A' && route === '/me/overview') { delayed = o; return false } })
    const pending = p.onShow(); await tick(); f.switchActor('B'); await p.onShow()
    f.response(delayed, { customerId: 'A', habit: f.habit('A'), dailyRecords: [{ note: 'STALE_A' }] }, code)
    await pending; assert.equal(p.data.state.habit.id, 'habit-B'); assert.equal(p.data.dailyNote, '')
    assert.equal(f.storage.get(f.key), 'synthetic-B')
  }
})
test('switch during save preflight prevents writing the A draft with a B token', async () => {
  const f = fixture(), p = f.page(); await p.onShow(); edit(p); let delayed
  f.intercept((o, actor, route) => { if (actor === 'A' && route === '/me/overview') { delayed = o; return false } })
  const pending = p.saveDaily(); await tick(); f.switchActor('B'); await p.onShow()
  f.response(delayed, { customerId: 'A', habit: f.habit('A'), dailyRecords: [] }); await pending
  assert.equal(f.requests.filter(r => r.method === 'PUT').length, 0); assert.equal(p.data.dailyNote, '')
})
test('switch during in-flight write does not expose A response or success in B', async () => {
  const f = fixture(), p = f.page(); await p.onShow(); edit(p); let delayed
  f.intercept((o, actor, route) => { if (route === '/daily-records/today') { delayed = o; return false } })
  const pending = p.saveDaily(); await tick(); f.switchActor('B'); await p.onShow(); f.response(delayed, null); await pending
  assert.equal(p.data.dailyNote, ''); assert.equal(p.data.savedRecord, null); assert.equal(p.data.dailySaving, false)
  assert.equal(f.toasts.includes('私人记录已保存'), false)
})
test('same-owner failures preserve drafts; retry reads then saves and confirms server content', async () => {
  const f = fixture(), p = f.page(); await p.onShow(); edit(p)
  f.intercept((o, actor, route) => { if (route === '/me/overview') { o.fail({ errMsg: 'SYNTHETIC_OFFLINE' }); return false } })
  await p.saveDaily(); assert.equal(p.data.dailyNote, 'SYNTHETIC_PRIVATE_A'); assert.match(p.data.dailyError, /SYNTHETIC_OFFLINE/)
  assert.equal(f.requests.filter(r => r.method === 'PUT').length, 0)
  f.intercept(null); await p.retryPrivate(); assert.equal(p.data.dailyNote, 'SYNTHETIC_PRIVATE_A')
  await p.saveDaily(); assert.equal(p.data.savedRecord.note, 'SYNTHETIC_PRIVATE_A'); assert.equal(f.toasts.at(-1), '私人记录已保存')
})
test('successful PUT followed by failed readback is truthful, and retry does not repeat the write', async () => {
  const f = fixture(), p = f.page(); await p.onShow(); edit(p)
  f.intercept((o, actor, route) => { if (route === '/me/overview' && f.records.A.length) { o.fail({ errMsg: 'READBACK_OFFLINE' }); return false } })
  await p.saveDaily(); assert.match(p.data.dailyError, /已提交.*回读暂未确认/); assert.equal(p.data.savedRecord, null)
  await p.saveDaily(); assert.equal(f.requests.filter(r => r.method === 'PUT').length, 1)
  f.intercept(null); await p.retryPrivate(); assert.equal(p.data.savedRecord.note, 'SYNTHETIC_PRIVATE_A')
  assert.equal(f.requests.filter(r => r.method === 'PUT').length, 1)
})
test('edits during preflight/write/readback stay unsaved and repeated taps send only one PUT', async () => {
  const f = fixture(), p = f.page(); await p.onShow(); edit(p); let delayed
  f.intercept((o, actor, route) => { if (route === '/daily-records/today') { delayed = o; return false } })
  const pending = p.saveDaily(); await tick(); await p.saveDaily(); edit(p, 'NEW_UNSAVED_A')
  f.records.A = [{ ...delayed.data, id: 'record-A' }]; f.response(delayed, null); await pending
  assert.equal(p.data.dailyNote, 'NEW_UNSAVED_A'); assert.equal(p.data.savedRecord.note, 'SYNTHETIC_PRIVATE_A')
  assert.match(f.toasts.at(-1), /新修改未保存/); assert.equal(f.requests.filter(r => r.method === 'PUT').length, 1)
})
test('hide/unload clears drafts and delayed work cannot revive the hidden page', async () => {
  const f = fixture(), p = f.page(); await p.onShow(); edit(p); p.onHide()
  assert.equal(p.data.dailyNote, ''); assert.equal(p.data.dailyReady, false)
  await p.onShow(); assert.equal(p.data.dailyNote, '')
})
test('obsolete login attempt cannot overwrite a newly installed identity', async () => {
  const f = fixture({ cold: true }); let delayed
  f.intercept((o, actor, route) => { if (route === '/auth/login') { delayed = o; return false } })
  const pending = f.api.login(); await tick(); f.switchActor('B'); f.response(delayed, { token: 'synthetic-A' })
  await assert.rejects(pending, /身份已变化/); assert.equal(f.storage.get(f.key), 'synthetic-B')
})

test('credential removal and raw storage replacement clear before onShow finishes', async () => {
  const f = fixture(), p = f.page(); await p.onShow(); edit(p)
  f.storage.set(f.key, 'synthetic-B')
  const read = p.onShow(); assert.equal(p.data.dailyNote, ''); assert.equal(p.data.dailyReady, false); await read
  edit(p, 'UNSAVED_B'); f.switchActor(null)
  assert.equal(p.data.dailyNote, ''); assert.equal(p.data.records.length, 0)
  await p.saveDaily(); assert.equal(f.requests.filter(r => r.method === 'PUT').length, 0)
})
test('unknown server owner invalidates credentials instead of adopting local records', async () => {
  const f = fixture(), p = f.page()
  f.intercept((o, actor, route) => { if (route === '/me/overview') { f.response(o, { dailyRecords: [{ note: 'OWNERLESS' }] }); return false } })
  await p.onShow(); assert.equal(p.data.records.length, 0); assert.equal(p.data.dailyReady, false)
  assert.equal(f.storage.has(f.key), false)
})
test('owner changes under the same credential invalidate the old page before accepting new content', async () => {
  const f = fixture(), p = f.page(); await p.onShow(); edit(p)
  f.intercept((o, actor, route) => { if (route === '/me/overview') { f.response(o, { customerId: 'replacement', habit: f.habit('replacement'), dailyRecords: [] }); return false } })
  await p.loadPrivate(true); assert.equal(p.data.dailyNote, ''); assert.equal(p.data.dailyReady, false)
  await p.retryPrivate(); assert.equal(p.data.state.habit.id, 'habit-replacement'); assert.equal(p.data.records.length, 0)
})
test('PUT failure preserves the same-owner draft and allows a successful explicit retry', async () => {
  const f = fixture(), p = f.page(); await p.onShow(); edit(p)
  f.intercept((o, actor, route) => { if (route === '/daily-records/today') { o.fail({ errMsg: 'WRITE_OFFLINE' }); return false } })
  await p.saveDaily(); assert.equal(p.data.dailyNote, 'SYNTHETIC_PRIVATE_A'); assert.equal(p.data.savedRecord, null)
  assert.match(p.data.dailyError, /WRITE_OFFLINE/); assert.equal(f.records.A.length, 0)
  f.intercept(null); await p.saveDaily(); assert.equal(p.data.savedRecord.note, 'SYNTHETIC_PRIVATE_A')
})
test('hiding while a private read is pending cannot revive its content when the response arrives', async () => {
  const f = fixture(), p = f.page(); let delayed
  f.intercept((o, actor, route) => { if (route === '/me/overview') { delayed = o; return false } })
  const read = p.onShow(); await tick(); p.onHide()
  f.response(delayed, { customerId: 'A', habit: f.habit('A'), dailyRecords: [{ note: 'HIDDEN' }] }); await read
  assert.equal(p.data.records.length, 0); assert.equal(p.data.dailyReady, false)
})
test('a stale expected scope is rejected before transmission, even in the request microtask', async () => {
  const f = fixture(), scope = f.api.identityScope()
  const write = f.api.saveDailyRecord({ note: 'MUST_NOT_SEND' }, scope); f.switchActor('B')
  await assert.rejects(write, /身份已变化/); assert.equal(f.requests.length, 0)
})
test('paused or expired authoritative plan cannot write and retains its valid owner draft', async () => {
  const f = fixture(), p = f.page(); await p.onShow(); edit(p)
  f.intercept((o, actor, route) => { if (route === '/me/overview') { f.response(o, { customerId: 'A', habit: { ...f.habit('A'), canRecordToday: false, status: 'paused' }, dailyRecords: [] }); return false } })
  await p.saveDaily(); assert.equal(p.data.dailyNote, 'SYNTHETIC_PRIVATE_A'); assert.match(p.data.dailyError, /不在可记录/)
  assert.equal(f.requests.filter(r => r.method === 'PUT').length, 0)
})

test('formal archive never loads the unowned private cache into hidden page data', () => {
  const f = fixture(), p = f.page('friend-flow', 'archive')
  f.store.updateState(s => { s.dailyRecords.old = { note: 'UNOWNED_SECRET' }; return s })
  p.refresh(); assert.equal(JSON.stringify(p.data).includes('UNOWNED_SECRET'), false)
})

test('login retry cannot load private content into a page hidden before login finishes', async () => {
  const f = fixture({ cold: true }), p = f.page(); await p.onShow(); let login
  f.wx.login = o => { login = o }
  const retry = p.retryPrivate(); p.onHide(); login.success({ code: 'synthetic-delayed' }); await retry
  assert.equal(p.data.dailyReady, false); assert.equal(p.data.dailyNote, '')
  assert.equal(f.requests.filter(r => r.route === '/me/overview').length, 0)
})
