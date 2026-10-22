const { test } = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const vm = require('node:vm')
const root = path.resolve(__dirname, '..')
const plain = value => JSON.parse(JSON.stringify(value))
const feedback = require('../utils/form-feedback')
const event = (field, value) => ({ currentTarget: { dataset: { field, value } }, detail: { value } })
const validForm = { name: '测试轻友', birthDate: '1990-01-01', phone: '13800000000', heightCm: '170', weightKg: '60', province: '上海市', city: '上海',
  cleanBodyGoals: ['调理'], dietPreference: '荤素各半', waterIntakeMl: '0', wakeTime: '07:00', sleepTime: '22:00',
  bowelStatus: '1次/天', energyStatus: '一般', exerciseStatus: '偶尔运动', emotionalStatus: ['平静'], healthConditions: ['无'],
  referralSource: '朋友小雨', sensitiveConsent: true }

function fixture(name, apiEnabled = true) {
  const storage = new Map(), toasts = [], scrolls = [], renders = [], submissions = []
  const wx = {
    getStorageSync: key => storage.has(key) ? plain(storage.get(key)) : '',
    setStorageSync: (key, value) => storage.set(key, plain(value)),
    removeStorageSync: key => storage.delete(key),
    showToast: value => toasts.push(value.title),
    pageScrollTo: value => scrolls.push({ ...plain(value), step: page.data.step }),
    navigateBack() {}
  }
  const business = {
    enabled: () => apiEnabled, friendProfile: async () => ({ id: 'test-owner', authorizedPhone: '13800000000' }),
    register: async value => { submissions.push(plain(value)); return {} },
    submitFriendAssessment: async value => { submissions.push(plain(value)); return { assessmentCount: 2 } }
  }
  const storeModule = { exports: {} }
  vm.runInNewContext(fs.readFileSync(path.join(root, 'utils/store.js'), 'utf8'), {
    module: storeModule, wx, require: id => require(path.resolve(root, 'utils', id))
  })
  const store = storeModule.exports
  store.ensureState()
  const mocks = { '../../services/business-api': business, '../../utils/store': store, '../../utils/navigation': {} }
  let page
  vm.runInNewContext(fs.readFileSync(path.join(root, 'pages', name, 'index.js'), 'utf8'), {
    wx, Page: value => { page = value }, require: id => mocks[id] || require(path.resolve(root, 'pages', name, id))
  })
  page.data = plain(page.data)
  page.setData = (patch, callback) => {
    for (const [key, value] of Object.entries(patch)) {
      const parts = key.split('.')
      if (parts.length === 2) page.data[parts[0]][parts[1]] = value
      else page.data[key] = value
    }
    if (callback) renders.push(callback)
  }
  const flush = () => { while (renders.length) renders.shift()() }
  if (name === 'camp-flow') {
    page.data.state = { ...plain(store.getState()), backend: true, phone: '13800000000' }
    page.data.activity = { ...require('../data/demo').activities[0], status: '开放报名', seatsLeft: 10 }
    page.data.view = 'register'
    page.data.draft = { serviceConsent: true, contactName: '联系人', contactPhone: '13800000000', motivation: '用户填写原因',
      participants: [{ id: 'person-self', name: '本人', selected: true, phone: '' },
        { id: 'person-2', name: '家人甲', relation: '家人', selected: true, phone: '' },
        { id: 'person-3', name: '家人乙', relation: '家人', selected: true, phone: '' }] }
    page.data.selectedCount = 3
  } else { page.data.form = { ...page.data.form, ...plain(validForm) }; page.customerId = 'test-owner'; page.draftKey = 'qinglife_friend_assessment_draft_2026_v1:test-owner' }
  return { page, wx, business, store, storage, submissions, toasts, scrolls, flush }
}

function expectError(f, target, step) {
  assert.equal(f.page.data.formError.target, target)
  assert.equal(f.page.data.step, step)
  assert.equal(f.scrolls.length, 0, 'must wait for rendering')
  f.flush()
  assert.deepEqual(f.scrolls, [{ selector: '#' + target, duration: 180, step }])
  assert.equal(f.toasts.at(-1), f.page.data.formError.message)
}

test('camp contact errors identify the exact field without changing the draft', () => {
  for (const [field, value, target] of [['contactName', '', 'camp-contact-name'], ['contactPhone', '123', 'camp-contact-phone']]) {
    const f = fixture('camp-flow')
    f.page.data.draft[field] = value
    const before = plain(f.page.data.draft)
    f.page.nextStep()
    expectError(f, target, 1)
    assert.deepEqual(plain(f.page.data.draft), before)
    assert.equal(f.submissions.length, 0)
  }
})

