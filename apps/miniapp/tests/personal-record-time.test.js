const { test } = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const vm = require('node:vm')
const root = path.resolve(__dirname, '..')
const plain = value => JSON.parse(JSON.stringify(value))

function fixture(records = [], options = {}) {
  const context = {
    state: { loggedIn: options.loggedIn !== false, registrations: {}, registration: { status: 'none' },
      dailyRecords: options.dailyRecords || {}, experienceReviews: options.experienceReviews || {} },
    activities: [{ id: 'session-1', name: '同一期活动' }, { id: 'session-2', name: '另一期活动' }],
    overview: { experienceRecords: records, registrations: options.registrations || [{ registrationId: 'self', isSelf: 1, sessionId: 'session-1' }] }
  }
  const writes = [], business = {
    enabled: () => options.backend !== false, context: async () => context,
    saveExperience: () => { throw new Error('Display must not write records') }
  }
  const store = { getState: () => context.state, updateState: value => writes.push(value) }
  let page
  vm.runInNewContext(fs.readFileSync(path.join(root, 'pages/personal/index.js'), 'utf8'), {
    Page: value => { page = value }, require: name => ({ '../../services/business-api': business, '../../utils/store': store,
      '../../data/demo': { activities: context.activities }, '../../utils/navigation': {} })[name]
  })
  page.data = plain(page.data)
  page.setData = patch => Object.assign(page.data, patch)
  page.onLoad({ view: 'records', id: options.sessionId || '' })
  return { page, business, context, writes }
}
const record = (id, updatedAt, extra = {}) => ({ id, registrationId: 'self', phase: 'during', note: '私人正文 ' + id, visibility: 'private', updatedAt, ...extra })

test('same-session same-phase records are distinguished by their own save date and second', async () => {
  const source = [record('day-1', '2026-09-07 09:30:01'), record('day-2', '2026-09-08 09:30:01'), record('day-3', '2026-09-08 09:30:02')]
  const f = fixture(source), before = plain(f.context)
  await f.page.onShow()
  assert.deepEqual(plain(f.page.data.records).map(row => row.recordTimeText), ['2026年9月7日 09:30:01', '2026年9月8日 09:30:01', '2026年9月8日 09:30:02'])
  assert.equal(new Set(f.page.data.records.map(row => row.recordTimeText)).size, 3)
  assert.ok(f.page.data.records.every(row => row.label === '活动期间' && row.name === '同一期活动' && row.visibility === 'private'))
  assert.deepEqual(plain(f.context), before, 'response data must not be mutated')
  assert.deepEqual(f.writes, [])
})

test('updatedAt represents latest save rather than creation or a guessed activity date', async () => {
  const f = fixture([record('edited', '2026-10-06 10:11:12', { createdAt: '2026-09-07 08:00:00', savedAt: '2026-09-08 08:00:00' }), record('missing', null, { createdAt: '2026-09-07 08:00:00' })])
  await f.page.refresh()
  assert.equal(f.page.data.records[0].recordTimeText, '2026年10月6日 10:11:12')
  assert.equal(f.page.data.records[1].recordTimeText, '')
})

test('timezone-free server values retain their calendar time; valid date-only history does not invent a clock', async () => {
  const f = fixture([record('sql', '2026-02-28 23:59:59.123'), record('local-iso', '2026-02-28T23:59:59'), record('date', '2024-02-29'), record('minute', '2026-10-06 09:30')])
  await f.page.refresh()
  assert.deepEqual(plain(f.page.data.records).map(row => row.recordTimeText), ['2026年2月28日 23:59:59', '2026年2月28日 23:59:59', '2024年2月29日', '2026年10月6日 09:30'])
})

test('ISO timestamps with zones display using the device local clock, including midnight boundaries', async () => {
  const morning = new Date(2026, 9, 6, 0, 1, 2).toISOString()
  const evening = new Date(2026, 9, 6, 23, 59, 58).toISOString()
  const f = fixture([record('morning', morning), record('evening', evening)])
  await f.page.refresh()
  assert.deepEqual(plain(f.page.data.records).map(row => row.recordTimeText), ['2026年10月6日 00:01:02', '2026年10月6日 23:59:58'])
})

