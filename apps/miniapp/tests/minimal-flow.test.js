const assert = require('assert')
const fs = require('fs')
const vm = require('vm')
const path = require('path')
const { createRequire } = require('module')
const domain = require('../utils/domain')
const store = require('../utils/store')
const demo = require('../data/demo')
const api = require('../services/legacy-api')
const root = path.resolve(__dirname, '..')
function page(name) {
  const filename = path.join(root, 'pages', name, 'index.js')
  let instance
  vm.runInNewContext(fs.readFileSync(filename, 'utf8'), { require: createRequire(filename), Page(config) { instance = config }, wx: global.wx, Date, setTimeout, clearTimeout })
  instance.data = JSON.parse(JSON.stringify(instance.data))
  instance.setData = values => Object.assign(instance.data, values)
  return instance
}
async function run() {
  global.wx = { showToast() {}, showModal() {}, previewImage() {} }
  store.resetState()
  const state = store.getState()
  state.registration.status = 'pending'
  assert.equal(domain.homeTask(state, demo.activities).route, 'journey', '待确认不能落回获客首页')
  assert.equal(domain.homeTask({ ...state, registration: { ...state.registration, status: 'confirmed' } }, demo.activities, new Date('2026-09-06T12:00:00')).action, '查看准备与行程')
  assert.equal(domain.homeTask({ ...state, registration: { ...state.registration, status: 'confirmed' } }, demo.activities, new Date('2026-09-13T12:00:00')).title, '查看今天的行程')
  assert.equal(domain.homeTask({ ...state, loggedIn: false }, []).route, 'reading')
  assert.equal(domain.activityState({ status: '开放报名', seatsLeft: 0 }).canRegister, false)
  assert.equal(domain.activityState({ status: '开放报名', seatsLeft: 0 }).label, '已满')
  assert.equal(domain.activityState({ status: '候补中', seatsLeft: 0 }).canRegister, true)
  const overlapping = { ...state, registration: { ...state.registration, status: 'completed' }, registrations: { 'session-017': { ...state.registration, activityId: 'session-017', status: 'confirmed' } } }
  assert.equal(domain.homeTask(overlapping, demo.activities).activityId, 'session-017')
  const flow = page('camp-flow')
  store.updateState(s => { s.registration.status = 'confirmed'; s.checkedDays = []; return s })
  flow.refresh()
  flow.data.dayViews = [{ id: 'day-1', today: false }]
  flow.checkDay({ currentTarget: { dataset: { id: 'day-1' } } })
  assert.equal(Object.keys(store.getState().arrivalConfirmations || {}).length, 0)
  flow.data.dayViews = [{ id: 'day-1', today: true }]
  flow.checkDay({ currentTarget: { dataset: { id: 'day-1' } } })
  assert.equal(store.getState().arrivalConfirmations['session-016:day-1'], true)
  assert.equal(store.getState().checkedDays.length, 0, '辅助确认不得写真实签到')
  assert.equal(store.getState().registration.status, 'confirmed')
  store.updateState(s => { s.registration.status = 'completed'; return s })
  flow.refresh()
  assert.equal(flow.data.canReview, true, '缺少签到也可回顾')
  const beforeDraft = JSON.stringify(store.getState().registration)
  flow.data.view = 'register'
  flow.toggleParticipant({ currentTarget: { dataset: { id: 'person-child' } } })
  assert.equal(JSON.stringify(store.getState().registration), beforeDraft, '选择参与人不能提前修改原报名')
  const daily = page('friend-flow')
  daily.refresh()
  assert.equal(daily.data.dailyChoice, '')
  daily.saveDaily()
  assert.equal(Object.keys(store.getState().dailyRecords).length, 0, '未选择不能默认保存完成')
  daily.chooseDaily({ currentTarget: { dataset: { value: 'rest' } } })
  daily.saveDaily()
  assert.equal(store.getState().dailyRecords[daily.data.dailyKey].choice, 'rest')
  assert.equal(store.getState().dailyRecords[daily.data.dailyKey].private, true)
  assert.equal(store.getState().newPosts.length, demo.seedPosts.length, '私人保存不创建公开副本')
  // 真实接口的空数据和失败不得替换成 Demo；分页保留原文与媒体。
  let requestedPage
  const raw = { id: 81, nickName: '测试轻友', createDate: '2020.01.02', content: '原文\n第二行', imgList: ['https://example.com/photo.jpg'], comments: [{ id: 9, nickName: '测试', content: '历史回应' }] }
  global.wx.getStorageSync = () => 'test-token'
  global.wx.request = options => { requestedPage = options.data.pageNum; options.success({ statusCode: 200, data: { code: 200, data: { list: [raw], isNext: true } } }) }
  const result = await api.getLegacyPosts(2)
  assert.equal(requestedPage, 2)
  assert.equal(result.list[0].content, raw.content)
  assert.equal(result.list[0].time, raw.createDate)
  assert.deepEqual(result.list[0].images, raw.imgList)
  assert.equal(result.hasMore, true)
  global.wx.request = options => options.success({ statusCode: 200, data: { code: 200, data: { list: [], isNext: false } } })
  assert.equal((await api.getLegacyPosts()).list.length, 0)
  global.wx.request = options => options.fail(new Error('offline'))
  const failed = await api.getLegacyPosts()
  assert.equal(failed.list.length, 0)
  assert.ok(failed.error)
  assert.equal((await api.getArticle('81')).item.id, undefined, '文章失败不替换其他文章')
  delete global.wx.getStorageSync
  const archive = page('friend-flow')
  archive.onLoad({ view: 'compose' })
  assert.equal(archive.data.view, 'archive', '旧发布深链降级为只读')
  let attempts = 0
  const original = api.getLegacyPosts
  api.getLegacyPosts = async n => { attempts++; return attempts === 1 ? { list: [result.list[0]], hasMore: true } : attempts === 2 ? { error: 'offline', list: [] } : { list: [{ ...result.list[0], id: '82' }], hasMore: false } }
  await archive.loadArchive(); await archive.loadArchive()
  assert.equal(archive.data.archivePosts.length, 1)
  assert.equal(archive.data.archivePage, 2, '失败不跳过页码')
  await archive.loadArchive()
  assert.equal(archive.data.archivePosts.length, 2)
  assert.equal(archive.data.archivePage, 3)
  api.getLegacyPosts = original
  // 每个页面脚本必须可解析。
  const app = JSON.parse(fs.readFileSync(path.join(root, 'app.json'), 'utf8'))
  for (const route of app.pages) new vm.Script(fs.readFileSync(path.join(root, route + '.js'), 'utf8'))
  console.log('minimal-flow.test.js OK: state, arrival, privacy, archive pagination and errors')
}
run().catch(error => { console.error(error); process.exitCode = 1 })
