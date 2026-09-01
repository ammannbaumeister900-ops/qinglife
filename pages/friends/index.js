const store = require('../../utils/store')
const navigation = require('../../utils/navigation')

Page({
  data: {
    state: {},
    posts: [],
    dailyTitle: '',
    dailySaved: false,
    expandedPostId: '',
    commentText: ''
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
      posts: state.newPosts.map((post) => ({
        ...post,
        reported: state.reportedPostIds.includes(post.id),
        commentsOpen: post.id === this.data.expandedPostId
      })),
      dailyTitle,
      dailySaved: !!state.dailyRecords[dailyKey]
    })
  },

  openDaily() {
    const state = store.getState()
    if (!state.phoneLinked) return navigation.navigateTo({ url: '/pages/mine-flow/index?view=auth&next=daily' })
    navigation.navigateTo({ url: '/pages/friend-flow/index?view=daily' })
  },

  openArchive() {
    navigation.navigateTo({ url: '/pages/friend-flow/index?view=archive' })
  },

  openPost(event) {
    navigation.navigateTo({ url: `/pages/friend-flow/index?view=post&id=${encodeURIComponent(event.currentTarget.dataset.id)}` })
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
  },

  toggleComments(event) {
    const id = event.currentTarget.dataset.id
    this.setData({
      expandedPostId: this.data.expandedPostId === id ? '' : id,
      commentText: ''
    })
    this.refresh()
  },

  onCommentInput(event) {
    this.setData({ commentText: event.detail.value })
  },

  addComment(event) {
    const state = store.getState()
    if (!state.phoneLinked) return navigation.navigateTo({ url: '/pages/mine-flow/index?view=auth&next=friends' })
    if (state.profile.minor) return wx.showToast({ title: '未成年人暂不开放评论', icon: 'none' })
    const content = this.data.commentText.trim()
    if (!content) return wx.showToast({ title: '请写一句友善回应', icon: 'none' })
    const id = event.currentTarget.dataset.id
    store.updateState((next) => {
      const post = next.newPosts.find((item) => item.id === id)
      if (post) post.comments.push({ author: next.profile.nickname || next.profile.name, content })
      return next
    })
    this.setData({ expandedPostId: id, commentText: '' })
    this.refresh()
  }
})
