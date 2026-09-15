const store = require('../../utils/store')
const business = require('../../services/business-api')
const legacyApi = require('../../services/legacy-api')
const domain = require('../../utils/domain')
const navigation = require('../../utils/navigation')
Page({
  data: { view: 'daily', state: {}, dailyKey: '', dailyTitle: '', dailyChoice: '', dailyNote: '', savedRecord: null,
    records: [], archiveLoading: false, archiveLoaded: false, archiveError: '', archivePage: 1, archiveHasMore: true, archivePosts: [] },
  onLoad(options) {
    const view = ['daily', 'records', 'archive'].includes(options.view) ? options.view : 'archive'
    this.setData({ view })
  },
  onShow() {
    this.refresh()
    if (this.data.view === 'archive' && !this.data.archiveLoaded) this.loadArchive()
  },
  refresh() {
    const state = store.getState()
    const dailyKey = domain.dailyKey(state)
    const savedRecord = state.dailyRecords[dailyKey] || null
    this.setData({ state, dailyKey, savedRecord,
      dailyTitle: state.stage === 'refeed' ? '今天让饮食保持简单' : '给今天留下一点感受',
      dailyChoice: savedRecord ? savedRecord.choice : this.data.dailyChoice,
      dailyNote: savedRecord ? savedRecord.note : this.data.dailyNote,
      records: state.loggedIn ? Object.entries(state.dailyRecords).map(([key, record]) => ({ ...record, key,
        choiceText: { done: '完成了', light: '做了轻量版本', rest: '休息了' }[record.choice] || '已记录'
      })).reverse() : [] })
  },
  chooseDaily(event) { this.setData({ dailyChoice: event.currentTarget.dataset.value }) },
  onDailyNote(event) { this.setData({ dailyNote: event.detail.value }) },
  async saveDaily() {
    if (!this.data.state.phoneLinked) return navigation.navigateTo({ url: '/pages/mine-flow/index?view=auth&next=daily' })
    if (!['done', 'light', 'rest'].includes(this.data.dailyChoice)) return wx.showToast({ title: '请选择今天的实际情况', icon: 'none' })
    const record = domain.privateRecord(this.data.dailyChoice, this.data.dailyNote)
    if (business.enabled()) {
      try {
        const state = this.data.state
        await business.saveDailyRecord({
          sessionId: state.registration && state.registration.activityId || null,
          recordDate: domain.localDate(),
          recordStage: ['refeed', 'habit'].includes(state.stage) ? state.stage : 'general',
          planDay: state.stage === 'habit' ? state.habit.currentDay : 0,
          choiceValue: this.data.dailyChoice,
          note: this.data.dailyNote
        })
      } catch (error) {
        return wx.showToast({ title: error.message || '记录保存失败', icon: 'none' })
      }
    }
    store.updateState(state => {
      state.dailyRecords[this.data.dailyKey] = record
      if (state.stage === 'habit' && !state.habit.completedDays.includes(state.habit.currentDay)) state.habit.completedDays.push(state.habit.currentDay)
      return state
    })
    this.refresh()
    wx.showToast({ title: business.enabled() ? '私人记录已保存' : '演示记录已保存', icon: 'success' })
  },
  async loadArchive() {
    if (this.data.archiveLoading) return
    this.setData({ archiveLoading: true, archiveError: '' })
    const result = await legacyApi.getLegacyPosts(this.data.archivePage)
    if (result.error) {
      this.setData({ archiveLoading: false, archiveLoaded: true, archiveError: '历史打卡暂时无法读取，请重试。' })
      return
    }
    const seen = new Set(this.data.archivePosts.map(item => item.id))
    this.setData({ archiveLoading: false, archiveLoaded: true,
      archivePosts: this.data.archivePosts.concat(result.list.filter(item => !seen.has(item.id))),
      archivePage: this.data.archivePage + 1, archiveHasMore: result.hasMore })
  },
  onReachBottom() { if (this.data.view === 'archive' && this.data.archiveHasMore && !this.data.archiveError) this.loadArchive() },
  previewImage(event) {
    const post = this.data.archivePosts.find(item => item.id === event.currentTarget.dataset.id)
    if (post && post.images.length) wx.previewImage({ current: event.currentTarget.dataset.url, urls: post.images })
  },
  contactArchive() {
    wx.showModal({ title: '历史内容协助', content: '如需核对、撤回或反馈某条历史内容，请将该记录编号和日期告知原活动工作人员。这里不会自动提交申请。', showCancel: false })
  },
  openDaily() { navigation.navigateTo({ url: '/pages/friend-flow/index?view=daily' }) },
  openPlan() { navigation.navigateTo({ url: '/pages/mine-flow/index?view=habit' }) }
})
