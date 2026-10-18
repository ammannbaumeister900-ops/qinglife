#!/usr/bin/env node
/*
 * 认知页（PLAN §5-P4）状态截图：照片全开长图、#50/#46 关闭且无最近一期长图、模拟人物故事获批（#46 应隐藏）、1.4 倍字号首屏。
 *   node tools/capture/about-states.cjs --photos http://127.0.0.1:8765/   （目录下需有 manifest.json 中的导出图）
 * 只在模拟器里用 setData 切换展示状态，不改源码或配置；故事获批态使用 tests/about-presentation.test.js 同款单元夹具文字，
 * 经正式 presentAbout() 计算，确认授权逻辑本身给出 storyScene:null。
 */
const fs = require('fs')
const path = require('path')
const { REPO, loadAutomator, relaunch, shoot, sleep, captureLong } = require('./capture.cjs')

const args = process.argv.slice(2)
const option = (name, fallback) => { const i = args.indexOf('--' + name); return i >= 0 ? args[i + 1] : fallback }
const OUT = path.resolve(option('out', path.join(REPO, 'test-artifacts/visual-2026q4/p4')))
const PHOTOS = option('photos', '')
const STORY = option('story', '') // 候选故事 JSON（docs/design/redesign-2026q4/story-candidates/*.json），按"审定后"状态渲染
const NAV = 91, TABBAR = 96 // iPhone 12 固定导航栏与 Tab 栏逻辑高度

const MINIAPP = path.join(REPO, 'apps/miniapp')
const content = require(path.join(MINIAPP, 'config/about-content'))
const { presentAbout } = require(path.join(MINIAPP, 'utils/about-presentation'))
const clone = value => JSON.parse(JSON.stringify(value))

// 与 tests/about-presentation.test.js 的 person() 夹具相同：单元测试文字，不是业务事实。
function approvedStoryContent() {
  if (STORY) {
    const candidate = JSON.parse(fs.readFileSync(path.resolve(STORY), 'utf8'))
    const c = clone(content)
    c.storySlot = { ...c.storySlot, item: { ...candidate.storySlot.item, editorialReview: 'approved', factCheck: 'confirmed' } }
    c.assets = [...c.assets.map(a => a.id === 'scene-46' ? { ...a, url: 'https://cdn.example.invalid/qing-about-return-46.jpg', state: 'releaseReady', authorization: 'confirmed', identityCheck: 'confirmed' } : a), { ...candidate.asset, url: 'https://cdn.example.invalid/' + candidate.asset.file }]
    const presented = presentAbout(c)
    // 小程序里图片走本机静态服务；presentAbout 只认 https，故在通过校验后再替换地址
    if (presented.story && presented.story.portrait && PHOTOS) presented.story.portrait = { ...presented.story.portrait, url: PHOTOS + candidate.asset.file }
    return presented
  }
  const approved = { state: 'releaseReady', authorization: 'confirmed', editorialReview: 'approved', factCheck: 'confirmed', source: { document: 'unit-test-only' } }
  const person = { ...approved, id: 'unit-person-a', personKey: 'unit-person-a', name: '单元人物甲', identityCheck: 'confirmed', history: ['单元测试经历，不是业务事实'], intro: '单元测试简介', quote: '完整单元引语。', closingQuote: '单元收束引语。', attribution: '单元人物甲 · 单元夹具' }
  const c = clone(content)
  c.storySlot = { ...c.storySlot, heading: '单元通用故事标题', item: person }
  // 同时把 #46 设为已获批，验证故事显示时它仍被隐藏。
  c.assets = c.assets.map(a => a.id === 'scene-46' ? { ...a, url: 'https://cdn.example.invalid/qing-about-return-46.jpg', state: 'releaseReady', authorization: 'confirmed', identityCheck: 'confirmed' } : a)
  return presentAbout(c)
}

function photo(id, file) {
  const asset = content.assets.find(a => a.id === id)
  return { id, url: PHOTOS + file, caption: asset.caption, alt: asset.alt }
}

async function main() {
  fs.mkdirSync(OUT, { recursive: true })
  const miniProgram = await loadAutomator().connect({ wsEndpoint: option('ws', 'ws://127.0.0.1:9420') })
  const system = await miniProgram.systemInfo()
  const shot = async name => { const file = path.join(OUT, name + '.png'); const size = await shoot(miniProgram, file, 390); console.log(`✔ ${name}  ${size.width}×${size.height}`); return file }
  const long = async name => { await miniProgram.pageScrollTo(0); await sleep(800); const first = await shot(name); await captureLong(miniProgram, { out: OUT, name, first, viewport: system.windowHeight, fixedTop: NAV, fixedBottom: TABBAR }) }
  try {
    let page = await relaunch(miniProgram, '/pages/about/index')
    await sleep(5000)
    const base = await page.data()
    console.log(base.latestCamp ? `最近一期：${base.latestCamp.periodLabel}` : '最近一期：未读到（卡片隐藏）')

    if (PHOTOS) {
      const transparency = { ...base.transparency, photo: photo('scene-67', 'qing-about-juice-67.jpg') }
      await page.setData({ entry: { ...base.entry, photo: photo('scene-50', 'qing-about-opening-50.jpg') }, storyScene: photo('scene-46', 'qing-about-return-46.jpg'), transparency })
      await sleep(2500)
      await long('about-long-photos-on')
      await page.setData({ entry: { ...base.entry, photo: null }, storyScene: null, latestCamp: null })
      await sleep(800)
      await long('about-long-50-46-off-no-latest')
    }

    const story = approvedStoryContent()
    if (story.storyScene !== null) throw new Error('故事获批时 storyScene 应为 null')
    await page.setData({ story: story.story, storyScene: story.storyScene, returnHeading: story.returnHeading })
    await sleep(800)
    const element = await page.$('#about-story-evidence')
    const offset = await element.offset() // 相对视口；加上当前滚动量得到页面坐标
    const scrolled = await miniProgram.evaluate(() => new Promise(r => wx.createSelectorQuery().selectViewport().scrollOffset().exec(x => r(x[0].scrollTop))))
    await miniProgram.pageScrollTo(Math.max(0, offset.top + scrolled - NAV))
    await sleep(800)
    await shot(STORY ? 'about-story-candidate-approved' : 'about-story-approved-46-hidden')
    if (STORY) {
      const scrolledNow = await miniProgram.evaluate(() => new Promise(r => wx.createSelectorQuery().selectViewport().scrollOffset().exec(x => r(x[0].scrollTop))))
      await miniProgram.pageScrollTo(scrolledNow + system.windowHeight - NAV - TABBAR)
      await sleep(1200)
      await shot('about-story-candidate-approved-2')
    }

    page = await relaunch(miniProgram, '/pages/about/index')
    await sleep(4000)
    await page.setData({ fontScale: 1.4 })
    await sleep(600)
    await shot('about-first-font-1.4')
  } finally {
    await miniProgram.pageScrollTo(0).catch(() => {})
    miniProgram.disconnect()
  }
}

main().catch(error => { console.error(`about-states 失败：${error.message || JSON.stringify(error)}`); process.exit(1) })
