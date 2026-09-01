const store = require('../../utils/store')
const legacyApi = require('../../services/legacy-api')
const demo = require('../../data/demo')
const navigation = require('../../utils/navigation')

Page({
  data: {
    view: 'auth',
    next: '',
    state: {},
    phoneInput: '13800002190',
    codeInput: '',
    codeSent: false,
    agreement: false,
    collectionsLoading: false,
    collectionsSource: 'demo',
    collections: [],
    visibleHabitDays: []
  },

  onLoad(options) {
    this.setData({ view: options.view || 'auth', next: options.next || '' })
  },

  onShow() {
    this.refresh()
    if (this.data.view === 'collections' && !this.data.collections.length) this.loadCollections()
  },

  refresh() {
    const state = store.getState()
    this.setData({
      state,
      phoneInput: state.phone || this.data.phoneInput,
      visibleHabitDays: demo.habitDays.slice(0, state.habit.planLength).map((day) => ({
        ...day,
        completed: state.habit.completedDays.includes(day.day),
        current: day.day === state.habit.currentDay
      }))
    })
  },

  onPhoneInput(event) { this.setData({ phoneInput: event.detail.value }) },
  onCodeInput(event) { this.setData({ codeInput: event.detail.value }) },
  toggleAgreement() { this.setData({ agreement: !this.data.agreement }) },

  sendCode() {
    if (!/^1\d{10}$/.test(this.data.phoneInput)) return wx.showToast({ title: '请输入正确的11位手机号', icon: 'none' })
    this.setData({ codeSent: true })
    wx.showModal({ title: '模拟验证码', content: '本次不会真实发送短信。请使用验证码 123456。', showCancel: false })
  },

  login() {
    if (!/^1\d{10}$/.test(this.data.phoneInput)) return wx.showToast({ title: '请输入正确手机号', icon: 'none' })
    if (this.data.codeInput !== '123456') return wx.showToast({ title: '演示验证码为123456', icon: 'none' })
    if (!this.data.agreement) return wx.showToast({ title: '请先同意服务必要条款', icon: 'none' })
    store.updateState((state) => {
      state.loggedIn = true
      state.phoneLinked = true
      state.phone = this.data.phoneInput
      return state
    })
    wx.showToast({ title: '手机号关联成功', icon: 'success' })
    const next = this.data.next
    setTimeout(() => {
      if (next === 'register') return wx.redirectTo({ url: '/pages/camp-flow/index?view=register' })
      if (next === 'daily') return wx.redirectTo({ url: '/pages/friend-flow/index?view=daily' })
      if (next === 'compose') return wx.redirectTo({ url: '/pages/friend-flow/index?view=compose' })
      navigation.switchTab({ url: '/pages/mine/index' })
    }, 500)
  },

  setPlanLength(event) {
    const value = Number(event.currentTarget.dataset.value)
    store.updateState((state) => {
      state.habit.planLength = value
      if (state.habit.currentDay > value) state.habit.currentDay = value
      state.habit.completedDays = state.habit.completedDays.filter((day) => day <= value)
      return state
    })
    this.refresh()
  },

  togglePause() {
    store.updateState((state) => {
      state.habit.paused = !state.habit.paused
      return state
    })
    this.refresh()
    wx.showToast({ title: this.data.state.habit.paused ? '计划已暂停，恢复后顺延' : '计划已恢复', icon: 'none' })
  },

  async loadCollections() {
    this.setData({ collectionsLoading: true })
    const result = await legacyApi.getCollections()
    this.setData({ collectionsLoading: false, collectionsSource: result.source, collections: result.list })
  },

  openArticle(event) {
    navigation.navigateTo({ url: `/pages/article/index?id=${encodeURIComponent(event.currentTarget.dataset.id)}` })
  },

  toggleSubscription() {
    store.updateState((state) => {
      state.subscriptionEnabled = !state.subscriptionEnabled
      return state
    })
    this.refresh()
    wx.showModal({
      title: '订阅消息 Demo',
      content: this.data.state.subscriptionEnabled ? '已模拟同意活动提醒和第3天回访。未调用真实微信订阅接口。' : '已模拟关闭订阅消息。',
      showCancel: false
    })
  },

  togglePrivacy(event) {
    const key = event.currentTarget.dataset.key
    store.updateState((state) => {
      state[key] = !state[key]
      return state
    })
    this.refresh()
  },

  openExperience() {
    wx.redirectTo({ url: '/pages/camp-flow/index?view=experience' })
  }
})
