const business = require('../../services/business-api')
const store = require('../../utils/store')
const demo = require('../../data/demo')
const domain = require('../../utils/domain')
const discovery = require('../../utils/discovery')
const navigation = require('../../utils/navigation')
const readingSamples = require('../../data/reading-samples')
function showHomeTask(state, view) {
  const reg = (state.registrations || {})[view.activityId] || state.registration || {}
  return !!state.loggedIn && reg.status && reg.status !== 'none' && !['confirmed', 'completed'].includes(reg.status)
}
function featuredCamp(activities) {
  const activity = discovery.activities(activities).find(a => a.canRegister)
  return activity ? { ...activity, dateLabel: discovery.dateRange(activity) } : null
}
Page({
  data: { view: {}, state: {}, featuredReadings: [], stories: [], storyIndex: 0, visible: false, readingError: '' },
  async onShow() {
    const version = this.requestVersion = (this.requestVersion || 0) + 1
    this.setData({ visible: true })
    const tab = this.getTabBar && this.getTabBar()
    if (tab) tab.setData({ selected: 0 })
    if (business.enabled()) {
      this.setData({ loading: !this.data.ready, refreshing: !!this.data.ready, loadError: '' })
      try {
        const [{ state, activities }, readings] = await Promise.all([business.context(), business.homeReadings().catch(() => null)])
        if (version !== this.requestVersion) return
        const view = domain.homeTask(state, activities)
        this.setData({ showHomeTask: showHomeTask(state, view), state, stories: readings && readings.stories || [], featuredReadings: readings && readings.featured || [],
          storyIndex: 0, readingError: readings ? '' : '轻读暂时未能读取，请稍后重试。', ready: true,
          returning: discovery.returning(state), featured: featuredCamp(activities),
          view })
      } catch (error) {
        if (version !== this.requestVersion) return
        this.setData({ loadError: error.message, ...(error.code === 401 ? { state: {}, returning: false, showHomeTask: false } : {}) })
      } finally {
        if (version === this.requestVersion) this.setData({ loading: false, refreshing: false })
      }
      return
    }
    const state = store.getState()
    const view = domain.homeTask(state, demo.activities)
    this.setData({ showHomeTask: showHomeTask(state, view), state, stories: [], storyIndex: 0, readingError: '', featuredReadings: readingSamples.slice(0, 4), ready: true,
      returning: discovery.returning(state), featured: featuredCamp(demo.activities),
      view })
  },
  onHide() { this.setData({ visible: false }) },
  onUnload() { this.requestVersion = (this.requestVersion || 0) + 1 },
  storyChanged(event) { this.setData({ storyIndex: event.detail.current }) },
  stepStory(event) {
    if (this.data.stories.length < 2) return
    const index = this.data.storyIndex + Number(event.currentTarget.dataset.step)
    this.setData({ storyIndex: Math.max(0, Math.min(this.data.stories.length - 1, index)) })
  },
  openActivity(event) {
    if (!this.data.featured) return
    navigation.navigateTo({ url: '/pages/camp-flow/index?view=' + (event.currentTarget.dataset.view || 'detail') + '&id=' + encodeURIComponent(this.data.featured.id) })
  },
  openReading() { navigation.openReadingList(false) },
  openStories() { navigation.openReadingList(true) },
  openArticle(event) { const { id, title } = event.currentTarget.dataset; navigation.navigateTo({ url: '/pages/article/index?id=' + encodeURIComponent(id) + '&title=' + encodeURIComponent(title || '') }) },
  openCamp() { navigation.switchTab({ url: '/pages/camp/index' }) },
  openPrimary() {
    const view = this.data.view
    if (view.activityId) store.updateState(state => { state.selectedActivityId = view.activityId; return state })
    if (view.route === 'reading') return navigation.openReadingList(false)
    if (view.route === 'habit') return navigation.navigateTo({ url: '/pages/mine-flow/index?view=habit' })
    if (view.route === 'daily') return navigation.navigateTo({ url: '/pages/friend-flow/index?view=daily' })
    navigation.navigateTo({ url: '/pages/camp-flow/index?view=' + view.route + (view.activityId ? '&id=' + encodeURIComponent(view.activityId) : '') })
  },
  about() { wx.showModal({ title: '认识轻生活', content: '轻生活是一段三日线下生活方式体验。和导游一起，从饮食、呼吸与日常行动中找到自己的节奏。具体安排、费用与身体边界请查看活动详情。', showCancel: false }) },
  onShareAppMessage() { return { title: '轻生活', path: '/pages/home/index' } }
})