test('camp selected invalid phones name the actual participant, including an unselected row before it', () => {
  for (const index of [0, 2]) {
    const f = fixture('camp-flow')
    f.page.data.draft.participants[1].selected = false
    f.page.data.draft.participants[1].phone = 'ignored-invalid'
    f.page.data.draft.participants[index].phone = 'bad'
    f.page.nextStep()
    expectError(f, index === 0 ? 'camp-self-phone' : 'camp-participant-2', 1)
    assert.match(f.page.data.formError.message, new RegExp('第' + (index + 1) + '位参与者'))
    assert.ok(f.page.data.formError.message.includes(f.page.data.draft.participants[index].name))
  }
})

test('camp submit returns to the contact or participant step with one targeted scroll', async () => {
  for (const type of ['contact', 'invalid', 'duplicate', 'contact-reuse']) {
    const f = fixture('camp-flow')
    f.page.data.step = 2
    if (type === 'contact') f.page.data.draft.contactPhone = 'bad'
    else if (type === 'invalid') f.page.data.draft.participants[2].phone = 'bad'
    else if (type === 'duplicate') {
      f.page.data.draft.participants[1].phone = '13900000001'
      f.page.data.draft.participants[2].phone = ' 13900000001 '
    } else f.page.data.draft.participants[2].phone = f.page.data.draft.contactPhone
    const before = plain(f.page.data.draft)
    await f.page.submitRegistration()
    expectError(f, type === 'contact' ? 'camp-contact-phone' : 'camp-participant-2', 1)
    if (type === 'duplicate') assert.match(f.page.data.formError.message, /第3位参与者（家人乙）.*第2位参与者（家人甲）.*重复/)
    if (type === 'contact-reuse') assert.match(f.page.data.formError.message, /家人乙.*主要联系人手机号/)
    assert.deepEqual(plain(f.page.data.draft), before)
    assert.equal(f.submissions.length, 0)
    assert.equal(f.page.data.requestId, '')
  }
})

test('camp participant selection and required consent have distinct visible anchors', async () => {
  const empty = fixture('camp-flow')
  empty.page.data.selectedCount = 0
  empty.page.data.draft.participants.forEach(person => { person.selected = false })
  empty.page.nextStep()
  expectError(empty, 'camp-participants', 1)
  for (const api of [false, true]) {
    const f = fixture('camp-flow', api)
    f.page.data.step = 2
    f.page.data.draft.serviceConsent = false
    await f.page.submitRegistration()
    expectError(f, 'camp-service-consent', 2)
    assert.equal(f.submissions.length, 0)
  }
})

test('camp add-person errors identify name, relationship and optional phone without adding a draft row', () => {
  for (const [name, relation, phone, target] of [['', '', '', 'camp-person-name'], ['同行', '', '', 'camp-person-relation'], ['同行', '朋友', 'bad', 'camp-person-phone']]) {
    const f = fixture('camp-flow')
    Object.assign(f.page.data, { addingPerson: true, personName: name, personRelation: relation, personPhone: phone })
    f.page.addPerson()
    expectError(f, target, 1)
    assert.equal(f.page.data.draft.participants.length, 3)
    assert.equal(f.page.data.addingPerson, true)
  }
})

test('camp forward and back reset after render once and retain all draft fields', () => {
  const f = fixture('camp-flow'), before = plain(f.page.data.draft)
  f.page.nextStep()
  assert.equal(f.page.data.step, 2)
  assert.equal(f.scrolls.length, 0)
  f.flush()
  assert.deepEqual(f.scrolls, [{ scrollTop: 0, duration: 0, step: 2 }])
  f.page.previousStep()
  assert.equal(f.scrolls.length, 1)
  f.flush()
  assert.deepEqual(f.scrolls[1], { scrollTop: 0, duration: 0, step: 1 })
  f.page.previousStep(); f.flush()
  assert.equal(f.scrolls.length, 2)
  assert.deepEqual(plain(f.page.data.draft), before)
})

