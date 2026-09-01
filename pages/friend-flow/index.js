const store = require('../../utils/store')
const legacyApi = require('../../services/legacy-api')
const domain = require('../../utils/domain')

Page({
  data: {
    view: 'daily',
    state: {},
    dailyKey: '',
    dailyTitle: '',
    dailyChoice: 'done',
    dailyNote: '',
    savedRecord: null,
    shareText: '',
    shareConfirmed: false,
    post: null,
    commentText: '',
    archiveLoading: false,
    archiveSource: 'demo',
    archivePosts: []
  },

  onLoad(options) {
    this.setData({ view: options.view || 'daily', postId: options.id || '' })
  },

  onShow() {
    this.refresh()
    if (this.data.view === 'archive' && !this.data.archivePosts.length) this.loadArchive()
  },

  refresh() {
    const state = store.getState()
    const dailyKey = domain.dailyKey(state)
    const savedRecord = state.dailyRecords[dailyKey] || null
    const dailyTitle = state.stage === 'refeed' ? '今天让饮食保持简单' : '给今天留一句话'
    const post = this.data.postId ? state.newPosts.find((item) => item.id === this.data.postId) || null : null
    this.setData({
      state,
      dailyKey,
      dailyTitle,
      savedRecord,
      dailyChoice: savedRecord ? savedRecord.choice : this.data.dailyChoice,
      dailyNote: savedRecord ? savedRecord.note : this.data.dailyNote,
      post: post ? { ...post, reported: state.reportedPostIds.includes(post.id) } : null
    })
  },

  chooseDaily(event) {
    this.setData({ dailyChoice: event.currentTarget.dataset.value })
  },

  onDailyNote(event) {
    this.setData({ dailyNote: event.detail.value })
  },

  saveDaily() {
    const record = domain.privateRecord(this.data.dailyChoice, this.data.dailyNote)
    store.updateState((state) => {
      state.dailyRecords[this.data.dailyKey] = record
      if (state.stage === 'habit' && !state.habit.completedDays.includes(state.habit.currentDay)) {
        state.habit.completedDays.push(state.habit.currentDay)
      }
      return state
    })
    this.refresh()
    wx.showToast({ title: '私人记录已保存', icon: 'success' })
  },

  prepareShare() {
    const fallback = this.data.dailyChoice === 'rest' ? '今天选择先休息，也给自己留一点空间。' : `${this.data.dailyTitle}，今天愿意试试看。`
    this.setData({
      view: 'compose',
      shareText: this.data.dailyNote.trim() || fallback,
      shareConfirmed: false
    })
  },

  onShareText(event) {
    this.setData({ shareText: event.detail.value })
  },

  toggleShareConfirm() {
    this.setData({ shareConfirmed: !this.data.shareConfirmed })
  },

  publish() {
    const state = store.getState()
    const permission = domain.canPublish(state)
    if (!permission.allowed && permission.reason === 'phone_required') return wx.navigateTo({ url: '/pages/mine-flow/index?view=auth&next=compose' })
    if (!permission.allowed) return wx.showToast({ title: '未成年人暂不开放社区发布', icon: 'none' })
    const content = this.data.shareText.trim()
    if (!content) return wx.showToast({ title: '请填写愿意公开的内容', icon: 'none' })
    if (!this.data.shareConfirmed) return wx.showToast({ title: '请先确认公开范围', icon: 'none' })
    store.updateState((next) => {
      next.newPosts.unshift(domain.publicPost(next, content, this.data.savedRecord ? this.data.dailyKey : '', Date.now()))
      return next
    })
    wx.showToast({ title: '公开副本已发布', icon: 'success' })
    setTimeout(() => wx.switchTab({ url: '/pages/friends/index' }), 500)
  },

  onCommentInput(event) {
    this.setData({ commentText: event.detail.value })
  },

  addComment() {
    const state = store.getState()
    if (!state.phoneLinked) return wx.navigateTo({ url: '/pages/mine-flow/index?view=auth&next=friends' })
    if (state.profile.minor) return wx.showToast({ title: '未成年人暂不开放评论', icon: 'none' })
    const content = this.data.commentText.trim()
    if (!content) return wx.showToast({ title: '请填写友善回应', icon: 'none' })
    store.updateState((next) => {
      const post = next.newPosts.find((item) => item.id === this.data.postId)
      if (post) post.comments.push({ author: next.profile.nickname || next.profile.name, content })
      return next
    })
    this.setData({ commentText: '' })
    this.refresh()
  },

  reportPost() {
    wx.showModal({
      title: '举报这条动态？',
      content: 'Demo 会记录为“待运营处理”，不会发送到真实后台。',
      success: (result) => {
        if (!result.confirm) return
        store.updateState((state) => {
          if (!state.reportedPostIds.includes(this.data.postId)) state.reportedPostIds.push(this.data.postId)
          return state
        })
        this.refresh()
        wx.showToast({ title: '已提交演示举报', icon: 'none' })
      }
    })
  },

  async loadArchive() {
    this.setData({ archiveLoading: true })
    const result = await legacyApi.getLegacyPosts()
    this.setData({ archiveLoading: false, archiveSource: result.source, archivePosts: result.list })
  },

  backToFriends() {
    wx.switchTab({ url: '/pages/friends/index' })
  }
})
