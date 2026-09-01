const store = require('../../utils/store')
const navigation = require('../../utils/navigation')
const demo = require('../../data/demo')

Page({
  data: {
    state: {},
    avatarText: '登录',
    profileLine: '',
    participationTimeline: []
  },

  onShow() {
    const tab = this.getTabBar && this.getTabBar()
    if (tab) tab.setData({ selected: 4 })
    this.refresh()
  },

  refresh() {
    const state = store.getState()
    const name = state.profile.nickname || state.profile.name || '轻友'
    this.setData({
      state,
      avatarText: state.loggedIn ? name.slice(0, 1) : '登录',
      profileLine: state.phoneLinked ? `参加过 ${demo.participations.length} 期轻体营 · 最近为第${demo.participations[0].sessionNumber}期` : '关联手机号后可使用报名与发布功能',
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
