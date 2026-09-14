const samples = require('../../data/reading-samples')
const legacyApi = require('../../services/legacy-api')
const store = require('../../utils/store')

Page({
  data: {
    id: '',
    routeTitle: '',
    loading: true,
    article: {},
    source: 'demo',
    collected: false
  },

  onLoad(options) {
    const id = decodeURIComponent(options.id || '')
    const routeTitle = decodeURIComponent(options.title || '')
    this.setData({ id, routeTitle })
    this.loadArticle()
  },

  async loadArticle() {
    this.setData({ loading: true })
    const sample = samples.find(a => a.id === this.data.id)
    const result = sample ? { item: sample, source: 'demo' } : await legacyApi.getArticle(this.data.id)
    const article = result.item
    if (this.data.routeTitle && article.title === '未命名文章') article.title = this.data.routeTitle
    const state = store.getState()
    this.setData({
      loading: false,
      article,
      source: result.source,
      collected: state.localFavorites.includes(String(article.id))
    })
  },

  async toggleCollect() {
    const id = String(this.data.article.id)
    if (this.data.source === 'legacy') {
      try {
        await legacyApi.toggleCollection(id)
      } catch (error) {
        wx.showToast({ title: '收藏暂未保存，请重试', icon: 'none' })
        return
      }
    }
    const nextCollected = !this.data.collected
    store.updateState((state) => {
      const exists = state.localFavorites.includes(id)
      if (nextCollected && !exists) state.localFavorites.push(id)
      if (!nextCollected && exists) state.localFavorites = state.localFavorites.filter((item) => item !== id)
      return state
    })
    this.setData({ collected: nextCollected })
    wx.showToast({ title: nextCollected ? '已收藏' : '已取消收藏', icon: 'none' })
  },

  onShareAppMessage() {
    const title = this.data.article.title || '轻生活内容'
    return { title, path: `/pages/article/index?id=${encodeURIComponent(this.data.id)}&title=${encodeURIComponent(title)}` }
  }
})
