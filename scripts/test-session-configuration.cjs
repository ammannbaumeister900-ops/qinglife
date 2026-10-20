'use strict'
const { test } = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const vm = require('node:vm')
const source = fs.readFileSync('apps/admin/src/views/life/session/edit.vue', 'utf8')
const script = source.match(/<script>([\s\S]*?)<\/script>/)[1]
const plain = value => JSON.parse(JSON.stringify(value))
async function fixture(old, options = {}) {
  const requests = [], errors = [], route = { params: old ? { id: old.id } : {} }
  const names = options.names || [{ dictValue: '生机清体营', dictLabel: '生机清体营', isDefault: 'Y' }]
  const venues = options.venues || [{ dictValue: '上海市安亭驿岛酒店', dictLabel: '上海市安亭驿岛酒店', isDefault: 'Y' }]
  const people = options.people || [{ id: '9007199254740993', name: '公主' }, { id: '102', name: '大海' }, { id: '103', name: '军军' }]
  const sandbox = { cities: [{ label: '上海市', children: [{ label: '上海城区' }] }], ImageUpload: {},
    getDicts: async type => { if (options.fail) throw Error('字典读取失败'); return { data: type === 'ql_session_name' ? names : venues } },
    sessionGuides: async () => ({ data: people }), getSession: async () => ({ data: plain(old) }),
    addSession: async payload => { requests.push(plain(payload)) }, updateSession: async payload => { requests.push(plain(payload)) },
    previewSessionCover: async () => new Blob([], { type: 'image/png' }), Blob, URL: { revokeObjectURL() {}, createObjectURL() { return 'blob:synthetic' } },
    setTimeout() { return 1 }, clearTimeout() {}, ...(options.Date ? { Date: options.Date } : {}) }
  vm.runInNewContext(script.replace(/^import .*$/gm, '').replace('export default', 'component ='), sandbox)
  const component = sandbox.component
  const p = Object.assign({ $route: route }, component.data.call({ $route: route }), component.methods, {
    $refs: { form: { validate: fn => fn(true) } }, msgError: error => errors.push(error), msgSuccess() {}, $router: { push() {} } })
  for (const [key, compute] of Object.entries(component.computed)) Object.defineProperty(p, key, { get: () => compute.call(p) })
  await component.created.call(p)
  const start = value => { p.form.startDate = value; component.watch['form.startDate'].call(p, value) }
  const save = async () => { p.submit(); await new Promise(resolve => setImmediate(resolve)) }
  return { p, start, save, requests, errors }
}
const historical = extra => ({ id: 'old', sessionNumber: 7, name: '历史营', theme: '历史原主题', coverUrl: 'https://example.invalid/public/session-covers/v1-7.png',
  startDate: '2026-03-01', endDate: '2026-03-03', capacity: 17, standardPrice: 123, returningPrice: 99, publicVenue: '旧地点', province: '上海市', city: '上海市',
  registrationConfirmMode: 'manual', status: 'completed', configuredStatus: 'open', registrationOpenAt: null, registrationCloseAt: '2026-02-27 23:59:59',
  leaderName: '历史导游甲', guideIds: null, intro: '历史简介', cancelPolicy: '历史规则', ...extra })

