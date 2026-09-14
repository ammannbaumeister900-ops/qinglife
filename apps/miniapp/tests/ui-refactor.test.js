const assert = require('assert')
const fs = require('fs')
const path = require('path')

const root = path.resolve(__dirname, '..')
const appStyles = fs.readFileSync(path.join(root, 'app.wxss'), 'utf8')
const demo = require(path.join(root, 'data/demo'))

for (const token of ['#F6F1E8', '#FFFCF7', '#26231F', '#A85B3A', '#7C8F78', '#A87C3E']) {
  assert.ok(appStyles.includes(token), `V1.1 Token ${token} 缺失`)
}

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
for (const marker of ['camp-cover', 'fact-card', 'rhythm-list', 'fit-grid', 'rules-card', 'detail-actionbar']) {
  assert.ok(campDetail.includes(marker), `轻体营详情缺少 ${marker}`)
}

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
