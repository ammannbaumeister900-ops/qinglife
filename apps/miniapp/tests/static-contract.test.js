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
  'swiper',
  'swiper-item',
  'text',
  'textarea',
  'view'
]

assert.deepStrictEqual(app.tabBar.list.map((item) => item.text), ['首页', '轻体营', '轻读', '我的'])
assert.strictEqual(new Set(app.pages).size, app.pages.length, '页面不能重复注册')

const contentPage = fs.readFileSync(path.join(root, 'pages/content/index.wxml'), 'utf8')
const friendsPage = fs.readFileSync(path.join(root, 'pages/friends/index.wxml'), 'utf8')
const minePage = fs.readFileSync(path.join(root, 'pages/mine/index.wxml'), 'utf8')
const mineFlowPage = fs.readFileSync(path.join(root, 'pages/mine-flow/index.wxml'), 'utf8')
const homePage = fs.readFileSync(path.join(root, 'pages/home/index.wxml'), 'utf8')
const campFlowPage = fs.readFileSync(path.join(root, 'pages/camp-flow/index.wxml'), 'utf8')
const staffWorkspacePage = fs.readFileSync(path.join(root, 'staff/workspace/index.wxml'), 'utf8')
const businessApi = fs.readFileSync(path.join(root, 'services/business-api.js'), 'utf8')
const demoData = require(path.join(root, 'data/demo'))
assert.ok(contentPage.includes('article-footer') && !contentPage.includes('阅读全文'), '文章卡片整体可点击时不应重复展示阅读入口')
assert.ok(contentPage.includes('chooseTopic') && !contentPage.includes('content-switch'), '轻读使用主题与统一列表')
assert.ok(!friendsPage.includes('＋ 发布'), '轻友页不能保留重复发布入口')
assert.ok(friendsPage.includes('openArchive'), '旧轻友页面必须兼容历史入口')
assert.ok(minePage.includes('journey-timeline'), '我的页面需展示参与经历时间轴')
assert.ok(minePage.includes('bindtap="login"') && minePage.includes('微信登录'), '我的页面未登录态需提供真实微信登录入口')
assert.ok(!minePage.includes('timeline-title') && !minePage.includes('timeline-note') && !minePage.includes('timeline-link'), '一级参与经历只保留期次、时间地点和状态')
assert.ok(!minePage.includes('我的轻习惯') && !minePage.includes('切换演示阶段'), '我的页面不能展示轻习惯或演示控制')
assert.deepStrictEqual(demoData.participations.map((item) => item.sessionNumber), [501, 357, 100], '一级参与经历需按明确期次展示')
assert.ok(mineFlowPage.includes('experience-accordion') && mineFlowPage.includes('toggleExperienceDetail'), '二级经历页需折叠展示当天节点')
assert.ok(homePage.includes('featured-camp') && homePage.includes('featuredReadings') && homePage.includes('<swiper'), '首页需保持当前活动和可滑动精选轻读')
assert.ok(campFlowPage.includes('主要联系人') && campFlowPage.includes('手机号（选填）'), '报名需区分可靠联系人和可缺失手机号的参与人')
assert.ok(campFlowPage.includes('结算状态：待工作人员确认') && campFlowPage.includes('结算结果：已使用') && !campFlowPage.includes('付款状态：'), '轻友端只展示报名状态和已确认的现金或卡次结算结果')
assert.ok(minePage.includes('当前剩余') && minePage.includes('卡次消耗记录'), '轻友端需展示当前卡次余额和消耗记录')
assert.ok(minePage.includes('联系轻生活') && minePage.includes('复制微信号'), '我的页面需提供稳定客服入口')
assert.ok(staffWorkspacePage.includes("view==='settlement'") && staffWorkspacePage.includes('确认结算结果'), '工作人员端需支持人工确认最终结算')
assert.ok(businessApi.includes("'/readings/featured'") && businessApi.includes("'/contact'"), '首页精选和客服必须读取统一业务配置')

for (const page of ['content', 'friends', 'mine', 'camp-flow', 'friend-flow', 'mine-flow']) {
  const wxml = fs.readFileSync(path.join(root, 'pages', page, 'index.wxml'), 'utf8')
  assert.ok(wxml.includes('editorial-page'), `${page} 需使用统一编辑感视觉体系`)
}

const articlePage = fs.readFileSync(path.join(root, 'pages/article/index.wxml'), 'utf8')
assert.ok(articlePage.includes('article-cover') && articlePage.includes('article-actionbar'), '文章详情需使用沉浸封面与弱操作栏')

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
