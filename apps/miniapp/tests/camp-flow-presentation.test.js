const { test } = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs'), path = require('node:path'), vm = require('node:vm')
const root = path.resolve(__dirname, '..')
const source = fs.readFileSync(path.join(root, 'pages/camp-flow/index.wxml'), 'utf8')
const plain = value => JSON.parse(JSON.stringify(value))

// Render the relevant WXML conditions and bindings, including if inside a repeated row.
function parse(xml = source) {
  const tree = { children: [] }, stack = [tree]
  for (const token of xml.match(/<(?:[^<>"']|"[^"]*"|'[^']*')+>|[^<]+/g) || []) {
    if (token.startsWith('</')) {
      assert.equal(stack.pop().tag, token.match(/^<\/([\w-]+)/)[1]); continue
    }
    if (!token.startsWith('<')) { stack.at(-1).children.push({ text: token }); continue }
    const tag = token.match(/^<([\w-]+)/)[1], attrs = {}
    for (const a of token.slice(tag.length + 1).replace(/\/?\s*>$/, '').matchAll(/([\w:-]+)(?:\s*=\s*(?:"([^"]*)"|'([^']*)'))?/g)) attrs[a[1]] = a[2] ?? a[3] ?? true
    const node = { tag, attrs, children: [] }
    stack.at(-1).children.push(node)
    if (!token.endsWith('/>')) stack.push(node)
  }
  assert.equal(stack.length, 1)
  return tree
}
function expression(code, scope) {
  try { return vm.runInNewContext(code, scope) } catch (error) {
    // WeChat treats a missing optional binding as empty (e.g. formError on a clean step).
    if (error.name === 'TypeError' || error.name === 'ReferenceError') return undefined
    throw error
  }
}
function value(template, scope) {
  if (template === true) return true
  const binding = template.match(/^{{([\s\S]*?)}}$/)
  if (binding && !binding[1].includes('}}')) return expression(binding[1], scope)
  return template.replace(/{{([\s\S]*?)}}/g, (_, code) => expression(code, scope) ?? '')
}
function render(children, scope) {
  const result = []
  let branch = false
  for (const node of children) {
    if ('text' in node) { result.push({ text: String(value(node.text, scope) ?? '') }); continue }
    const a = node.attrs
    const scopes = a['wx:for'] ? (value(a['wx:for'], scope) || []).map((item, index) => ({ ...scope, [a['wx:for-item'] || 'item']: item, [a['wx:for-index'] || 'index']: index })) : [scope]
    for (const itemScope of scopes) {
      let show = true
      if ('wx:if' in a) { show = !!value(a['wx:if'], itemScope); branch = show }
      else if ('wx:elif' in a) { show = !branch && !!value(a['wx:elif'], itemScope); branch ||= show }
      else if ('wx:else' in a) { show = !branch; branch = true }
      else branch = false
      if (!show) continue
      const attrs = Object.fromEntries(Object.entries(a).filter(([key]) => !key.startsWith('wx:')).map(([key, text]) => [key, value(text, itemScope)]))
      result.push({ tag: node.tag, attrs, children: render(node.children, itemScope) })
    }
  }
  return result
}
function flatten(nodes) { return nodes.flatMap(node => [node, ...flatten(node.children || [])]) }
const has = (node, name) => String(node.attrs?.class || '').split(' ').includes(name)
const content = node => flatten([node]).filter(row => 'text' in row).map(row => row.text).join('').trim()
const control = (nodes, handler) => nodes.find(node => node.attrs?.bindtap === handler || node.attrs?.bindinput === handler)

async function fixture(view = 'detail', status = 'confirmed') {
  let page
  const activity = { ...plain(require('../data/demo').activities[0]), id: 'session-live', sessionNumber: 101, startDate: '2099-01-01', endDate: '2099-01-03', date: '2099年1月1日—3日', fee: 300, returningFee: 0, theme: '本期饮食与身体体验', cover: '', sourceStatus: 'open' }
  const participants = [{ id: 'person-self', registrationId: 'self-reg', name: '小禾', relation: '本人', phone: '', selected: true }, { id: 'family', name: '家人', relation: '家人', phone: '', selected: true }]
  const registration = { activityId: activity.id, status, participants, serviceConsent: true }
  const state = { backend: true, invitationEligible: true, loggedIn: true, phoneLinked: true, phone: '13800000001', profile: { nickname: '小禾', minor: false }, registration, registrations: { [activity.id]: registration } }
  const context = { activity, activities: [activity], state, overview: { customerId: 'owner', experienceRecords: [] } }
  const reflectionContext = { canBefore: false, canAfter: false }
  const writes = [], scrolls = [], callbacks = [], notices = [], links = [], previews = []
  const business = { enabled: () => true, context: async () => context,
    campReflection: async () => reflectionContext,
    register: async data => writes.push(plain(data)), saveExperience: async data => writes.push(plain(data)),
    createInvitation: async id => ({ path: '/pages/camp-flow/index?id=' + id }) }
  const mocks = { '../../services/business-api': business, '../../utils/navigation': { navigateTo: data => links.push(data.url), switchTab: data => links.push(data.url), returnTo: data => links.push(data.url) } }
  vm.runInNewContext(fs.readFileSync(path.join(root, 'pages/camp-flow/index.js'), 'utf8'), {
    Page: data => { page = data }, wx: { previewImage: data => previews.push(data), getImageInfo: data => data.success({path:data.src}), showToast: data => notices.push(data.title), pageScrollTo: data => scrolls.push(plain(data)) },
    require: id => mocks[id] || require(path.resolve(root, 'pages/camp-flow', id))
  })
  page.data = plain(page.data)
  page.setData = (patch, callback) => { Object.assign(page.data, patch); if (callback) callbacks.push(callback) }
  page.onLoad({ view, id: activity.id })
  await page.refresh()
  const tree = () => render(parse().children, page.data)
  return { page, business, context, reflectionContext, writes, scrolls, notices, links, previews, tree, nodes: () => flatten(tree()), flush: () => { while (callbacks.length) callbacks.shift()() } }
}
const event = (id, value) => ({ currentTarget: { dataset: { id } }, detail: { value } })

test('detail follows the approved hierarchy with real content, both fees and existing admission gates', async () => {
  const f = await fixture(), a = f.context.activity
  a.cover = 'https://example.test/session-cover.jpg'
  a.leader = '公主、大海、军军'
  a.intro = '接口原文'
  await f.page.refresh()
  let nodes = f.nodes(), fees = nodes.find(node => has(node, 'detail-fees'))
  assert(nodes.findIndex(node => has(node, 'camp-cover')) < nodes.indexOf(fees))
  assert(nodes.findIndex(node => has(node, 'detail-guides')) < nodes.indexOf(fees))
  const core = nodes.find(node => has(node, 'detail-core-facts'))
  assert(content(core).includes('2099'));assert(content(core).includes('3天'));assert(content(core).includes(a.city))
  assert(content(fees).includes(String(a.fee)));assert(content(fees).includes('老轻友公开价'))
  assert.equal(content(nodes.filter(node => has(node, 'detail-fee-value'))[1]),'¥0/人')
  assert.equal(nodes.find(node => has(node, 'camp-cover-image')).attrs.src,a.cover)
  assert.equal(nodes.find(node => has(node, 'camp-cover-image')).attrs.mode,'widthFix')
  assert.equal(content(nodes.find(node => has(node, 'detail-intro'))),a.intro)
  assert.equal(control(nodes, 'openJourney').attrs.disabled,undefined)
  f.page.setData({isRegistered:false,availability:{canRegister:false,label:'已满'}})
  assert.equal(control(f.nodes(),'startRegistration').attrs.disabled,true)
  a.cover='';a.returningFee=null;a.intro='';a.leader='其他导游'
  await f.page.refresh();nodes=f.nodes()
  assert(!nodes.some(node=>has(node,'camp-cover')||has(node,'detail-guide-photo')||has(node,'detail-intro')))
  assert(!content(nodes.find(node=>has(node,'detail-fees'))).includes('老轻友公开价'))
  assert(!source.includes('内部视觉样稿'));assert(!source.includes('一起吃饭、活动'))
})

test('detail schedule preserves all rows, media failures and existing service/registration entries',async()=>{
  const f=await fixture('detail','none'),a=f.context.activity
  a.startDate='2099-01-01';a.endDate='2099-01-17'
  a.days=Array.from({length:17},(_,i)=>({id:'d'+i,dayNo:i+1,activityDate:'2099-01-'+String(i+1).padStart(2,'0')}))
  a.cover='https://example.test/scene.jpg';await f.page.refresh()
  assert.equal(f.page.data.detail.duration,17)
  assert.equal(f.nodes().filter(n=>has(n,'detail-day-row')).length,0)
  f.page.toggleDetailSchedule()
  assert.equal(f.nodes().filter(n=>has(n,'detail-day-row')).length,17)
  assert.equal(f.nodes().find(n=>has(n,'detail-full-schedule')).attrs.style,'height: 312px;')
  f.page.toggleDetailSchedule();assert.equal(f.nodes().filter(n=>has(n,'detail-day-row')).length,0)
  f.page.previewDetailImage({currentTarget:{dataset:{url:a.cover}}})
  assert.deepEqual(plain(f.previews[0].urls),[a.cover])
  f.page.detailImageFailed({currentTarget:{dataset:{url:a.cover}}})
  assert(!f.nodes().some(n=>has(n,'camp-cover')))
  f.page.previewDetailImage({currentTarget:{dataset:{url:a.cover}}});assert.equal(f.previews.length,1)
  await f.page.startRegistration();assert.equal(f.page.data.view,'register');assert.equal(f.writes.length,0)
  f.page.setData({view:'detail'});f.page.openJourney();assert.equal(f.page.data.view,'journey')
  f.page.setData({view:'detail'});a.id='different-session';await f.page.refresh()
  assert.equal(f.page.data.detailExpanded,false);assert.equal(f.page.data.detail.showCover,true)
})

test('registration renders inline participant feedback and preserves fields and draft through both steps', async () => {
  const f = await fixture('register', 'none')
  f.page.data.draft.participants.push({ id: 'family', name: '同行家人', relation: '家人', selected: true, phone: 'bad' })
  f.page.setData({ selectedCount: 2 })
  const before = plain(f.page.data.draft)
  f.page.nextStep()
  const error = f.nodes().find(node => node.attrs?.id === 'camp-participant-1')
  assert(content(error).includes('第2位参与者（同行家人）'))
  f.flush(); assert.equal(f.scrolls.at(-1).selector, '#camp-participant-1')
  f.page.data.draft.participants[1].phone = ''
  const valid = plain(f.page.data.draft)
  f.page.nextStep(); f.flush()
  assert.equal(f.page.data.step, 2)
  assert.deepEqual(f.scrolls.at(-1), { scrollTop: 0, duration: 0 })
  assert.equal(f.nodes().filter(node => has(node, 'summary-person')).length, 2)
  const consent = f.nodes().find(node => node.attrs?.id === 'camp-service-consent')
  assert.equal(consent.attrs.bindtap, 'toggleConsent')
  f.page.previousStep(); f.flush()
  assert.equal(f.page.data.step, 1)
  assert.deepEqual(plain(f.page.data.draft), valid)
  assert.equal(control(f.nodes(), 'onContactPhone').attrs.maxlength, '11')
  assert.equal(control(f.nodes(), 'onContactPhone').attrs.type, 'number')
  assert.equal(before.participants[1].phone, 'bad')
})

test('registration consent, busy state and selected payload remain the existing submission contract', async () => {
  const f = await fixture('register', 'none')
  f.page.setData({ step: 2, submitting: true })
  assert.equal(control(f.nodes(), 'previousStep').attrs.disabled, true)
  assert.equal(control(f.nodes(), 'submitRegistration').attrs.loading, true)
  f.page.setData({ submitting: false })
  f.page.toggleConsent()
  assert.equal(control(f.nodes(), 'submitRegistration').attrs.disabled, true)
  f.page.toggleConsent()
  f.business.registrationQuote = async payload => ({ participants: payload.participants.map((p,index) => ({ index,friendType:'new',unitPrice:'3800.00' })), quotedAmount:3800*payload.participants.length })
  await f.page.loadQuote()
  await f.page.submitRegistration()
  assert.equal(f.writes.length, 1)
  assert.equal(f.writes[0].sessionId, f.page.data.activity.id)
  assert.equal(f.writes[0].participants[0].self, true)
  assert.equal(f.page.data.view, 'success')
})

test('service keeps independent registration and settlement states with the original reflection eligibility', async () => {
  const f = await fixture('journey', 'pending')
  f.page.setData({ serviceStatus: { registration: '待确认', payment: '金额待确认', paymentNote: '待工作人员核对' } })
  const status = f.nodes().find(node => has(node, 'service-status-card'))
  assert(content(status).includes('待确认')); assert(content(status).includes('金额待确认'))
  assert.equal(control(f.nodes(), 'openReflection').attrs.disabled, true)
  assert(f.nodes().some(node => node.text?.includes('本人报名确认后可填写')))
  assert(f.nodes().some(node => node.text?.includes('当前不会自动发送确认通知')))
  f.page.data.registration.status = 'confirmed'
  await f.page.refresh()
  assert.equal(control(f.nodes(), 'openReflection').attrs.disabled, true, 'confirmed future booking is still outside its server window')
  f.reflectionContext.canBefore = true
  await f.page.refresh()
  assert.equal(control(f.nodes(), 'openReflection').attrs.disabled, false)
  f.context.state.registrations[f.context.activity.id].participants = [{ id: 'family', selected: true }]
  await f.page.refresh()
  assert.equal(control(f.nodes(), 'openReflection'), undefined)
  assert(!f.nodes().some(node => node.text?.includes('请由本期实际参与者填写')))
  assert(!control(f.nodes(), 'checkDay'))
  assert(!control(f.nodes(), 'toggleDay'))
})

test('feeling and experience keep private drafts, saved feedback and save-in-flight controls', async () => {
  const f = await fixture('feeling')
  f.reflectionContext.canAfter = true
  await f.page.refresh()
  f.page.setData({ canRecordFeeling: true, feelingPhase: 'after', feelingNote: '提交前的感受', feelingSaved: true })
  assert.equal(control(f.nodes(), 'onFeelingInput'), undefined, 'formal after uses only the unified editor')
  assert.ok(control(f.nodes(), 'openReflection'))
  await f.page.saveFeeling()
  assert.equal(f.links.at(-1), '/pages/camp-reflection/index?id=session-live')
  assert.equal(f.writes.length, 0, 'after must never call the obsolete private writer')
  f.page.setData({ feelingPhase: 'before' })
  assert.equal(control(f.nodes(), 'onFeelingInput').attrs.value, '提交前的感受')
  assert(content(f.nodes().find(node => has(node, 'feeling-editor'))).includes('默认仅自己可见'))
  f.page.onFeelingInput(event('', '新感受'))
  assert(!f.nodes().some(node => has(node, 'saved-note')))
  f.page.setData({ feelingSaving: true })
  assert.equal(control(f.nodes(), 'saveFeeling').attrs.disabled, true)
  f.page.setData({ feelingSaving: false })
  await f.page.saveFeeling()
  assert.equal(f.writes[0].note, '新感受')
  assert(f.nodes().some(node => has(node, 'saved-note')))
  f.page.setData({ view: 'experience', canReview: true, canWriteReview: false, experienceTimeline: [{ id: 'r', label: '活动期间', note: '此前记录' }] })
  assert(!control(f.nodes(), 'saveReview'))
  assert(!control(f.nodes(), 'onReviewInput'))
  assert(content(f.nodes().find(node => has(node, 'experience-records'))).includes('此前记录'))
})

test('invitation lists actual session name and date, retains exact IDs and shares only prepared paths', async () => {
  const f = await fixture('invite'), a = { ...f.page.data.activity, id: 'next-session', sessionNumber: 102 }
  assert(f.nodes().some(node => has(node, 'empty-state')))
  f.page.setData({ inviteActivities: [a], sharePath: '' })
  const row = control(f.nodes(), 'prepareInvitation')
  assert(content(row).includes(a.name)); assert(content(row).includes(a.date)); assert.equal(row.attrs['data-id'], a.id)
  assert(!f.nodes().some(node => node.attrs?.['open-type'] === 'share'))
  await f.page.prepareInvitation(event(row.attrs['data-id']))
  assert(f.nodes().some(node => node.attrs?.['open-type'] === 'share'))
  assert.equal(f.page.onShareAppMessage().path, '/pages/camp-flow/index?id=' + a.id)
  assert.equal(f.page.data.preparedInviteId, a.id)
})

test('every branch excludes normal task controls during loading or failure and exposes the existing retry', async () => {
  for (const view of ['detail', 'register', 'success', 'journey', 'feeling', 'invite', 'experience']) {
    const f = await fixture(view)
    f.page.setData({ loading: true })
    assert(!f.nodes().some(node => ['startRegistration', 'nextStep', 'submitRegistration', 'saveFeeling', 'saveReview', 'prepareInvitation'].includes(node.attrs?.bindtap)))
    f.page.setData({ loading: false, loadError: 'offline' })
    assert(control(f.nodes(), 'refresh'))
    assert(!f.nodes().some(node => has(node, 'detail-actionbar') || has(node, 'registration-actionbar')))
    await f.page.refresh()
    assert.equal(f.page.data.loadError, '')
  }
})

test('outcome preserves notification truth and all views retain the shared persistent back navigation', async () => {
  const f = await fixture('success', 'pending')
  assert(content(f.nodes().find(node => has(node, 'registration-result'))).includes('报名已提交'))
  assert(f.nodes().some(node => node.text?.includes('当前不会自动发送确认通知')))
  for (const view of ['detail', 'register', 'success', 'journey', 'feeling', 'invite', 'experience']) {
    f.page.setData({ view })
    const nav = f.nodes().find(node => node.tag === 'ql-nav-space')
    assert.equal(nav.attrs.back, true)
    assert(!nav.attrs.immersive)
  }
})

// The current-source layout verifier reuses this renderer; it is not app runtime code.
module.exports = { parse, render, flatten, fixture, has, content }

test('every reflection entry uses server windows, not the private feeling window or invitation eligibility', async () => {
  const f = await fixture('journey')
  assert.equal(f.page.data.reflectionAccess.allowed, false)
  await f.page.openReflection()
  assert.equal(f.links.length, 0)
  f.context.state.invitationEligible = false
  for (const permission of ['canBefore', 'canAfter']) {
    Object.assign(f.reflectionContext, { canBefore: false, canAfter: false, [permission]: true })
    await f.page.refresh()
    for (const view of ['journey', 'experience', 'feeling']) {
      f.page.setData({ view, feelingPhase: 'after' })
      const button = control(f.nodes(), 'openReflection')
      assert(button); assert.equal(button.attrs.disabled, false)
      await f.page.openReflection()
      assert.equal(f.links.at(-1), '/pages/camp-reflection/index?id=session-live')
    }
  }
  Object.assign(f.reflectionContext, { canBefore: false, canAfter: false })
  await f.page.refresh()
  for (const view of ['journey', 'experience', 'feeling']) {
    f.page.setData({ view, feelingPhase: 'after' })
    assert.equal(control(f.nodes(), 'openReflection').attrs.disabled, true)
  }
})

test('reflection access failures stay closed and retry does not break registration or private reading', async () => {
  const f = await fixture('journey')
  f.business.campReflection = async () => { throw Error('offline') }
  await f.page.refresh()
  assert.equal(f.page.data.loadError, '')
  assert.equal(f.page.data.reflectionAccess.allowed, false)
  assert.equal(f.page.data.reflectionAccess.retry, true)
  assert(f.nodes().some(node => node.text?.includes('心声资格读取失败')))
  assert(control(f.nodes(), 'refresh'))
  f.business.campReflection = async () => ({ canAfter: true })
  await f.page.refresh()
  assert.equal(f.page.data.reflectionAccess.allowed, true)
})

test('reflection opening rechecks a closed window and ignores callbacks after leaving or changing identity', async () => {
  const f = await fixture('journey')
  f.reflectionContext.canBefore = true
  await f.page.refresh()
  f.reflectionContext.canBefore = false
  await f.page.openReflection()
  assert.equal(f.links.length, 0)
  assert.equal(f.page.data.reflectionAccess.allowed, false)
  f.reflectionContext.canBefore = true
  await f.page.refresh()
  let resolve
  f.business.campReflection = () => new Promise(r => { resolve = r })
  const pending = f.page.openReflection()
  await f.page.openReflection()
  f.page.onHide()
  resolve({ canBefore: true })
  await pending
  assert.equal(f.links.length, 0)
  assert.equal(f.page.data.reflectionOpening, false)
  assert.equal(f.page.data.reflectionAccess.allowed, false)
})

test('first authoritative owner adoption does not discard the loaded activity', async () => {
  const f = await fixture('journey')
  f.business.context = async () => { f.page.clearInvitation(); return f.context }
  await f.page.refresh()
  assert.equal(f.page.data.activity.id, 'session-live')
  assert.equal(f.page.data.loadError, '')
  assert.equal(f.page.data.loading, false)
})
