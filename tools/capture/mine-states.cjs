#!/usr/bin/env node
/*
 * 我的页面状态截图：游客、私人入口待登录、取消登录、登录过期、网络失败、已登录、无小名、长小名、退出取消／成功／服务器未确认、页尾。
 *   node tools/capture/mine-states.cjs [--out DIR] [--only guest,signed,long]
 * 场景：guest cancel expired network signed logoutfail long。文件名标注"real"（真实交互或真实 401）、
 * "mocked"（mockWxMethod 模拟失败）或"injected"（setData 注入展示状态）。只用合成 token，结束时清除，不改源码或配置。
 */
const fs = require('fs')
const path = require('path')
const { REPO, loadAutomator, relaunch, shoot, sleep } = require('./capture.cjs')

const args = process.argv.slice(2)
const option = (name, fallback) => { const i = args.indexOf('--' + name); return i >= 0 ? args[i + 1] : fallback }
const OUT = path.resolve(option('out', path.join(REPO, 'test-artifacts/visual-2026q4/mine')))
const only = option('only', '')
// 与 services/business-api.js 的 tokenKey() 一致，随 config/runtime.js 的业务地址变化。
const TOKEN_KEY = 'qinglife_business_token:' + require(path.join(REPO, 'apps/miniapp/config/runtime.js')).businessBaseUrl

