#!/usr/bin/env node
/*
 * 第二 Tab 调整（PLAN §5-P2）的 6 条导航走查：每一步记录页面栈、Tab 选中项并截图。
 *   node tools/capture/nav-paths.cjs [--out DIR] [--ws ws://127.0.0.1:9420]
 * 输出 DIR/<路径>-<步骤>.png 与 DIR/nav-paths.json。点击优先用真实元素 tap；
 * Tab 栏是自定义组件，用它自己的 switchTab 方法（与点击同一入口）。
 */
const fs = require('fs')
const path = require('path')
const { REPO, loadAutomator, relaunch, shoot, sleep } = require('./capture.cjs')

const args = process.argv.slice(2)
const option = (name, fallback) => { const i = args.indexOf('--' + name); return i >= 0 ? args[i + 1] : fallback }
const OUT = path.resolve(option('out', path.join(REPO, 'test-artifacts/visual-2026q4/p2')))
const WS = option('ws', 'ws://127.0.0.1:9420')

async function state(miniProgram) {
  return miniProgram.evaluate(() => {
    const pages = getCurrentPages()
    const current = pages[pages.length - 1]
    const tab = current && current.getTabBar && current.getTabBar()
    return { stack: pages.map(page => page.route), selected: tab ? tab.data.selected : null }
  })
}

async function tap(miniProgram, selector) {
  const page = await miniProgram.currentPage()
  const element = await page.$(selector)
  if (!element) throw new Error(`找不到 ${selector}（${page.path}）`)
  await element.tap()
}

// automator 中自定义组件的标签名一律是 component，按组件内是否有返回键找到 ql-nav-space。
async function back(miniProgram) {
  const page = await miniProgram.currentPage()
  for (const component of await page.$$('component')) {
    const button = await component.$('.ql-back')
    if (button) return button.tap()
  }
  throw new Error(`找不到返回键（${page.path}）`)
}
const tabTo = (miniProgram, index, url) => miniProgram.evaluate((i, u) => {
  const pages = getCurrentPages()
  pages[pages.length - 1].getTabBar().switchTab({ currentTarget: { dataset: { index: i, path: u } } })
}, index, url)

const PATHS = [
  { name: '1-home-about-camp-detail', start: '/pages/home/index', steps: [
    ['about-tab', m => tabTo(m, 1, '/pages/about/index'), { route: 'pages/about/index', selected: 1 }],
    ['learn-registration', m => tap(m, '.about-primary'), { route: 'pages/camp/index' }],
    ['camp-detail', m => tap(m, '.camp-action'), { route: 'pages/camp-flow/index' }],
    ['back-to-camp', back, { route: 'pages/camp/index' }],
    ['back-to-about', back, { route: 'pages/about/index', selected: 1 }]
  ] },
  { name: '2-home-camp-list-back', start: '/pages/home/index', steps: [
    // P3 才有"全部营期 →"，此处直接调用新增的 openCampList()。
    ['open-camp-list', async m => (await m.currentPage()).callMethod('openCampList'), { route: 'pages/camp/index' }],
    ['back-to-home', back, { route: 'pages/home/index', selected: 0 }]
  ] },
  { name: '3-mine-personal-camp-back', start: '/pages/mine/index', steps: [
    ['personal', m => m.evaluate(() => { const p = getCurrentPages(); p[p.length - 1].openFlow({ currentTarget: { dataset: { view: 'experiences' } } }) }), { route: 'pages/personal/index' }],
    ['camp', async m => (await m.currentPage()).callMethod('openCamp'), { route: 'pages/camp/index' }],
    ['back-to-personal', back, { route: 'pages/personal/index' }],
    ['back-to-mine', back, { route: 'pages/mine/index', selected: 3 }]
  ] },
  { name: '4-share-camp-back-home', start: '/pages/camp/index', steps: [
    ['back-to-home', back, { route: 'pages/home/index', selected: 0 }]
  ] },
  { name: '5-share-about-tab', start: '/pages/about/index', steps: [] , expectStart: { route: 'pages/about/index', selected: 1 } },
  { name: '6-camp-flow-back-to-camp', start: '/pages/camp/index', steps: [
    ['camp-detail', m => tap(m, '.camp-action'), { route: 'pages/camp-flow/index' }],
    ['back-to-camp', async m => (await m.currentPage()).callMethod('backToCamp'), { route: 'pages/camp/index', depth: 1 }]
  ] },
  { name: '6b-direct-camp-flow-back-to-camp', start: '/pages/camp-flow/index', steps: [
    ['back-to-camp', async m => (await m.currentPage()).callMethod('backToCamp'), { route: 'pages/camp/index', depth: 1 }]
  ] }
]

function check(actual, expected) {
  if (!expected) return []
  const problems = []
  const route = actual.stack[actual.stack.length - 1]
  if (expected.route && route !== expected.route) problems.push(`当前页 ${route}，应为 ${expected.route}`)
  if ('selected' in expected && actual.selected !== expected.selected) problems.push(`Tab 选中 ${actual.selected}，应为 ${expected.selected}`)
  if (expected.depth && actual.stack.length !== expected.depth) problems.push(`页面栈 ${actual.stack.length} 层，应为 ${expected.depth}`)
  if (new Set(actual.stack).size !== actual.stack.length) problems.push('页面栈有重复页面')
  return problems
}

async function main() {
  fs.mkdirSync(OUT, { recursive: true })
  const miniProgram = await loadAutomator().connect({ wsEndpoint: WS })
  const report = []
  try {
    for (const item of PATHS) {
      const record = { name: item.name, steps: [] }
      report.push(record)
      const run = async (label, action, expected) => {
        if (action) await action(miniProgram)
        await sleep(2500)
        const actual = await state(miniProgram)
        const file = `${item.name}-${String(record.steps.length).padStart(2, '0')}-${label}.png`
        await shoot(miniProgram, path.join(OUT, file), 390)
        const problems = check(actual, expected)
        record.steps.push({ label, ...actual, screenshot: file, problems })
        console.log(`${problems.length ? '✖' : '✔'} ${item.name} / ${label}: [${actual.stack.join(' > ')}] tab=${actual.selected}${problems.length ? '  ' + problems.join('；') : ''}`)
      }
      try {
        await relaunch(miniProgram, item.start)
        await run('start', null, item.expectStart)
        for (const [label, action, expected] of item.steps) await run(label, action, expected)
      } catch (error) {
        record.error = error.message || String(error)
        console.log(`✖ ${item.name}: ${record.error}`)
      }
    }
  } finally {
    fs.writeFileSync(path.join(OUT, 'nav-paths.json'), JSON.stringify(report, null, 2))
    miniProgram.disconnect()
  }
  const failed = report.filter(r => r.error || r.steps.some(s => s.problems.length))
  console.log(`${report.length - failed.length}/${report.length} 条路径通过 → ${path.relative(REPO, OUT)}`)
  process.exit(failed.length ? 1 : 0)
}

main().catch(error => { console.error(`nav-paths 失败：${error.message || JSON.stringify(error)}`); process.exit(1) })