test('new sessions use maintained dictionary defaults, exact staff IDs, 50 seats, editable prices and auto mode', async () => {
  const { p } = await fixture()
  assert.equal(p.form.name, '生机清体营'); assert.equal(p.form.publicVenue, '上海市安亭驿岛酒店')
  assert.deepEqual(plain(p.guides), ['9007199254740993', '102', '103'])
  assert.equal(p.form.capacity, 50); assert.equal(p.form.standardPrice, 3800); assert.equal(p.form.returningPrice, 2500)
  assert.equal(p.form.registrationConfirmMode, 'auto'); assert.equal(p.form.intro, ''); assert.equal(p.form.cancelPolicy, '')
  assert.equal(p.form.theme, '')
})
test('dictionary edits/defaults control new selects without free-text or hardcoded replacement', async () => {
  const { p } = await fixture(null, { names: [{ dictValue: '新增营', dictLabel: '新增营', isDefault: 'Y' }], venues: [{ dictValue: '新增地点', dictLabel: '新增地点' }] })
  assert.equal(p.form.name, '新增营'); assert.equal(p.form.publicVenue, '新增地点')
  assert.equal(p.nameOptions.length, 1); assert.equal(p.venueOptions.length, 1)
  assert(!/label="当期主题"/.test(source)); assert(!/el-input v-model="form\.(name|publicVenue)"/.test(source))
})
test('new booking defaults cross leap days and years using local calendar dates', async () => {
  const { p, start } = await fixture()
  start('2028-03-01'); assert.equal(p.openDate, '2028-02-16'); assert.equal(p.closeDate, '2028-02-29')
  start('2027-01-01'); assert.equal(p.openDate, '2026-12-18'); assert.equal(p.closeDate, '2026-12-31')
})
test('manual open and close changes are independently preserved, including deliberate clearing', async () => {
  const { p, start } = await fixture()
  start('2026-11-01'); p.openManual = true; p.openDate = '2026-10-01'
  start('2026-12-01'); assert.equal(p.openDate, '2026-10-01'); assert.equal(p.closeDate, '2026-11-30')
  p.closeManual = true; p.closeDate = ''; start('2027-01-01')
  assert.equal(p.openDate, '2026-10-01'); assert.equal(p.closeDate, '')
})
test('historical names, venue, theme, values, manual mode and dates are never replaced by new defaults', async () => {
  const { p, start, save, requests } = await fixture(historical())
  assert.equal(p.form.name, '历史营'); assert.equal(p.form.publicVenue, '旧地点'); assert.equal(p.form.capacity, 17)
  assert.equal(p.form.standardPrice, 123); assert.equal(p.form.registrationConfirmMode, 'manual')
  assert.equal(p.form.theme, '历史原主题'); assert.equal(p.form.status, 'open')
  assert.match(p.nameOptions.at(-1).dictLabel, /历史值/); assert.match(p.venueOptions.at(-1).dictLabel, /历史值/)
  assert.equal(p.openDate, ''); assert.equal(p.closeDate, '2026-02-27')
  start('2026-04-01'); assert.equal(p.openDate, ''); assert.equal(p.closeDate, '2026-02-27')
  p.form.endDate = '2026-04-03'; await save()
  assert.equal(requests[0].registrationOpenAt, null); assert.equal(requests[0].theme, '历史原主题')
  assert.equal(requests[0].leaderName, '历史导游甲'); assert(!Object.hasOwn(requests[0], 'guideIds'))
})
test('historical blank venue and absent staff IDs remain saveable without inventing a mapping', async () => {
  const { p, save, requests } = await fixture(historical({ publicVenue: null }))
  await save(); assert.equal(requests.length, 1); assert.equal(requests[0].publicVenue, null); assert.equal(p.legacyGuides, '历史导游甲')
})
test('new missing or ambiguous default staff blocks silent selection and save', async () => {
  for (const people of [[{ id: '1', name: '公主' }], [{ id: '1', name: '公主' }, { id: '2', name: '公主' }, { id: '3', name: '大海' }, { id: '4', name: '军军' }]]) {
    const { p, start, save, requests, errors } = await fixture(null, { people })
    assert.equal(p.guides.length, 0); assert.match(p.guideDefaultsError, /未唯一匹配/)
    start('2026-12-01'); p.form.endDate = '2026-12-03'; await save()
    assert.equal(requests.length, 0); assert.equal(errors.length, 1)
  }
})
test('an explicit staff choice uses exact IDs, never a name-derived ID', async () => {
  const { p, start, save, requests } = await fixture()
  start('2026-12-01'); p.form.endDate = '2026-12-03'; p.guides = ['9007199254740993']; p.guidesTouched = true
  p.form.standardPrice = 0; p.form.returningPrice = 88; p.openManual = true; p.openDate = ''; await save()
  assert.deepEqual(requests[0].guideIds, ['9007199254740993']); assert.equal(requests[0].standardPrice, 0)
  assert.equal(requests[0].registrationOpenAt, null); assert.equal(requests[0].registrationOpenManual, true)
})
test('failed option reads block submission and preserve failure state', async () => {
  const { p, save, requests, errors } = await fixture(null, { fail: true }); await save()
  assert.equal(p.loadFailed, true); assert.equal(p.loading, false); assert.equal(requests.length, 0); assert.equal(errors[0], '字典读取失败')
})
test('unchanged historical automatic cover stays v1 while changing number requests the current template', async () => {
  const { p, save, requests } = await fixture(historical())
  assert.equal(p.coverPreviewUrl, p.form.coverUrl)
  await save(); assert.match(requests[0].coverUrl, /v1-7\.png$/)
  p.form.sessionNumber = 8; await save(); assert.equal(requests[1].coverUrl, '')
})
test('v2 and v3 automatic covers retain automatic mode and their original preview on editing', async () => {
  for (const version of [2, 3]) {
    const coverUrl = 'https://example.invalid/public/session-covers/v' + version + '-7.png'
    const { p, save, requests } = await fixture(historical({ coverUrl }))
    assert.equal(p.coverMode, 'auto'); assert.equal(p.coverPreviewUrl, coverUrl)
    await save(); assert.equal(requests[0].coverUrl, coverUrl)
  }
})
test('explicit manual dates retain inclusive day endpoints and invalid order is rejected', async () => {
  const { p, start, save, requests, errors } = await fixture()
  start('2026-12-01'); p.form.endDate = '2026-12-03'; p.openManual = p.closeManual = true
  p.openDate = '2026-11-20'; p.closeDate = '2026-11-19'; await save(); assert.equal(requests.length, 0); assert.equal(errors.length, 1)
  p.closeDate = '2026-11-25'; await save()
  assert.equal(requests[0].registrationOpenAt, '2026-11-20 00:00:00'); assert.equal(requests[0].registrationCloseAt, '2026-11-25 23:59:59')
})

