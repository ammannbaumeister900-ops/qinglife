const { test } = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const vm = require('node:vm')
const root = path.resolve(__dirname, '..')
const plain = value => JSON.parse(JSON.stringify(value))
const event = row => ({ currentTarget: { dataset: row } })

function world(options = {}) {
  const storage = options.storage || new Map()
  const requests = [], toasts = [], urls = [], businessReads = []
  const serverFavorites = new Set((options.serverFavorites || []).map(String))
  const legacyArticles = options.legacyArticles || {}
  const businessArticles = options.businessArticles || {}
  const cache = new Map()
  let writeFailure = false, legacyFailure = false, requestOverride = null, businessReadingOverride = null
  const runtime = { environment: 'demo', legacyEnabled: !!options.legacyEnabled, legacyWritesEnabled: !!options.legacyWritesEnabled, legacyBaseUrl: 'https://legacy.example.test' }
  if (runtime.legacyEnabled) storage.set('qinglife_legacy_token', 'synthetic-token')
  const wx = {
    getStorageSync: key => storage.has(key) ? plain(storage.get(key)) : '',
    setStorageSync: (key, value) => { if (writeFailure) throw new Error('storage full'); storage.set(key, plain(value)) },
    showToast: value => toasts.push(value.title), hideLoading() {},
    navigateTo: value => urls.push(value.url),
    request(value) {
      requests.push({ path: new URL(value.url).pathname, method: value.method, data: plain(value.data) })
      if (requestOverride) return requestOverride(value)
      if (legacyFailure) return value.fail(new Error('offline'))
      const requestPath = new URL(value.url).pathname
      if (requestPath === '/collect' && value.method === 'POST') {
        const id = String(value.data.essay)
        if (serverFavorites.has(id)) serverFavorites.delete(id)
        else serverFavorites.add(id)
        return value.success({ statusCode: 200, data: { code: 200, data: {} } })
      }
      if (requestPath === '/collect') return value.success({ statusCode: 200, data: { code: 200, data: { list: [...serverFavorites].map(id => legacyArticles[id]) } } })
      if (requestPath === '/essayDetail') return value.success({ statusCode: 200, data: { code: 200, data: legacyArticles[value.data.id] || {} } })
      throw new Error('unexpected request: ' + requestPath)
    }
  }
  const business = {
    isDemo: () => runtime.environment === 'demo',
    enabled: () => !!options.businessEnabled,
    reading: async id => {
      businessReads.push(id)
      if (businessReadingOverride) return businessReadingOverride(id)
      if (!businessArticles[id]) throw new Error('reading unavailable')
      return plain(businessArticles[id])
    }
  }
  function load(filename, capture) {
    filename = path.resolve(filename)
    if (!path.extname(filename)) filename += '.js'
    if (!capture && cache.has(filename)) return cache.get(filename).exports
    if (filename === path.join(root, 'config/runtime.js')) return runtime
    if (filename === path.join(root, 'services/business-api.js')) return business
    const module = { exports: {} }
    if (!capture) cache.set(filename, module)
    const requireLocal = name => name.startsWith('.') ? load(path.resolve(path.dirname(filename), name)) : require(name)
    vm.runInNewContext('(function(require,module,exports){' + fs.readFileSync(filename, 'utf8') + '\n})', { wx, Page: capture, console, setTimeout, clearTimeout }, { filename })(requireLocal, module, module.exports)
    return module.exports
  }
  const store = load(path.join(root, 'utils/store.js'))
  if (!storage.has(store.STORAGE_KEY)) {
    store.resetState()
    store.updateState(state => { state.localFavorites = options.localFavorites || []; return state })
  }
  function page(name) {
    let result
    load(path.join(root, 'pages', name, 'index.js'), value => { result = value })
    result.data = plain(result.data)
    result.setData = value => Object.assign(result.data, value)
    return result
  }
  async function article(id, source) {
    const result = page('article')
    await result.onLoad({ id: encodeURIComponent(id), source })
    return result
  }
  async function collections() {
    const result = page('mine-flow')
    result.onLoad({ view: 'collections' })
    await result.onShow()
    return result
  }
  async function reopen(list, row) {
    list.openArticle(event(row))
    const result = page('article')
    await result.onLoad(Object.fromEntries(new URLSearchParams(urls[urls.length - 1].split('?')[1])))
    return result
  }
  return { store, storage, requests, toasts, urls, businessReads, serverFavorites, page, article, collections, reopen,
    favorites: load(path.join(root, 'services/article-favorites.js')),
    failStorage: value => { writeFailure = value }, failLegacy: value => { legacyFailure = value }, overrideRequest: value => { requestOverride = value }, overrideBusinessReading: value => { businessReadingOverride = value } }
}

const reading = { id: 'reading & 1', title: '给日常留一点空间 & 余地', category: '日常练习', summary: '慢一点读', content: '<p>正文</p>' }

