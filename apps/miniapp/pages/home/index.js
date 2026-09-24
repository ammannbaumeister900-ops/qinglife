const business = require('../../services/business-api')
const store = require('../../utils/store')
const demo = require('../../data/demo')
const domain = require('../../utils/domain')
const discovery = require('../../utils/discovery')
const navigation = require('../../utils/navigation')
const readingSamples = require('../../data/reading-samples')
Page({
  data: { view: {}, state: {}, featuredReadings: [] },
  async onShow() {
    const tab = this.getTabBar && this.getTabBar()
    if (tab) tab.setData({ selected: 0 })
    if (business.enabled()) {
      this.setData({ loading: !this.data.ready, refreshing: !!this.data.ready, loadError: '' })
      try { const [{ state, activities }, featuredReadings] = await Promise.all([business.context(), business.featuredReadings().catch(() => [])]); this.setData({ state, featuredReadings: featuredReadings || [], ready: true, returning: discovery.returning(state), featured: discovery.activities(activities).find(a => a.canRegister) || null, view: domain.homeTask(state, activities) }) }
      catch (error) { this.setData({ loadError: error.message, ...(error.code === 401 ? { state: {}, participationTimeline: [], currentRegistrations: [], returning: false } : {}) }) }
      finally { this.setData({ loading: false, refreshing: false }) }
      return
    }
    const state = store.getState()
    this.setData({ state, featuredReadings: readingSamples.slice(0, 4), ready: true, returning: discovery.returning(state), featured: discovery.activities(demo.activities).find(a => a.canRegister) || null, view: domain.homeTask(state, demo.activities) })
  },
  openActivity(event) { navigation.navigateTo({ url: '/pages/camp-flow/index?view=' + (event.currentTarget.dataset.view || 'detail') + '&id=' + encodeURIComponent(this.data.featured.id) }) },
  openReading() { navigation.switchTab({ url: '/pages/content/index' }) },
  openArticle(event) { const { id, title } = event.currentTarget.dataset; navigation.navigateTo({ url: '/pages/article/index?id=' + encodeURIComponent(id) + '&title=' + encodeURIComponent(title || '') }) },
  openCamp() { navigation.switchTab({ url: '/pages/camp/index' }) },
  openPrimary() {
    const view = this.data.view
    if (view.activityId) store.updateState(state => { state.selectedActivityId = view.activityId; return state })
    if (view.route === 'reading') return navigation.switchTab({ url: '/pages/content/index' })
    if (view.route === 'habit') return navigation.navigateTo({ url: '/pages/mine-flow/index?view=habit' })
    if (view.route === 'daily') return navigation.navigateTo({ url: '/pages/friend-flow/index?view=daily' })
    navigation.navigateTo({ url: '/pages/camp-flow/index?view=' + view.route + (view.activityId ? '&id=' + encodeURIComponent(view.activityId) : '') })
  },
  about() { wx.showModal({ title: '认识轻生活', content: '轻生活是一段三日线下生活方式体验。和导游一起，从饮食、呼吸与日常行动中找到自己的节奏。具体安排、费用与身体边界请查看活动详情。', showCancel: false }) },
  onShareAppMessage() { return { title: '轻生活', path: '/pages/home/index' } }
})
