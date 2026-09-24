const business = require('../../services/business-api')
const store = require('../../utils/store')
const demo = require('../../data/demo')
const navigation = require('../../utils/navigation')
const labels = { pending: '待确认', confirmed: '已确认', completed: '已完成', cancelled: '已取消', waitlisted: '候补中', rescheduled: '安排有调整' }
Page({
  data: { view: 'experiences', title: '全部参与经历', rows: [], records: [], loading: true, error: '', loggedIn: false },
  onLoad(options) { this.setData({ view: options.view || 'experiences', sessionId: options.id || '', title: options.view === 'records' ? '私人记录' : options.view === 'experience' ? '本次参与经历' : '全部参与经历' }) },
  onShow() { return this.refresh() },
  onHide() { this.setData({ rows: [], records: [], loggedIn: false }) },
  async refresh() {
    if (this.busy) return
    this.busy = true
    this.setData({ loading: true, error: '' })
    try {
      const backend = business.enabled()
      const context = backend ? await business.context() : { state: store.getState(), activities: demo.activities, overview: {} }
      const { state, activities, overview } = context
      const registrations = { ...(state.registrations || {}) }
      if (!backend && state.registration.status !== 'none') registrations[state.registration.activityId] = state.registration
      const rows = state.loggedIn ? Object.values(registrations).filter(r => !this.data.sessionId || r.activityId === this.data.sessionId).map(r => {
        const a = activities.find(a => a.id === r.activityId) || {}
        return { id: r.activityId, name: a.name || '往期活动', participants: (r.participants || []).map(p => p.name || p.relation || '参与者').join('、'), date: a.date || '', status: labels[r.status] || r.status, settlement: r.settlementStatus !== 'confirmed' ? '结算待确认' : r.settlementType === 'pass' ? '卡次已使用' : r.paymentStatus === 'paid' ? '现金已收款' : Number(r.finalAmount) === 0 ? '无需现金收款' : '现金金额已确认，待线下收款', endDate: a.endDate || '' }
      }).sort((a, b) => b.endDate.localeCompare(a.endDate)) : []
      const selfRows = (overview.registrations || []).filter(r => r.isSelf === true || r.isSelf === 1 || r.isSelf === '1')
      const records = !state.loggedIn ? [] : backend ? (overview.experienceRecords || []).filter(r => selfRows.some(s => s.registrationId === r.registrationId && (!this.data.sessionId || s.sessionId === this.data.sessionId))).map(r => {
        const session = selfRows.find(s => s.registrationId === r.registrationId)
        const a = activities.find(a => a.id === session.sessionId) || {}
        return { ...r, name: a.name || '往期活动', label: { before: '活动前', during: '活动期间', after: '活动后' }[r.phase] || '个人感受' }
      }) : Object.entries(state.dailyRecords || {}).map(([id, r]) => ({ ...r, id, name: '日常记录', label: r.savedAt ? r.savedAt.slice(0, 10) : '', note: r.note || r.choice || '' })).concat(Object.entries(state.experienceReviews || {}).filter(([id]) => !this.data.sessionId || id === this.data.sessionId).map(([id, r]) => ({ ...r, id, name: (activities.find(a => a.id === id) || {}).name || '轻体营', label: '活动后' })))
      this.setData({ rows, records, loggedIn: state.loggedIn, demo: !backend })
    } catch (error) { this.setData({ error: error.message, rows: [], records: [] }) }
    finally { this.busy = false; this.setData({ loading: false }) }
  },
  async login() {
    if (!business.enabled()) return navigation.navigateTo({ url: '/pages/mine-flow/index?view=auth' })
    try { await business.login(); await this.refresh() } catch (error) { this.setData({ error: error.message }) }
  },
  openSession(event) { navigation.navigateTo({ url: '/pages/camp-flow/index?view=journey&id=' + encodeURIComponent(event.currentTarget.dataset.id) }) },
  openCamp() { navigation.switchTab({ url: '/pages/camp/index' }) }
})
