const { test } = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs'), path = require('node:path'), vm = require('node:vm')
function page(api, storage = new Map()) {
  let result
  const toasts = []
  const wx = { getStorageSync: key => storage.get(key), setStorageSync: (key, value) => storage.set(key, JSON.parse(JSON.stringify(value))), removeStorageSync: key => storage.delete(key), showToast: value => toasts.push(value.title), navigateBack() {} }
  vm.runInNewContext(fs.readFileSync(path.resolve(__dirname, '../pages/friend-registration/index.js'), 'utf8'), { require: () => api, Page: value => { result = value }, wx, Date, Math, console })
  result.data = JSON.parse(JSON.stringify(result.data))
  result.setData = patch => { for (const [key, value] of Object.entries(patch)) { const parts = key.split('.'); if (parts.length === 2) result.data[parts[0]][parts[1]] = value; else result.data[key] = value } }
  return { result, toasts, storage }
}
const profile = { id: 'owner-a', name: '测试轻友', phone: '13800001234', birthDate: '1990-01-01', heightCm: 165, weightKg: 55.5, city: '上海', assessmentCount: 1 }
const record = { ...profile, id: 'record-a', province: '上海市', city: '上海市', revision: 1, canEdit: true, profileSnapshotAvailable: true, submittedAt: '2026-10-02 10:00:00', editableUntil: '2099-01-01 10:00:00', cleanBodyGoals: '["调理"]', dietPreference: '荤素各半', waterIntakeMl: 1500, wakeTime: '07:00', sleepTime: '23:00', bowelStatus: '1次/天', energyStatus: '一般', exerciseStatus: '偶尔运动', emotionalStatus: '["平静"]', healthConditions: '["无"]', referralSource: '虚构测试' }
test('province/city picker replaces free text and keeps the two structured values', async () => {
  const { result: p } = page({ friendProfile: async () => profile })
  await p.onLoad()
  assert.ok(p.validate(1))
  p.pickRegion({ detail: { value: ['浙江省', '杭州市', '西湖区'] } })
  assert.equal(p.data.form.province, '浙江省'); assert.equal(p.data.form.city, '杭州市')
  assert.equal(p.validate(1), '')
  p.pickRegion({ detail: { value: [] } })
  assert.equal(p.data.form.city, '杭州市')
})
test('drafts are scoped to the verified profile and unowned legacy drafts never appear', async () => {
  const storage = new Map([['qinglife_friend_assessment_draft_2026_v1', { clientRequestId: 'legacy-request', name: '其他账号', phone: 'private-legacy' }]])
  const { result: a } = page({ friendProfile: async () => profile }, storage)
  await a.onLoad(); assert.equal(a.data.form.name, '测试轻友')
  a.field({ currentTarget: { dataset: { field: 'name' } }, detail: { value: '甲的草稿' } })
  const { result: b } = page({ friendProfile: async () => ({ ...profile, id: 'owner-b' }) }, storage)
  await b.onLoad(); assert.equal(b.data.form.name, '测试轻友')
  const { result: again } = page({ friendProfile: async () => profile }, storage)
  await again.onLoad(); assert.equal(again.data.form.name, '甲的草稿')
})
test('history is normalized and editing uses PUT with revision without creating a new record', async () => {
  let saved, creates = 0
  const { result: p, storage } = page({ friendProfile: async () => profile, friendAssessments: async () => [record], submitFriendAssessment: async () => { creates++ }, amendFriendAssessment: async (id, payload) => { saved = { id, payload }; return { assessmentId: id, assessmentCount: 1, editableUntil: record.editableUntil } } })
  await p.onLoad()
  p.field({ currentTarget: { dataset: { field: 'name' } }, detail: { value: '新登记草稿' } })
  const newDraft = storage.get(p.draftKey)
  await p.openHistory(); assert.equal(p.data.mode, 'history')
  p.viewAssessment({ currentTarget: { dataset: { id: record.id } } })
  assert.equal(p.data.mode, 'detail'); assert.equal(p.data.record.healthConditions[0], '无')
  await p.editAssessment()
  assert.equal(p.data.editingId, record.id); assert.equal(p.data.form.revision, 1)
  assert.equal(p.data.form.sensitiveConsent, false)
  p.data.form.sensitiveConsent = true
  await p.submit()
  assert.equal(creates, 0); assert.equal(saved.id, record.id); assert.equal(saved.payload.revision, 1)
  assert.equal(p.data.assessmentCount, 1); assert.equal(p.data.wasEditing, true)
  assert.equal(storage.get(p.draftKey).clientRequestId, newDraft.clientRequestId)
})
test('expired or unreadable history cannot enter edit mode; failed saves keep the retry identity', async () => {
  let rows = [{ ...record, canEdit: false }]
  const { result: p, toasts } = page({ friendProfile: async () => profile, friendAssessments: async () => rows, amendFriendAssessment: async () => { throw Error('网络暂时不可用') } })
  await p.onLoad(); await p.openHistory(); p.viewAssessment({ currentTarget: { dataset: { id: record.id } } })
  await p.editAssessment(); assert.equal(p.data.editingId, '')
  rows = [{ ...record, editableUntil: '2000-01-01 00:00:00' }]
  await p.editAssessment(); assert.equal(p.data.editingId, '')
  rows = [record]; await p.editAssessment()
  const request = p.data.form.clientRequestId
  p.data.form.sensitiveConsent = true; await p.submit()
  assert.equal(p.data.form.clientRequestId, request); assert.equal(p.data.editingId, record.id)
  assert.equal(p.data.submitted, false); assert.equal(toasts.at(-1), '网络暂时不可用')
  p.cancelEdit(); assert.equal(p.data.editingId, ''); assert.equal(p.data.mode, 'detail')
})
