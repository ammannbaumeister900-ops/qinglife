const assert = require('assert')
const domain = require('../utils/domain')

const state = {
  stage: 'refeed',
  loggedIn: true,
  phoneLinked: true,
  profile: { name: '林乔', nickname: '乔乔', minor: false },
  habit: { currentDay: 5 }
}

assert.strictEqual(domain.dailyKey(state), 'refeed-3')
assert.strictEqual(domain.dailyKey({ ...state, stage: 'habit' }), 'habit-5')
assert.strictEqual(domain.registrationStatus({ confirmMode: 'manual' }), 'pending')
assert.strictEqual(domain.registrationStatus({ confirmMode: 'auto' }), 'confirmed')
assert.deepStrictEqual(domain.canPublish(state), { allowed: true, reason: '' })
assert.strictEqual(domain.canPublish({ ...state, phoneLinked: false }).reason, 'phone_required')
assert.strictEqual(domain.canPublish({ ...state, profile: { ...state.profile, minor: true } }).reason, 'minor_blocked')
assert.strictEqual(domain.cleanDisplayTitle('❥【慢一点吃饭】(^_-)'), '慢一点吃饭')

const record = domain.privateRecord('light', '  今天慢一点  ')
const post = domain.publicPost(state, record.note, 'habit-5', 100)
post.content = '公开副本已编辑'
assert.strictEqual(record.note, '今天慢一点', '编辑公开副本不能改变私人记录')
assert.strictEqual(record.private, true)
assert.strictEqual(post.sourcePrivateKey, 'habit-5')

console.log('domain.test.js OK')
