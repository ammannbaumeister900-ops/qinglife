const discovery = require('../../utils/discovery')
const domain = require('../../utils/domain')
const business = require('../../services/business-api')
const demo = require('../../data/demo')
const store = require('../../utils/store')
const navigation = require('../../utils/navigation')
const listPresentation = require('../../utils/camp-list-presentation')
const arrangementsPresentation = require('../../utils/camp-arrangements-presentation')
const reflection = require('../../utils/reflection')
const campBrand = require('../../config/camp-brand')

Page({
  data: {
    activities: demo.activities,
    openActivities: demo.activities.filter((item) => item.status !== '已结束'),
    leaders: demo.leaders,
    state: {},
    current: null,
    arrangementCount: 0, arrangementGroups: [],
    visibleActivities: [], visible: true, voicesBySession: {}, voicesError: '',
    reflectionOpening: false,
    activeFilter: 'all', activeFilterLabel: '全部', campFilters: [], expandedVoices: {}, voicePreviews: {},
    campHeader: campBrand.header, headerHidden: false
  },

  headerFailed() { this.setData({ headerHidden: true }) },

  async onShow() {
    const version = this.voiceRequest = (this.voiceRequest || 0) + 1
    this.setData({ visible: true, voicesBySession: {}, expandedVoices: {}, voicePreviews: {} })
    if (business.enabled()) {
      this.setData({ loading: !this.data.ready, refreshing: !!this.data.ready, loadError: '' })
      try {
        const { state, activities, overview } = await business.context()
        const currentRegistrations = Object.values(state.registrations).map(reg => ({ ...reg, activity: activities.find(a => a.id === reg.activityId), label: { pending:'待确认', confirmed:'已确认', completed:'已结束', waitlisted:'候补中', cancelled:'已取消', rescheduled:'安排有调整' }[reg.status] || reg.status }))
        const scope = business.identityScope ? business.identityScope() : null
        const openActivities = await Promise.all(discovery.activities(activities).map(async activity => {
          const reg = state.registrations[activity.id]
          const access = await reflection.readEntry(() => business.campReflection(activity.id, scope), reg, activity)
          return { ...activity, hasRegistration: domain.blocksRegistration(reg), canReapply: reg?.canReapply, showReflectionEntry: reflection.activeSelf(reg), reflectionAccess: access }
        }))
        if (business.assertIdentity) business.assertIdentity(scope)
        if (version !== this.voiceRequest || !this.data.visible) return
        this.setData({ ready: true, state, activities, currentRegistrations, ...arrangementsPresentation.present(currentRegistrations, state.registration, overview), current: activities.find(a => a.id === state.registration.activityId) || null, ...this.publicListPatch(openActivities) })
        await this.loadVoices(openActivities)
      } catch (error) { this.setData({ loadError: error.message || '暂时无法读取，请重试', ...(error.code === 401 ? { ready: false, state: {}, participationTimeline: [], currentRegistrations: [], arrangementCount: 0, arrangementGroups: [], returning: false } : {}) }) }
      finally { this.setData({ loading: false, refreshing: false }) }
      return
    }
    const state = store.getState()
    const registrations = { ...(state.registrations || {}) }
    if (state.registration.status !== 'none') registrations[state.registration.activityId] = state.registration
    const currentRegistrations = Object.values(registrations).map(reg => ({ ...reg, activity: demo.activities.find(item => item.id === reg.activityId), label: { pending: '待确认', confirmed: '已确认', completed: '已结束', waitlist: '候补中', cancelled: '已取消', rescheduled: '安排有调整' }[reg.status] || reg.status })).filter(reg => reg.activity)
    this.setData({
      state, loading: false, refreshing: false, loadError: '', ready: true, currentRegistrations,
      ...arrangementsPresentation.present(currentRegistrations, state.registration),
      current: demo.activities.find((item) => item.id === state.registration.activityId) || demo.activities[0],
      ...this.publicListPatch(discovery.activities(demo.activities).map(activity => ({ ...activity, hasRegistration: domain.hasRegistration(registrations[activity.id]) })))
    })
  },

  publicListPatch(activities) {
    const rows = listPresentation.presentRows(activities)
    const filter = this.data.activeFilter || 'all'
    return { openActivities: rows, visibleActivities: listPresentation.select(rows, filter),
      campFilters: listPresentation.filters(rows, filter), activeFilterLabel: listPresentation.filterLabel(filter) }
  },
  chooseFilter(event) {
    const filter = event.currentTarget.dataset.filter
    if (!listPresentation.FILTERS.some(item => item.key === filter)) return
    this.setData({ activeFilter: filter, activeFilterLabel: listPresentation.filterLabel(filter),
      campFilters: listPresentation.filters(this.data.openActivities, filter),
      visibleActivities: listPresentation.select(this.data.openActivities, filter) })
  },
  toggleVoices(event) {
    const id = event.currentTarget.dataset.id
    if (!this.data.visibleActivities.some(activity => activity.id === id) || !this.data.voicePreviews[id]?.isLong) return
    this.setData({ expandedVoices: { ...this.data.expandedVoices, [id]: !this.data.expandedVoices[id] } })
  },

  switchVoice(event) {
    const { id, direction } = event.currentTarget.dataset
    const voices = this.data.voicesBySession[id] || [], current = this.data.voicePreviews[id]
    const step = Number(direction)
    if (!this.data.visibleActivities.some(activity => activity.id === id) || !current || ![-1, 1].includes(step)) return
    const index = current.index + step
    if (index < 0 || index >= voices.length) return
    this.setData({ voicePreviews: { ...this.data.voicePreviews, [id]: listPresentation.voicePreview(voices, index) },
      expandedVoices: { ...this.data.expandedVoices, [id]: false } })
  },

  onHide() { this.voiceRequest = (this.voiceRequest || 0) + 1; this.setData({ visible: false, reflectionOpening: false }) },
  async loadVoices(activities) {
    const request = (this.voiceRequest || 0) + 1; this.voiceRequest = request
    const results = await Promise.all(activities.map(async activity => { try { return [activity.id, await business.campVoices(activity.id)] } catch (error) { return [activity.id, []] } }))
    if (request === this.voiceRequest) this.setData({ voicesBySession: Object.fromEntries(results),
      voicePreviews: Object.fromEntries(results.map(([id, voices]) => [id, listPresentation.voicePreview(voices)])), expandedVoices: {} })
  },
  async openReflection(event) {
    const id = event.currentTarget.dataset.id, activity = this.data.visibleActivities.find(row => row.id === id)
    if (!activity || !this.data.visible || this.data.loading || this.data.loadError || this.data.reflectionOpening) return
    if (!activity.reflectionAccess?.allowed) return wx.showToast({ title: activity.reflectionAccess?.reason || '当前阶段暂不可填写心声', icon: 'none' })
    const version = this.voiceRequest, scope = business.identityScope ? business.identityScope() : null
    this.setData({ reflectionOpening: true })
    try {
      const access = await reflection.readEntry(() => business.campReflection(id, scope), this.data.state.registrations[id], activity)
      if (business.assertIdentity) business.assertIdentity(scope)
      if (version !== this.voiceRequest || !this.data.visible) return
      this.setData(this.publicListPatch(this.data.openActivities.map(row => row.id === id ? { ...row, reflectionAccess: access } : row)))
      if (!access.allowed) return wx.showToast({ title: access.reason, icon: 'none' })
      navigation.navigateTo({ url: '/pages/camp-reflection/index?id=' + encodeURIComponent(id) })
    } catch (error) { if (version === this.voiceRequest) wx.showToast({ title: error.message, icon: 'none' }) }
    finally { if (version === this.voiceRequest) this.setData({ reflectionOpening: false }) }
  },
  signup(event) {
    const id = event.currentTarget.dataset.id
    const reg = (this.data.state.registrations || {})[id] || (this.data.state.registration && this.data.state.registration.activityId === id ? this.data.state.registration : null)
    if (domain.blocksRegistration(reg)) return navigation.navigateTo({ url: '/pages/camp-flow/index?view=journey&id=' + encodeURIComponent(id) })
    const activity = this.data.openActivities.find(activity => activity.id === id)
    if (!activity || !activity.canRegister) return
    // Reuse the reviewed detail page for conditions before the existing registration flow.
    return this.openActivity(event)
  },
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
    return { title: '清体营｜轻生活', path: '/pages/camp/index' }
  }
})
