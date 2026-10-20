#!/usr/bin/env node
/*
 * 清体营二级页与详情头部（PLAN §5-P5）截图：列表（#48 页头关 / 开）、筛选态、详情头部、底部报名栏。
 *   node tools/capture/camp-states.cjs [--photos http://127.0.0.1:8765/]
 * 只在模拟器里用 setData 打开页头图，不改源码或配置。
 */
const fs = require('fs')
const path = require('path')
const { REPO, loadAutomator, relaunch, shoot, sleep } = require('./capture.cjs')

const args = process.argv.slice(2)
const option = (name, fallback) => { const i = args.indexOf('--' + name); return i >= 0 ? args[i + 1] : fallback }
const OUT = path.resolve(option('out', path.join(REPO, 'test-artifacts/visual-2026q4/p5')))
const PHOTOS = option('photos', '')

async function main() {
  fs.mkdirSync(OUT, { recursive: true })
  const miniProgram = await loadAutomator().connect({ wsEndpoint: option('ws', 'ws://127.0.0.1:9420') })
  const shot = async name => { const size = await shoot(miniProgram, path.join(OUT, name + '.png'), 390); console.log(`✔ ${name}  ${size.width}×${size.height}`) }
  try {
    let page = await relaunch(miniProgram, '/pages/camp/index')
    await sleep(5000)
    await shot('camp-list')
    if (PHOTOS) {
      const header = (await page.data()).campHeader
      await page.setData({ campHeader: { ...header, url: PHOTOS + 'qing-camp-header-48.jpg' } })
      await sleep(1500)
      await shot('camp-list-header-48')
    }
    const filters = await page.$$('.camp-filter')
    if (filters.length > 1) { await filters[1].tap(); await sleep(800); await shot('camp-list-filtered') } else console.log('— 只有一个筛选项，跳过')

    page = await relaunch(miniProgram, '/pages/camp/index')
    await sleep(4000)
    const detail = await page.$('.camp-action')
    if (!detail) throw new Error('列表里没有可进入详情的营期')
    await detail.tap()
    await sleep(4000)
    await shot('detail-header-and-signup-bar')
  } finally {
    miniProgram.disconnect()
  }
}

main().catch(error => { console.error(`camp-states 失败：${error.message || JSON.stringify(error)}`); process.exit(1) })
