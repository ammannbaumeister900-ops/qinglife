const legacyApi = require('../../services/legacy-api')
const store = require('../../utils/store')

Page({
  data: {
    id: '',
    loading: true,
    article: {},
    source: 'demo',
    collected: false
  },

  onLoad(options) {
    const id = decodeURIComponent(options.id || '')
    this.setData({ id })
    this.loadArticle()
  },

  async loadArticle() {
    const result = await legacyApi.getArticle(this.data.id)
    const state = store.getState()
    this.setData({
      loading: false,
      article: result.item,
      source: result.source,
      collected: state.localFavorites.includes(String(result.item.id))
    })
  },

  async toggleCollect() {
    const id = String(this.data.article.id)
    if (this.data.source === 'legacy') {
      try {
        await legacyApi.toggleCollection(id)
      } catch (error) {
        // 旧收藏接口不可用时，仍保留本机收藏状态。
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
    return { title: this.data.article.title || '轻生活内容', path: `/pages/article/index?id=${encodeURIComponent(this.data.id)}` }
  }
})
