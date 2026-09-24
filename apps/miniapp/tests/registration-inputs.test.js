'use strict'
const { test } = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const vm = require('node:vm')
const path = require('node:path')
function fixture() {
  let page
  const submissions = []
  const business = { enabled: () => true, register: async payload => { submissions.push(payload) } }
  const mocks = {
    '../../services/business-api': business,
    '../../data/demo': { activities: [] },
    '../../utils/store': { updateState: () => { throw new Error('API draft must not mutate demo state') } },
    '../../utils/domain': {}, '../../utils/navigation': {}
  }
  vm.runInNewContext(fs.readFileSync(path.resolve(__dirname, '../pages/camp-flow/index.js'), 'utf8'), {
    Page: value => { page = value }, require: name => mocks[name], wx: { showToast() {} }
  })
  page.data = { ...page.data, activity: { id: 's1' }, state: { phone: '13800000000' }, draft: { serviceConsent: true,
    contactName: '测试轻友', contactPhone: '13800000000',
    participants: [{ id: 'person-self', name: '测试轻友', selected: true, minor: false }], motivation: '用户主动填写的原因' } }
  page.setData = value => Object.assign(page.data, value)
  page.refresh = async () => {}
  return { page, submissions }
}
test('API registration sends the selected motivation with the same participant draft', async () => {
  const { page, submissions } = fixture()
  await page.submitRegistration()
  assert.equal(submissions[0].motivation, '用户主动填写的原因')
  assert.equal(submissions[0].participants[0].name, '测试轻友')
})
test('choosing a motivation edits the draft without resetting participants or consent', () => {
  const { page } = fixture()
  const participants = page.data.draft.participants
  page.chooseMotivation({ currentTarget: { dataset: { value: '和家人一起体验' } } })
  assert.equal(page.data.draft.motivation, '和家人一起体验')
  assert.equal(page.data.draft.participants, participants)
  assert.equal(page.data.draft.serviceConsent, true)
})
test('an unfilled motivation remains optional and does not use demo defaults', async () => {
  const { page, submissions } = fixture()
  delete page.data.draft.motivation
  await page.submitRegistration()
  assert.equal(submissions[0].motivation, null)
})

test('companion cannot reuse the main contact phone as a separate identity', async () => {
  const { page, submissions } = fixture()
  page.data.draft.participants.push({ id: 'person-2', name: '同行人', phone: '13800000000', selected: true, minor: false })
  await page.submitRegistration()
  assert.equal(submissions.length, 0)
})