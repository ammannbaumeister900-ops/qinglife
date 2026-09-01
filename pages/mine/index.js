const store = require('../../utils/store')

Page({
  data: {
    state: {},
    avatarText: '登录',
    completedCount: 0,
    todaySaved: false,
    stageOptions: [
      { value: 'visitor', label: '新访客' },
      { value: 'journey', label: '轻体营中' },
      { value: 'refeed', label: '复食第3天' },
      { value: 'habit', label: '习惯期' }
    ]
  },

  onShow() {
    const tab = this.getTabBar && this.getTabBar()
    if (tab) tab.setData({ selected: 4 })
    this.refresh()
  },

  refresh() {
    const state = store.getState()
    const dailyKey = state.stage === 'refeed' ? 'refeed-3' : `habit-${state.habit.currentDay}`
    const name = state.profile.nickname || state.profile.name || '轻友'
    this.setData({
      state,
      avatarText: state.loggedIn ? name.slice(0, 1) : '登录',
      completedCount: state.habit.completedDays.length,
      todaySaved: !!state.dailyRecords[dailyKey]
    })
  },

  openFlow(event) {
    const view = event.currentTarget.dataset.view
    if (view === 'daily') return wx.navigateTo({ url: '/pages/friend-flow/index?view=daily' })
    if (!this.data.state.phoneLinked && !['auth', 'privacy'].includes(view)) {
      return wx.navigateTo({ url: `/pages/mine-flow/index?view=auth&next=${view}` })
    }
    wx.navigateTo({ url: `/pages/mine-flow/index?view=${view}` })
  },

  openCamp() {
    wx.switchTab({ url: '/pages/camp/index' })
  },

  openFriends() {
    wx.switchTab({ url: '/pages/friends/index' })
  },

  setStage(event) {
    store.applyStage(event.currentTarget.dataset.value)
    this.refresh()
    wx.showToast({ title: '演示阶段已切换', icon: 'none' })
  },

  resetDemo() {
    wx.showModal({
      title: '重置演示状态？',
      content: '只会清除本工程的本地 Demo 数据，不影响旧小程序和服务器数据。',
      success: (result) => {
        if (!result.confirm) return
        store.resetState()
        this.refresh()
        wx.showToast({ title: '已恢复初始演示状态', icon: 'none' })
      }
    })
  }
})
