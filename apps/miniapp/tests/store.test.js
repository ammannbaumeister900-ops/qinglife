const assert = require('assert')
const store = require('../utils/store')

store.resetState()
let state = store.getState()
assert.strictEqual(state.stage, 'refeed')
assert.strictEqual(state.registration.status, 'completed')
assert.strictEqual(state.registration.activityId, 'session-016')

store.applyStage('visitor')
state = store.getState()
assert.strictEqual(state.loggedIn, false)
assert.strictEqual(state.phoneLinked, false)
assert.strictEqual(state.registration.status, 'none')

store.applyStage('journey')
state = store.getState()
assert.strictEqual(state.loggedIn, true)
assert.strictEqual(state.registration.status, 'confirmed')
assert.deepStrictEqual(state.checkedDays, ['day-1'])

store.applyStage('refeed')
state = store.getState()
assert.strictEqual(state.registration.status, 'completed')
assert.strictEqual(state.checkedDays.length, 3)

console.log('store.test.js OK')
