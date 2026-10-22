#!/usr/bin/env node
/*
 * 可点区域检查（PLAN §5-P6）：在开发者工具里打开改版页面，量出所有 button 和绑定了 tap 的元素，宽高都应 ≥ 44px。
 *   node tools/audit/touch.cjs [--json out.json]
 * 绑定 tap 的元素按 WXML 中的静态类名查找；尺寸为 0 的（当前状态未显示）跳过并计数。
 * 自定义 Tab 栏在页面节点之外取不到，按样式核算：高 118rpx（390 宽约 61px），宽为屏宽 1/4。
 */
const fs = require('fs')
const path = require('path')
const { loadAutomator, relaunch, sleep } = require('../capture/capture.cjs')

const MINIAPP = path.resolve(__dirname, '../../apps/miniapp')
const MIN = 44
const PAGES = [
  { name: '首页', route: '/pages/home/index', wxml: 'pages/home/index.wxml' },
  { name: '了解轻生活', route: '/pages/about/index', wxml: 'pages/about/index.wxml' },
  { name: '清体营', route: '/pages/camp/index', wxml: 'pages/camp/index.wxml' },
  { name: '营期详情', route: '/pages/camp/index', open: '.camp-action', wxml: 'pages/camp-flow/index.wxml', only: /^(detail-|action-|primary)/ }
]

function tapClasses(file, only) {
  const wxml = fs.readFileSync(path.join(MINIAPP, file), 'utf8'), found = new Set()
  for (const tag of wxml.match(/<(?:[^<>"']|"[^"]*"|'[^']*')+>/g) || []) {
    if (!/\b(bind|catch)tap=/.test(tag) && !/^<button\b/.test(tag)) continue
    const cls = ((tag.match(/\sclass="([^"]*)"/) || [])[1] || '').replace(/\{\{[\s\S]*?\}\}/g, ' ').trim().split(/\s+/)[0]
    if (cls && (!only || only.test(cls))) found.add(cls)
  }
  return [...found]
}

async function main() {
  const miniProgram = await loadAutomator().connect({ wsEndpoint: 'ws://127.0.0.1:9420' })
  const results = []
  try {
    for (const target of PAGES) {
      let page = await relaunch(miniProgram, target.route)
      await sleep(4000)
      if (target.open) { const el = await page.$(target.open); if (el) { await el.tap(); await sleep(4000); page = await miniProgram.currentPage() } }
      for (const cls of tapClasses(target.wxml, target.only)) {
        for (const element of await page.$$('.' + cls)) {
          const { width, height } = await element.size()
          if (!width || !height) { results.push({ page: target.name, cls, hidden: true }); continue }
          results.push({ page: target.name, cls, width: Math.round(width), height: Math.round(height), pass: width >= MIN && height >= MIN })
        }
      }
    }
  } finally {
    miniProgram.disconnect()
  }
  const out = process.argv.indexOf('--json')
  if (out > 0) fs.writeFileSync(process.argv[out + 1], JSON.stringify(results, null, 2))
  const measured = results.filter(r => !r.hidden), failed = measured.filter(r => !r.pass)
  console.log(`触控尺寸：量到 ${measured.length} 个可见可点元素，未达 ${MIN}px 的 ${failed.length} 个；当前状态未显示 ${results.length - measured.length} 个`)
  for (const r of failed) console.log(`✖ ${r.page}  .${r.cls}  ${r.width}×${r.height}`)
  process.exit(failed.length ? 1 : 0)
}

main().catch(error => { console.error(`touch 失败：${error.message || JSON.stringify(error)}`); process.exit(1) })