async function main() {
  fs.mkdirSync(OUT, { recursive: true })
  const mp = await loadAutomator().connect({ wsEndpoint: 'ws://127.0.0.1:9420' })
  const shot = async name => { const s = await shoot(mp, path.join(OUT, name + '.png'), 300); console.log('✔', name, s.width + '×' + s.height) }
  const open = async () => { const page = await relaunch(mp, '/pages/mine/index'); await sleep(3500); return page }
  const data = async (page, keys) => { const d = await page.data(); return Object.fromEntries(keys.map(k => [k, d[k]])) }
  const run = name => !only || only.split(',').includes(name)
  try {
    const info = await mp.systemInfo(); console.log('device', info.model, info.windowWidth + '×' + info.windowHeight, 'pixelRatio', info.pixelRatio)
    await mp.callWxMethod('removeStorageSync', TOKEN_KEY)

    if (run('guest')) {
      const page = await open()
      console.log(await data(page, ['signedIn', 'notice', 'profileError', 'state']))
      await shot('01-guest-real')
      // 真实点击"私人记录"，确认入口走就地登录说明。
      const rows = await page.$$('.asset'); await rows[2].tap(); await sleep(800)
      console.log('pending after tap', (await page.data()).pendingEntry)
      await shot('02-guest-private-entry-real-tap')
    }

    if (run('cancel')) {
      const page = await open()
      await mp.mockWxMethod('login', { errMsg: 'login:fail cancel' })
      const btn = await page.$('.profile-login'); await btn.tap(); await sleep(1500)
      console.log('after mocked cancel', await data(page, ['notice', 'loginLoading', 'signedIn']))
      await shot('03-login-cancel-mocked-wxlogin')
      await mp.restoreWxMethod('login')
    }

    if (run('expired')) {
      // 服务器会拒绝的合成 token：走真实 401 路径。
      await mp.callWxMethod('setStorageSync', TOKEN_KEY, 'synthetic-expired-ui-check')
      const page = await open(); await sleep(3000)
      console.log('expired', await data(page, ['signedIn', 'notice', 'profileError', 'state']))
      await shot('04-expired-real-401')
      await mp.callWxMethod('removeStorageSync', TOKEN_KEY)
    }

    if (run('network')) {
      await mp.callWxMethod('setStorageSync', TOKEN_KEY, 'synthetic-offline-ui-check')
      await mp.mockWxMethod('request', { errMsg: 'request:fail timeout' })
      const page = await open(); await sleep(2000)
      console.log('network', await data(page, ['signedIn', 'notice', 'profileError']))
      await shot('05-signed-in-network-failure-mocked-request')
      await mp.restoreWxMethod('request')
      await mp.callWxMethod('removeStorageSync', TOKEN_KEY)
    }

    if (run('signed')) {
      const page = await open()
      const state = { backend: true, loggedIn: true, phoneLinked: true, profile: { nickname: '小禾' }, passAccounts: [{ id: 1 }], currentPassBalance: 4 }
      const passConsumptions = [{ id: 1, sessionNumber: 48, occurredAt: '2026-10-07 09:00', quantityDelta: -1, balanceAfter: 4 }]
      await page.setData({ signedIn: true, ready: true, state, avatarText: '小', passConsumptions, staffAccess: true })
      await sleep(600); await shot('06-signed-in-injected')
      await page.setData({ state: { ...state, profile: { nickname: '' } }, avatarText: '轻', passConsumptions: [], staffAccess: false })
      await sleep(400); await shot('07-no-nickname-injected')
      await page.setData({ state: { ...state, profile: { nickname: '一个特别特别长的轻友小名用于检查换行' } }, avatarText: '一' })
      await sleep(400); await shot('08-long-nickname-injected')
      // 通过真实处理函数取消、再确认退出；本机没有凭证，不会发出请求。
      await mp.mockWxMethod('showModal', { confirm: false, cancel: true })
      await (await page.$('.logout-entry')).tap(); await sleep(800)
      console.log('after cancel', await data(page, ['signedIn', 'logoutLoading']))
      await shot('09-logout-cancel-real-handler')
      await mp.mockWxMethod('showModal', { confirm: true, cancel: false })
      await (await page.$('.logout-entry')).tap(); await sleep(1500)
      console.log('after confirm', await data(page, ['signedIn', 'state', 'notice']))
      await shot('10-logout-success-real-handler')
      await mp.restoreWxMethod('showModal')
    }

    if (run('logoutfail')) {
      await mp.callWxMethod('setStorageSync', TOKEN_KEY, 'synthetic-logout-ui-check')
      const page = await open(); await sleep(2500)
      // 保持已登录展示，再让注销请求在网络层失败。
      await page.setData({ signedIn: true, ready: true, profileError: '', state: { backend: true, loggedIn: true, profile: { nickname: '小禾' } }, avatarText: '小' })
      await mp.mockWxMethod('showModal', { confirm: true, cancel: false })
      await mp.mockWxMethod('request', { errMsg: 'request:fail timeout' })
      await (await page.$('.logout-entry')).tap(); await sleep(1500)
      console.log('after failed logout', await data(page, ['signedIn', 'state', 'notice']))
      console.log('token left', JSON.stringify(await mp.callWxMethod('getStorageSync', TOKEN_KEY)))
      await shot('11-logout-server-unconfirmed-mocked-request')
      await mp.restoreWxMethod('request'); await mp.restoreWxMethod('showModal')
      await mp.callWxMethod('removeStorageSync', TOKEN_KEY)
    }

    if (run('long')) {
      const page = await open()
      const state = { backend: true, loggedIn: true, profile: { nickname: '小禾' }, passAccounts: [{ id: 1 }], currentPassBalance: 4 }
      await page.setData({ signedIn: true, ready: true, state, avatarText: '小', staffAccess: true,
        passConsumptions: [1, 2, 3].map(id => ({ id, sessionNumber: 45 + id, occurredAt: '2026-10-0' + id + ' 09:00', quantityDelta: -1, balanceAfter: 5 - id })) })
      await sleep(500)
      await mp.pageScrollTo(2000); await sleep(800)
      await shot('12-bottom-safe-area-injected')
      await mp.pageScrollTo(0)
    }
  } finally {
    await mp.callWxMethod('removeStorageSync', TOKEN_KEY).catch(() => {})
    mp.disconnect()
  }
}

main().catch(e => { console.error('mine-states 失败：', e && (e.message || JSON.stringify(e))); process.exit(1) })