test('business collect -> persisted list -> reopen -> cancel -> return and reopen', async () => {
  const w = world({ businessEnabled: true, businessArticles: { [reading.id]: reading } })
  const detail = await w.article(reading.id)
  assert.equal(detail.data.collected, false)
  await detail.toggleCollect()
  assert.equal(detail.data.collected, true)
  const list = await w.collections()
  assert.equal(list.data.collectionsError, '')
  assert.equal(list.data.collections.length, 1)
  assert.equal(list.data.collections[0].title, reading.title)
  const revisit = await w.reopen(list, list.data.collections[0])
  assert.equal(revisit.data.article.id, reading.id)
  assert.equal(revisit.data.collected, true)
  assert.match(w.urls[0], /source=business/)
  await revisit.toggleCollect()
  await list.onShow()
  assert.equal(list.data.collections.length, 0, 'return must refresh an already populated list')
  assert.equal((await w.article(reading.id)).data.collected, false)
  assert.equal(w.requests.length, 0, 'ordinary favorites do not call legacy service')
})

test('local favorites survive a new app/page session and list without article fetches', async () => {
  const w = world({ businessEnabled: true, businessArticles: { [reading.id]: reading } })
  await (await w.article(reading.id)).toggleCollect()
  const restarted = world({ storage: w.storage, businessEnabled: true })
  const list = await restarted.collections()
  assert.equal(list.data.collections[0].title, reading.title)
  assert.equal(restarted.businessReads.length, 0)
  assert.equal(restarted.requests.length, 0)
})

test('repeated saves and rapid page taps do not duplicate or immediately undo a favorite', async () => {
  const w = world({ businessEnabled: true, businessArticles: { [reading.id]: reading } })
  await w.favorites.setCollected(reading, 'business', true)
  await w.favorites.setCollected(reading, 'business', true)
  assert.deepEqual(plain(w.store.getState().localFavorites), [reading.id])
  assert.equal((await w.collections()).data.collections.length, 1)
  await w.favorites.setCollected(reading, 'business', false)
  const detail = await w.article(reading.id)
  await Promise.all([detail.toggleCollect(), detail.toggleCollect()])
  assert.equal(detail.data.collected, true)
  assert.equal((await w.collections()).data.collections.length, 1)
})

test('old sample/demo ID-only favorites resolve and reopen even with business enabled', async () => {
  const w = world({ businessEnabled: true, localFavorites: ['sample-intro', 'demo-article-2', 'sample-intro'] })
  const list = await w.collections()
  assert.equal(list.data.collections.length, 2)
  assert.equal(w.businessReads.length, 0)
  const detail = await w.reopen(list, list.data.collections.find(item => item.id === 'demo-article-2'))
  assert.equal(detail.data.source, 'demo')
  assert.equal(detail.data.article.id, 'demo-article-2')
  await detail.toggleCollect()
  await list.onShow()
  assert.deepEqual(list.data.collections.map(item => String(item.id)).join(','), 'sample-intro')
})

test('old numeric/string business IDs are deduplicated and recovered through the existing API', async () => {
  const w = world({ businessEnabled: true, localFavorites: [42, '42'], businessArticles: { 42: { ...reading, id: '42' } } })
  const list = await w.collections()
  assert.equal(list.data.collections.length, 1)
  assert.deepEqual(w.businessReads, ['42'])
  const detail = await w.reopen(list, list.data.collections[0])
  assert.equal(detail.data.collected, true)
  await detail.toggleCollect()
  assert.deepEqual(plain(w.store.getState().localFavorites), [])
})

test('unresolved historical IDs remain stored and retryable instead of disappearing', async () => {
  const w = world({ businessEnabled: true, localFavorites: ['missing'] })
  const list = await w.collections()
  assert.equal(list.data.collections[0].id, 'missing')
  assert.ok(list.data.collectionsError)
  assert.deepEqual(plain(w.store.getState().localFavorites), ['missing'])
})

test('legacy list/route/status/toggle stay server-backed when business is enabled', async () => {
  const w = world({ businessEnabled: true, legacyEnabled: true, legacyWritesEnabled: true, serverFavorites: ['81'], legacyArticles: { 81: { id: 81, title: '历史文章', content: '<p>历史正文</p>' } } })
  const list = await w.collections()
  assert.equal(list.data.collections.length, 1)
  assert.equal(list.data.collections[0].source, 'legacy')
  const detail = await w.reopen(list, list.data.collections[0])
  assert.equal(detail.data.article.title, '历史文章')
  assert.equal(detail.data.collected, true, 'remote favorite need not have a local marker')
  assert.equal(w.businessReads.length, 0)
  await detail.toggleCollect()
  assert.equal(detail.data.collected, false)
  assert.equal(w.serverFavorites.has('81'), false)
  await list.onShow()
  assert.equal(list.data.collections.length, 0)
  await Promise.all([detail.toggleCollect(), detail.toggleCollect()])
  assert.equal(w.serverFavorites.has('81'), true)
  await list.onShow()
  assert.equal(list.data.collections.length, 1)
  const posts = w.requests.filter(item => item.method === 'POST')
  assert.deepEqual(posts, [{ path: '/collect', method: 'POST', data: { essay: '81' } }, { path: '/collect', method: 'POST', data: { essay: '81' } }])
  assert.ok(w.requests.filter(item => item.path === '/collect' && item.method === 'GET').every(item => item.data.pageNum === 1))
})

