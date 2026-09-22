'use strict'
const { test } = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const vm = require('node:vm')
const path = require('node:path')
function fixture() {
  let page
  let state = { habit: { id: 'plan-1', planLength: 14, currentDay: 3, paused: false, status: 'active', completedDays: [1, 3] } }
  const calls = [], messages = []
  const business = { enabled: () => true,
    changeHabit: async (id, paused) => { calls.push({ id, paused }); return { ...state.habit, paused, status: paused ? 'paused' : 'active', completedDays: [1] } },
    applyHabit: habit => { state = { ...state, habit } }
  }
  const mocks = { '../../services/business-api': business, '../../services/legacy-api': {},
    '../../utils/store': { getState: () => state, updateState: fn => { state = fn(state) } },
    '../../data/demo': { habitDays: [], participations: [{}] }, '../../utils/navigation': {}, '../../utils/domain': {} }
  vm.runInNewContext(fs.readFileSync(path.resolve(__dirname, '../pages/mine-flow/index.js'), 'utf8'), {
    Page: value => { page = value }, require: name => mocks[name], wx: { showToast: value => messages.push(value.title) }
  })
  page.setData = value => Object.assign(page.data, value)
  page.refresh()
  return { page, business, calls, messages, state: () => state }
}
test('pause applies authoritative progress and resume targets the same plan', async () => {
  const f = fixture()
  await f.page.togglePause()
  assert.deepEqual(f.calls[0], { id: 'plan-1', paused: true })
  assert.equal(f.state().habit.paused, true)
  assert.deepEqual(f.state().habit.completedDays, [1])
  await f.page.togglePause()
  assert.deepEqual(f.calls[1], { id: 'plan-1', paused: false })
  assert.equal(f.state().habit.paused, false)
})
test('a failed pause does not mutate progress or announce success', async () => {
  const f = fixture()
  f.business.changeHabit = async () => { throw new Error('network failed') }
  await f.page.togglePause()
  assert.equal(f.state().habit.paused, false)
  assert.deepEqual(f.state().habit.completedDays, [1, 3])
  assert.deepEqual(f.messages, ['network failed'])
  assert.equal(f.page.data.habitBusy, false)
})
test('repeated tap during a pending pause sends one request', async () => {
  const f = fixture()
  let complete
  f.business.changeHabit = () => { f.calls.push('pending'); return new Promise(resolve => { complete = resolve }) }
  const first = f.page.togglePause()
  await f.page.togglePause()
  assert.equal(f.calls.length, 1)
  complete({ ...f.state().habit, paused: true, status: 'paused' })
  await first
  assert.equal(f.page.data.habitBusy, false)
})