test('camp valid API submission preserves optional phone, selected participants and motivation', async () => {
  const f = fixture('camp-flow'), before = plain(f.page.data.draft)
  f.page.refresh = async () => {}
  f.page.data.step = 2
  f.business.registrationQuote = async payload => ({participants:payload.participants.map((p,index)=>({index,friendType:'new',unitPrice:'3800.00'})),quotedAmount:3800*payload.participants.length})
  await f.page.loadQuote()
  await f.page.submitRegistration()
  assert.equal(f.submissions.length, 1)
  assert.equal(f.submissions[0].participants.length, 3)
  assert.equal(f.submissions[0].participants[0].phone, f.page.data.state.phone)
  assert.equal(f.submissions[0].participants[1].phone, '')
  assert.equal(f.submissions[0].motivation, before.motivation)
  assert.equal(f.page.data.view, 'success')
  assert.deepEqual(plain(f.page.data.draft), before)
  assert.equal(f.page.data.submitting, false)
})

test('friend required items in every step identify their field or selection group', () => {
  const steps = { name: 1, birthDate: 1, phone: 1, heightCm: 1, weightKg: 1, province: 1, city: 1,
    cleanBodyGoals: 2, dietPreference: 2, waterIntakeMl: 2, wakeTime: 2, sleepTime: 2, bowelStatus: 2, energyStatus: 2, exerciseStatus: 2,
    emotionalStatus: 3, healthConditions: 3, referralSource: 4 }
  for (const [key, step] of Object.entries(steps)) {
    const f = fixture('friend-registration')
    f.page.data.step = step
    f.page.data.form[key] = Array.isArray(f.page.data.form[key]) ? [] : ''
    const before = plain(f.page.data.form)
    if (step === 4) f.page.submit()
    else f.page.next()
    expectError(f, ['province', 'city'].includes(key) ? 'friend-region' : 'friend-' + key, step)
    assert.deepEqual(plain(f.page.data.form), before)
    assert.equal(f.submissions.length, 0)
  }
})

test('friend height/weight and other-health errors keep the existing ranges and conditional requirement', () => {
  for (const [key, value] of [['heightCm', '79'], ['heightCm', '251'], ['weightKg', '19'], ['weightKg', '301']]) {
    const f = fixture('friend-registration')
    f.page.data.form[key] = value
    f.page.next()
    expectError(f, 'friend-' + key, 1)
  }
  const f = fixture('friend-registration')
  f.page.data.step = 3
  f.page.multi(event('healthConditions', '其他'))
  f.page.next()
  expectError(f, 'friend-otherHealthCondition', 3)
  f.page.field(event('otherHealthCondition', '用户主动填写'))
  assert.equal(f.page.data.formError, null)
  assert.equal(f.page.validate(3), null)
})

test('friend original permissive values and range endpoints remain valid', () => {
  const f = fixture('friend-registration')
  for (const [height, weight] of [['80', '20'], ['250', '300']]) {
    Object.assign(f.page.data.form, { heightCm: height, weightKg: weight })
    assert.equal(f.page.validate(1), null, 'no new phone format rule')
  }
  assert.equal(f.page.validate(2), null, 'zero water remains allowed')
  assert.equal(f.page.validate(3), null, 'no other-health explanation unless selected')
})

test('friend every forward/back step resets once after render and keeps the existing draft shape', () => {
  const f = fixture('friend-registration'), before = plain(f.page.data.form)
  for (const step of [2, 3, 4]) {
    f.page.next()
    assert.equal(f.page.data.step, step)
    assert.equal(f.scrolls.length, step - 2)
    f.flush()
    assert.deepEqual(f.scrolls.at(-1), { scrollTop: 0, duration: 0, step })
    assert.deepEqual(f.storage.get(f.page.draftKey), before)
  }
  for (const step of [3, 2, 1]) { f.page.previous(); f.flush(); assert.deepEqual(f.scrolls.at(-1), { scrollTop: 0, duration: 0, step }) }
  f.page.previous(); f.flush()
  assert.equal(f.scrolls.length, 6)
  assert.deepEqual(plain(f.page.data.form), before)
})

