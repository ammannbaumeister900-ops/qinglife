const staffApi = require('../../services/staff-api')
const business = require('../../services/business-api')
const store = require('../../utils/store')
const navigation = require('../../utils/navigation')
const { resolveContact } = require('../../utils/contact')

// Entries that open the visitor's own records. Guests are asked to log in here, then continued.
const PRIVATE_ENTRIES = { registration: '轻友登记', experiences: '全部参与经历', records: '私人记录', collections: '内容收藏' }
const NOTICES = {
  cancelled: '已取消登录，需要时可以再登录。',
  loginNetwork: '网络不太稳定，登录没有完成，请稍后重试。',
  loginFailed: '登录没有完成，请重试。',
  expired: '登录已过期，请重新登录后查看个人信息。',
  profileNetwork: '网络不太稳定，个人信息暂时没能读取。',
  profileFailed: '个人信息暂时没能读取。',
  logoutPartial: '已退出此设备，暂未确认服务端注销。',
  logoutFailed: '退出没有完成，请重试。'
}

// Raw API or WeChat messages are never shown; only the kind of failure decides the wording.
function failureKind(error) {
  if (!error) return 'failed'
  if (error.code === 'IDENTITY_CHANGED') return 'changed'
  if (error.code === 401) return 'expired'
  const message = String(error.errMsg || error.message || '')
  if (/cancel|deny/i.test(message)) return 'cancelled'
  if (/request:fail|timeout|offline|network|网络/i.test(message)) return 'network'
  return 'failed'
}

function hasCredential() {
  try { return !!(business.identityScope && business.identityScope().token) } catch (error) { return false }
}

