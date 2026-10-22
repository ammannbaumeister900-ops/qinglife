const { test } = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const vm = require('node:vm')
const root = path.resolve(__dirname, '..')
const componentRoot = path.join(root, 'components/ql-nav-space')
const source = fs.readFileSync(path.join(componentRoot, 'index.js'), 'utf8')
const wxml = fs.readFileSync(path.join(componentRoot, 'index.wxml'), 'utf8')
const css = fs.readFileSync(path.join(componentRoot, 'index.wxss'), 'utf8')
const plain = value => JSON.parse(JSON.stringify(value))

function fixture(options = {}) {
  let component, pages = options.pages || [{}]
  const calls = []
  const wx = {
    getWindowInfo: () => options.window || { windowWidth: 375, statusBarHeight: 44 },
    getSystemInfoSync: () => options.window || { windowWidth: 375, statusBarHeight: 44 },
    getMenuButtonBoundingClientRect: () => {
      if (options.menuError) throw new Error('menu unavailable')
      return options.menu === undefined ? { top: 50, height: 32, left: 276, right: 363 } : options.menu
    },
    navigateBack: () => calls.push({ method: 'navigateBack' }),
    switchTab: value => calls.push({ method: 'switchTab', ...value })
  }
  if (options.legacy) delete wx.getWindowInfo
  if (options.noMenuApi) delete wx.getMenuButtonBoundingClientRect
  vm.runInNewContext(source, { Component: value => { component = value }, wx, getCurrentPages: () => pages })
  component.data = { ...plain(component.data), immersive: !!options.immersive }
  component.setData = patch => Object.assign(component.data, patch)
  component.lifetimes.attached.call(component)
  return { component, calls, setPages: value => { pages = value } }
}
const style = value => Object.fromEntries([...value.matchAll(/([\w-]+):\s*([\d.]+)px/g)].map(match => [match[1], Number(match[2])]))
const rule = selector => css.match(new RegExp('\\.' + selector + '\\s*\\{([^}]+)\\}'))[1]

test('back uses the current page stack and retains the home-tab fallback', () => {
  const f = fixture({ pages: [{}, {}] })
  f.component.methods.goBack.call(f.component)
  assert.deepEqual(f.calls, [{ method: 'navigateBack' }])
  f.setPages([{}]); f.component.methods.goBack.call(f.component)
  f.setPages([]); f.component.methods.goBack.call(f.component)
  assert.deepEqual(f.calls.slice(1), [
    { method: 'switchTab', url: '/pages/home/index' }, { method: 'switchTab', url: '/pages/home/index' }
  ])
})

test('different status bars and capsules retain dynamic vertical alignment and a matching content spacer', () => {
  for (const device of [
    { window: { windowWidth: 320, statusBarHeight: 20 }, menu: { top: 26, height: 32, left: 220 } },
    { window: { windowWidth: 375, statusBarHeight: 44 }, menu: { top: 50, height: 32, left: 276 } },
    { window: { windowWidth: 430, statusBarHeight: 59 }, menu: { top: 65, height: 34, left: 330 } }
  ]) {
    const { data } = fixture(device).component
    const control = style(data.controlStyle), bar = style(data.barStyle), space = style(data.spaceStyle)
    assert.equal(control.top, device.menu.top)
    assert.equal(control.height, device.menu.height)
    assert.equal(data.totalHeight, device.menu.top + device.menu.height + 8)
    assert.equal(space.height, bar.height)
    assert.equal(space.height, data.totalHeight)
    assert.ok(control.top >= device.window.statusBarHeight)
    assert.ok(control.top + control.height <= bar.height)
  }
})

test('title margins are symmetric and leave a gap before the measured capsule', () => {
  for (const width of [320, 375, 430]) for (const capsuleWidth of [88, 104]) {
    const menu = { top: 30, height: 32, left: width - capsuleWidth - 12 }
    const f = fixture({ window: { windowWidth: width, statusBarHeight: 24 }, menu })
    const title = style(f.component.data.titleStyle)
    assert.equal(title.left, title.right)
    assert.equal(width - title.right, menu.left - 12)
    assert.ok(title.left >= 56, 'title must also clear the existing back control')
    assert.ok(width - title.left - title.right > 0)
  }
})

