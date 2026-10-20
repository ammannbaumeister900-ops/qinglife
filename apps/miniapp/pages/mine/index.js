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
    logoutLoading: false,
    staffAccess: false,
    contact: resolveContact()
  },

  openReferrals() { navigation.navigateTo({ url: "/pages/referrals/index" }) },

  openStaff() { wx.navigateTo({url: "/staff/workspace/index?view=desk"}) },

  async onShow() {
    this._mineVisit = (this._mineVisit || 0) + 1
    if (business.watchIdentity && !this._unwatchIdentity) {
      business.identityScope()
      this._unwatchIdentity = business.watchIdentity(() => this.clearPrivate())
    }
    this.setData({ staffAccess: false })
    const tab = this.getTabBar && this.getTabBar()
    if (tab) tab.setData({ selected: 3 })
    await Promise.all([this.refresh(), this.refreshContact()])
    if (this.data.state.loggedIn) await this.refreshStaffAccess()
  },

  clearPrivate() {
    this._mineRun = (this._mineRun || 0) + 1
    this.setData({ state: {}, avatarText: '登录', passConsumptions: [], staffAccess: false,
      ready: false, loading: false, refreshing: false, loadError: '' })
  },
  onHide() {
    this._mineVisit = (this._mineVisit || 0) + 1
    if (this._unwatchIdentity) this._unwatchIdentity()
    this._unwatchIdentity = null
    this.clearPrivate()
  },
  onUnload() { this.onHide() },

  async logout() {
    if (!this.data.state.loggedIn || this.data.loginLoading || this.data.logoutLoading) return
    const scope = business.identityScope ? business.identityScope() : null
    const visit = this._mineVisit
    const answer = await new Promise(resolve => wx.showModal({ title: '退出登录',
      content: '退出后可重新微信登录，已有参与和记录会保留。', confirmText: '退出登录',
      success: resolve, fail: () => resolve({ confirm: false }) }))
    if (!answer.confirm || visit !== this._mineVisit || this.data.logoutLoading ||
      (scope && scope !== business.identityScope())) return
    this.setData({ logoutLoading: true })
    let message = '已退出登录'
    try { await business.logout() }
    catch (error) { message = '已退出本机，服务端注销未确认' }
    finally {
      this.clearPrivate()
      this.setData({ logoutLoading: false })
    }
    if (visit === this._mineVisit) wx.showToast({ title: message, icon: 'none' })
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
    const run = this._mineRun
    try { await staffApi.request('/me'); if (run === this._mineRun) this.setData({ staffAccess: true }) }
    catch (error) { if (run === this._mineRun) this.setData({ staffAccess: false }) }
  },

  async login() {
    if (this.data.loginLoading || this.data.logoutLoading) return
    if (!business.enabled()) return navigation.navigateTo({ url: '/pages/mine-flow/index?view=auth' })
    this.setData({ loginLoading: true, loadError: '' })
    const visit = this._mineVisit
    try {
      await business.login()
      if (visit !== this._mineVisit) return
      await this.refresh()
      if (this.data.loadError) return wx.showToast({ title: '个人信息读取失败，请重试', icon: 'none' })
      await this.refreshStaffAccess()
      wx.showToast({ title: '登录成功', icon: 'success' })
    } catch (error) {
      wx.showToast({ title: error.message || '登录失败，请重试', icon: 'none' })
    } finally {
      this.setData({ loginLoading: false })
    }
  },

  async refresh() {
    const run = this._mineRun = (this._mineRun || 0) + 1
    if (business.enabled()) {
      this.setData({ loading: !this.data.ready, refreshing: !!this.data.ready, loadError: '' })
      try {
        const { state } = await business.context()
        if (run !== this._mineRun) return
        const passConsumptions=(state.passLedger||[]).filter(item=>item.entryType==='consume').slice(0,3)
        this.setData({ ready: true, state, passConsumptions, avatarText: state.loggedIn ? (state.profile.nickname || '轻').slice(0,1) : '登录' })
      } catch (error) { if (run === this._mineRun) this.setData({ loadError: error.message || '暂时无法读取，请重试', ...(error.code === 401 ? { ready: false, state: {}, passConsumptions: [] } : {}) }) }
      finally { if (run === this._mineRun) this.setData({ loading: false, refreshing: false }) }
      return
    }
    const state = store.getState()
    const name = state.profile.nickname || '轻'
    this.setData({
      state, loading: false, refreshing: false, loadError: '', ready: true,
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
    navigation.navigateTo({ url: '/pages/camp/index' })
  },

  openFriends() {
    navigation.switchTab({ url: '/pages/friends/index' })
  },

  openFriendRegistration() {
    if (!business.enabled()) return wx.showToast({ title: '登记服务尚未配置', icon: 'none' })
    navigation.navigateTo({ url: '/pages/friend-registration/index' })
  }
})