Page({
  data: {
    state: {},
    avatarText: '轻',
    passConsumptions: [],
    signedIn: false,
    loginLoading: false,
    logoutLoading: false,
    profileError: '',
    notice: null,
    pendingEntry: null,
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
    this.setData({ state: {}, avatarText: '轻', passConsumptions: [], staffAccess: false, signedIn: hasCredential(),
      ready: false, loading: false, refreshing: false, profileError: '' })
  },
  onHide() {
    this._mineVisit = (this._mineVisit || 0) + 1
    if (this._unwatchIdentity) this._unwatchIdentity()
    this._unwatchIdentity = null
    this.clearPrivate()
    this.setData({ notice: null, pendingEntry: null })
  },
  onUnload() { this.onHide() },

  // The complete public directory for a visitor without a credential; never an error page.
  showGuest(notice = null) {
    this._mineRun = (this._mineRun || 0) + 1
    this.setData({ state: { backend: true, loggedIn: false }, ready: true, loading: false, refreshing: false, profileError: '',
      signedIn: false, avatarText: '轻', passConsumptions: [], staffAccess: false, notice })
  },

  async logout() {
    if (!this.data.signedIn || this.data.loginLoading || this.data.logoutLoading) return
    const scope = business.identityScope ? business.identityScope() : null
    const visit = this._mineVisit
    const answer = await new Promise(resolve => wx.showModal({ title: '退出登录？',
      content: '退出后本机不再显示你的个人信息，参与和记录仍会保留。', confirmText: '退出', cancelText: '取消',
      success: resolve, fail: () => resolve({ confirm: false }) }))
    if (!answer.confirm || visit !== this._mineVisit || this.data.logoutLoading ||
      (scope && scope !== business.identityScope())) return
    this.setData({ logoutLoading: true, notice: null, pendingEntry: null })
    let failed = false
    try { await business.logout() }
    catch (error) { failed = true }
    finally {
      this.clearPrivate()
      this.setData({ logoutLoading: false })
    }
    if (visit !== this._mineVisit) return
    // Logout starts at the page footer; bring its result and next action back into view.
    if (wx.pageScrollTo) wx.pageScrollTo({ scrollTop: 0, duration: 200 })
    // Only report what happened on this device: the local credential is either gone or still here.
    if (failed && hasCredential()) {
      this.setData({ notice: { kind: 'error', text: NOTICES.logoutFailed } })
      return this.refresh()
    }
    this.showGuest(failed ? { kind: 'warning', text: NOTICES.logoutPartial } : null)
    if (!failed) wx.showToast({ title: '已退出登录', icon: 'none' })
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
    const pending = this.data.pendingEntry
    this.setData({ loginLoading: true, notice: null })
    const visit = this._mineVisit
    try {
      await business.login()
      if (visit !== this._mineVisit) return
      await this.refresh()
      // A WeChat credential alone is not success: the personal overview must also be readable.
      if (visit !== this._mineVisit || !this.data.state.loggedIn) return
      this.setData({ pendingEntry: null })
      await this.refreshStaffAccess()
      wx.showToast({ title: '登录成功', icon: 'success' })
      if (pending) this.openEntry(pending.key)
    } catch (error) {
      if (visit !== this._mineVisit) return
      const kind = failureKind(error)
      if (kind === 'changed') return
      this.setData({ notice: kind === 'cancelled' ? { kind: 'info', text: NOTICES.cancelled }
        : { kind: 'error', text: kind === 'network' ? NOTICES.loginNetwork : NOTICES.loginFailed } })
    } finally {
      this.setData({ loginLoading: false })
    }
  },

  async refresh() {
    if (business.enabled()) {
      // No credential is the ordinary guest state and needs no private request.
      if (!hasCredential()) return this.showGuest(this.data.notice)
      const run = this._mineRun = (this._mineRun || 0) + 1
      const visit = this._mineVisit
      this.setData({ signedIn: true, loading: !this.data.ready, refreshing: !!this.data.ready, profileError: '' })
      try {
        const { state } = await business.context()
        if (run !== this._mineRun) return
        const passConsumptions=(state.passLedger||[]).filter(item=>item.entryType==='consume').slice(0,3)
        this.setData({ ready: true, state, passConsumptions, avatarText: state.loggedIn ? (state.profile.nickname || '轻').slice(0,1) : '轻' })
      } catch (error) {
        const kind = failureKind(error)
        if (kind === 'changed') return
        // The identity watcher may already have cleared private data; a dropped credential still needs its own message.
        if (visit === this._mineVisit && !hasCredential()) return this.showGuest({ kind: 'error', text: NOTICES.expired })
        if (run === this._mineRun) this.setData({ profileError: kind === 'network' ? NOTICES.profileNetwork : NOTICES.profileFailed })
      }
      finally { if (run === this._mineRun) this.setData({ loading: false, refreshing: false }) }
      return
    }
    const state = store.getState()
    const name = state.profile.nickname || '轻'
    this.setData({
      state, loading: false, refreshing: false, profileError: '', ready: true,
      avatarText: state.loggedIn ? name.slice(0, 1) : '轻',
      passConsumptions: []
    })
  },

  // Guests stay here with an explained login step instead of a detour through the account page.
  requiresLogin(key) {
    if (!business.enabled() || this.data.signedIn) return false
    this.setData({ pendingEntry: { key, label: PRIVATE_ENTRIES[key] }, notice: null })
    if (wx.pageScrollTo) wx.pageScrollTo({ scrollTop: 0, duration: 200 })
    return true
  },

  dismissPending() { this.setData({ pendingEntry: null }) },

  openEntry(key) {
    if (key === 'registration') return this.openFriendRegistration()
    return this.openFlow({ currentTarget: { dataset: { view: key } } })
  },

  openFlow(event) {
    const view = event.currentTarget.dataset.view
    const id = event.currentTarget.dataset.id || ''
    if (PRIVATE_ENTRIES[view] && this.requiresLogin(view)) return
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
    if (this.requiresLogin('registration')) return
    navigation.navigateTo({ url: '/pages/friend-registration/index' })
  }
})
