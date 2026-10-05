const { test } = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs'), path = require('node:path'), vm = require('node:vm')
const root = path.resolve(__dirname, '..'), domain = require('../utils/domain')
const activity = { id: 'session', name: '一场相聚', startDate: '2099-01-01', endDate: '2099-01-03', days: [], status: '开放报名', seatsLeft: 5, statusTone: 'open' }
function page(name, business, nav = [], notices = []) {
  let p
  vm.runInNewContext(fs.readFileSync(path.join(root, 'pages', name, 'index.js'), 'utf8'), {
    Page: value => { p = value }, wx: { showToast: value => notices.push(value.title), pageScrollTo() {} },
    require: id => id.includes('business-api') ? business : id.includes('navigation') ? { navigateTo: value => nav.push(value.url) } : require(path.resolve(root, 'pages', name, id))
  })
  p.data = JSON.parse(JSON.stringify(p.data)); p.setData = data => Object.assign(p.data, data)
  return p
}
function context(nickname = '小禾', registrations = {}) {
  return { activity, activities: [activity], overview: { customerId: 'owner' }, state: { backend: true, loggedIn: true, phoneLinked: true, phone: '13800000001', profile: { name: '真实姓名', nickname, minor: false }, registrations, registration: { status: 'none' } } }
}
test('business context maps real nickname without falling back to real name', async () => {
  let nickname = ' 小禾 '
  const module = { exports: {} }
  vm.runInNewContext('(function(require,module,exports){' + fs.readFileSync(path.join(root,'services/business-api.js'),'utf8') + '\n})', {
    wx: { getStorageSync: () => 'fixture-token', request: options => options.success({statusCode:200,data:{code:200,data: options.url.endsWith('/sessions') ? [{id:'s',startDate:'2099-01-01',endDate:'2099-01-03',status:'open',capacity:5}] : {customerId:'own',profile:{name:'真实姓名',nickname,phone:'13800000001'},registrations:[{sessionId:'s',isSelf:1,participantName:'真实姓名',registrationStatus:'pending'}]}}}) }
  })(id => id.includes('runtime') ? {environment:'staging',businessBaseUrl:'https://example.test'} : {getState:()=>({})},module,module.exports)
  let result = await module.exports.context()
  assert.equal(result.state.profile.nickname,'小禾'); assert.equal(result.state.profile.name,'真实姓名'); assert.equal(result.state.registrations.s.participants[0].name,'小禾')
  nickname = ''
  result = await module.exports.context()
  assert.equal(result.state.profile.nickname,''); assert.equal(result.state.registrations.s.participants[0].name,'本人')
})
test('new signup defaults to nickname and necessary information; explicit unchecking survives navigation and refresh', async () => {
  const state = context(), notices = [], p = page('camp-flow',{enabled:()=>true,context:async()=>state},[],notices)
  await p.refresh(); await p.startRegistration(); assert.equal(p.data.draft.participants[0].name,'小禾'); assert.equal(p.data.draft.contactName,'小禾')
  p.nextStep(); assert.equal(p.data.step,2); assert.equal(p.data.draft.serviceConsent,true)
  p.toggleConsent(); p.previousStep(); p.nextStep(); await p.refresh(); assert.equal(p.data.draft.serviceConsent,false)
  await p.submitRegistration(); assert.equal(p.data.view,'register'); assert(notices.includes('请确认参与人和必要授权'))
})
test('missing nickname stays blank for signup and is never replaced with the real name', async () => {
  const p=page('camp-flow',{enabled:()=>true,context:async()=>context('')})
  await p.refresh(); assert.equal(p.data.draft.participants[0].name,''); assert.equal(p.data.draft.contactName,'')
})
test('booked camp reopens service even when registration window is closed or full', async () => {
  for (const status of ['pending','confirmed','completed','waitlisted','cancelled','rescheduled']) {
    const reg={activityId:'session',status,participants:[{id:'person-self',selected:true,status}]}, c=context('小禾',{session:reg}); c.activity={...activity,seatsLeft:0,status:'已满'}; c.activities=[c.activity]
    const nav=[], business={enabled:()=>true,context:async()=>c,campVoices:async()=>[]}, camp=page('camp',business,nav)
    await camp.onShow(); assert.equal(camp.data.visibleActivities[0].hasRegistration,true); camp.signup({currentTarget:{dataset:{id:'session'}}}); assert.equal(nav[0],'/pages/camp-flow/index?view=journey&id=session')
    const flow=page('camp-flow',business); flow.onLoad({id:'session',view:'register'}); await flow.onShow(); assert.equal(flow.data.view,'journey'); await flow.startRegistration(); assert.equal(flow.data.view,'journey')
  }
})
test('service status distinguishes pending settlement, unpaid, paid, no payment and pass usage', () => {
  assert.equal(domain.campServiceStatus({status:'pending',paymentStatus:'unpaid',finalAmount:999}).payment,'金额待确认')
  const money={status:'confirmed',settlementStatus:'confirmed',settlementType:'money',finalAmount:'200.00',paymentStatus:'unpaid'}
  assert.equal(domain.campServiceStatus(money).payment,'待付款'); assert.equal(domain.campServiceStatus({...money,paymentStatus:'paid'}).payment,'已付款'); assert.equal(domain.campServiceStatus({...money,finalAmount:0}).payment,'无需付款')
  assert.equal(domain.campServiceStatus({...money,finalAmount:null}).payment,'金额待确认'); assert.equal(domain.campServiceStatus({...money,settlementType:'pass',passUnits:2}).paymentNote,'本次使用 2 次卡次')
  assert.equal(domain.campServiceStatus({...money,status:'cancelled',settlementStatus:'pending'}).registration,'已取消')
})
test('mine handles missing nickname and opens existing friend dossier entry', async () => {
  const nav=[],p=page('mine',{enabled:()=>true,context:async()=>context('')},nav)
  await p.refresh(); assert.equal(p.data.state.profile.nickname,''); assert.equal(p.data.avatarText,'轻'); p.openFriendRegistration(); assert.equal(nav[0],'/pages/friend-registration/index')
})