test('friend saved draft reloads, health exclusivity survives edits and sensitive consent still gates submission', async () => {
  const f = fixture('friend-registration'), draftKey = f.page.draftKey
  f.page.saveDraft()
  const before = plain(f.page.data.form)
  f.page.data.form.name = 'temporary'
  await f.page.onLoad()
  assert.deepEqual(plain(f.page.data.form), before)
  f.page.multi(event('healthConditions', '高血压'))
  assert.deepEqual(plain(f.page.data.form.healthConditions), ['高血压'])
  f.page.multi(event('healthConditions', '无'))
  assert.deepEqual(plain(f.page.data.form.healthConditions), ['无'])
  f.page.data.step = 4
  f.page.data.form.sensitiveConsent = false
  await f.page.submit()
  expectError(f, 'friend-sensitiveConsent', 4)
  assert.equal(f.submissions.length, 0)
  f.page.toggleConsent()
  assert.equal(f.page.data.formError, null)
  await f.page.submit()
  assert.equal(f.submissions.length, 1)
  assert.equal(f.submissions[0].heightCm, 170)
  assert.equal(f.submissions[0].weightKg, 60)
  assert.equal(f.submissions[0].waterIntakeMl, 0)
  assert.equal(f.submissions[0].clientRequestId, before.clientRequestId)
  assert.equal(f.page.data.submitted, true)
  assert.equal(f.storage.has(draftKey), false)
})

test('friend failed submission retains draft and request ID with persistent feedback', async () => {
  const f = fixture('friend-registration')
  f.page.data.step = 4
  f.page.saveDraft()
  const before = plain(f.page.data.form)
  f.business.submitFriendAssessment = async () => { throw new Error('网络断开') }
  await f.page.submit()
  expectError(f, 'friend-submit-error', 4)
  assert.equal(f.page.data.formError.message, '网络断开')
  assert.deepEqual(f.storage.get(f.page.draftKey), before)
  assert.equal(f.page.data.form.clientRequestId, before.clientRequestId)
  assert.equal(f.page.data.submitting, false)
})

test('repeat registration resets step and scroll while preserving the original reuse rules', () => {
  const f = fixture('friend-registration')
  f.page.data.step = 4; f.page.data.submitted = true
  const oldId = f.page.data.form.clientRequestId
  f.page.registerAgain()
  assert.equal(f.page.data.step, 1)
  assert.equal(f.page.data.form.name, validForm.name)
  assert.equal(f.page.data.form.weightKg, '')
  assert.notEqual(f.page.data.form.clientRequestId, oldId)
  f.flush()
  assert.deepEqual(f.scrolls, [{ scrollTop: 0, duration: 0, step: 1 }])
})

test('new feedback cancels queued old scrolls and leaving the page prevents late scrolling', () => {
  const f = fixture('camp-flow')
  feedback.showError(f.page, f.wx, { target: 'camp-contact-phone', message: '错误' }, { step: 1 })
  feedback.changeStep(f.page, f.wx, { step: 2 })
  f.flush()
  assert.deepEqual(f.scrolls, [{ scrollTop: 0, duration: 0, step: 2 }])
  feedback.showError(f.page, f.wx, { target: 'camp-service-consent', message: '错误' })
  f.page.onUnload(); f.flush()
  assert.equal(f.scrolls.length, 1)
})

test('every validation target has an inline message and page anchor, including repeated participant rows', () => {
  for (const name of ['camp-flow', 'friend-registration']) {
    const js = fs.readFileSync(path.join(root, 'pages', name, 'index.js'), 'utf8')
    const wxml = fs.readFileSync(path.join(root, 'pages', name, 'index.wxml'), 'utf8')
    for (const match of js.matchAll(/(?:target: |registrationError\()'(camp-[a-z-]+|friend-[a-zA-Z-]+)'/g)) {
      assert.ok(wxml.includes('id="' + match[1] + '"'), match[1] + ' anchor missing')
      assert.ok(wxml.includes("formError.target === '" + match[1] + "'"), match[1] + ' inline feedback missing')
    }
    assert.match(wxml, /class="form-error">{{formError.message}}/)
  }
  const wxml = fs.readFileSync(path.join(root, 'pages/camp-flow/index.wxml'), 'utf8')
  assert.match(wxml, /id="camp-participant-{{index}}"/)
  assert.ok(wxml.includes("formError.target === 'camp-participant-' + index"))
})


test('latest camp nickname rule locates the selected participant without weakening validation', () => {
  const f=fixture('camp-flow');f.page.data.draft.participants[0].name='  ';f.page.nextStep();expectError(f,'camp-self-name',1);assert.match(f.page.data.formError.message,/第1位参与者/);assert.equal(f.submissions.length,0)
})

test('latest friend final submission revisits earlier region errors and preserves scoped drafts', async () => {
  const f=fixture('friend-registration');f.page.data.step=4;f.page.data.form.province='';await f.page.submit();expectError(f,'friend-region',1);assert.equal(f.submissions.length,0);assert.equal(f.page.data.form.city,'上海')
})
