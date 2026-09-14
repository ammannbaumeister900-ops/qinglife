'use strict'
// Runs the real page handlers against an isolated loopback HTTP Mock. No remote calls.
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const vm = require('node:vm')
const root = path.resolve(__dirname, '..')
process.env.QL_E2E = '1'
const server = require(path.resolve(root, '../../server/api/mock-demo-server.js'))
let base, now = '2026-09-06T04:00:00Z', count = 0
const cases = []
const admin = { authorization: 'Bearer ql-demo-token-30-days' }
async function http(route, method = 'GET', data, headers = admin, expected = 200) {
  const res = await fetch(base + route, { method, headers: { 'content-type': 'application/json', ...headers }, ...(data === undefined ? {} : { body: JSON.stringify(data) }) })
  const result = await res.json()
  assert.equal(result.code, expected, route + ': ' + JSON.stringify(result))
  return result
}
async function test(name, run) { await run(); cases.push(name); console.log('PASS ' + name) }
async function clock(date) { now = date + 'T04:00:00Z'; await http('/__test/clock', 'POST', { now }) }
const event = (dataset = {}, value) => ({ currentTarget: { dataset }, detail: { value } })
async function actor(name, minor = false) {
  const identity = (await http('/__test/actors', 'POST', { name, minor })).data
  const storage = new Map([['qinglife_business_base_url', base], ['qinglife_business_token:' + base, identity.token]])
  const cache = new Map(), toasts = [], requests = [], nav = []
  let offline = false
  class Clock extends Date { constructor(...args) { super(...(args.length ? args : [now])) } static now() { return new Date(now).getTime() } }
  const wx = {
    getStorageSync: key => storage.get(key), setStorageSync: (key, value) => storage.set(key, value), removeStorageSync: key => storage.delete(key),
    showToast: value => toasts.push(value.title), showModal() {},
    navigateTo: x => nav.push(x.url), redirectTo: x => nav.push(x.url), switchTab: x => nav.push(x.url),
    request(options) {
      assert.ok(options.url.startsWith(base + '/'), 'NO PRODUCTION NETWORK')
      requests.push({ url: options.url, data: options.data, method: options.method })
      if (offline) { options.fail({ errMsg: '测试断网' }); return }
      fetch(options.url, { method: options.method || 'GET', headers: options.header, ...(['POST','PUT'].includes(options.method) ? { body: JSON.stringify(options.data) } : {}) })
        .then(async res => options.success({ statusCode: res.status, data: await res.json() })).catch(options.fail)
    }, login() { throw new Error('Fixture token missing; do not call WeChat login in this test') }
  }
  function load(filename, pageCapture) {
    filename = path.resolve(filename)
    if (!path.extname(filename)) filename += '.js'
    if (cache.has(filename) && !pageCapture) return cache.get(filename).exports
    const module = { exports: {} }; cache.set(filename, module)
    const requireLocal = target => target.startsWith('.') ? load(path.resolve(path.dirname(filename), target)) : require(target)
    vm.runInNewContext('(function(require,module,exports){' + fs.readFileSync(filename, 'utf8') + '\n})', { wx, Page: pageCapture, Date: Clock, console, setTimeout, clearTimeout, Promise, getApp: () => ({ globalData: {} }) }, { filename })(requireLocal, module, module.exports)
    return module.exports
  }
  return { ...identity, name, storage, requests, toasts, nav, setOffline: x => { offline = x }, auth: { token: identity.token },
    async page(name, options = {}) {
      let page
      load(path.join(root, 'pages', name, 'index.js'), value => { page = value })
      page.data = JSON.parse(JSON.stringify(page.data)); page.setData = value => Object.assign(page.data, value)
      if (page.onLoad) await page.onLoad(options)
      if (page.onShow) await page.onShow()
      return page
    }
  }
}
async function create(number, start, end, capacity = 8, mode = 'manual') {
  return (await http('/life/session', 'POST', { sessionNumber: number, name: '闭环测试第' + number + '期', startDate: start, endDate: end, capacity, standardPrice: 199, status: 'open', registrationConfirmMode: mode, publicVenue: '虚拟测试场地', city: '测试城市' })).data
}
async function status(session, value) { Object.assign(session, (await http('/life/session', 'PUT', { ...session, status: value })).data) }
async function enter(person, invitation) {
  const options = Object.fromEntries(new URLSearchParams(invitation.path.split('?')[1]))
  return person.page('camp-flow', options)
}
async function signup(person, page) {
  page.startRegistration()
  assert.equal(page.data.view, 'register')
  page.onSelfName(event({}, person.name)); page.onSelfPhone(event({}, '1990000' + String(++count).padStart(4,'0')))
  page.nextStep(); assert.equal(page.data.step, 2)
  page.toggleConsent()
  await Promise.all([page.submitRegistration(), page.submitRegistration()])
  assert.equal(page.data.loadError, '')
  assert.equal(page.data.view, 'journey', person.toasts.join(';'))
  assert.notEqual(page.data.registration.status, 'none', person.toasts.join(';'))
  return page.data.registration.participants.find(p => p.id === 'person-self').registrationId
}
async function confirm(id) { await http('/life/registration', 'PUT', { id, registrationStatus: 'confirmed' }) }
async function overview(person) { return (await http('/app/qinglife/me/overview', 'GET', undefined, person.auth)).data }
async function main() {
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve)); base = 'http://127.0.0.1:' + server.address().port
  try {
    await clock('2026-09-06')
    let first, next, third, staffInvite, a, d, b, p, aReg, dReg, bReg, aInvite
    await test('01 工作人员创建活动及邀请，普通轻友无管理权限', async () => {
      first = await create(900001, '2026-09-07', '2026-09-09')
      next = await create(900002, '2026-09-12', '2026-09-14', 8, 'auto')
      third = await create(900003, '2026-09-17', '2026-09-19', 8, 'auto')
      staffInvite = (await http('/life/session/' + first.id + '/invitation', 'POST', {})).data
      assert.match(staffInvite.code, /^[a-f0-9]{32}$/)
      a = await actor('测试新轻友甲'); d = await actor('测试同期轻友丁'); b = await actor('测试受邀轻友乙')
      await http('/life/session/' + first.id + '/invitation', 'POST', {}, a.auth, 403)
      await http('/life/registration', 'PUT', { id: 'anything', registrationStatus: 'confirmed' }, a.auth, 403)
    })
    await test('02 新轻友受邀，授权前不能报名，双击只产生一次报名', async () => {
      p = await enter(a, staffInvite); assert.equal(p.data.activity.id, first.id); assert.equal(p.data.registration.status, 'none')
      p.startRegistration(); await p.submitRegistration(); assert.equal(p.data.registration.status, 'none')
      p.setData({ view: 'detail' }); aReg = await signup(a, p)
      assert.equal(p.data.registration.status, 'pending'); assert.equal(p.data.registration.paymentStatus, 'unpaid')
      assert.equal((await overview(a)).registrations.length, 1)
      assert.equal(a.requests.filter(r => r.method === 'POST' && r.url.endsWith('/registrations')).length, 1)
      dReg = await signup(d, await enter(d, staffInvite))
    })
    await test('03 重试幂等、不同内容复用请求号拒绝、跨账号无报名泄漏', async () => {
      const payload = a.requests.find(r => r.url.endsWith('/registrations')).data
      const replay = (await http('/app/qinglife/registrations', 'POST', payload, a.auth)).data
      assert.equal(replay.registrations[0].registrationId, aReg)
      await http('/app/qinglife/registrations', 'POST', { ...payload, sessionId: next.id }, a.auth, 409)
      await http('/app/qinglife/registrations', 'POST', { ...payload, sessionId: next.id }, b.auth, 400) // invitation mismatch, not somebody else's replay
      assert.equal((await overview(b)).registrations.length, 0)
      await http('/app/qinglife/registrations', 'POST', { ...payload, clientRequestId: 'new-duplicate' }, a.auth, 409)
    })
    await test('04 待确认不能记录或老带新；工作人员确认后状态回读且不等于付款', async () => {
      await http('/app/qinglife/experience-records', 'PUT', { registrationId: aReg, phase: 'before', note: '待确认' }, a.auth, 400)
      await http('/app/qinglife/sessions/' + next.id + '/invitations', 'POST', {}, a.auth, 403)
      await confirm(aReg); await confirm(dReg); await p.refresh()
      assert.equal(p.data.registration.status, 'confirmed'); assert.equal(p.data.registration.paymentStatus, 'unpaid')
      const home = await a.page('home'), camp = await a.page('camp'), mine = await a.page('mine')
      assert.equal(home.data.view.activityId, first.id); assert.equal(camp.data.currentRegistrations[0].status, 'confirmed'); assert.equal(mine.data.participationTimeline[0].id, first.id)
      p.openFeeling(); assert.equal(p.data.energy, null); assert.equal((await overview(a)).experienceRecords.length, 0)
      p.chooseScale(event({ field:'energy', value:3 })); p.onFeelingInput(event({}, '测试活动前感受')); await p.saveFeeling()
      assert.equal((await overview(a)).experienceRecords[0].phase, 'before')
    })
    await test('05 三日感受逐日保存、重进可读、跨日不带默认答案、漏签到不阻断', async () => {
      await status(first, 'in_progress')
      for (let day=7; day<=9; day++) {
        await clock('2026-09-0' + day); await p.refresh()
        assert.equal(p.data.loadError, '', '邀请过期不应挡住已经打开的本人行程')
        assert.equal(p.data.feelingPhase, 'during'); assert.equal(p.data.energy, null); assert.equal(p.data.feelingNote, '')
        p.openFeeling(); p.chooseScale(event({ field:'energy', value:day-4 })); p.chooseScale(event({ field:'relaxation', value:4 })); p.onFeelingInput(event({}, '测试第' + (day-6) + '天私人感受')); await p.saveFeeling()
        await p.refresh(); assert.equal(p.data.energy, day-4)
      }
      const rows = (await overview(a)).experienceRecords
      assert.equal(rows.length, 4); assert.equal(new Set(rows.filter(r=>r.phase==='during').map(r=>r.nodeKey)).size,3)
      assert.ok(rows.every(r=>r.visibility==='private'))
      const relaunch = await a.page('camp-flow', { id:first.id, view:'journey' }); assert.equal(relaunch.data.feelingNote, '测试第3天私人感受')
      await http('/app/qinglife/experience-records', 'PUT', { registrationId:aReg, phase:'during', sessionDayId:first.id+'-day-1', note:'跨日覆盖' }, a.auth, 400)
      await http('/app/qinglife/experience-records', 'PUT', { registrationId:aReg, phase:'during', sessionDayId:first.id+'-day-3', energy:6 }, a.auth, 400)
      assert.equal((await overview(d)).experienceRecords.length,0)
      await http('/app/qinglife/experience-records', 'PUT', { registrationId:aReg, phase:'after', note:'越权' }, d.auth, 403)
    })
    await test('06 完成轻体可回顾并生成下期邀请，分享不包含私人记录或身份', async () => {
      await clock('2026-09-10'); await status(first,'completed'); await p.refresh()
      assert.equal((await a.page('home')).data.view.route,'experience', '完成后的首页不得跳到本地演示记录');
      assert.equal(p.data.canReview,true); assert.ok(p.data.dayViews.every(day=>!day.checked))
      p.openExperience(); p.onReviewInput(event({}, '测试完成后的私人感受')); await p.saveReview(); assert.equal(p.data.reviewSaved,true)
      p.openInvite(); await p.prepareInvitation(event({id:next.id})); aInvite=p.onShareAppMessage()
      assert.ok(aInvite.path.includes('invite=')); assert.equal(aInvite.title,next.name)
      assert.ok(!aInvite.path.includes(a.customerId)); assert.ok(!JSON.stringify(aInvite).includes('私人感受'))
      assert.equal((await overview(a)).experienceRecords.length,5)
      await http('/app/qinglife/invitations/' + staffInvite.code,'GET',undefined,{},400)
    })
    await test('07 受邀新轻友报名闭环，首次及本期推荐人为甲，自动确认仍未付款', async () => {
      const bp=await enter(b,aInvite); bReg=await signup(b,bp)
      assert.equal(bp.data.registration.status,'confirmed'); assert.equal(bp.data.registration.paymentStatus,'unpaid')
      const customers=(await http('/life/customer/list?pageSize=1000')).rows
      assert.equal(customers.find(c=>c.id===b.customerId).firstReferrerCustomerId,a.customerId)
      const regs=(await http('/life/registration/list?pageSize=1000')).rows
      assert.equal(regs.find(r=>r.id===bReg).sessionReferrerCustomerId,a.customerId)
      assert.equal((await overview(b)).experienceRecords.length,0)
    })
    await test('08 老轻友再次参加，首次推荐不变，本期推荐独立，历史感受不覆盖', async () => {
      const dInvite=(await http('/app/qinglife/sessions/'+third.id+'/invitations','POST',{},d.auth)).data
      const secondReg=await signup(b,await enter(b,dInvite))
      const customers=(await http('/life/customer/list?pageSize=1000')).rows
      const regs=(await http('/life/registration/list?pageSize=1000')).rows
      assert.equal(customers.find(c=>c.id===b.customerId).firstReferrerCustomerId,a.customerId)
      assert.equal(regs.find(r=>r.id===secondReg).sessionReferrerCustomerId,d.customerId)
      assert.equal((await overview(a)).experienceRecords.length,5)
      assert.equal((await overview(b)).registrations.length,2)
    })
    await test('09 代报名只见参与状态，不能代写私人感受；未成年不能发起老带新', async () => {
      const parent=await actor('测试家长'), child=await actor('测试未成年',true)
      const body={sessionId:third.id,clientRequestId:'family',serviceConsent:true,participants:[{self:true,name:parent.name,phone:'19999990001',minor:false},{self:false,name:'测试同行儿童',minor:true,relation:'子女'}]}
      const result=(await http('/app/qinglife/registrations','POST',body,parent.auth)).data
      const childReg=result.registrations.find(r=>r.customerId!==parent.customerId)
      assert.equal((await overview(parent)).registrations.length,2)
      await http('/app/qinglife/experience-records','PUT',{registrationId:childReg.registrationId,phase:'before',note:'代写'},parent.auth,403)
      await http('/app/qinglife/sessions/'+third.id+'/invitations','POST',{},child.auth,403)
      await http('/app/qinglife/registrations','POST',{...body,clientRequestId:'forged-person',participants:[{name:'冒用参与者',customerId:a.customerId,phone:'19999990002'}]},parent.auth,403)
    })
    await test('10 无效邀请、错期邀请、满额、断网均无假成功，错误不退回演示活动', async () => {
      const invalid=await a.page('camp-flow',{id:first.id,invite:'invalid'}); assert.ok(invalid.data.loadError)
      const mismatch=await a.page('camp-flow',{id:third.id,invite:new URLSearchParams(aInvite.path.split('?')[1]).get('invite')}); assert.match(mismatch.data.loadError,/不匹配/)
      const unknown=await a.page('camp-flow',{id:'does-not-exist'}); assert.ok(unknown.data.loadError)
      const tiny=await create(900004,'2026-09-20','2026-09-22',1,'auto')
      const tinyInvite=(await http('/life/session/'+tiny.id+'/invitation','POST',{})).data
      await signup(a,await enter(a,tinyInvite))
      const full=await enter(d,tinyInvite); assert.equal(full.data.availability.canRegister,false)
      const body={sessionId:tiny.id,clientRequestId:'full',serviceConsent:true,participants:[{self:true,name:d.name,phone:'19999990003'}]}
      await http('/app/qinglife/registrations','POST',body,d.auth,400)
      a.setOffline(true); const offline=await a.page('camp-flow',{id:third.id}); assert.ok(offline.data.loadError); a.setOffline(false)
      assert.equal((await overview(d)).registrations.length,1)
      const retry=await d.page('camp-flow',{id:third.id}); await retry.startRegistration(); retry.onSelfPhone(event({},'19999990009')); retry.toggleConsent()
      d.setOffline(true); await retry.submitRegistration(); assert.equal(retry.data.view,'register'); assert.equal((await overview(d)).registrations.length,1); d.setOffline(false)
      await retry.submitRegistration(); assert.equal(retry.data.view,'journey'); assert.equal((await overview(d)).registrations.length,2)
      a.storage.delete('qinglife_business_token:' + base); const guest=await a.page('mine'); assert.equal(guest.data.state.loggedIn,false); assert.equal(guest.data.participationTimeline.length,0)
    })
    fs.mkdirSync(path.join(root,'test-artifacts'),{recursive:true})
    fs.writeFileSync(path.join(root,'test-artifacts/lifecycle-e2e-results.json'),JSON.stringify({generatedAt:new Date().toISOString(),mode:'isolated HTTP Mock + actual Mini Program page handlers',production:false,groups:cases.length,cases},null,2))
    console.log('LIFECYCLE PASS: '+cases.length+' groups; real HTTP; no production access')
  } finally { await new Promise(resolve=>server.close(resolve)) }
}
main().catch(error=>{console.error(error);process.exitCode=1})
