const demo = require('../../data/demo')
const store = require('../../utils/store')
const domain = require('../../utils/domain')

Page({
  data: {
    view: 'detail',
    step: 1,
    activity: demo.activities[0],
    state: {},
    selectedCount: 1,
    allChecked: false,
    dayViews: [],
    motivationOptions: ['想让身体慢下来', '改善日常饮食节奏', '和家人一起体验', '朋友推荐'],
    todayDay: demo.activities[0].days[0]
  },

  onLoad(options) {
    this.setData({ view: options.view || 'detail' })
  },

  onShow() {
    this.refresh()
  },

  refresh() {
    const state = store.getState()
    const activity = demo.activities.find((item) => item.id === state.selectedActivityId) || demo.activities[0]
    const selectedCount = state.registration.participants.filter((item) => item.selected).length
    this.setData({
      state,
      activity,
      selectedCount,
      allChecked: activity.days.every((day) => state.checkedDays.includes(day.id)),
      dayViews: activity.days.map((day) => ({ ...day, checked: state.checkedDays.includes(day.id) })),
      todayDay: activity.days[Math.min(state.checkedDays.length, 2)] || activity.days[0]
    })
  },

  startRegistration() {
    const state = store.getState()
    if (!state.phoneLinked) {
      return wx.navigateTo({ url: '/pages/mine-flow/index?view=auth&next=register' })
    }
    this.setData({ view: 'register', step: 1 })
  },

  openJourney() {
    this.setData({ view: 'journey' })
  },

  toggleParticipant(event) {
    const id = event.currentTarget.dataset.id
    store.updateState((state) => {
      const person = state.registration.participants.find((item) => item.id === id)
      if (person) person.selected = !person.selected
      return state
    })
    this.refresh()
  },

  chooseMotivation(event) {
    const value = event.currentTarget.dataset.value
    store.updateState((state) => {
      state.registration.motivation = value
      return state
    })
    this.refresh()
  },

  toggleConsent(event) {
    const key = event.currentTarget.dataset.key
    store.updateState((state) => {
      if (key === 'serviceConsent') state.registration.serviceConsent = !state.registration.serviceConsent
      else state[key] = !state[key]
      return state
    })
    this.refresh()
  },

  nextStep() {
    if (this.data.step === 1 && this.data.selectedCount < 1) {
      return wx.showToast({ title: '请至少选择一位参与者', icon: 'none' })
    }
    this.setData({ step: Math.min(3, this.data.step + 1) })
  },

  previousStep() {
    this.setData({ step: Math.max(1, this.data.step - 1) })
  },

  submitRegistration() {
    if (!this.data.state.registration.serviceConsent) {
      return wx.showToast({ title: '请先同意服务必要信息处理', icon: 'none' })
    }
    const status = domain.registrationStatus(this.data.activity)
    store.updateState((state) => {
      state.registration.status = status
      state.stage = status === 'pending' ? 'visitor' : 'journey'
      return state
    })
    this.setData({ view: 'success' })
    this.refresh()
  },

  checkDay(event) {
    const id = event.currentTarget.dataset.id
    store.updateState((state) => {
      if (!state.checkedDays.includes(id)) state.checkedDays.push(id)
      if (state.checkedDays.length === 3) {
        state.registration.status = 'completed'
        state.stage = 'refeed'
      }
      return state
    })
    this.refresh()
    wx.showToast({ title: '签到状态已保存', icon: 'success' })
  },

  simulateConfirm() {
    store.updateState((state) => {
      state.registration.status = 'confirmed'
      state.stage = 'journey'
      return state
    })
    this.setData({ view: 'journey' })
    this.refresh()
    wx.showToast({ title: '已模拟运营确认', icon: 'none' })
  },

  openExperience() {
    this.setData({ view: 'experience' })
  },

  backToCamp() {
    wx.switchTab({ url: '/pages/camp/index' })
  }
})