test('empty, malformed and impossible historical timestamps remain readable without invalid date text', async () => {
  const values = [undefined, null, '', ' ', 'Invalid Date', '2026-02-30 10:00:00', '2026-13-01', '2026-04-31', '2026-10-06 24:00:00', '2026-10-06 10:60:00', '2026-10-06 10:00:60', '2026-10-06T10:00:00+99:99', 0, {}, true]
  const f = fixture(values.map((value,index) => record(String(index), value)))
  await f.page.refresh()
  assert.equal(f.page.data.error, '')
  assert.equal(f.page.data.records.length, values.length)
  assert.ok(f.page.data.records.every(row => row.recordTimeText === '' && row.note.startsWith('私人正文')))
})

test('self ownership, selected session, logout and read failure still exclude private records', async () => {
  const f = fixture([record('mine', '2026-09-07 09:00:00'), record('companion', '2026-09-07 09:01:00', { registrationId: 'other' }), record('different-session', '2026-09-07 09:02:00', { registrationId: 'self-2' })], {
    sessionId: 'session-1', registrations: [{ registrationId: 'self', isSelf: '1', sessionId: 'session-1' }, { registrationId: 'other', isSelf: 0, sessionId: 'session-1' }, { registrationId: 'self-2', isSelf: true, sessionId: 'session-2' }]
  })
  await f.page.refresh()
  assert.deepEqual(f.page.data.records.map(row => row.id), ['mine'])
  f.page.onHide()
  assert.equal(f.page.data.records.length, 0)
  f.context.state.loggedIn = false
  await f.page.refresh()
  assert.equal(f.page.data.records.length, 0)
  f.context.state.loggedIn = true
  await f.page.refresh()
  f.business.context = async () => { throw new Error('offline') }
  await f.page.refresh()
  assert.equal(f.page.data.records.length, 0)
  assert.equal(f.page.data.error, 'offline')
})

test('local daily and activity-review records reuse savedAt without changing stored records', async () => {
  const savedAt = new Date(2026, 9, 6, 10, 11, 12).toISOString()
  const f = fixture([], { backend: false, dailyRecords: { daily: { note: '日常正文', savedAt }, old: { note: '旧记录' }, malformed: { note: '旧数值时间', savedAt: 123 } }, experienceReviews: { 'session-1': { note: '活动后正文', savedAt } } })
  const before = plain(f.context.state)
  await f.page.refresh()
  assert.equal(f.page.data.error, '')
  const rows = plain(f.page.data.records)
  assert.equal(rows[0].recordTimeText, '2026年10月6日 10:11:12')
  assert.equal(rows[1].recordTimeText, '')
  assert.equal(rows[2].recordTimeText, '')
  assert.equal(rows[3].recordTimeText, '2026年10月6日 10:11:12')
  assert.equal(rows[3].label, '活动后')
  assert.deepEqual(plain(f.context.state), before)
  assert.deepEqual(f.writes, [])
})

test('time is conditional secondary metadata and does not replace the stage or private record body', () => {
  const wxml = fs.readFileSync(path.join(root,'pages/personal/index.wxml'),'utf8')
  const css = fs.readFileSync(path.join(root,'pages/personal/index.wxss'),'utf8')
  assert.ok(wxml.includes('{{item.label}} · 仅自己可见'))
  assert.ok(wxml.includes('wx:if="{{item.recordTimeText}}" class="record-time">最近保存 · {{item.recordTimeText}}'))
  assert.ok(wxml.includes("{{item.note || '这一次，留下了一份状态记录。'}}"))
  assert.match(css, /\.record-time\s*\{[^}]*color:\s*var\(--ql-ink-ghost\);[^}]*font-size:\s*22rpx/)
})
