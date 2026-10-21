const { test } = require('node:test')
const assert = require('node:assert/strict'), fs = require('node:fs'), path = require('node:path'), vm = require('node:vm')
const root = path.resolve(__dirname, '..')

function page(bindPhone, stack = 2) {
  let p
  const toasts = [], back = [], tabs = [], codes = []
  const business = { bindPhone: async code => { codes.push(code); return bindPhone(code) } }
  const navigation = { switchTab: x => tabs.push(x.url) }
  vm.runInNewContext(fs.readFileSync(path.join(root, 'pages/phone-auth/index.js'), 'utf8'), {
    Page: v => { p = v }, require: id => id.includes('business-api') ? business : navigation,
    wx: { showToast: x => toasts.push(x.title), navigateBack: () => back.push(true), openPrivacyContract: () => {} },
    getCurrentPages: () => new Array(stack), setTimeout: fn => fn()
  })
  p.data = JSON.parse(JSON.stringify(p.data)); p.setData = patch => Object.assign(p.data, patch)
  return { p, toasts, back, tabs, codes }
}

test('the only login path is the WeChat phone button; there is no hand-typed phone field', () => {
  const markup = fs.readFileSync(path.join(root, 'pages/phone-auth/index.wxml'), 'utf8')
  assert.match(markup, /open-type="getPhoneNumber\|agreePrivacyAuthorization" bindgetphonenumber="onPhoneNumber"/)
  assert.doesNotMatch(markup, /<input/)
  assert.match(markup, /暂不登录，继续浏览/)
  const app = JSON.parse(fs.readFileSync(path.join(root, 'app.json'), 'utf8'))
  assert.ok(app.pages.includes('pages/phone-auth/index'))
})

test('a refused authorization stays on the page without a request; success returns to where the friend came from', async () => {
  const refused = page(async () => ({ phoneAuthorized: true }))
  await refused.p.onPhoneNumber({ detail: { errMsg: 'getPhoneNumber:fail user deny' } })
  assert.deepEqual(refused.codes, []); assert.match(refused.p.data.notice, /需要授权手机号/); assert.deepEqual(refused.back, [])
  const ok = page(async () => ({ phoneAuthorized: true }))
  await ok.p.onPhoneNumber({ detail: { code: 'synthetic-phone-code' } })
  assert.deepEqual(ok.codes, ['synthetic-phone-code']); assert.equal(ok.toasts.at(-1), '登录成功'); assert.deepEqual(ok.back, [true])
  const alone = page(async () => ({ phoneAuthorized: true }), 1)
  await alone.p.onPhoneNumber({ detail: { code: 'synthetic-phone-code' } }); assert.deepEqual(alone.tabs, ['/pages/mine/index'])
})

test('a failed authorization never shows the raw server message and allows retry', async () => {
  const f = page(async () => { throw Object.assign(new Error('WeChat errcode 40029 invalid code'), { code: 400 }) })
  await f.p.onPhoneNumber({ detail: { code: 'expired' } })
  assert.equal(f.p.data.notice, '手机号授权没有完成，请重试。'); assert.equal(f.p.data.busy, false); assert.deepEqual(f.back, [])
  const slow = page(async () => { throw new Error('request:fail timeout') })
  await slow.p.onPhoneNumber({ detail: { code: 'c' } }); assert.match(slow.p.data.notice, /网络不太稳定/)
})
