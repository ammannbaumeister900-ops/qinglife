const content = require('../../config/about-content')
const { presentAbout, storyArticle } = require('../../utils/about-presentation')
const navigation = require('../../utils/navigation')
const business = require('../../services/business-api')
const { camps } = require('../../utils/home-presentation')
Page({
  data: { ...presentAbout(content), fontScale: 1, failedImages: {}, latestCamp: null },
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
})
