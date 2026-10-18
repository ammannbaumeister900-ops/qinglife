const { test } = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs'), path = require('node:path'), vm = require('node:vm')
const root = path.resolve(__dirname, '..')

function loadNavigation(stack) {
  const calls = [], module = { exports: {} }
  const wx = { navigateBack: o => calls.push({ method: 'navigateBack', ...o }), redirectTo: o => calls.push({ method: 'redirectTo', url: o.url }), hideLoading: () => {} }
  vm.runInNewContext(fs.readFileSync(path.join(root, 'utils/navigation.js'), 'utf8'), { module, wx, getCurrentPages: () => stack.map(route => ({ route })) })
  return { navigation: module.exports, calls }
}

test('returnTo goes back to the previous camp page instead of stacking it again', () => {
  const { navigation, calls } = loadNavigation(['pages/home/index', 'pages/camp/index', 'pages/camp-flow/index'])
  navigation.returnTo({ url: '/pages/camp/index' })
  assert.deepEqual(calls, [{ method: 'navigateBack' }])
})

test('returnTo replaces the current page when camp is not the previous page (share entry or other origin)', () => {
  for (const stack of [['pages/camp-flow/index'], ['pages/mine/index', 'pages/camp-flow/index']]) {
    const { navigation, calls } = loadNavigation(stack)
    navigation.returnTo({ url: '/pages/camp/index' })
    assert.deepEqual(calls, [{ method: 'redirectTo', url: '/pages/camp/index' }])
  }
})

test('about is the second tab: it selects tab 1 on show and camp is no longer a tab', () => {
  const app = JSON.parse(fs.readFileSync(path.join(root, 'app.json'), 'utf8'))
  assert.equal(app.tabBar.list[1].pagePath, 'pages/about/index')
  assert(app.pages.includes('pages/camp/index'))
  assert(!app.tabBar.list.some(item => item.pagePath === 'pages/camp/index'))
  let page; const tabs = []
  vm.runInNewContext(fs.readFileSync(path.join(root, 'pages/about/index.js'), 'utf8'), {
    Page: v => { page = v }, wx: { getSystemInfoSync: () => ({}) },
    require: id => id.includes('utils/navigation') ? {} : require(path.resolve(root, 'pages/about', id))
  })
  page.setData = patch => Object.assign(page.data, patch)
  page.getTabBar = () => ({ setData: patch => tabs.push(patch) })
  page.onShow()
  assert.deepEqual(JSON.parse(JSON.stringify(tabs)), [{ selected: 1 }])
})
