const content = require('../../config/about-content')
const { presentAbout, storyArticle } = require('../../utils/about-presentation')
const navigation = require('../../utils/navigation')
const { withStoryContent } = require('../../utils/story-content')
const business = require('../../services/business-api')
const { camps } = require('../../utils/home-presentation')
Page(withStoryContent({
  data: { ...presentAbout(content), fontScale: 1, failedImages: {}, latestCamp: null, recommendedStories: [], storyError: '' },
  onLoad() {
    this.setData({ ...presentAbout(content), failedImages: {} })
    this.measureFont()
    if (wx.showShareMenu) wx.showShareMenu({ menus: ['shareAppMessage'] })
  },
  onShow() {
    this.measureFont()
    const tab = this.getTabBar && this.getTabBar()
    if (tab) tab.setData({ selected: 1 })
    this.loadLatestCamp()
    this.loadRecommendedStories()
  },
  onHide() { this.storyRequest = (this.storyRequest || 0) + 1 },
  onUnload() { this.onHide() },
  async loadRecommendedStories() {
    const version = this.storyRequest = (this.storyRequest || 0) + 1
    this.setData({ recommendedStories: [], storyError: '' })
    if (!business.enabled()) return
    try {
      // The public endpoint enforces enabled + recommended + canonical story-label membership.
      const response = await business.homeReadings()
      if (!response || !Array.isArray(response.stories)) throw new Error('故事数据无效')
      const stories = response.stories.filter(item => item && item.isStory === true && /^\d{1,19}$/.test(String(item.id)) && typeof item.title === 'string' && item.title.trim())
        .map(item => ({ id: String(item.id), title: item.title }))
      if (version === this.storyRequest) this.setData({ recommendedStories: stories })
    } catch (error) {
      if (version === this.storyRequest) this.setData({ storyError: '轻友故事暂时未能读取，请重试。' })
    }
  },
  openRecommendedStory(event) {
    const id = event.currentTarget.dataset.id
    if (this.data.recommendedStories.some(item => item.id === String(id))) {
      navigation.navigateTo({ url: '/pages/article/index?id=' + encodeURIComponent(id) + '&source=business' })
    }
  },
  // Public period list only (no token, no private overview); any failure or empty list hides the card.
  async loadLatestCamp() {
    const version = this.campRequest = (this.campRequest || 0) + 1
    let latestCamp = null
    try {
      const featured = business.enabled() ? camps(await business.sessions()).featured : null
      if (featured) latestCamp = { id: featured.id, periodLabel: featured.periodLabel, status: featured.status, placeLabel: featured.placeLabel, dateLabel: featured.dateLabel, dateParts: featured.dateParts }
    } catch (error) {
      latestCamp = null
    }
    if (version === this.campRequest) this.setData({ latestCamp })
  },
  measureFont() {
    const info = wx.getSystemInfoSync ? wx.getSystemInfoSync() : {}
    this.setData({ fontScale: Math.max(1, Math.min(1.4, (Number(info.fontSizeSetting) || 16) / 16)) })
  },
  imageFailed(event) {
    const id = event.currentTarget.dataset.id
    if (id) this.setData({ failedImages: { ...this.data.failedImages, [id]: true } })
  },
  openFullStory() {
    const url = storyArticle(this.data.story)
    if (url) navigation.navigateTo({ url })
  },
  learnRegistration() { navigation.navigateTo({ url: '/pages/camp/index' }) },
  openCampList() { navigation.navigateTo({ url: '/pages/camp/index' }) },
  onShareAppMessage() { return { title: this.data.share.title, path: '/pages/about/index', imageUrl: this.data.share.imageUrl } }
}))
