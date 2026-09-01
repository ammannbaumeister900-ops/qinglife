const store = require('../../utils/store')

Page({
  data: {
    state: {},
    posts: [],
    dailyTitle: '',
    dailySaved: false
  },

  onShow() {
    const tab = this.getTabBar && this.getTabBar()
    if (tab) tab.setData({ selected: 3 })
    this.refresh()
  },

  refresh() {
    const state = store.getState()
    const dailyKey = state.stage === 'refeed' ? 'refeed-3' : `habit-${state.habit.currentDay}`
    const dailyTitle = state.stage === 'refeed' ? '今天让饮食保持简单' : '给今天留一句话'
    this.setData({
      state,
      posts: state.newPosts.map((post) => ({ ...post, reported: state.reportedPostIds.includes(post.id) })),
      dailyTitle,
      dailySaved: !!state.dailyRecords[dailyKey]
    })
  },

  openDaily() {
    const state = store.getState()
    if (!state.phoneLinked) return wx.navigateTo({ url: '/pages/mine-flow/index?view=auth&next=daily' })
    wx.navigateTo({ url: '/pages/friend-flow/index?view=daily' })
  },

  compose() {
    const state = store.getState()
    if (!state.phoneLinked) return wx.navigateTo({ url: '/pages/mine-flow/index?view=auth&next=compose' })
    if (state.profile.minor) return wx.showToast({ title: '未成年人暂不开放社区发布', icon: 'none' })
    wx.navigateTo({ url: '/pages/friend-flow/index?view=compose' })
  },

  openArchive() {
    wx.navigateTo({ url: '/pages/friend-flow/index?view=archive' })
  },

  openPost(event) {
    wx.navigateTo({ url: `/pages/friend-flow/index?view=post&id=${encodeURIComponent(event.currentTarget.dataset.id)}` })
  },

  toggleLike(event) {
    const id = event.currentTarget.dataset.id
    store.updateState((state) => {
      const post = state.newPosts.find((item) => item.id === id)
      if (post) {
        post.liked = !post.liked
        post.likes += post.liked ? 1 : -1
      }
      return state
    })
    this.refresh()
  }
})

