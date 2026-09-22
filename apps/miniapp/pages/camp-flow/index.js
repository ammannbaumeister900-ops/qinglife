const business = require('../../services/business-api')
const demo = require('../../data/demo')
const store = require('../../utils/store')
const domain = require('../../utils/domain')
const navigation = require('../../utils/navigation')

Page({
  data: {
    view: 'detail', loading: false, loadError: '', submitting: false, feelingSaving: false, invitationCode: '', routeActivityId: '', sharePath: '', requestId: '', scales: [1,2,3,4,5], feelingPhase: '', feelingDayId: '', energy: null, relaxation: null, feelingNote: '', feelingSaved: false, inviteActivities: [],
    step: 1,
    activity: demo.activities[0],
    state: {},
    selectedCount: 1,
    allChecked: false,
    dayViews: [],
    motivationOptions: ['想让身体慢下来', '改善日常饮食节奏', '和家人一起体验', '朋友推荐'],
    todayDay: null,
    registration: {}, draft: null, availability: {}, isRegistered: false, canReview: false, expandedDay: '', reviewNote: '', reviewSaved: false, addingPerson: false, personName: '', personPhone: '', personRelation: '', personMinor: false
  },

  onLoad(options) {
    options = options || {}
    let scene = ''
    if (options.scene) {
      try { scene = decodeURIComponent(options.scene) } catch (error) { scene = '' }
      if (!/^[a-f0-9]{32}$/i.test(scene)) scene = ''
    }
    this.setData({ routeActivityId: options.id || '', invitationCode: options.invite || scene })
    if (options.id && demo.activities.some(item => item.id === options.id)) store.updateState(state => { state.selectedActivityId = options.id; return state })
    this.setData({ view: options.view || 'detail' })
  },

  async onShow() {
    await this.refresh()
    if (this.data.view !== 'register' || this.data.loadError) return
    if (['pending', 'confirmed', 'completed', 'waitlisted', 'waitlist'].includes(this.data.registration.status)) {
      this.setData({ view: 'journey' })
      return
    }
    if (!require('../../utils/discovery').activities([this.data.activity])[0].canRegister) {
      this.setData({ view: 'detail' })
      return
    }
    return this.startRegistration()
  },

  async refresh() {
    if (business.enabled()) {
      this.setData({ loading: true, loadError: '' })
      try {
        let id = this.data.routeActivityId || null
        if (this.data.invitationCode && !this.data.invitationResolved) {
          const invitation = await business.resolveInvitation(this.data.invitationCode)
          if (this.data.routeActivityId && invitation.sessionId !== this.data.routeActivityId) throw new Error('邀请与活动不匹配')
          id = invitation.sessionId
          this.setData({ invitationResolved: true })
        }
        const context = await business.context(id)
        if (!context.activity) throw new Error('活动不存在或未发布')
        const { activity, state, overview } = context
        const registration = state.registrations[activity.id] || { activityId: activity.id, status: 'none', participants: [], serviceConsent: false }
        const self = registration.participants.find(person => person.id === 'person-self')
        const today = domain.localDate()
        const todayDay = activity.days.find(day => String(day.activityDate).slice(0,10) === today) || null
        const phase = registration.status === 'completed' || today > activity.endDate ? 'after' : today < activity.startDate ? 'before' : todayDay ? 'during' : ''
        const record = (overview.experienceRecords || []).find(record => self && record.registrationId === self.registrationId && record.phase === phase && (record.nodeKey || '') === (phase === 'during' ? todayDay.id : ''))
        const identityChanged = this.data.loadedCustomerId !== overview.customerId
        if (identityChanged) this.setData({ draft: null, sharePath: '', shareTitle: '', requestId: '', loadedCustomerId: overview.customerId })
        const draft = this.data.draft || { serviceConsent: false, participants: [{ id: 'person-self', name: state.profile.name, relation: '本人', minor: state.profile.minor, phone: state.phone || '', selected: true }] }
        const experienceTimeline = (overview.experienceRecords || []).filter(row => self && row.registrationId === self.registrationId).map(row => ({ ...row, label: row.phase === 'before' ? '活动前' : row.phase === 'after' ? '活动后' : ((activity.days.find(day => day.id === row.nodeKey) || {}).label || '活动期间') })).reverse()
        this.setData({ activity, state, registration, draft, overview, experienceTimeline, loading: false, routeActivityId: activity.id,
          selectedCount: (this.data.view === 'register' ? draft : registration).participants.filter(person => person.selected).length,
          isRegistered: registration.status !== 'none', availability: domain.activityState(activity), todayDay,
          dayViews: activity.days.map(day => ({ ...day, date: String(day.activityDate).slice(0,10), today: day.id === (todayDay && todayDay.id), checked: (overview.attendance || []).some(row => row.sessionDayId === day.id && ['checked_in','late'].includes(row.attendanceStatus)) })),
          canReview: !!self && ['confirmed','completed'].includes(registration.status) && phase === 'after', canWriteReview: !!self, canRecordFeeling: !!self && ['confirmed','completed'].includes(registration.status) && !!phase,
          feelingRegistrationId: self ? self.registrationId : '', feelingPhase: phase, feelingDayId: todayDay ? todayDay.id : '',
          energy: record ? record.energy : null, relaxation: record ? record.relaxation : null, feelingNote: record ? record.note : '', feelingSaved: !!record,
          reviewNote: record && phase === 'after' ? record.note : '', reviewSaved: !!record && phase === 'after',
          inviteActivities: context.activities.filter(item => item.id !== activity.id && domain.activityState(item).canRegister) })
      } catch (error) { this.setData({ loading: false, loadError: error.message }) }
      return
    }
    const state = store.getState()
    const activity = demo.activities.find((item) => item.id === state.selectedActivityId) || demo.activities[0]
    const registration = (state.registrations || {})[activity.id] || (state.registration.activityId === activity.id ? state.registration : { ...state.registration, status: 'none' })
    const draft = this.data.draft || JSON.parse(JSON.stringify({ ...state.registration, serviceConsent: false }))
    const selectedCount = (this.data.view === 'register' ? draft : registration).participants.filter(item => item.selected).length
    const today = domain.localDate()
    const dayViews = activity.days.map((day, index) => {
      const date = activity.startDate ? new Date(activity.startDate + 'T12:00:00') : null
      if (date) date.setDate(date.getDate() + index)
      const dateKey = date ? domain.localDate(date) : ''
      return { ...day, date: dateKey, checked: (state.registration.activityId === activity.id ? state.checkedDays : (state.attendanceByActivity || {})[activity.id] || []).includes(day.id), today: dateKey === today,
        selfConfirmed: !!(state.arrivalConfirmations || {})[activity.id + ':' + day.id] }
    })
    const review = (state.experienceReviews || {})[activity.id]
    const canReview = state.loggedIn && state.phoneLinked && registration.status !== 'none' && !['pending', 'waitlist', 'cancelled'].includes(registration.status) && (registration.status === 'completed' || !!activity.endDate && today > activity.endDate)
    this.setData({
      state,
      activity,
      selectedCount,
      registration, draft, canWriteReview: registration.participants.some(person => person.selected && person.id === 'person-self'), availability: domain.activityState(activity), isRegistered: registration.status !== 'none', canReview,
      dayViews, todayDay: dayViews.find(day => day.today) || null,
      reviewNote: review && registration.participants.some(person => person.selected && person.id === 'person-self') ? review.note : this.data.reviewNote, reviewSaved: !!review
    })
  },

  async startRegistration() {
    const state = this.data.state
    if (!domain.activityState(this.data.activity).canRegister) {
      return wx.showToast({ title: '请查看本期名额状态', icon: 'none' })
    }
    if (state.backend && !state.loggedIn) {
      try { await business.login(); await this.refresh(); if (this.data.loadError) return; return this.startRegistration() }
      catch (error) { return wx.showToast({ title: error.message, icon: 'none' }) }
    }
    if (!state.phoneLinked) {
      return navigation.navigateTo({ url: '/pages/mine-flow/index?view=auth&next=register' })
    }
    this.setData({ view: 'register', step: 1, selectedCount: this.data.draft.participants.filter(person => person.selected).length })
  },

  openJourney() {
    this.setData({ view: 'journey' })
  },

  toggleParticipant(event) {
    const draft = JSON.parse(JSON.stringify(this.data.draft))
    const person = draft.participants.find(item => item.id === event.currentTarget.dataset.id)
    if (person) person.selected = !person.selected
    this.setData({ draft, selectedCount: draft.participants.filter(item => item.selected).length })
  },
  chooseMotivation(event) {
    const value = event.currentTarget.dataset.value
    this.setData({ draft: { ...this.data.draft, motivation: value } })
  },

  toggleConsent() {
    this.setData({ draft: { ...this.data.draft, serviceConsent: !this.data.draft.serviceConsent } })
  },
  nextStep() {
    if (this.data.step === 1 && this.data.selectedCount < 1) {
      return wx.showToast({ title: '请至少选择一位参与者', icon: 'none' })
    }
    const state = this.data.state
    const missingPhone = this.data.draft.participants.some(person => person.selected && !person.minor && !/^1\d{10}$/.test(person.phone || (person.relation === '本人' ? state.phone : '')))
    if (missingPhone) return wx.showToast({ title: '请补充成年参与者本人手机号', icon: 'none' })
    this.setData({ step: Math.min(2, this.data.step + 1) })
  },

  previousStep() {
    this.setData({ step: Math.max(1, this.data.step - 1) })
  },

  async submitRegistration() {
    if (this.data.submitting) return
    if (business.enabled()) {
      const participants = this.data.draft.participants.filter(person => person.selected)
      if (!this.data.draft.serviceConsent || !participants.length) return wx.showToast({ title: '请确认参与人和必要授权', icon: 'none' })
      if (participants.some(person => !person.minor && !/^1\d{10}$/.test(person.phone || (person.id === 'person-self' ? this.data.state.phone : '')))) return wx.showToast({ title: '请填写成年参与者本人手机号', icon: 'none' })
      if (!this.data.requestId) this.setData({ requestId: 'registration-' + Date.now() + '-' + Math.random().toString(36).slice(2) })
      this.setData({ submitting: true })
      try {
        await business.register({ sessionId: this.data.activity.id, clientRequestId: this.data.requestId, invitationCode: this.data.invitationCode || null, serviceConsent: true, motivation: this.data.draft.motivation || null,
          participants: participants.map(person => ({ self: person.id === 'person-self', customerId: person.id === 'person-self' ? null : person.customerId || null, name: person.name, relation: person.relation, minor: !!person.minor, phone: person.phone || (person.id === 'person-self' ? this.data.state.phone : '') })) })
        this.setData({ view: 'journey' })
        await this.refresh()
        wx.showToast({ title: '报名已收到', icon: 'none' })
      } catch (error) { wx.showToast({ title: error.message, icon: 'none' }) }
      finally { this.setData({ submitting: false }) }
      return
    }
    if (!this.data.state.phoneLinked) return navigation.navigateTo({ url: '/pages/mine-flow/index?view=auth&next=register' })
    if (!this.data.draft.participants.some(person => person.selected)) return wx.showToast({ title: '请至少选择一位参与者', icon: 'none' })
    if (!this.data.draft.serviceConsent) {
      return wx.showToast({ title: '请先同意服务必要信息处理', icon: 'none' })
    }
    if (!domain.activityState(this.data.activity).canRegister) return wx.showToast({ title: '本期暂不能报名', icon: 'none' })
    const status = this.data.activity.status === '候补中' ? 'waitlist' : domain.registrationStatus(this.data.activity)
    store.updateState((state) => {
      state.registrations = state.registrations || {}
      if (state.registration.status !== 'none') state.registrations[state.registration.activityId] = JSON.parse(JSON.stringify(state.registration))
      state.attendanceByActivity = state.attendanceByActivity || {}
      state.attendanceByActivity[state.registration.activityId] = state.checkedDays.slice()
      if (state.registration.activityId !== this.data.activity.id) state.checkedDays = []
      state.registration = { ...JSON.parse(JSON.stringify(this.data.draft)), activityId: this.data.activity.id, status }
      state.registrations[this.data.activity.id] = JSON.parse(JSON.stringify(state.registration))
      if (status === 'confirmed') state.stage = 'journey'
      return state
    })
    this.setData({ view: 'journey' })
    wx.showToast({ title: status === 'pending' ? '报名已收到，等待确认' : status === 'waitlist' ? '候补申请已收到' : '报名已确认', icon: 'none' })
    this.refresh()
  },

  checkDay(event) {
    if (this.data.state.backend) return wx.showToast({ title: '到场状态由工作人员核验', icon: 'none' })
    const day = this.data.dayViews.find(item => item.id === event.currentTarget.dataset.id)
    if (!day || !day.today || this.data.registration.status !== 'confirmed') return wx.showToast({ title: '仅能确认当天到场', icon: 'none' })
    store.updateState(state => {
      state.arrivalConfirmations = state.arrivalConfirmations || {}
      state.arrivalConfirmations[this.data.activity.id + ':' + day.id] = true
      return state
    })
    this.refresh()
    wx.showToast({ title: '已记录，现场仍需核验', icon: 'none' })
  },
  toggleDay(event) { const id = event.currentTarget.dataset.id; this.setData({ expandedDay: this.data.expandedDay === id ? '' : id }) },
  onReviewInput(event) { this.setData({ reviewNote: event.detail.value }) },
  async saveReview() {
    if (business.enabled()) { this.setData({ feelingNote: this.data.reviewNote }); return this.saveFeeling() }
    if (!this.data.canReview) return
    if (!this.data.registration.participants.some(person => person.selected && person.id === 'person-self')) return wx.showToast({ title: '请由实际参与者保存自己的感受', icon: 'none' })
    store.updateState(state => { state.experienceReviews = state.experienceReviews || {}; state.experienceReviews[this.data.activity.id] = { note: this.data.reviewNote.trim(), savedAt: new Date().toISOString(), private: true }; return state })
    this.refresh()
    wx.showToast({ title: '感受已保存', icon: 'none' })
  },
  showAddPerson() { this.setData({ addingPerson: !this.data.addingPerson }) },
  onSelfPhone(event) { const draft = JSON.parse(JSON.stringify(this.data.draft)); const self = draft.participants.find(p => p.id === 'person-self'); if (self) self.phone = event.detail.value; this.setData({ draft }) },
  onSelfName(event) { const draft = JSON.parse(JSON.stringify(this.data.draft)); const self = draft.participants.find(p => p.id === 'person-self'); if (self) self.name = event.detail.value; this.setData({ draft }) },
  onPersonName(event) { this.setData({ personName: event.detail.value }) },
  onPersonPhone(event) { this.setData({ personPhone: event.detail.value }) },
  onPersonRelation(event) { this.setData({ personRelation: event.detail.value }) },
  toggleMinor() { this.setData({ personMinor: !this.data.personMinor }) },
  addPerson() {
    const name = this.data.personName.trim(), phone = this.data.personPhone.trim(), relation = this.data.personRelation.trim()
    if (!name || !relation || !this.data.personMinor && !/^1\d{10}$/.test(phone)) return wx.showToast({ title: '请补充姓名、关系及成人本人手机号', icon: 'none' })
    const draft = JSON.parse(JSON.stringify(this.data.draft))
    draft.participants.push({ id: 'person-' + Date.now(), name, phone: this.data.personMinor ? '' : phone, relation, minor: this.data.personMinor, selected: true })
    this.setData({ draft })
    this.setData({ addingPerson: false, personName: '', personPhone: '', personRelation: '', personMinor: false })
    this.refresh()
  },
  openFeeling() { this.setData({ view: 'feeling' }) },
  chooseScale(event) { const { field, value } = event.currentTarget.dataset; if (['energy','relaxation'].includes(field)) this.setData({ [field]: this.data[field] === Number(value) ? null : Number(value) }) },
  onFeelingInput(event) { this.setData({ feelingNote: event.detail.value }) },
  async saveFeeling() {
    if (this.data.feelingSaving || !this.data.canRecordFeeling) return
    this.setData({ feelingSaving: true })
    try {
      await business.saveExperience({ registrationId: this.data.feelingRegistrationId, phase: this.data.feelingPhase, sessionDayId: this.data.feelingPhase === 'during' ? this.data.feelingDayId : null, energy: this.data.energy, relaxation: this.data.relaxation, note: this.data.feelingNote })
      this.setData({ feelingSaved: true, reviewSaved: this.data.feelingPhase === 'after' })
      wx.showToast({ title: '感受已保存，仅自己可见', icon: 'none' })
    } catch (error) { wx.showToast({ title: error.message, icon: 'none' }) }
    finally { this.setData({ feelingSaving: false }) }
  },
  openInvite() { this.setData({ view: 'invite' }) },
  async prepareInvitation(event) {
    try { const result = await business.createInvitation(event.currentTarget.dataset.id); this.setData({ sharePath: result.path, shareTitle: (this.data.inviteActivities.find(item => item.id === event.currentTarget.dataset.id) || {}).name || '轻生活活动邀请' }); wx.showToast({ title: '邀请已准备好，请自主分享', icon: 'none' }) }
    catch (error) { wx.showToast({ title: error.message, icon: 'none' }) }
  },
  onShareAppMessage() { return { title: this.data.shareTitle || this.data.activity.name, path: this.data.sharePath || '/pages/camp-flow/index?view=detail&id=' + encodeURIComponent(this.data.activity.id) + (this.data.invitationCode ? '&invite=' + encodeURIComponent(this.data.invitationCode) : '') } },

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
    navigation.switchTab({ url: '/pages/camp/index' })
  }
})
