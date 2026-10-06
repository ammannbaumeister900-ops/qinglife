const store = require('../../utils/store')
const business = require('../../services/business-api')
const favorites = require('../../services/article-favorites')
const demo = require('../../data/demo')
const navigation = require('../../utils/navigation')
const domain = require('../../utils/domain')

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
    collectionsSource: 'legacy',
    collectionsError: '',
    collections: [],
    visibleHabitDays: [],
    experienceId: '',
    currentExperience: null,
    experienceTabs: demo.participations,
    expandedDetailId: ''
  },

  onLoad(options) {
    this.setData({
      view: options.view || 'auth',
      next: options.next || '',
      experienceId: options.id || demo.participations[0].id
    })
  },

  onShow() {
    this.refresh()
    if (this.data.view === 'habit' && business.enabled()) business.refreshHabit().then(() => this.refresh()).catch(error => wx.showToast({ title: error.message || '计划读取失败', icon: 'none' }))
    if (this.data.view === 'collections') return this.loadCollections()
  },

  refresh() {
    const state = store.getState()
    this.setData({
      state,
      phoneInput: state.phone || this.data.phoneInput,
      currentExperience: demo.participations.find((item) => item.id === this.data.experienceId) || demo.participations[0],
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
    wx.showModal({ title: '验证码', content: '请输入 123456。', showCancel: false })
  },

  login() {
    if (!/^1\d{10}$/.test(this.data.phoneInput)) return wx.showToast({ title: '请输入正确手机号', icon: 'none' })
    if (this.data.codeInput !== '123456') return wx.showToast({ title: '请输入验证码 123456', icon: 'none' })
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
      if (['records', 'archive'].includes(next)) return wx.redirectTo({ url: '/pages/friend-flow/index?view=' + next })
      if (['collections', 'experiences', 'experience', 'habit'].includes(next)) return wx.redirectTo({ url: '/pages/mine-flow/index?view=' + next })
      navigation.switchTab({ url: '/pages/mine/index' })
    }, 500)
  },

  async setPlanLength(event) {
    const value = Number(event.currentTarget.dataset.value)
    if (business.enabled()) {
      try {
        const state = this.data.state
        const habit = await business.startHabit({ sessionId: state.registration && state.registration.activityId || null, planLength: value, startedAt: domain.localDate() })
        business.applyHabit(habit)
        this.refresh()
        return
      } catch (error) {
        return wx.showToast({ title: error.message || '计划创建失败', icon: 'none' })
      }
    }
    store.updateState((state) => {
      state.habit.planLength = value
      if (state.habit.currentDay > value) state.habit.currentDay = value
      return state
    })
    this.refresh()
  },

  async togglePause() {
    if (business.enabled()) {
      if (this.data.habitBusy) return
      const habit = this.data.state.habit
      if (!habit.id) return wx.showToast({ title: '请先开始计划', icon: 'none' })
      this.setData({ habitBusy: true })
      try {
        business.applyHabit(await business.changeHabit(habit.id, !habit.paused))
        this.refresh()
        wx.showToast({ title: this.data.state.habit.paused ? '计划已暂停，今天不计入' : '计划已恢复', icon: 'none' })
      } catch (error) {
        wx.showToast({ title: error.message || '计划更新失败', icon: 'none' })
      } finally { this.setData({ habitBusy: false }) }
      return
    }
    store.updateState((state) => {
      state.habit.paused = !state.habit.paused
      return state
    })
    this.refresh()
    wx.showToast({ title: this.data.state.habit.paused ? '计划已暂停，恢复后顺延' : '计划已恢复', icon: 'none' })
  },

  async loadCollections() {
    this.setData({ collectionsLoading: true, collectionsError: '' })
    const requestId = this._collectionsRequest = (this._collectionsRequest || 0) + 1
    try {
      const result = await favorites.getCollections()
      if (requestId !== this._collectionsRequest) return
      this.setData({ collectionsLoading: false, collectionsSource: result.source, collections: result.list,
        collectionsError: result.error ? result.list.length ? '部分收藏暂时无法读取，请重试。' : '收藏暂时无法读取，请重试。' : '' })
    } catch (error) {
      if (requestId !== this._collectionsRequest) return
      this.setData({ collectionsLoading: false, collectionsError: this.data.collections.length ? '收藏暂未更新，请重试；下方保留上次读取的内容。' : '收藏暂时无法读取，请重试。' })
    }
  },

  openArticle(event) {
    const { id, title, source } = event.currentTarget.dataset
    navigation.navigateTo({ url: `/pages/article/index?id=${encodeURIComponent(id)}&title=${encodeURIComponent(title || '')}${source ? '&source=' + encodeURIComponent(source) : ''}` })
  },

  toggleSubscription() {
    try {
      store.updateState((state) => {
        state.subscriptionEnabled = !state.subscriptionEnabled
        return state
      })
    } catch (error) {
      return wx.showToast({ title: '本机偏好保存失败，请重试', icon: 'none' })
    }
    this.refresh()
    wx.showModal({
      title: '本机提醒偏好',
      content: this.data.state.subscriptionEnabled ? '希望接收提醒的偏好已保存到本机。当前不会发送活动提醒或结营回访消息。' : '不接收提醒的偏好已保存到本机。此操作不会更改微信订阅授权。',
      showCancel: false
    })
  },

  togglePrivacy(event) {
    const key = event.currentTarget.dataset.key
    if (!['operationConsent', 'aiConsent'].includes(key)) return
    try {
      store.updateState((state) => {
        state[key] = !state[key]
        return state
      })
    } catch (error) {
      return wx.showToast({ title: '本机偏好保存失败，请重试', icon: 'none' })
    }
    this.refresh()
    wx.showToast({ title: '偏好已保存到本机', icon: 'none' })
  },

  openAccount() { this.setData({ view: 'auth' }) },
  openReading() { navigation.switchTab({ url: '/pages/content/index' }) },
  selectExperience(event) {
    this.setData({ view: 'experience', experienceId: event.currentTarget.dataset.id, expandedDetailId: '' })
    this.refresh()
  },

  toggleExperienceDetail(event) {
    const id = event.currentTarget.dataset.id
    this.setData({ expandedDetailId: this.data.expandedDetailId === id ? '' : id })
  }
})
