const store = require('../utils/store')
const legacyApi = require('./legacy-api')
const business = require('./business-api')
const runtime = require('../config/runtime')
const samples = require('../data/reading-samples')
const demo = require('../data/demo')

function articleId(value) {
  return typeof value === 'string' || typeof value === 'number' ? String(value) : ''
}

function ids(state) {
  return [...new Set((Array.isArray(state.localFavorites) ? state.localFavorites : []).map(articleId).filter(Boolean))]
}

function snapshot(article, source) {
  return { id: articleId(article.id), title: article.title || '未命名文章', summary: article.summary || '', category: article.category || '轻生活', author: article.author || '', date: article.date || '', source }
}

function localCollected(id, source, state = store.getState()) {
  const saved = Object.values(state.localFavoriteArticles || {}).filter(item => item.id === id)
  return ids(state).includes(id) && (!saved.length || saved.some(item => item.source === source))
}

async function isCollected(id, source) {
  id = articleId(id)
  if (!id) return false
  if (source === 'legacy') {
    const result = await legacyApi.getCollections()
    if (!result.error && result.list.some(item => articleId(item.id) === id)) return true
  }
  return localCollected(id, source)
}

async function setCollected(article, source, collected) {
  const id = articleId(article.id)
  if (!id) throw new Error('文章暂时无法收藏')
  // Legacy remains server-backed: a rejected write must never become local success.
  if (source === 'legacy') await legacyApi.toggleCollection(id)
  return store.updateState(state => {
    const savedIds = ids(state)
    const articles = state.localFavoriteArticles || {}
    const key = source + ':' + id
    if (collected) {
      if (!savedIds.includes(id)) savedIds.push(id)
      articles[key] = snapshot(article, source)
    } else {
      delete articles[key]
    }
    const retained = collected || Object.values(articles).some(item => item.id === id)
    state.localFavorites = retained ? savedIds : savedIds.filter(item => item !== id)
    state.localFavoriteArticles = articles
    return state
  })
}

async function restoreLocal(id) {
  const article = samples.concat(demo.articles).find(item => articleId(item.id) === id)
  if (article) return snapshot(article, 'demo')
  if (business.enabled()) {
    const result = await business.reading(id)
    if (!result || !result.id) throw new Error('文章暂时无法读取')
    return snapshot(result, 'business')
  }
  const result = await legacyApi.getArticle(id)
  if (!result.item.id) throw new Error(result.error || '文章暂时无法读取')
  return snapshot(result.item, result.source)
}

async function getCollections() {
  const remote = runtime.legacyEnabled ? await legacyApi.getCollections() : { list: [], source: 'local' }
  const state = store.getState()
  let localError = false
  const local = await Promise.all(ids(state).map(async id => {
    const saved = Object.values(state.localFavoriteArticles || {}).filter(item => item.id === id)
    if (saved.length) {
      // Keep older locally known legacy rows: the existing endpoint reads page one.
      return saved.filter(item => item.source !== 'legacy' || !remote.list.some(row => articleId(row.id) === id))
    }
    try {
      // Old ID-only legacy favorites can already be resolved by the server list.
      if (remote.list.some(item => articleId(item.id) === id) && !business.enabled()) return []
      return [await restoreLocal(id)]
    } catch (error) {
      if (remote.list.some(item => articleId(item.id) === id)) return []
      localError = true
      return [{ id, title: '暂时无法读取的收藏文章', summary: '', category: '轻生活', source: '' }]
    }
  }))
  const seen = new Set()
  const list = [].concat(...local).filter(item => localCollected(item.id, item.source)).concat(remote.list.map(item => snapshot(item, 'legacy'))).filter(item => {
    const key = item.source + ':' + item.id
    if (!item.id || seen.has(key)) return false
    seen.add(key)
    item.favoriteKey = key
    return true
  })
  return { list, source: remote.source, error: remote.error || (localError ? '部分收藏暂时无法读取' : '') }
}

module.exports = { isCollected, setCollected, getCollections }
