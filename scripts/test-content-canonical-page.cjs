// Read the canonical page without copying/altering its visual work. Apply only the 3-line hook in memory.
const fs = require('node:fs'), path = require('node:path'), vm = require('node:vm'), assert = require('node:assert/strict'), crypto = require('node:crypto')
const { withStoryContent } = require('../apps/miniapp/utils/story-content')
async function main() {
  const root = path.resolve(process.argv[2] || '../qinglife/apps/miniapp'), file = path.join(root, 'pages/about/index.js')
  const original = fs.readFileSync(file, 'utf8'), digest = text => crypto.createHash('sha256').update(text).digest('hex')
  let source = original
  if (!source.includes('withStoryContent')) source = "const { withStoryContent } = require('../../utils/story-content')\n" + source.replace('Page({', 'Page(withStoryContent({').replace(/\}\)\s*$/, '}))')
  let page, calls = 0
  const approved = { state: 'releaseReady', authorization: 'confirmed', editorialReview: 'approved', factCheck: 'confirmed', identityCheck: 'confirmed', source: { type: 'synthetic-test' }, publicVerified: true, evidenceRef: 'synthetic-only', businessArticleId: '71', title: 'SYNTHETIC full text' }
  const api = { enabled: () => true, sessions: async () => [], articlePlacement: async key => { calls++; return { contractVersion: 1, placementKey: key, item: { ...approved, personKey: key.slice(6) } } } }
  vm.runInNewContext(source, { Page: config => { page = config }, wx: { getSystemInfoSync: () => ({ fontSizeSetting: 16 }), showShareMenu() {} }, require: id => id.endsWith('story-content') ? { withStoryContent: config => withStoryContent(config, api) } : id.endsWith('business-api') ? api : require(path.resolve(root, 'pages/about', id)) })
  page.data = JSON.parse(JSON.stringify(page.data)); page.setData = patch => Object.assign(page.data, JSON.parse(JSON.stringify(patch)))
  page.onLoad(); const before = page.data.story && { name: page.data.story.name, intro: page.data.story.intro, personKey: page.data.story.personKey }
  page.onShow(); await new Promise(resolve => setImmediate(resolve))
  if (before) { assert.match(page.data.story.articleUrl, /id=71&source=business/); for (const key of Object.keys(before)) assert.equal(page.data.story[key], before[key]); assert.equal(calls, 1) }
  assert.equal(digest(fs.readFileSync(file, 'utf8')), digest(original), 'Canonical file must remain untouched')
  console.log(JSON.stringify({ canonicalPageUnchanged: true, visualCopyUsedAsImplementation: false, existingLifecyclePreserved: true, approvedStoryPresent: !!before, syntheticLinkResolved: !!(page.data.story && page.data.story.articleUrl), physicalDevice: false }))
}
main().catch(error => { console.error(error); process.exitCode = 1 })
