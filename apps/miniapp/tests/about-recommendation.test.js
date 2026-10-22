const { test } = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs'), path = require('node:path'), vm = require('node:vm')
function fixture(api = {}) {
  let page; const calls = []
  const business = { enabled: () => true, homeReadings: async () => ({ stories: [] }), ...api }
  vm.runInNewContext(fs.readFileSync(path.join(__dirname, '../pages/about/index.js'), 'utf8'), {
    Page: value => { page = value }, wx: {}, require: id => id.includes('services/business-api') ? business
      : id.includes('utils/navigation') ? { navigateTo: value => calls.push(value) }
        : require(path.resolve(__dirname, '../pages/about', id))
  })
  page.data = JSON.parse(JSON.stringify(page.data)); page.setData = patch => Object.assign(page.data, patch)
  return { page, calls }
}
test('about uses only recommended stories from the public endpoint and preserves exact article identity', async () => {
  const { page, calls } = fixture({ homeReadings: async () => ({
    stories: [{ id: '9007199254741001', title: '测试故事', isStory: true }, { id: '2', title: '普通内容', isStory: false },
      { id: '../3', title: '无效编号', isStory: true }, { id: '4', title: '无明确分类' }, { id: '5', title: '', isStory: true }],
    featured: [{ id: '6', title: '首页精选', isStory: false }]
  }) })
  await page.loadRecommendedStories()
  assert.deepEqual(JSON.parse(JSON.stringify(page.data.recommendedStories)), [{ id: '9007199254741001', title: '测试故事' }])
  page.openRecommendedStory({ currentTarget: { dataset: { id: '9007199254741001' } } })
  assert.equal(calls[0].url, '/pages/article/index?id=9007199254741001&source=business')
  page.openRecommendedStory({ currentTarget: { dataset: { id: '6' } } })
  assert.equal(calls.length, 1)
  assert.equal(page.data.story.name, '花花一家', 'recommended articles do not overwrite the separately approved portrait/story')
  assert.equal(page.data.story.articleUrl, '', 'never infer the portrait full-story target from titles or labels')
})
test('empty/failed/demo requests clear stale recommendations, and retry recovers without samples', async () => {
  let response = { stories: [] }
  const { page } = fixture({ homeReadings: async () => { if (response instanceof Error) throw response; return response } })
  page.data.recommendedStories = [{ id: 'old' }]; await page.loadRecommendedStories()
  assert.equal(page.data.recommendedStories.length, 0); assert.equal(page.data.storyError, '')
  response = Error('offline'); await page.loadRecommendedStories()
  assert.equal(page.data.recommendedStories.length, 0); assert(page.data.storyError)
  response = { stories: [{ id: '7', title: '测试恢复', isStory: true }] }; await page.loadRecommendedStories()
  assert.equal(page.data.storyError, ''); assert.equal(page.data.recommendedStories[0].id, '7')
  const demo = fixture({ enabled: () => false, homeReadings: () => { throw Error('must not call') } }).page
  demo.data.recommendedStories = [{ id: 'old' }]; await demo.loadRecommendedStories()
  assert.equal(demo.data.recommendedStories.length, 0); assert.equal(demo.data.storyError, '')
})
test('stale requests after re-entry or leaving the page cannot restore old recommendations', async () => {
  let finish, count = 0
  const { page } = fixture({ homeReadings: () => ++count === 1 ? new Promise(resolve => { finish = resolve }) : Promise.resolve({ stories: [] }) })
  const pending = page.loadRecommendedStories(); await page.loadRecommendedStories()
  finish({ stories: [{ id: '8', title: '旧请求', isStory: true }] }); await pending
  assert.equal(page.data.recommendedStories.length, 0)
  const leaving = fixture({ homeReadings: () => new Promise(resolve => { finish = resolve }) }).page
  const beforeLeave = leaving.loadRecommendedStories(); leaving.onHide(); finish({ stories: [{ id: '9', title: '已离开', isStory: true }] }); await beforeLeave
  assert.equal(leaving.data.recommendedStories.length, 0)
})
