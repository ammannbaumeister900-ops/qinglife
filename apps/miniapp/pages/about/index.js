const content = require('../../config/about-content')
const { presentAbout, storyArticle } = require('../../utils/about-presentation')
const navigation = require('../../utils/navigation')
const { withStoryContent } = require('../../utils/story-content')
Page(withStoryContent({
  data: { ...presentAbout(content), fontScale: 1, failedImages: {} },
  onLoad() {
    this.setData({ ...presentAbout(content), failedImages: {} })
    this.measureFont()
    if (wx.showShareMenu) wx.showShareMenu({ menus: ['shareAppMessage'] })
  },
  onShow() { this.measureFont() },
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
  learnRegistration() { navigation.switchTab({ url: '/pages/camp/index' }) },
  onShareAppMessage() { return { title: '认识轻生活', path: '/pages/about/index', imageUrl: '/assets/brand/logo-sage.png' } }
}))
