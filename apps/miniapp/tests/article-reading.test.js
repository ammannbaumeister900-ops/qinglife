const { test } = require('node:test')
const assert = require('node:assert/strict')
const vm = require('node:vm'), fs = require('node:fs'), path = require('node:path')
const root = path.resolve(__dirname, '..')
function world(options = {}) {
  const calls = [], paused = [], cache = new Map()
  const runtime = { environment: 'staging', businessBaseUrl: 'https://api.invalid/test', legacyEnabled: false, legacyBaseUrl: 'https://legacy.invalid', ...options.runtime }
  const wx = { getStorageSync: () => '', createVideoContext: id => ({ pause: () => paused.push(id) }), showToast() {},
    login() { throw new Error('Public reading must not request login') },
    request(o) {
      calls.push({ url: o.url, header: o.header, method: o.method })
      const id = decodeURIComponent(o.url.split('/').pop())
      if (id === '404') return o.success({ statusCode: 404, data: { code: 404, msg: '轻读不存在或未发布' } })
      o.success({ statusCode: 200, data: { code: 200, data: options.article || { id, title: '公开原文', content: '<p>内容</p>', cover: '' } } })
    } }
  function load(f, capture) {
    f = path.resolve(f); if (!path.extname(f)) f += '.js'
    if (f.endsWith(path.normalize('config/runtime.js'))) return runtime
    if (f.endsWith(path.normalize('services/article-favorites.js'))) return { isCollected: async () => false }
    if (f.endsWith(path.normalize('services/legacy-api.js'))) return { getArticle: async id => { if (!runtime.legacyEnabled || runtime.environment === 'staging') return { source: 'legacy', error: '历史内容服务未启用' }; calls.push({ legacy: id }); return { item: { id, title: '历史原文', content: '<img src="/profile/public/old.jpg">' }, source: 'legacy' } } }
    if (cache.has(f)) return cache.get(f).exports
    const module = { exports: {} }; cache.set(f, module)
    const requireLocal = name => name.startsWith('.') ? load(path.resolve(path.dirname(f), name)) : require(name)
    vm.runInNewContext('(function(require,module,exports){' + fs.readFileSync(f, 'utf8') + '\n})', { wx, Page: capture, console, setTimeout, clearTimeout })(requireLocal, module, module.exports)
    return module.exports
  }
  let page; load(path.join(root, 'pages/article/index.js'), p => { page = p })
  page.data = JSON.parse(JSON.stringify(page.data))
  page.setData = patch => Object.entries(patch).forEach(([key, value]) => {
    const parts = key.replace(/\[(\d+)\]/g, '.$1').split('.'); let target = page.data
    parts.slice(0, -1).forEach(p => { target = target[p] }); target[parts[parts.length - 1]] = value
  })
  return { page, calls, paused, runtime }
}
test('source-aware share cold entry and old source-less links read business anonymously without login', async () => {
  const w = world(); await w.page.onLoad({ id: '9007199254741000', title: encodeURIComponent('原题 & 新题') })
  assert.equal(w.page.data.source, 'business'); assert.equal(w.calls.length, 1)
  assert.equal(w.calls[0].url, 'https://api.invalid/test/app/qinglife/readings/9007199254741000')
  assert.equal(w.calls[0].header.token, undefined)
  const share = w.page.onShareAppMessage(); assert.match(share.path, /source=business/)
  const cold = world(); await cold.page.onLoad(Object.fromEntries(new URLSearchParams(share.path.split('?')[1])))
  assert.equal(cold.page.data.article.id, '9007199254741000'); assert.equal(cold.page.data.error, '')
  assert.equal(cold.page.data.presentation.cover, '')
})
test('explicit business source takes precedence over an identically named demo sample', async () => {
  const w = world({ runtime: { environment: 'demo' } }); await w.page.onLoad({ id: 'sample-intro', source: 'business' })
  assert.equal(w.page.data.source, 'business'); assert.equal(w.calls.length, 1)
})
test('explicit legacy reads only configured legacy and resolves its relative media at its own base', async () => {
  const w = world({ runtime: { environment: 'production', legacyEnabled: true } })
  await w.page.onLoad({ id: '12', source: 'legacy' }); assert.deepEqual(w.calls, [{ legacy: '12' }])
  assert.equal(w.page.data.source, 'legacy')
  assert.match(JSON.stringify(w.page.data.presentation), /https:\/\/legacy.invalid\/profile\/public\/old.jpg/)
  assert.match(w.page.onShareAppMessage().path, /source=legacy/)
})
test('invalid source, disabled legacy and production demo never cause a fallback read', async () => {
  for (const input of [{ id: '1', source: 'unknown' }, { id: '1', source: 'legacy' }, { id: 'sample-intro', source: 'demo' }]) {
    const w = world(); await w.page.onLoad(input); assert.ok(w.page.data.error); assert.equal(w.calls.length, 0)
    assert.equal(w.page.onShareAppMessage().path, '/pages/content/index')
  }
})
test('disabled article and wrong-ID responses cannot display stale content or try another source', async () => {
  const w = world(); await w.page.onLoad({ id: '1' }); await w.page.onLoad({ id: '404', source: 'business' })
  assert.ok(w.page.data.error); assert.equal(w.page.data.article.id, undefined); assert.equal(w.calls.length, 2)
  const wrong = world({ article: { id: '2', content: '错文' } }); await wrong.page.onLoad({ id: '1' })
  assert.ok(wrong.page.data.error); assert.equal(wrong.page.data.article.id, undefined)
})
test('missing IDs and malformed escapes never crash or invoke login', async () => {
  const w = world(); await w.page.onLoad({}); assert.ok(w.page.data.error); assert.equal(w.calls.length, 0)
  await w.page.onLoad({ id: '12', title: '100%真实故事' }); assert.equal(w.page.data.error, '')
  await w.page.onLoad({ id: '%ZZ' }); assert.equal(w.page.data.id, '%ZZ'); assert.match(w.calls[1].url, /%25ZZ$/)
})
test('video errors can retry, playing pauses other media, hide/unload pauses all and late reads do not mutate a detached page', async () => {
  const w = world({ article: { id: '1', content: '<video src="https://media.invalid/a.mp4"></video><p>原文</p><video src="https://media.invalid/b.mp4"></video>' } })
  await w.page.onLoad({ id: '1' }); const videos = w.page.data.presentation.blocks.filter(b => b.type === 'video')
  w.page.onVideoPlay({ currentTarget: { id: videos[0].id } }); assert.deepEqual(w.paused, [videos[1].id])
  w.page.onVideoError({ currentTarget: { dataset: { index: 0 } } }); assert.equal(videos[0].failed, true)
  w.page.retryVideo({ currentTarget: { dataset: { index: 0 } } }); assert.equal(videos[0].failed, false)
  w.page.onHide(); w.page.onUnload(); assert.equal(w.paused.length, 5)
  const late = world(); const pending = late.page.onLoad({ id: '1' }); late.page.onUnload(); await pending
  assert.equal(late.page.data.article.id, undefined)
})
