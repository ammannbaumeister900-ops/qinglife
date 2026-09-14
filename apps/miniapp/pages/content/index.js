const samples = require('../../data/reading-samples')
const runtime = require('../../config/runtime')
const legacyApi = require('../../services/legacy-api')
const navigation = require('../../utils/navigation')
Page({
  data: { loading: false, loaded: false, error: '', query: '', topic: '', topics: [], articles: [], visibleArticles: [], page: 1, hasMore: true },
  onShow() { const tab = this.getTabBar && this.getTabBar(); if (tab) tab.setData({ selected: 2 }); if (!this.data.loaded) this.loadArticles() },
  async loadArticles() {
    if (this.data.loading) return
    this.setData({ loading: true, error: '' })
    const sampleMode = ['demo', 'staging'].includes(runtime.environment)
    const result = sampleMode ? { list: samples, hasMore: false } : await legacyApi.getArticles(this.data.page)
    this.setData({ sampleMode })
    if (result.error) { this.setData({ loading: false, loaded: true, error: '内容暂时无法读取，请重试。' }); return }
    const seen = new Set(this.data.articles.map(item => item.id))
    const articles = this.data.articles.concat(result.list.filter(item => !seen.has(item.id)))
    this.setData({ loading: false, loaded: true, articles, topics: [...new Set(articles.map(item => item.category))].slice(0, 5), page: this.data.page + 1, hasMore: result.hasMore })
    this.filterArticles()
  },
  filterArticles() {
    const query = this.data.query.toLowerCase()
    this.setData({ visibleArticles: this.data.articles.filter(item => (!this.data.topic || item.category === this.data.topic) && (item.title + item.summary + item.category).toLowerCase().includes(query)) })
  },
  onQueryInput(event) { this.setData({ query: event.detail.value.trim() }); this.filterArticles() },
  clearSearch() { this.setData({ query: '', topic: '' }); this.filterArticles() },
  chooseTopic(event) { this.setData({ topic: event.currentTarget.dataset.topic || '' }); this.filterArticles() },
  openArticle(event) { const { id, title } = event.currentTarget.dataset; navigation.navigateTo({ url: '/pages/article/index?id=' + encodeURIComponent(id) + '&title=' + encodeURIComponent(title || '') }) },
  retry() { this.loadArticles() },
  onReachBottom() { if (this.data.hasMore && !this.data.error) this.loadArticles() }
})
