const { test } = require('node:test')
const assert = require('node:assert/strict')
const { withStoryContent } = require('../utils/story-content')
const approved = { state: 'releaseReady', authorization: 'confirmed', editorialReview: 'approved', factCheck: 'confirmed', identityCheck: 'confirmed', source: { type: 'synthetic-test' } }
const link = personKey => ({ contractVersion: 1, placementKey: 'about.' + personKey, item: { ...approved, personKey, businessArticleId: '71', publicVerified: true, evidenceRef: 'synthetic-only', title: '合成全文' } })
function page(api, personKey = 'huahua-family') {
  const p = withStoryContent({ data: { story: { ...approved, personKey, name: '合成测试人物', intro: '不得替换的编排', articleUrl: 'old' } }, onShow() { this.originalShows = (this.originalShows || 0) + 1 } }, api)
  p.setData = patch => Object.assign(p.data, patch)
  return p
}
test('data hook preserves story identity, copy and existing lifecycle; reviewed link uses target business ID', async () => {
  const calls = [], p = page({ enabled: () => true, articlePlacement: async key => { calls.push(key); return link('huahua-family') } })
  p.onShow(); await new Promise(resolve => setImmediate(resolve))
  assert.equal(p.originalShows, 1); assert.equal(p.data.story.intro, '不得替换的编排'); assert.match(p.data.story.articleUrl, /id=71&source=business/); assert.deepEqual(calls, ['about.huahua-family'])
})
test('mismatched people, withdrawn links and failed API never fall back to old article or sample', async () => {
  for (const reply of [link('leilei'), { contractVersion: 1, placementKey: 'about.huahua-family', item: null }, null]) {
    const p = page({ enabled: () => true, articlePlacement: async () => reply }); await p.loadStoryContent(); assert.equal(p.data.story.articleUrl, ''); assert.equal(p.data.story.personKey, 'huahua-family')
  }
  const p = page({ enabled: () => true, articlePlacement: async () => { throw new Error('offline') } }); await p.loadStoryContent(); assert.equal(p.data.story.articleUrl, '')
})
test('late response after hide cannot restore a stale link; showing again refreshes', async () => {
  let resolve; const p = page({ enabled: () => true, articlePlacement: () => new Promise(r => { resolve = r }) })
  const pending = p.loadStoryContent(); p.onHide(); resolve(link('huahua-family')); await pending; assert.equal(p.data.story.articleUrl, '')
})
test('missing approved story and unknown person never load or substitute another person', async () => {
  let calls = 0; const api = { enabled: () => true, articlePlacement: async () => { calls++; return link('huahua-family') } }
  const p = page(api, 'unknown'); await p.loadStoryContent(); p.data.story = null; await p.loadStoryContent(); assert.equal(calls, 0)
})
