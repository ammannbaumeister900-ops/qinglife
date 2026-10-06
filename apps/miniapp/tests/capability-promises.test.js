const { test } = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const vm = require('node:vm')
const root = path.resolve(__dirname, '..')
const plain = value => JSON.parse(JSON.stringify(value))
const event = key => ({ currentTarget: { dataset: { key } } })

function fixture(storage = new Map(), business = { enabled: () => false }) {
  const modals = [], toasts = []
  let failStorage = false
  const wx = {
    getStorageSync: key => storage.has(key) ? plain(storage.get(key)) : '',
    setStorageSync: (key, value) => {
      if (failStorage) throw new Error('storage full')
      storage.set(key, plain(value))
    },
    showModal: value => modals.push(value),
    showToast: value => toasts.push(value),
    request: () => { throw new Error('Local preferences must not call an API') },
    requestSubscribeMessage: () => { throw new Error('No subscription authorization is connected') }
  }
  const storeModule = { exports: {} }
  vm.runInNewContext(fs.readFileSync(path.join(root, 'utils/store.js'), 'utf8'), {
    module: storeModule, wx, require: id => require(path.resolve(root, 'utils', id))
  })
  const store = storeModule.exports
  store.ensureState()
  function page(name) {
    let result
    const mocks = {
      '../../utils/store': store, '../../services/business-api': business,
      '../../services/article-favorites': {}, '../../utils/navigation': {}
    }
    vm.runInNewContext(fs.readFileSync(path.join(root, 'pages', name, 'index.js'), 'utf8'), {
      wx, Page: value => { result = value },
      require: id => mocks[id] || require(path.resolve(root, 'pages', name, id))
    })
    result.data = plain(result.data)
    result.setData = value => Object.assign(result.data, value)
    return result
  }
  return { page, store, modals, toasts, storage, failStorage: () => { failStorage = true } }
}

test('reminder preference persists locally across reopening without enabling or revoking subscriptions', () => {
  for (const enabled of [false, true]) {
    const f = fixture(new Map(), { enabled: () => enabled })
    const page = f.page('mine-flow')
    page.toggleSubscription()
    assert.equal(f.storage.get(f.store.STORAGE_KEY).subscriptionEnabled, true)
    assert.equal(f.modals[0].title, '本机提醒偏好')
    assert.match(f.modals[0].content, /偏好已保存到本机/)
    assert.match(f.modals[0].content, /当前不会发送活动提醒或结营回访消息/)
    assert.doesNotMatch(f.modals[0].content, /已开启.*提醒/)
    const reopened = fixture(f.storage, { enabled: () => enabled })
    assert.equal(reopened.store.getState().subscriptionEnabled, true)
    reopened.page('mine-flow').toggleSubscription()
    assert.equal(reopened.store.getState().subscriptionEnabled, false)
    assert.match(reopened.modals[0].content, /不接收提醒的偏好已保存到本机/)
    assert.match(reopened.modals[0].content, /不会更改微信订阅授权/)
    assert.doesNotMatch(reopened.modals[0].content, /已关闭订阅消息/)
  }
})

test('privacy toggles only save their local preference and preserve registration consent', () => {
  const f = fixture()
  const page = f.page('mine-flow')
  const before = plain(f.store.getState())
  for (const key of ['operationConsent', 'aiConsent']) {
    page.togglePrivacy(event(key))
    assert.equal(f.store.getState()[key], !before[key])
    assert.equal(fixture(f.storage).store.getState()[key], !before[key])
    assert.equal(f.toasts.at(-1).title, '偏好已保存到本机')
    page.togglePrivacy(event(key))
    assert.equal(f.store.getState()[key], before[key])
  }
  assert.deepEqual(plain(f.store.getState().registration), before.registration)
  for (const key of ['registration', 'subscriptionEnabled', '__proto__', 'unknown']) page.togglePrivacy(event(key))
  assert.deepEqual(plain(f.store.getState()), before)
  assert.equal(f.toasts.length, 4)
})

test('failed local storage never reports a saved preference or changes the displayed switch', () => {
  for (const key of ['subscriptionEnabled', 'operationConsent', 'aiConsent']) {
    const f = fixture()
    const page = f.page('mine-flow')
    page.refresh()
    const before = plain(f.store.getState())
    f.failStorage()
    if (key === 'subscriptionEnabled') page.toggleSubscription()
    else page.togglePrivacy(event(key))
    assert.deepEqual(plain(f.store.getState()), before)
    assert.equal(page.data.state[key], before[key])
    assert.equal(f.modals.length, 0)
    assert.equal(f.toasts.length, 1)
    assert.equal(f.toasts[0].title, '本机偏好保存失败，请重试')
  }
})

test('settings label each switch as local and do not promise server consent changes', () => {
  const source = fs.readFileSync(path.join(root, 'pages/mine-flow/index.wxml'), 'utf8')
  const privacy = source.split("view === 'privacy'")[1].split('</block>')[0]
  assert.match(privacy, /下方开关仅保存本机偏好，不会同步或更改已提交的授权/)
  for (const label of ['运营联系偏好（本机）', '个性化分析偏好（本机）', '提醒偏好（本机）']) assert.ok(privacy.includes(label))
  assert.match(privacy, /不会更改服务端授权或联系安排/)
  assert.match(privacy, /不会开启分析或撤回已提交的授权/)
  assert.match(privacy, /当前不会发送提醒消息/)
})

test('successful API registration opens the pending journey with a received-only acknowledgement', async () => {
  const submissions = []
  const state = plain(require('../utils/store').defaultState())
  const activity = { ...require('../data/demo').activities[0], id: 'session-test', days: [] }
  const registration = { activityId: activity.id, status: 'pending', serviceConsent: true,
    participants: [{ id: 'person-self', registrationId: 'registration-test', selected: true }] }
  state.registration = registration
  state.registrations = { [activity.id]: registration }
  const business = {
    enabled: () => true,
    register: async payload => { submissions.push(plain(payload)); return { registrationStatus: 'pending' } },
    context: async () => ({ activity, state, activities: [activity], overview: { customerId: 'customer-test', experienceRecords: [] } })
  }
  const f = fixture(new Map(), business)
  const page = f.page('camp-flow')
  page.setData({ activity, state, draft: { serviceConsent: true, contactName: '测试轻友', contactPhone: '13800000000',
    participants: [{ id: 'person-self', name: '测试轻友', selected: true, phone: '' }] } })
  await page.submitRegistration()
  assert.equal(submissions.length, 1)
  assert.equal(page.data.view, 'journey')
  assert.equal(page.data.registration.status, 'pending')
  assert.equal(page.data.submitting, false)
  assert.deepEqual(f.toasts.map(value => value.title), ['报名已收到'])
})

test('success and actual pending journey direct users to status lookup without promising automatic notification', () => {
  const source = fs.readFileSync(path.join(root, 'pages/camp-flow/index.wxml'), 'utf8')
  for (const view of ['success', 'journey']) {
    const section = source.split('<block wx:elif="{{view === \'' + view + '\'}}">')[1].split('</block>')[0]
    assert.match(section, /wx:if="{{(?:state\.)?registration\.status === 'pending'}}" class="privacy-note">请返回轻体营查看本期报名状态，或联系工作人员确认。当前不会自动发送确认通知。/)
    assert.doesNotMatch(section, /确认后将通知你|确认后会通知|将自动通知|会自动通知/)
  }
})