test('legacy rejected writes and disabled writes never become successful local favorites', async () => {
  for (const writes of [true, false]) {
    const w = world({ legacyEnabled: true, legacyWritesEnabled: writes, legacyArticles: { 81: { id: 81, title: '历史文章' } } })
    const detail = await w.article('81', 'legacy')
    if (writes) w.failLegacy(true)
    await detail.toggleCollect()
    assert.equal(detail.data.collected, false)
    assert.deepEqual(plain(w.store.getState().localFavorites), [])
    assert.deepEqual(w.toasts, ['收藏暂未保存，请重试'])
    if (!writes) assert.equal(w.requests.filter(item => item.method === 'POST').length, 0)
  }
})

test('legacy read failure does not hide saved ordinary articles', async () => {
  const w = world({ businessEnabled: true, legacyEnabled: true })
  await w.favorites.setCollected(reading, 'business', true)
  w.failLegacy(true)
  const list = await w.collections()
  assert.equal(list.data.collections[0].id, reading.id)
  assert.ok(list.data.collectionsError)
})

test('same ID in business and legacy remains distinct; legacy cancel retains business favorite', async () => {
  const w = world({ businessEnabled: true, legacyEnabled: true, legacyWritesEnabled: true, serverFavorites: ['81'], legacyArticles: { 81: { id: 81, title: '历史文章' } }, businessArticles: { 81: { ...reading, id: '81' } } })
  await (await w.article('81', 'business')).toggleCollect()
  const list = await w.collections()
  assert.equal(list.data.collections.length, 2)
  assert.equal(new Set(list.data.collections.map(item => item.favoriteKey)).size, 2)
  const old = await w.reopen(list, list.data.collections.find(item => item.source === 'legacy'))
  await old.toggleCollect()
  await list.onShow()
  assert.equal(list.data.collections.length, 1)
  assert.equal(list.data.collections[0].source, 'business')
  assert.equal((await w.reopen(list, list.data.collections[0])).data.collected, true)
})

test('storage failure is reported instead of showing a false collect success', async () => {
  const w = world({ businessEnabled: true, businessArticles: { [reading.id]: reading } })
  const detail = await w.article(reading.id)
  w.failStorage(true)
  await detail.toggleCollect()
  assert.equal(detail.data.collected, false)
  assert.deepEqual(w.toasts, ['收藏暂未保存，请重试'])
  assert.deepEqual(plain(w.store.getState().localFavorites), [])
})

test('late list response cannot resurrect a canceled collection after returning', async () => {
  const w = world({ businessEnabled: true })
  const list = w.page('mine-flow')
  list.onLoad({ view: 'collections' })
  let resolve
  const stale = { list: [{ ...reading, source: 'business', favoriteKey: 'business:' + reading.id }], source: 'local' }
  w.favorites.getCollections = () => new Promise(done => { resolve = done })
  const first = list.onShow()
  w.favorites.getCollections = async () => ({ list: [], source: 'local' })
  await list.onShow()
  resolve(stale)
  await first
  assert.equal(list.data.collections.length, 0)
  assert.equal(list.data.collectionsLoading, false)
})

test('legacy favorites outside the first server page retain their existing local status and snapshot', async () => {
  const w = world({ legacyEnabled: true, legacyWritesEnabled: true, legacyArticles: { 81: { id: 81, title: '较早的历史收藏' } } })
  const detail = await w.article('81', 'legacy')
  await detail.toggleCollect()
  w.overrideRequest(value => value.success({ statusCode: 200, data: { code: 200, data: { list: [] } } }))
  assert.equal(await w.favorites.isCollected('81', 'legacy'), true)
  const list = await w.collections()
  assert.equal(list.data.collections[0].title, '较早的历史收藏')
  w.overrideRequest(null)
  await detail.toggleCollect()
  await list.onShow()
  assert.equal(list.data.collections.length, 0)
})

test('cancel during old-ID recovery is not overwritten by the finishing list read', async () => {
  const w = world({ businessEnabled: true, localFavorites: [reading.id] })
  let resolve
  w.overrideBusinessReading(() => new Promise(done => { resolve = done }))
  const pending = w.favorites.getCollections()
  assert.equal(typeof resolve, 'function')
  await w.favorites.setCollected(reading, 'business', false)
  resolve(reading)
  const result = await pending
  assert.equal(result.list.length, 0)
  assert.deepEqual(plain(w.store.getState().localFavorites), [])
})