const store = require('../../utils/store')
const navigation = require('../../utils/navigation')

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
    const checkedCount = state.checkedDays.length
    const sessionStatus = state.registration.status === 'completed' ? '已完成' : state.registration.status === 'confirmed' ? '已确认' : '已报名'
    this.setData({
      state,
      avatarText: state.loggedIn ? name.slice(0, 1) : '登录',
      profileLine: state.phoneLinked ? '第16期轻友 · 最近参与三日轻体营' : '关联手机号后可使用报名与发布功能',
      participationTimeline: state.loggedIn ? [
        { marker: '报名', title: '第16期 · 身体知道答案', meta: `上海青浦 · ${sessionStatus}`, note: '报名、参与人与服务确认统一归入本次经历。', status: '已归档' },
        { marker: '三日', title: '三日现场体验', meta: `签到 ${checkedCount}/3 天`, note: '每天的参与记录按同一期轻体营连续保存。', status: checkedCount === 3 ? '已完成' : '进行中' },
        { marker: '回访', title: '结营后的陪伴', meta: '与第16期体验关联', note: '回访与个人感受继续留在这段参与经历里。', status: '持续中' }
      ] : []
    })
  },

  openFlow(event) {
    const view = event.currentTarget.dataset.view
    if (view === 'daily') return navigation.navigateTo({ url: '/pages/friend-flow/index?view=daily' })
    if (!this.data.state.phoneLinked && !['auth', 'privacy'].includes(view)) {
      return navigation.navigateTo({ url: `/pages/mine-flow/index?view=auth&next=${view}` })
    }
    navigation.navigateTo({ url: `/pages/mine-flow/index?view=${view}` })
  },

  openCamp() {
    navigation.switchTab({ url: '/pages/camp/index' })
  },

  openFriends() {
    navigation.switchTab({ url: '/pages/friends/index' })
  }
})
