const samples = require('../../data/reading-samples')
const runtime = require('../../config/runtime')
const business = require('../../services/business-api')
const navigation = require('../../utils/navigation')
Page({
  data: { loading: false, loaded: false, error: '', query: '', topic: '', topics: [], articles: [], visibleArticles: [], page: 1, hasMore: true, sampleMode: false, stories: false },
  onLoad(options = {}) { if (options.stories === '1') this.setData({ stories: true }) },
  onShow() {
    const tab = this.getTabBar && this.getTabBar(); if (tab) tab.setData({ selected: 2 })
    const stories = navigation.consumeReadingFilter()
    if (stories !== null) {
      this.setData({ stories, query: '', topic: '' })
      return this.restartArticles()
    }
    if (!this.data.loaded) return this.loadArticles()
  },
  onUnload() { clearTimeout(this.searchTimer); this.requestVersion = (this.requestVersion || 0) + 1 },
  async loadArticles() {
    if (this.data.loading) return
    const version = this.requestVersion || 0
    const page = this.data.page
    this.setData({ loading: true, error: '' })
    try {
      const sampleMode = runtime.environment === 'demo' && !business.enabled()
      let result, topics
      if (sampleMode) {
        result = { list: samples, hasMore: false }
        topics = [...new Set(samples.map(item => item.category))].map(name => ({ id: name, name }))
      } else {
        const filters = { pageNum: page, pageSize: 12, stories: this.data.stories }
        if (this.data.topic) filters.labelId = this.data.topic
        if (this.data.query) filters.query = this.data.query
        const results = await Promise.all([
          business.readings(filters),
          page === 1 ? business.readingTopics().catch(() => this.data.topics) : Promise.resolve(this.data.topics)
        ])
        result = results[0]; topics = results[1]
      }
      if (version !== (this.requestVersion || 0)) return
      const seen = new Set(this.data.articles.map(item => String(item.id)))
      const articles = this.data.articles.concat((result.list || []).filter(item => !seen.has(String(item.id))))
      this.setData({ loading: false, loaded: true, sampleMode, articles, topics: topics || [], page: page + 1, hasMore: !!result.hasMore })
      this.filterArticles()
    } catch (error) {
      if (version !== (this.requestVersion || 0)) return
      this.setData({ loading: false, loaded: true, sampleMode: false, error: '内容暂时无法读取，请重试。' })
    }
  },
  filterArticles() {
    const query = this.data.query.toLowerCase()
    const visibleArticles = this.data.sampleMode
      ? this.data.articles.filter(item => (!this.data.topic || item.category === this.data.topic) && (item.title + item.summary + item.category).toLowerCase().includes(query))
      : this.data.articles
    this.setData({ visibleArticles })
  },
  restartArticles() {
    clearTimeout(this.searchTimer)
    this.requestVersion = (this.requestVersion || 0) + 1
    this.setData({ loading: false, loaded: false, error: '', articles: [], visibleArticles: [], page: 1, hasMore: true })
    return this.loadArticles()
  },
  onQueryInput(event) {
    this.setData({ query: event.detail.value.trim().slice(0, 100) })
    clearTimeout(this.searchTimer)
    // Invalidate old responses immediately, including while the debounce is pending.
    this.requestVersion = (this.requestVersion || 0) + 1
    this.setData({ loading: false })
    this.searchTimer = setTimeout(() => this.restartArticles(), 250)
  },
  clearSearch() { this.setData({ query: '', topic: '', stories: false }); return this.restartArticles() },
  chooseStories() { this.setData({ topic: '', stories: true }); return this.restartArticles() },
  chooseTopic(event) { this.setData({ topic: event.currentTarget.dataset.topic || '', stories: false }); return this.restartArticles() },
  openArticle(event) { const { id, title } = event.currentTarget.dataset; navigation.navigateTo({ url: '/pages/article/index?id=' + encodeURIComponent(id) + '&title=' + encodeURIComponent(title || '') }) },
  retry() { if (this.data.error && !this.data.loaded) return this.restartArticles(); return this.loadArticles() },
  onPullDownRefresh() { return this.restartArticles().finally(() => wx.stopPullDownRefresh()) },
  onReachBottom() { if (this.data.hasMore && !this.data.error) return this.loadArticles() }
})