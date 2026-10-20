#!/usr/bin/env node
/*
 * 首页（PLAN §5-P3）状态截图：切换第二期、心声失败态、心声卡片、#43 开 / 关、1.4 倍字号首屏。
 *   node tools/capture/home-states.cjs [--out DIR] [--photo43 URL]
 * 只通过 setData 在模拟器里临时切换展示状态，不改任何源码或配置；
 * "voices-sample" 用的是测试夹具文字，仅供看版式。#43 打开态需要一个可访问的图片地址（如本机静态服务）。
 */
const fs = require('fs')
const path = require('path')
const { REPO, loadAutomator, relaunch, shoot, sleep } = require('./capture.cjs')

const args = process.argv.slice(2)
const option = (name, fallback) => { const i = args.indexOf('--' + name); return i >= 0 ? args[i + 1] : fallback }
const OUT = path.resolve(option('out', path.join(REPO, 'test-artifacts/visual-2026q4/p3')))
const PHOTO43 = option('photo43', '')
const NAV = 91 // 固定导航栏逻辑高度（iPhone 12）

const sampleVoices = Array.from({ length: 4 }, (_, i) => ({
  id: 'sample-' + i, personKey: 'sample-' + i, author: '示例轻友' + (i + 1),
  excerpt: ['三天下来，最大的变化是睡得踏实了。', '原来慢下来吃饭，是要练习的。', '回家以后，早上会给自己留十分钟。', '和同屋的人聊了很久，没想到会这么投缘。'][i],
  fullExcerpt: '示例全文。'
}))

async function scrollTo(miniProgram, selector, offset = 12) {
  const page = await miniProgram.currentPage()
  const element = await page.$(selector)
  if (!element) throw new Error('找不到 ' + selector)
  const current = await miniProgram.evaluate(() => new Promise(r => wx.createSelectorQuery().selectViewport().scrollOffset().exec(x => r(x[0].scrollTop))))
  const { top } = await element.offset()
  await miniProgram.pageScrollTo(Math.max(0, top + current - NAV - offset))
  await sleep(700)
}

async function main() {
  fs.mkdirSync(OUT, { recursive: true })
  const miniProgram = await loadAutomator().connect({ wsEndpoint: option('ws', 'ws://127.0.0.1:9420') })
  const shot = async name => { const size = await shoot(miniProgram, path.join(OUT, name + '.png'), 390); console.log(`✔ ${name}  ${size.width}×${size.height}`) }
  try {
    let page = await relaunch(miniProgram, '/pages/home/index')
    await sleep(5000)

    await scrollTo(miniProgram, '.home-camp')
    await shot('camp-period-1')
    const tabs = await page.$$('.home-camp-tab')
    if (tabs.length > 1) { await tabs[1].tap(); await sleep(600); await shot('camp-period-2') } else console.log('— 只有一期，跳过切换')

    const data = await page.data()
    if (!data.voiceError) await page.setData({ voiceCards: [], voiceGroups: [], voiceError: '本期心声暂时未能读取，请重试。' })
    await scrollTo(miniProgram, '.home-voice-status', 120)
    await shot('voices-failed' + (data.voiceError ? '' : '-simulated'))

    await page.setData({ voiceError: '', voiceGroups: [sampleVoices], voiceCards: sampleVoices, voiceGroup: 0, voiceHasMore: false, voicePage: 1 })
    await sleep(400)
    await scrollTo(miniProgram, '.home-voices')
    await shot('voices-sample')

    await scrollTo(miniProgram, '.home-discover')
    await shot('discover-43-off')
    if (PHOTO43) {
      const brand = (await page.data()).brand
      await page.setData({ brand: { ...brand, aboutPhoto: { ...brand.aboutPhoto, releaseReady: true, url: PHOTO43 } } })
      await sleep(1500)
      await scrollTo(miniProgram, '.home-discover')
      await shot('discover-43-on')
    }

    page = await relaunch(miniProgram, '/pages/home/index')
    await sleep(4000)
    await page.setData({ fontScale: 1.4 })
    await sleep(500)
    await shot('home-first-font-1.4')
  } finally {
    await miniProgram.pageScrollTo(0).catch(() => {})
    miniProgram.disconnect()
  }
}

main().catch(error => { console.error(`home-states 失败：${error.message || JSON.stringify(error)}`); process.exit(1) })
