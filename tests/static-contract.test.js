const assert = require('assert')
const fs = require('fs')
const path = require('path')

const root = path.resolve(__dirname, '..')
const app = JSON.parse(fs.readFileSync(path.join(root, 'app.json'), 'utf8'))
const nativeTags = [
  'block',
  'button',
  'checkbox',
  'cover-view',
  'image',
  'input',
  'rich-text',
  'switch',
  'text',
  'textarea',
  'view'
]

assert.deepStrictEqual(app.tabBar.list.map((item) => item.text), ['首页', '轻体营', '内容', '轻友', '我的'])
assert.strictEqual(new Set(app.pages).size, app.pages.length, '页面不能重复注册')

const contentPage = fs.readFileSync(path.join(root, 'pages/content/index.wxml'), 'utf8')
const friendsPage = fs.readFileSync(path.join(root, 'pages/friends/index.wxml'), 'utf8')
const minePage = fs.readFileSync(path.join(root, 'pages/mine/index.wxml'), 'utf8')
const mineFlowPage = fs.readFileSync(path.join(root, 'pages/mine-flow/index.wxml'), 'utf8')
const demoData = require(path.join(root, 'data/demo'))
assert.ok(contentPage.includes('article-footer') && !contentPage.includes('阅读全文'), '文章卡片整体可点击时不应重复展示阅读入口')
assert.ok(contentPage.includes('concern-grid') && contentPage.includes('directory-panel'), '按问题找需使用聚合式双层结构')
assert.ok(!friendsPage.includes('＋ 发布'), '轻友页不能保留重复发布入口')
assert.ok(friendsPage.includes('comment-panel'), '评论需在动态卡片内展开')
assert.ok(minePage.includes('journey-timeline'), '我的页面需展示参与经历时间轴')
assert.ok(!minePage.includes('timeline-title') && !minePage.includes('timeline-note') && !minePage.includes('timeline-link'), '一级参与经历只保留期次、时间地点和状态')
assert.ok(!minePage.includes('我的轻习惯') && !minePage.includes('切换演示阶段'), '我的页面不能展示轻习惯或演示控制')
assert.deepStrictEqual(demoData.participations.map((item) => item.sessionNumber), [501, 357, 100], '一级参与经历需按明确期次展示')
assert.ok(mineFlowPage.includes('experience-accordion') && mineFlowPage.includes('toggleExperienceDetail'), '二级经历页需折叠展示当天节点')

for (const page of ['content', 'friends', 'mine', 'article', 'camp-flow', 'friend-flow', 'mine-flow']) {
  const wxml = fs.readFileSync(path.join(root, 'pages', page, 'index.wxml'), 'utf8')
  assert.ok(wxml.includes('editorial-page'), `${page} 需使用统一编辑感视觉体系`)
}

for (const page of app.pages) {
  const base = path.join(root, page)
  for (const extension of ['.js', '.json', '.wxml', '.wxss']) {
    assert.ok(fs.existsSync(`${base}${extension}`), `${page}${extension} 缺失`)
  }

  const js = fs.readFileSync(`${base}.js`, 'utf8')
  const pageConfig = JSON.parse(fs.readFileSync(`${base}.json`, 'utf8'))
  const wxml = fs.readFileSync(`${base}.wxml`, 'utf8')
  const allowedTags = new Set([
    ...nativeTags,
    ...Object.keys(app.usingComponents || {}),
    ...Object.keys(pageConfig.usingComponents || {})
  ])
  assert.ok(!/\.(includes|slice|filter|find)\(/.test(wxml), `${page} 的 WXML 使用了不支持的方法调用`)

  for (const match of wxml.matchAll(/<\/?([a-z-]+)/g)) {
    assert.ok(allowedTags.has(match[1]), `${page} 使用未知标签 ${match[1]}`)
  }

  for (const match of wxml.matchAll(/bind(?:tap|input)="([A-Za-z0-9_]+)"/g)) {
    assert.ok(new RegExp(`\\n\\s*(?:async\\s+)?${match[1]}\\s*\\(`).test(js), `${page} 缺少事件方法 ${match[1]}`)
  }
}

for (const file of fs.readdirSync(path.join(root, 'pages'), { withFileTypes: true })) {
  if (!file.isDirectory()) continue
  const jsPath = path.join(root, 'pages', file.name, 'index.js')
  if (!fs.existsSync(jsPath)) continue
  const js = fs.readFileSync(jsPath, 'utf8')
  for (const match of js.matchAll(/['"](\/pages\/[a-z-]+\/index)(?:\?[^'"]*)?['"]/g)) {
    assert.ok(app.pages.includes(match[1].slice(1)), `未注册的跳转目标 ${match[1]}`)
  }
}

console.log('static-contract.test.js OK')
