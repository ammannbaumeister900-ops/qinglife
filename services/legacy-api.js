const demo = require('../data/demo')

const BASE_URL = 'https://wxapi.qinglife.top/servers'
const TOKEN_KEY = 'qinglife_legacy_token'

function request(path, method, data, useToken = true) {
  return new Promise((resolve, reject) => {
    const header = { 'content-type': 'application/json' }
    if (useToken) header.token = wx.getStorageSync(TOKEN_KEY) || wx.getStorageSync('token') || ''
    wx.request({
      url: `${BASE_URL}${path}`,
      method,
      data: data || {},
      header,
      timeout: 8000,
      success(res) {
        if (res.statusCode === 200 && res.data && res.data.code === 200) resolve(res.data.data)
        else reject(new Error((res.data && res.data.message) || '现有服务暂不可用'))
      },
      fail: reject
    })
  })
}

function ensureToken() {
  const token = wx.getStorageSync(TOKEN_KEY) || wx.getStorageSync('token')
  if (token) return Promise.resolve(token)
  return new Promise((resolve, reject) => {
    wx.login({
      success(loginResult) {
        request('/token', 'POST', { code: loginResult.code }, false)
          .then((data) => {
            const nextToken = typeof data === 'string' ? data : data && data.token
            if (!nextToken) return reject(new Error('现有登录服务未返回 token'))
            wx.setStorageSync(TOKEN_KEY, nextToken)
            resolve(nextToken)
          })
          .catch(reject)
      },
      fail: reject
    })
  })
}

function normalizeArticle(item) {
  return {
    id: String(item.id),
    title: item.title || item.name || '未命名文章',
    summary: item.summary || item.intro || item.description || item.brief || '',
    author: item.author || '轻生活',
    category: item.labelName || item.category || '轻生活',
    date: item.insertTime || item.publishTime || '',
    cover: item.head || item.cover || '',
    content: item.content || ''
  }
}

function normalizeLegacyPost(item) {
  return {
    id: String(item.id),
    author: item.nickName || item.nickname || item.insertUser || '轻友',
    time: item.insertTime || item.createTime || '',
    tag: item.tagName || item.tag || '历史动态',
    content: item.content || item.publishContent || item.text || '历史动态'
  }
}

async function getArticles(pageNum = 1, labelId = '') {
  try {
    await ensureToken()
    const data = await request('/essayNew', 'GET', { pageNum, labelId })
    const page = data && data.essayList
    const list = page && Array.isArray(page.list) ? page.list.map(normalizeArticle) : []
    if (!list.length) throw new Error('现有文章为空')
    return { list, source: 'legacy', hasMore: !!page.isNext }
  } catch (error) {
    return { list: demo.articles, source: 'demo', hasMore: false, error: error.message }
  }
}

async function getArticle(id) {
  if (String(id).startsWith('demo-')) {
    return { item: demo.articles.find((article) => article.id === id) || demo.articles[0], source: 'demo' }
  }
  try {
    await ensureToken()
    const data = await request('/essayDetail', 'GET', { id })
    return { item: normalizeArticle({ id, ...data }), source: 'legacy' }
  } catch (error) {
    return { item: demo.articles[0], source: 'demo', error: error.message }
  }
}

async function getCollections() {
  try {
    await ensureToken()
    const data = await request('/collect', 'GET', { pageNum: 1 })
    const list = data && Array.isArray(data.list) ? data.list.map(normalizeArticle) : []
    if (!list.length) throw new Error('现有收藏为空')
    return { list, source: 'legacy' }
  } catch (error) {
    return { list: [demo.articles[0]], source: 'demo', error: error.message }
  }
}

async function toggleCollection(id) {
  await ensureToken()
  return request('/collect', 'POST', { essay: id })
}

async function getLegacyPosts() {
  try {
    await ensureToken()
    const data = await request('/getPublishPageList', 'GET', { pageNum: 1 })
    const list = data && Array.isArray(data.list) ? data.list.map(normalizeLegacyPost) : []
    if (!list.length) throw new Error('历史动态为空')
    return { list, source: 'legacy' }
  } catch (error) {
    return { list: demo.legacyPosts, source: 'demo', error: error.message }
  }
}

module.exports = {
  BASE_URL,
  ensureToken,
  getArticles,
  getArticle,
  getCollections,
  toggleCollection,
  getLegacyPosts,
  normalizeArticle,
  normalizeLegacyPost
}
