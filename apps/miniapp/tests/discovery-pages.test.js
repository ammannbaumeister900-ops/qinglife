const { test } = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const vm = require('node:vm')
const discovery = require('../utils/discovery')
const root = path.resolve(__dirname, '..')
function page(name, mocks) {
  let result
  vm.runInNewContext(fs.readFileSync(path.join(root, 'pages', name, 'index.js'), 'utf8'), {
    Page: value => { result = value },
    require: id => mocks[id] || require(path.resolve(root, 'pages', name, id))
  })
  result.data = JSON.parse(JSON.stringify(result.data))
  result.setData = patch => Object.assign(result.data, patch)
  return result
}
const activity = (id, date, extra = {}) => ({ id, name: id, startDate: date, endDate: date, seatsLeft: 5, sourceStatus: 'open', status: '开放报名', statusTone: 'open', ...extra })
test('all visible sessions remain flat; next available first; closed windows cannot register', () => {
  const rows = discovery.activities([activity('late', '2099-02-01'), activity('next', '2099-01-01'), activity('closed', '2099-01-02', { registrationCloseAt: '2020-01-01' }), activity('draft', '2099-01-03', { sourceStatus: 'draft' })])
  assert.deepEqual(rows.map(r => r.id), ['next', 'late', 'closed'])
  assert.equal(rows[2].canRegister, false)
})
test('returning welcome requires completed self participation, not a family purchase', () => {
  const state = { loggedIn: true, registrations: { a: { status: 'completed', participants: [{ id: 'child', status: 'completed' }] } } }
  assert.equal(discovery.returning(state), false)
  state.registrations.a.participants.push({ id: 'person-self', status: 'completed' })
  assert.equal(discovery.returning(state), true)
  state.loggedIn = false
  assert.equal(discovery.returning(state), false)
})
test('backend eligibility overrides client-side registration inference', () => {
  const locallyCompleted = { loggedIn: true, returningEligible: false, registrations: { a: { status: 'completed', participants: [{ id: 'person-self', status: 'completed' }] } } }
  assert.equal(discovery.returning(locallyCompleted), false)
  assert.equal(discovery.returning({ loggedIn: true, returningEligible: true, registrations: {} }), true)
})
test('home and camp signup routes preserve the exact session ID', () => {
  const urls = [], mocks = { '../../utils/navigation': { navigateTo: x => urls.push(x.url) } }
  const home = page('home', mocks); home.data.featured = { id: 'session & 2' }
  home.openActivity({ currentTarget: { dataset: { view: 'register' } } })
  const camp = page('camp', mocks); camp.signup({ currentTarget: { dataset: { id: 'session & 2' } } })
  assert.equal(urls[0], '/pages/camp-flow/index?view=register&id=session%20%26%202')
  assert.equal(urls[1], urls[0])
})
test('private records exclude a companion and clear on load failure', async () => {
  let fail = false
  const business = { enabled: () => true, context: async () => {
    if (fail) throw new Error('offline')
    return { state: { loggedIn: true, registrations: {} }, activities: [], overview: { registrations: [{ registrationId: 'self', isSelf: 1, sessionId: 's' }, { registrationId: 'other', isSelf: 0 }], experienceRecords: [{ id: '1', registrationId: 'self', note: 'mine' }, { id: '2', registrationId: 'other', note: 'private-other' }] } }
  } }
  const personal = page('personal', { '../../services/business-api': business })
  personal.onLoad({ view: 'records' }); await personal.refresh()
  assert.equal(personal.data.records.length, 1)
  assert.equal(personal.data.records[0].note, 'mine')
  fail = true; await personal.refresh()
  assert.equal(personal.data.records.length, 0)
  assert.equal(personal.data.error, 'offline')
})
test('refresh keeps the last good camp list while request is pending', async () => {
  let resolve
  const camp = page('camp', { '../../services/business-api': { enabled: () => true, context: () => new Promise(r => { resolve = r }) } })
  camp.data.ready = true; camp.data.visibleActivities = [{ id: 'existing' }]
  const pending = camp.onShow()
  assert.equal(camp.data.visibleActivities[0].id, 'existing')
  assert.equal(camp.data.loading, false)
  resolve({ state: { registrations: {}, registration: {} }, activities: [] }); await pending
  assert.equal(camp.data.refreshing, false)
})
test('sample reading opens complete labeled local articles without calling legacy API', async () => {
  const samples = require('../data/reading-samples')
  assert.equal(new Set(samples.map(a => a.category)).size, 4)
  const content = page('content', { '../../config/runtime': { environment: 'staging' }, '../../services/legacy-api': { getArticles: () => { throw new Error('production forbidden') } } })
  await content.loadArticles()
  assert.equal(content.data.visibleArticles.length, 4)
  assert.ok(samples.every(a => a.sample && a.content.includes('演示')))
})
