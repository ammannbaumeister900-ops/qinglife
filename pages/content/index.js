const legacyApi = require('../../services/legacy-api')
const demo = require('../../data/demo')
const navigation = require('../../utils/navigation')

Page({
  data: {
    loading: true,
    error: '',
    mode: 'recommend',
    query: '',
    articles: [],
    visibleArticles: [],
    hierarchy: demo.hierarchy,
    selectedConcern: demo.hierarchy[0],
    notices: [
      { id: 'notice-1', title: '三日轻体营集合与携带物品提醒', note: '完整地点仅向已报名轻友展示。' },
      { id: 'notice-2', title: '本周拍打练习更新', note: '内容由运营人员维护并控制上下架。' }
    ],
    videos: [
      { id: 'video-1', title: '拍打前的站立与呼吸', note: '入门 · 3:20' },
      { id: 'video-2', title: '手臂内外侧拍打', note: '日常 · 6:10' },
      { id: 'video-3', title: '足底轻叩与收束', note: '收束 · 3:40' }
    ]
  },

  onShow() {
    const tab = this.getTabBar && this.getTabBar()
    if (tab) tab.setData({ selected: 2 })
    if (!this.data.articles.length) this.loadArticles()
  },

  async loadArticles() {
    this.setData({ loading: true })
    const result = await legacyApi.getArticles(1)
    this.setData({
      loading: false,
      error: result.error || '',
      articles: result.list,
      visibleArticles: result.list
    })
  },

  setMode(event) {
    const mode = event.currentTarget.dataset.mode
    this.setData({
      mode,
      selectedConcern: mode === 'directory' ? (this.data.selectedConcern || this.data.hierarchy[0]) : this.data.selectedConcern
    })
  },

  onQueryInput(event) {
    const query = event.detail.value.trim().toLowerCase()
    const visibleArticles = this.data.articles.filter((item) => {
      return `${item.title}${item.summary}${item.category}`.toLowerCase().includes(query)
    })
    this.setData({ query, visibleArticles })
  },

  clearSearch() {
    this.setData({ query: '', visibleArticles: this.data.articles })
  },

  selectConcern(event) {
    const id = event.currentTarget.dataset.id
    this.setData({ selectedConcern: this.data.hierarchy.find((item) => item.id === id) || null })
  },

  openArticle(event) {
    navigation.navigateTo({ url: `/pages/article/index?id=${encodeURIComponent(event.currentTarget.dataset.id)}` })
  },

  showVideo(event) {
    const item = this.data.videos.find((video) => video.id === event.currentTarget.dataset.id)
    wx.showModal({
      title: item.title,
      content: item.note,
      showCancel: false
    })
  },

  retry() {
    this.loadArticles()
  }
})
