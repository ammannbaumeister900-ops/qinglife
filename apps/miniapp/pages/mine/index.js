const staffApi = require('../../services/staff-api')
const business = require('../../services/business-api')
const store = require('../../utils/store')
const navigation = require('../../utils/navigation')
const { resolveContact } = require('../../utils/contact')

Page({
  data: {
    state: {},
    avatarText: '登录',
    passConsumptions: [],
    loginLoading: false,
    staffAccess: false,
    contact: resolveContact()
  },

  openReferrals() { navigation.navigateTo({ url: "/pages/referrals/index" }) },

  openStaff() { wx.navigateTo({url: "/staff/workspace/index?view=desk"}) },

  async onShow() {
    this.setData({ staffAccess: false })
    const tab = this.getTabBar && this.getTabBar()
    if (tab) tab.setData({ selected: 3 })
    await Promise.all([this.refresh(), this.refreshContact()])
    if (this.data.state.loggedIn) await this.refreshStaffAccess()
  },

  async refreshContact() {
    if (!business.enabled()) return
    try { this.setData({ contact: resolveContact(await business.contact()) }) } catch (error) { this.setData({ contact: resolveContact() }) }
  },

  copyContact() {
    wx.setClipboardData({
      data: this.data.contact.wechat,
      success: () => wx.showToast({ title: '微信号已复制', icon: 'success' }),
      fail: () => wx.showToast({ title: '复制未成功，可长按微信号复制', icon: 'none' })
    })
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
        const { state } = await business.context()
        const passConsumptions=(state.passLedger||[]).filter(item=>item.entryType==='consume').slice(0,3)
        this.setData({ state, passConsumptions, avatarText: state.loggedIn ? (state.profile.nickname || '轻').slice(0,1) : '登录' })
      } catch (error) { this.setData({ loadError: error.message, ...(error.code === 401 ? { state: {}, passConsumptions: [] } : {}) }) }
      finally { this.setData({ loading: false, refreshing: false, ready: true }) }
      return
    }
    const state = store.getState()
    const name = state.profile.nickname || '轻'
    this.setData({
      state,
      avatarText: state.loggedIn ? name.slice(0, 1) : '登录',
      passConsumptions: []
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
  },

  openFriendRegistration() {
    if (!business.enabled()) return wx.showToast({ title: '登记服务尚未配置', icon: 'none' })
    navigation.navigateTo({ url: '/pages/friend-registration/index' })
  }
})
