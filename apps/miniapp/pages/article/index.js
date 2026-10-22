const samples = require('../../data/reading-samples')
const legacyApi = require('../../services/legacy-api')
const favorites = require('../../services/article-favorites')
const demo = require('../../data/demo')
const business = require('../../services/business-api')
const runtime = require('../../config/runtime')
const { presentArticle } = require('../../utils/article-presentation')
const { routeText, resolveSource } = require('../../utils/article-source')

Page({
  data: {
    id: '',
    routeTitle: '',
    routeSource: '',
    loading: true,
    error: '',
    article: {},
    presentation: {},
    source: '',
    collected: false
  },

  onLoad(options = {}) {
    this._unloaded = false
    const id = routeText(options.id)
    const routeTitle = routeText(options.title)
    const routeSource = routeText(options.source)
    this.setData({ id, routeTitle, routeSource })
    return this.loadArticle()
  },

  async loadArticle() {
    if (this._articleLoading) return
    this._articleLoading = true
    this.pauseVideos()
    this.setData({ loading: true, error: '' })
    try {
      if (!this.data.id) throw new Error('文章链接缺少编号')
      const sample = samples.concat(demo.articles).find(a => String(a.id) === this.data.id)
      const source = resolveSource(this.data.routeSource, runtime, () => business.enabled(), sample)
      let result
      if (source === 'legacy') {
        // The old adapter has a demo shortcut; explicit legacy routes must never enter it.
        if (sample || this.data.id.startsWith('demo-')) throw new Error('历史文章编号无效')
        result = await legacyApi.getArticle(this.data.id)
      } else if (source === 'demo') result = { item: sample, source }
      else result = { item: await business.reading(this.data.id), source }
      if (result.error) throw new Error(result.error)
      const article = result.item || {}
      if (article.id && String(article.id) !== this.data.id || result.source !== source) throw new Error('未找到可查看的文章')
      if (this.data.routeTitle && article.title === '未命名文章') article.title = this.data.routeTitle
      const collected = await favorites.isCollected(article.id, result.source)
      if (this._unloaded) return
      const baseUrl = source === 'legacy' ? runtime.legacyBaseUrl : runtime.businessBaseUrl
      this.setData({ article, presentation: presentArticle(article, baseUrl), source, collected })
    } catch (error) {
      if (!this._unloaded) this.setData({ error: error.message || '文章暂时无法读取，请重试', article: {}, presentation: {}, source: '', collected: false })
    } finally {
      this._articleLoading = false
      if (!this._unloaded) this.setData({ loading: false })
    }
  },

  pauseVideos(exceptId) {
    const blocks = this.data.presentation.blocks || []
    blocks.forEach(block => {
      if (block.type !== 'video' || block.id === exceptId || !wx.createVideoContext) return
      try { wx.createVideoContext(block.id, this).pause() } catch (error) { /* Already detached. */ }
    })
  },

  onVideoPlay(event) { this.pauseVideos(event.currentTarget.id) },
  onVideoError(event) {
    const index = Number(event.currentTarget.dataset.index)
    const block = (this.data.presentation.blocks || [])[index]
    if (block && block.type === 'video') this.setData({ ['presentation.blocks[' + index + '].failed']: true })
  },
  retryVideo(event) {
    const index = Number(event.currentTarget.dataset.index)
    const block = (this.data.presentation.blocks || [])[index]
    if (block && block.type === 'video' && block.src) this.setData({ ['presentation.blocks[' + index + '].failed']: false })
  },
  onHide() { this.pauseVideos() },
  onUnload() { this._unloaded = true; this.pauseVideos() },

  onCoverError() {
    this.setData({ 'presentation.cover': '' })
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
    if (!this.data.article.id || !this.data.source) return { title: '轻生活', path: '/pages/content/index' }
    return { title, path: `/pages/article/index?id=${encodeURIComponent(this.data.id)}&title=${encodeURIComponent(title)}&source=${encodeURIComponent(this.data.source)}` }
  }
})
