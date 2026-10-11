const business = require('../../services/business-api')
const store = require('../../utils/store')
const demo = require('../../data/demo')
const domain = require('../../utils/domain')
const discovery = require('../../utils/discovery')
const navigation = require('../../utils/navigation')
const readingSamples = require('../../data/reading-samples')
const brand = require('../../config/home-brand')
const registrationHistory = require('../../utils/registration-history')
function showHomeTask(state, view) {
  const reg = (state.registrations || {})[view.activityId] || state.registration || {}
  return !!state.loggedIn && reg.status && reg.status !== 'none' && reg.status !== 'cancelled' && !['confirmed', 'completed'].includes(reg.status)
}
function featuredCamp(activities) {
  const activity = discovery.activities(activities).find(a => a.canRegister)
  if (!activity) return null
  const parts = String(activity.name || '').match(/^(.*?)\s*[·｜|]?\s*(三日轻体营)$/)
  return { ...activity, dateLabel: discovery.dateRange(activity),
    title: parts && parts[1].trim() ? parts[1].trim() : activity.name,
    kind: parts && parts[1].trim() ? parts[2] : '' }
}
function taskContext(view, activities) {
  const activity = activities.find(item => item.id === view.activityId)
  return activity && activity.sessionNumber ? '第 ' + activity.sessionNumber + ' 期 · 你的参与' : '你的轻生活'
}
Page({
  data: { view: {}, state: {}, featuredReadings: [], stories: [], storyIndex: 0, visible: false, readingError: '', brand, failedImages: {}, taskContext: '', cancellationNotice: null },
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
        const cancellationNotice = registrationHistory.cancellation(state, activities, business.baseUrl ? business.baseUrl() : '')
        this.setData({ showHomeTask: showHomeTask(state, view), cancellationNotice, state, stories: readings && readings.stories || [], featuredReadings: readings && readings.featured || [],
          storyIndex: 0, readingError: readings ? '' : '轻读暂时未能读取，请稍后重试。', ready: true,
          returning: discovery.returning(state), featured: featuredCamp(activities),
          view, taskContext: taskContext(view, activities), failedImages: {} })
      } catch (error) {
        if (version !== this.requestVersion) return
        this.setData({ loadError: error.message || '首页暂时无法读取，请重试', ...(error.code === 401 ? { ready: false, state: {}, cancellationNotice: null, returning: false, showHomeTask: false } : {}) })
      } finally {
        if (version === this.requestVersion) this.setData({ loading: false, refreshing: false })
      }
      return
    }
    const state = store.getState()
    const view = domain.homeTask(state, demo.activities)
    this.setData({ loading: false, refreshing: false, loadError: '', showHomeTask: showHomeTask(state, view), state, stories: [], storyIndex: 0, readingError: '', featuredReadings: readingSamples.slice(0, 4), ready: true,
      returning: discovery.returning(state), featured: featuredCamp(demo.activities),
      view, taskContext: taskContext(view, demo.activities), failedImages: {} })
  },
  dismissCancellation() {
    const notice = this.data.cancellationNotice
    if (!notice) return
    store.updateState(state => {
      state.dismissedRegistrationCancellations = { ...state.dismissedRegistrationCancellations, [notice.key]: true }
      return state
    })
    this.setData({ cancellationNotice: null })
  },
  openCancellation() {
    const notice = this.data.cancellationNotice
    if (notice) navigation.navigateTo({ url: '/pages/camp-flow/index?view=journey&id=' + encodeURIComponent(notice.activityId) + '&registrationId=' + encodeURIComponent(notice.registrationId) })
  },
  onHide() { this.setData({ visible: false }) },
  onUnload() { this.requestVersion = (this.requestVersion || 0) + 1 },
  imageFailed(event) {
    const src = event.currentTarget.dataset.src
    if (src) this.setData({ failedImages: { ...this.data.failedImages, [src]: true } })
  },
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
