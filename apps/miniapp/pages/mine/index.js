const staffApi = require('../../services/staff-api')
const business = require('../../services/business-api')
const store = require('../../utils/store')
const navigation = require('../../utils/navigation')
const demo = require('../../data/demo')

Page({
  data: {
    state: {},
    avatarText: '登录',
    participationTimeline: [],
    loginLoading: false,
    staffAccess: false
  },

  openStaff() { wx.navigateTo({url: "/staff/workspace/index?view=desk"}) },

  async onShow() {
    this.setData({ staffAccess: false })
    const tab = this.getTabBar && this.getTabBar()
    if (tab) tab.setData({ selected: 3 })
    await this.refresh()
    if (this.data.state.loggedIn) await this.refreshStaffAccess()
  },

  async refreshStaffAccess() {
    if (!business.enabled()) return
    try { await staffApi.request('/me'); this.setData({ staffAccess: true }) }
    catch (error) { this.setData({ staffAccess: false }) }
  },

  async login() {
    if (this.data.loginLoading) return
    if (!business.enabled()) return navigation.navigateTo({ url: '/pages/mine-flow/index?view=auth' })
    this.setData({ loginLoading: true, loadError: '' })
    try {
      await business.login()
      await this.refresh()
      await this.refreshStaffAccess()
      wx.showToast({ title: '登录成功', icon: 'success' })
    } catch (error) {
      wx.showToast({ title: error.message || '登录失败，请重试', icon: 'none' })
    } finally {
      this.setData({ loginLoading: false })
    }
  },

  async refresh() {
    if (business.enabled()) {
      this.setData({ loading: !this.data.ready, refreshing: !!this.data.ready, loadError: '' })
      try {
        const { state, activities } = await business.context()
        const participationTimeline = Object.values(state.registrations).map(reg => { const a = activities.find(a => a.id === reg.activityId) || { id: reg.activityId, date: '', place: '', endDate: '' }; return { id: a.id, sessionNumber: a.sessionNumber, status: { pending:'待确认', confirmed:'已确认', completed:'已完成', cancelled:'已取消', waitlisted:'候补中' }[reg.status] || reg.status, meta: a.date + ' · ' + a.place, endDate: a.endDate } }).sort((a,b) => b.endDate.localeCompare(a.endDate))
        this.setData({ state, participationTimeline, avatarText: state.loggedIn ? state.profile.nickname.slice(0,1) : '登录' })
      } catch (error) { this.setData({ loadError: error.message, ...(error.code === 401 ? { state: {}, participationTimeline: [], currentRegistrations: [], returning: false } : {}) }) }
      finally { this.setData({ loading: false, refreshing: false, ready: true }) }
      return
    }
    const state = store.getState()
    const name = state.profile.nickname || state.profile.name || '轻友'
    this.setData({
      state,
      avatarText: state.loggedIn ? name.slice(0, 1) : '登录',
      participationTimeline: state.loggedIn ? demo.participations.map((item) => ({
        ...item,
        marker: `${item.sessionNumber}期`,
        meta: `${item.period} · ${item.location}`
      })) : []
    })
  },

  openFlow(event) {
    const view = event.currentTarget.dataset.view
    const id = event.currentTarget.dataset.id || ''
    if (['experience', 'experiences', 'records'].includes(view)) return navigation.navigateTo({ url: '/pages/personal/index?view=' + view + '&id=' + encodeURIComponent(id) })
    if (view === 'archive') return navigation.navigateTo({ url: '/pages/friend-flow/index?view=archive' })
    if (view === 'records') return navigation.navigateTo({ url: '/pages/friend-flow/index?view=records' })
    if (view === 'daily') return navigation.navigateTo({ url: '/pages/friend-flow/index?view=daily' })
    if (!this.data.state.phoneLinked && !['auth', 'privacy'].includes(view)) {
      return navigation.navigateTo({ url: `/pages/mine-flow/index?view=auth&next=${view}` })
    }
    navigation.navigateTo({ url: `/pages/mine-flow/index?view=${view}${id ? `&id=${encodeURIComponent(id)}` : ''}` })
  },

  openCamp() {
    navigation.switchTab({ url: '/pages/camp/index' })
  },

  openFriends() {
    navigation.switchTab({ url: '/pages/friends/index' })
  }
})
