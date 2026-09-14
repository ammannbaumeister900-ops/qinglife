const discovery = require('../../utils/discovery')
const business = require('../../services/business-api')
const demo = require('../../data/demo')
const store = require('../../utils/store')
const navigation = require('../../utils/navigation')

Page({
  data: {
    activities: demo.activities,
    openActivities: demo.activities.filter((item) => item.status !== '已结束'),
    leaders: demo.leaders,
    state: {},
    current: null,
    visibleActivities: []
  },

  async onShow() {
    const tab = this.getTabBar && this.getTabBar()
    if (tab) tab.setData({ selected: 1 })
    if (business.enabled()) {
      this.setData({ loading: !this.data.ready, refreshing: !!this.data.ready, loadError: '' })
      try {
        const { state, activities } = await business.context()
        const currentRegistrations = Object.values(state.registrations).map(reg => ({ ...reg, activity: activities.find(a => a.id === reg.activityId), label: { pending:'待确认', confirmed:'已确认', completed:'已结束', waitlisted:'候补中', cancelled:'已取消', rescheduled:'安排有调整' }[reg.status] || reg.status }))
        const openActivities = discovery.activities(activities)
        this.setData({ state, activities, currentRegistrations, current: activities.find(a => a.id === state.registration.activityId) || null, openActivities, visibleActivities: openActivities })
      } catch (error) { this.setData({ loadError: error.message, ...(error.code === 401 ? { state: {}, participationTimeline: [], currentRegistrations: [], returning: false } : {}) }) }
      finally { this.setData({ loading: false, refreshing: false, ready: true }) }
      return
    }
    const state = store.getState()
    const registrations = { ...(state.registrations || {}) }
    if (state.registration.status !== 'none') registrations[state.registration.activityId] = state.registration
    const currentRegistrations = Object.values(registrations).map(reg => ({ ...reg, activity: demo.activities.find(item => item.id === reg.activityId), label: { pending: '待确认', confirmed: '已确认', completed: '已结束', waitlist: '候补中', cancelled: '已取消', rescheduled: '安排有调整' }[reg.status] || reg.status })).filter(reg => reg.activity)
    this.setData({
      state, currentRegistrations,
      current: demo.activities.find((item) => item.id === state.registration.activityId) || demo.activities[0],
      openActivities: demo.activities.filter((item) => item.status !== '已结束'),
      visibleActivities: discovery.activities(demo.activities)
    })
  },

  signup(event) { navigation.navigateTo({ url: '/pages/camp-flow/index?view=register&id=' + encodeURIComponent(event.currentTarget.dataset.id) }) },
  openActivity(event) {
    const id = event.currentTarget.dataset.id
    store.updateState((state) => {
      state.selectedActivityId = id
      return state
    })
    navigation.navigateTo({ url: '/pages/camp-flow/index?view=detail&id=' + encodeURIComponent(id) })
  },

  openRegistration(event) {
    const id = event.currentTarget.dataset.id
    store.updateState(state => { state.selectedActivityId = id; return state })
    navigation.navigateTo({ url: '/pages/camp-flow/index?view=journey&id=' + encodeURIComponent(id) })
  },
  openCurrent() {
    const status = this.data.state.registration.status
    const view = status === 'none' ? 'detail' : status === 'completed' ? 'experience' : 'journey'
    if (this.data.current) {
      store.updateState((state) => {
        state.selectedActivityId = this.data.current.id
        return state
      })
    }
    navigation.navigateTo({ url: `/pages/camp-flow/index?view=${view}&id=${encodeURIComponent(this.data.current.id)}` })
  },

  onShareAppMessage() {
    return { title: '三日轻体营｜轻生活', path: '/pages/camp/index' }
  }
})