// Reproduces the observed browser: the constructor uses UTC+8, while local getters expose UTC.
class MixedZoneDate extends Date {
  constructor(...args) {
    if (args.length >= 2) super(Date.UTC(args[0], args[1], args[2] == null ? 1 : args[2], ...args.slice(3)) - 8 * 3600000)
    else super(...args)
  }
  getFullYear() { return this.getUTCFullYear() }
  getMonth() { return this.getUTCMonth() }
  getDate() { return this.getUTCDate() }
  getDay() { return this.getUTCDay() }
}

test('date-only inputs preserve API days and unchanged save payloads even with mixed browser time zones', async () => {
  const row = historical({ startDate: '2026-10-23', endDate: '2026-10-25', registrationOpenAt: '2026-10-08 00:00:00', registrationCloseAt: '2026-10-22 23:59:59' })
  const { p, save, requests } = await fixture(row, { Date: MixedZoneDate })
  assert.equal(p.form.startDate, '2026-10-23'); assert.equal(p.form.endDate, '2026-10-25')
  assert.equal(p.openDate, '2026-10-08'); assert.equal(p.closeDate, '2026-10-22')
  await save()
  assert.equal(requests[0].startDate, row.startDate); assert.equal(requests[0].endDate, row.endDate)
  assert.equal(requests[0].registrationOpenAt, row.registrationOpenAt); assert.equal(requests[0].registrationCloseAt, row.registrationCloseAt)
})

test('new booking defaults remain calendar days across years and leap days with inconsistent local getters', async () => {
  assert.equal(new MixedZoneDate(2026, 9, 23).getDate(), 22, 'fixture actually reproduces the one-day loss')
  const { p, start } = await fixture(null, { Date: MixedZoneDate })
  start('2026-10-23'); assert.equal(p.openDate, '2026-10-09'); assert.equal(p.closeDate, '2026-10-22')
  start('2028-03-01'); assert.equal(p.openDate, '2028-02-16'); assert.equal(p.closeDate, '2028-02-29')
  start('2027-01-01'); assert.equal(p.openDate, '2026-12-18'); assert.equal(p.closeDate, '2026-12-31')
})

test('edited date strings survive submit and reversed activity dates are still rejected', async () => {
  const { p, save, requests, errors } = await fixture(historical(), { Date: MixedZoneDate })
  p.form.startDate = '2026-10-23'; p.form.endDate = '2026-10-22'
  await save(); assert.equal(requests.length, 0); assert.equal(errors.at(-1), '结束日期不能早于开始日期')
  p.form.endDate = '2026-10-25'; p.openDate = '2026-10-08'; p.closeDate = '2026-10-22'; p.openManual = p.closeManual = true
  await save()
  assert.equal(requests[0].startDate, '2026-10-23'); assert.equal(requests[0].endDate, '2026-10-25')
  assert.equal(requests[0].registrationOpenAt, '2026-10-08 00:00:00'); assert.equal(requests[0].registrationCloseAt, '2026-10-22 23:59:59')
})
