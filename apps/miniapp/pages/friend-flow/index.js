const store = require('../../utils/store')
const business = require('../../services/business-api')
const legacyApi = require('../../services/legacy-api')
const domain = require('../../utils/domain')
const navigation = require('../../utils/navigation')
Page({
  data: { view: 'daily', state: {}, dailyKey: '', dailyTitle: '', dailyChoice: '', dailyNote: '', savedRecord: null, dailySaving: false, dailyError: '', dailyReady: false, dailyLoading: false,
    records: [], archiveLoading: false, archiveLoaded: false, archiveError: '', archivePage: 1, archiveHasMore: true, archivePosts: [] },
  onLoad(options) {
    const view = ['daily', 'records', 'archive'].includes(options.view) ? options.view : 'archive'
    this.setData({ view })
  },
  usesServer() { return !business.isDemo() || business.enabled() },
  onShow() {
    this._privateHidden = false
    this._privateVisit = (this._privateVisit || 0) + 1
    if (this.data.view !== 'archive' && this.usesServer()) {
      this.clearPrivate()
      if (this._unwatchIdentity) this._unwatchIdentity()
      this._unwatchIdentity = business.watchIdentity(() => {
        this.clearPrivate()
        this.setData({ dailyError: '身份已变化，请重新读取本人记录' })
      })
      return this.loadPrivate()
    }
    this.refresh()
    if (this.data.view === 'archive' && !this.data.archiveLoaded) return this.loadArchive()
  },
  onHide() {
    this._privateHidden = true
    this._privateVisit = (this._privateVisit || 0) + 1
    if (this._unwatchIdentity) this._unwatchIdentity()
    this._unwatchIdentity = null
    if (!business.isDemo() || this._privateScope) this.clearPrivate()
  },
  onUnload() { this.onHide() },
  clearPrivate() {
    this._privateRun = (this._privateRun || 0) + 1
    this._privateScope = null
    this._dailySubmitted = false
    this.setData({ state: {}, dailyKey: '', dailyChoice: '', dailyNote: '', savedRecord: null,
      records: [], dailyReady: false, dailyLoading: false, dailySaving: false, dailyError: '' })
  },
  privateCurrent(run, scope) {
    if (run !== this._privateRun || scope !== this._privateScope) return false
    try { business.assertIdentity(scope) } catch (error) {
      this.clearPrivate(); this.setData({ dailyError: '身份已变化，请重新读取本人记录' }); return false
    }
    return true
  },
  async retryPrivate() {
    if (this._privateHidden) return
    const visit = this._privateVisit
    if (!this.usesServer()) return this.refresh(true)
    try {
      if (!business.identityScope().token) await business.login()
      if (!this._privateHidden && visit === this._privateVisit) return this.loadPrivate(true)
    } catch (error) { if (!this._privateHidden && visit === this._privateVisit && error.code !== 'IDENTITY_CHANGED') this.setData({ dailyError: error.message || '本人记录暂时无法读取' }) }
  },
  async loadPrivate(preserveDraft = false) {
    let scope
    try { scope = business.identityScope() } catch (error) {
      this.clearPrivate(); this.setData({ dailyError: error.message }); return
    }
    if (this._privateScope !== scope) this.clearPrivate()
    this._privateScope = scope
    const run = this._privateRun = (this._privateRun || 0) + 1
    this.setData({ dailyLoading: true, dailyError: '' })
    try {
      const result = await business.privateOverview(scope)
      if (!this.privateCurrent(run, scope)) return
      this.applyPrivate(result.overview, preserveDraft && this.data.dailyReady)
      this._dailySubmitted = false
    } catch (error) {
      if (this.privateCurrent(run, scope)) this.setData({ dailyError: error.message || '本人记录暂时无法读取' })
    } finally { if (this.privateCurrent(run, scope)) this.setData({ dailyLoading: false }) }
  },
  applyPrivate(overview, preserveDraft) {
    const habit = business.normalizeHabit(overview.habit)
    const state = { backend: true, loggedIn: true, habit,
      stage: habit.id ? 'habit' : 'general', registration: { activityId: habit.sessionId || '' } }
    const date = habit.id ? habit.serverDate : domain.localDate()
    const records = (overview.dailyRecords || []).map(row => ({ ...row, key: row.id,
      choice: row.choiceValue, note: row.note || '', savedAt: row.updatedAt || '', private: true,
      choiceText: { done: '完成了', light: '做了轻量版本', rest: '休息了' }[row.choiceValue] || '已记录' }))
    const savedRecord = records.find(row => String(row.recordDate).slice(0, 10) === date &&
      row.recordStage === state.stage && (state.stage !== 'habit' ||
        Number(row.planDay) === Number(habit.currentDay) && (row.sessionId || '') === (habit.sessionId || ''))) || null
    this.setData({ state, records, savedRecord, dailyKey: state.stage + '-' + date,
      dailyTitle: '给今天留下一点感受', dailyReady: true,
      dailyChoice: preserveDraft ? this.data.dailyChoice : savedRecord ? savedRecord.choice : '',
      dailyNote: preserveDraft ? this.data.dailyNote : savedRecord ? savedRecord.note : '' })
  },
  async savePrivateDaily() {
    const scope = this._privateScope, run = this._privateRun
    if (!this.privateCurrent(run, scope) || !this.data.dailyReady) return
    if (this._dailySubmitted) return this.setData({ dailyError: '记录已提交，请先重新读取确认结果' })
    if (!['done', 'light', 'rest'].includes(this.data.dailyChoice)) {
      this.setData({ dailyError: '请选择今天的实际情况' }); return
    }
    const draft = { choice: this.data.dailyChoice, note: this.data.dailyNote }
    const record = domain.privateRecord(draft.choice, draft.note)
    this.setData({ dailySaving: true, dailyError: '' })
    try {
      const result = await business.privateOverview(scope)
      if (!this.privateCurrent(run, scope)) return
      this.applyPrivate(result.overview, true)
      const state = this.data.state, habit = state.habit
      if (state.stage === 'habit' && !habit.canRecordToday) throw new Error('今天不在可记录的计划日内')
      await business.saveDailyRecord({ sessionId: habit.sessionId || null,
        recordDate: habit.id ? habit.serverDate : domain.localDate(), recordStage: state.stage,
        planDay: habit.id ? habit.currentDay : 0, choiceValue: record.choice, note: record.note }, scope)
      if (!this.privateCurrent(run, scope)) return
      this._dailySubmitted = true
      const readback = await business.privateOverview(scope)
      if (!this.privateCurrent(run, scope)) return
      const changed = draft.choice !== this.data.dailyChoice || draft.note !== this.data.dailyNote
      this.applyPrivate(readback.overview, true)
      if (!this.data.savedRecord || this.data.savedRecord.choice !== record.choice || this.data.savedRecord.note !== record.note) {
        throw new Error('暂未确认提交内容')
      }
      this._dailySubmitted = false
      if (!changed) this.setData({ dailyChoice: record.choice, dailyNote: record.note })
      wx.showToast({ title: changed ? '提交内容已保存，新修改未保存' : '私人记录已保存', icon: changed ? 'none' : 'success' })
    } catch (error) {
      if (!this.privateCurrent(run, scope)) return
      this.setData({ dailyError: this._dailySubmitted ? '记录已提交，回读暂未确认，请重新读取' : error.message || '记录保存失败' })
    } finally { if (this.privateCurrent(run, scope)) this.setData({ dailySaving: false }) }
  },
  refresh(preserveDraft = false) {
    if (this.data.view === 'archive') return
    if (this.data.view !== 'archive' && this.usesServer()) return this.loadPrivate(preserveDraft)
    const state = store.getState()
    const dailyKey = domain.dailyKey(state)
    const savedRecord = state.dailyRecords[dailyKey] || null
    this.setData({ state, dailyKey, savedRecord, dailyReady: true,
      dailyTitle: state.stage === 'refeed' ? '今天让饮食保持简单' : '给今天留下一点感受',
      dailyChoice: preserveDraft ? this.data.dailyChoice : savedRecord ? savedRecord.choice : this.data.dailyChoice,
      dailyNote: preserveDraft ? this.data.dailyNote : savedRecord ? savedRecord.note : this.data.dailyNote,
      records: state.loggedIn ? Object.entries(state.dailyRecords).map(([key, record]) => ({ ...record, key,
        choiceText: { done: '完成了', light: '做了轻量版本', rest: '休息了' }[record.choice] || '已记录'
      })).reverse() : [] })
  },
  chooseDaily(event) { if (this.usesServer() && !this.privateCurrent(this._privateRun, this._privateScope)) return; this.setData({ dailyChoice: event.currentTarget.dataset.value, dailyError: '' }) },
  onDailyNote(event) { if (this.usesServer() && !this.privateCurrent(this._privateRun, this._privateScope)) return; this.setData({ dailyNote: event.detail.value, dailyError: '' }) },
  async saveDaily() {
    if (this.data.dailySaving) return
    if (this.usesServer()) return this.savePrivateDaily()
    if (!this.data.state.phoneLinked) return navigation.navigateTo({ url: '/pages/mine-flow/index?view=auth&next=daily' })
    if (!['done', 'light', 'rest'].includes(this.data.dailyChoice)) {
      this.setData({ dailyError: '请选择今天的实际情况' })
      return wx.showToast({ title: '请选择今天的实际情况', icon: 'none' })
    }
    const draft = { dailyChoice: this.data.dailyChoice, dailyNote: this.data.dailyNote }
    const record = domain.privateRecord(draft.dailyChoice, draft.dailyNote)
    this.setData({ dailySaving: true, dailyError: '' })
    try {
      const draftChanged = this.data.dailyChoice !== draft.dailyChoice || this.data.dailyNote !== draft.dailyNote
      store.updateState(state => {
        state.dailyRecords[this.data.dailyKey] = record
        if (state.stage === 'habit' && !state.habit.completedDays.includes(state.habit.currentDay)) state.habit.completedDays.push(state.habit.currentDay)
        return state
      })
      this.refresh(draftChanged)
      wx.showToast({ title: draftChanged ? '提交内容已保存，新修改未保存' : '演示记录已保存', icon: draftChanged ? 'none' : 'success' })
    } catch (error) {
      const message = error.message || '演示记录保存失败'
      this.setData({ dailyError: message })
      wx.showToast({ title: message, icon: 'none' })
    } finally { this.setData({ dailySaving: false }) }
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
