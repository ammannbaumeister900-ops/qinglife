const assert = require('assert')
const fs = require('fs')
const path = require('path')

const root = path.resolve(__dirname, '..')
const appStyles = fs.readFileSync(path.join(root, 'app.wxss'), 'utf8')
const demo = require(path.join(root, 'data/demo'))

// P7 bridge: legacy --ql-* colours resolve to the 2026Q4 --q-* tokens, so unrevised pages share one palette.
const bridged = { '--ql-bg': '--q-linen', '--ql-surface': '--q-white', '--ql-ink': '--q-ink', '--ql-ink-light': '--q-ink-2', '--ql-border': '--q-line', '--ql-nav-bg': '--q-linen', '--ql-clay-deep': '--q-moss', '--ql-sage-deep': '--q-moss', '--ql-warning': '--q-amber' }
for (const [legacy, token] of Object.entries(bridged)) {
  assert.ok(new RegExp(`${legacy}:\\s*var\\(${token}\\);`).test(appStyles), `${legacy} 未桥接到 ${token}`)
}
assert.ok(appStyles.indexOf('@import "styles/qing-tokens.wxss"') === 0, '令牌文件须在 app.wxss 顶部导入')
const appConfig = JSON.parse(fs.readFileSync(path.join(root, 'app.json'), 'utf8'))
const linen = fs.readFileSync(path.join(root, 'styles/qing-tokens.wxss'), 'utf8').match(/--q-linen:\s*(#[0-9A-Fa-f]{6})/)[1]
assert.strictEqual(appConfig.window.navigationBarBackgroundColor, linen, '系统导航栏须与亚麻纸色一致')
assert.strictEqual(appConfig.window.backgroundColor, linen, '窗口背景须与亚麻纸色一致')

for (const activity of demo.activities) {
  assert.ok(activity.capacity > 0, `${activity.id} 缺少人数数据`)
  assert.ok(activity.suitedFor.length && activity.talkFirst.length, `${activity.id} 缺少适合边界`)
  assert.ok(activity.rules.change && activity.rules.body && activity.rules.privacy, `${activity.id} 缺少规则与隐私数据`)
}

assert.deepStrictEqual(demo.uiTestScenarios.empty, {
  activities: [], articles: [], posts: [], participations: []
})
assert.deepStrictEqual(demo.activityStatusFixtures.map((item) => item.status), ['开放报名', '即将满员', '已满', '候补中', '已取消'])

const navPages = ['home', 'camp', 'content', 'friends', 'mine', 'camp-flow', 'article', 'friend-flow', 'mine-flow']
for (const page of navPages) {
  const config = JSON.parse(fs.readFileSync(path.join(root, 'pages', page, 'index.json'), 'utf8'))
  const wxml = fs.readFileSync(path.join(root, 'pages', page, 'index.wxml'), 'utf8')
  assert.strictEqual(config.navigationStyle, 'custom', `${page} 未使用动态胶囊安全区`)
  assert.ok(wxml.includes('<ql-nav-space'), `${page} 缺少动态胶囊安全区`)
}

const campDetail = fs.readFileSync(path.join(root, 'pages/camp-flow/index.wxml'), 'utf8')
// Verify decision facts and actions rather than obsolete card-layout class names.
const detail = campDetail.slice(campDetail.indexOf("view === 'detail' && detail.ready"), campDetail.indexOf("view === 'register'}}"));
for (const binding of ['activity.name', 'activity.sessionNumber', 'detail.date', 'detail.place', 'activity.fee', 'activity.returningFee', 'activity.capacity', 'detail.changeRule', 'detail.scheduleDays']) assert.ok(detail.includes(binding), '详情缺少真实字段 ' + binding);
assert.ok(detail.includes('bindtap="startRegistration"'));
assert.ok(detail.includes('disabled="{{!availability.canRegister}}"'));
assert.ok(detail.includes('bindtap="openJourney"'));
assert.ok(!detail.includes('toggleDetailSchedule') && !detail.includes('activity.theme'), '详情不再显示主题和日期展开入口');

const emptyStatePages = ['camp', 'content', 'friends', 'camp-flow', 'friend-flow', 'mine-flow']
for (const page of emptyStatePages) {
  const wxml = fs.readFileSync(path.join(root, 'pages', page, 'index.wxml'), 'utf8')
  assert.ok(wxml.includes('empty-state'), `${page} 缺少缺省页样式`)
}

const articlePage = fs.readFileSync(path.join(root, 'pages', 'article', 'index.js'), 'utf8')
assert.ok(articlePage.includes("article.title === '未命名文章'"), '旧文章详情未使用列表标题兜底')
for (const page of ['content', 'mine-flow']) {
  const js = fs.readFileSync(path.join(root, 'pages', page, 'index.js'), 'utf8')
  assert.ok(js.includes('&title='), `${page} 打开文章时未传递旧接口缺失的标题`)
}

console.log('ui-refactor.test.js OK')