test('missing, zero or failed capsule readings and legacy window API preserve safe fallbacks', () => {
  for (const extra of [{ menu: null }, { menu: {} }, { menu: { top: 0, height: 0, left: 0 } }, { menuError: true }, { noMenuApi: true }]) {
    const f = fixture({ ...extra, legacy: true, window: { screenWidth: 375, statusBarHeight: 24 } })
    assert.deepEqual(style(f.component.data.controlStyle), { top: 30, height: 32, 'line-height': 32 })
    assert.equal(f.component.data.totalHeight, 70)
    assert.deepEqual(style(f.component.data.titleStyle), { left: 112, right: 112 })
  }
})

test('immersive cover keeps its zero spacer and restores full height when returning to a normal view', () => {
  const f = fixture({ immersive: true })
  const bar = f.component.data.barStyle, control = f.component.data.controlStyle
  assert.equal(style(f.component.data.spaceStyle).height, 0)
  f.component.observers.immersive.call(f.component, false)
  assert.equal(style(f.component.data.spaceStyle).height, f.component.data.totalHeight)
  f.component.observers.immersive.call(f.component, true)
  assert.equal(style(f.component.data.spaceStyle).height, 0)
  assert.equal(f.component.data.barStyle, bar)
  assert.equal(f.component.data.controlStyle, control)
})

test('navigation stays fixed to the viewport while the spacer remains in document flow', () => {
  assert.match(rule('ql-nav-layer'), /position:\s*fixed/)
  assert.match(rule('ql-nav-layer'), /top:\s*0/)
  assert.match(rule('ql-nav-layer'), /z-index:\s*80/)
  assert.match(rule('ql-nav-space'), /position:\s*relative/)
  assert.match(rule('ql-nav-layer'), /pointer-events:\s*none/)
  assert.match(rule('ql-back'), /pointer-events:\s*auto/)
  assert.ok(css.includes('.ql-nav-layer.overlay { background: transparent; }'))
  assert.ok(wxml.includes('style="{{spaceStyle}}"') && wxml.includes('style="{{barStyle}}"'))
  assert.ok(wxml.includes('style="{{controlStyle}} {{titleStyle}}"'))
  assert.ok(wxml.includes('wx:if="{{back}}"') && wxml.includes('bindtap="goBack"') && wxml.includes('aria-label="返回"'))
  assert.ok(wxml.includes('wx:if="{{title}}"'))
})

test('article, camp-flow and all other consumers keep their shared component configuration and appropriate content spacers', () => {
  const app = JSON.parse(fs.readFileSync(path.join(root, 'app.json'), 'utf8'))
  const users = []
  for (const route of app.pages) {
    const page = fs.readFileSync(path.join(root, route + '.wxml'), 'utf8')
    if (!page.includes('<ql-nav-space')) continue
    users.push(route)
    const config = JSON.parse(fs.readFileSync(path.join(root, route + '.json'), 'utf8'))
    assert.equal(config.navigationStyle, 'custom')
    assert.equal(config.usingComponents['ql-nav-space'], '/components/ql-nav-space/index')
  }
  assert.ok(users.includes('pages/camp-reflection/index'))
  const article = fs.readFileSync(path.join(root, 'pages/article/index.wxml'), 'utf8')
  const articleNav = article.match(/<ql-nav-space\b[^>]*\/>/)[0]
  assert.match(articleNav, /\bback(?:\s|\/)/)
  assert.ok(!/\bimmersive(?:=|\s|\/)/.test(articleNav), 'article must retain the shared content spacer')
  const camp = fs.readFileSync(path.join(root, 'pages/camp-flow/index.wxml'), 'utf8')
  assert.match(camp, /<ql-nav-space title="[^"]*" back\s*\/>/)
  assert.ok(!camp.includes('back immersive='), 'decision-first camp detail must retain the measured content spacer')
  assert.deepEqual(app.tabBar.list.map(item => item.pagePath), ['pages/home/index', 'pages/about/index', 'pages/content/index', 'pages/mine/index'])
  assert.equal(app.tabBar.custom, true)
  assert.equal(JSON.parse(fs.readFileSync(path.join(componentRoot, 'index.json'), 'utf8')).component, true)
})
