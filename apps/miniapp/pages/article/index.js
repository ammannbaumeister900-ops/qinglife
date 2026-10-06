const samples = require('../../data/reading-samples')
const legacyApi = require('../../services/legacy-api')
const favorites = require('../../services/article-favorites')
const demo = require('../../data/demo')
const business = require('../../services/business-api')

Page({
  data: {
    id: '',
    routeTitle: '',
    routeSource: '',
    loading: true,
    error: '',
    article: {},
    source: 'demo',
    collected: false
  },

  onLoad(options) {
    const id = decodeURIComponent(options.id || '')
    const routeTitle = decodeURIComponent(options.title || '')
    const routeSource = ['legacy', 'business'].includes(options.source) ? options.source : ''
    this.setData({ id, routeTitle, routeSource })
    return this.loadArticle()
  },

  async loadArticle() {
    if (this._articleLoading) return
    this._articleLoading = true
    this.setData({ loading: true, error: '' })
    try {
      const sample = samples.concat(demo.articles).find(a => String(a.id) === this.data.id)
      let result
      if (this.data.routeSource === 'legacy') result = await legacyApi.getArticle(this.data.id)
      else if (sample) result = { item: sample, source: 'demo' }
      else if (this.data.routeSource === 'business' || business.enabled()) result = { item: await business.reading(this.data.id), source: 'business' }
      else result = await legacyApi.getArticle(this.data.id)
      if (result.error) throw new Error(result.error)
      const article = result.item || {}
      if (this.data.routeTitle && article.title === '未命名文章') article.title = this.data.routeTitle
      const collected = await favorites.isCollected(article.id, result.source)
      this.setData({ article, source: result.source, collected })
    } catch (error) {
      this.setData({ error: error.message || '文章暂时无法读取，请重试', article: {}, collected: false })
    } finally {
      this._articleLoading = false
      this.setData({ loading: false })
    }
  },

  async toggleCollect() {
    if (this._collecting) return
    this._collecting = true
    const nextCollected = !this.data.collected
    try {
      await favorites.setCollected(this.data.article, this.data.source, nextCollected)
      this.setData({ collected: nextCollected })
      wx.showToast({ title: nextCollected ? '已收藏' : '已取消收藏', icon: 'none' })
    } catch (error) {
      wx.showToast({ title: '收藏暂未保存，请重试', icon: 'none' })
    } finally {
      this._collecting = false
    }
  },

  onShareAppMessage() {
    const title = this.data.article.title || '轻生活内容'
    return { title, path: `/pages/article/index?id=${encodeURIComponent(this.data.id)}&title=${encodeURIComponent(title)}` }
  }
})
