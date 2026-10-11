const business = require('../../services/business-api')
const formFeedback = require('../../utils/form-feedback')
const demo = require('../../data/demo')
const store = require('../../utils/store')
const domain = require('../../utils/domain')
const navigation = require('../../utils/navigation')
const detailPresentation = require('../../utils/camp-detail-presentation')
const experiencePresentation = require('../../utils/experience-presentation')
const reflection = require('../../utils/reflection')
const registrationDraft = require('../../utils/registration-draft')
const registrationHistory = require('../../utils/registration-history')

function currentFeelingPhase(activity, status) {
  const now = new Date(), today = domain.localDate(now)
  const time = [now.getHours(), now.getMinutes(), now.getSeconds()].map(value => String(value).padStart(2, '0')).join(':')
  const days = activity.days || [], first = days[0] || {}, last = days[days.length - 1] || {}
  if (today === activity.startDate && (!first.startTime || time < first.startTime)) return 'before'
  if (status === 'completed' || today > activity.endDate || today === activity.endDate && last.endTime && time >= last.endTime) return 'after'
  return ''
}

Page({
  data: {
    view: 'detail', loading: false, loadError: '', submitting: false, feelingSaving: false, invitationCode: '', routeActivityId: '', sharePath: '', preparedInviteId: '', requestId: '', scales: [1,2,3,4,5], feelingPhase: '', feelingDayId: '', energy: null, relaxation: null, feelingNote: '', feelingLength: 0, feelingSaved: false, feelingError: '', inviteActivities: [],
    step: 1, formError: null, submittedServiceStatus: null, submittedParticipantCount: 0,
    relationOptions: registrationDraft.relations, contactOverrides: { name: false, phone: false },
    quote: { ready: false, rows: [], total: '', error: '' }, quoteLoading: false,
    reflectionAccess: { allowed: false, reason: '正在读取心声资格', retry: false }, reflectionOpening: false,
    experienceTimeline: [], reviewScope: '', reviewEditing: false, reviewDirty: false, reviewSavedNote: '', reviewHasAfter: false, reviewError: '', reviewExpanded: {},
    detail: {}, detailExpanded: false, detailActivityId: '', detailFailedImages: {},
    activity: demo.activities[0],
    state: {},
    selectedCount: 1,
    allChecked: false,
    dayViews: [],
    motivationOptions: ['想让身体慢下来', '改善日常饮食节奏', '和家人一起体验', '朋友推荐'],
    todayDay: null,
    registration: {}, serviceStatus: {}, draft: null, availability: {}, isRegistered: false, canReview: false, expandedDay: '', reviewNote: '', reviewSaved: false, addingPerson: false, personName: '', personPhone: '', personRelation: '', personMinor: false
  },

  onLoad(options) {
    options = options || {}
    let scene = ''
    if (options.scene) {
      try { scene = decodeURIComponent(options.scene) } catch (error) { scene = '' }
      if (!/^[a-f0-9]{32}$/i.test(scene)) scene = ''
    }
    this.setData({ routeActivityId: options.id || '', linkedRegistrationId: options.registrationId || '', invitationCode: options.invite || scene })
    if (options.id && demo.activities.some(item => item.id === options.id)) store.updateState(state => { state.selectedActivityId = options.id; return state })
    this.setData({ view: options.view || 'detail' })
    if (business.watchIdentity) this.unwatchInvitation = business.watchIdentity(() => {
      this.clearInvitation(); this.invalidateQuote()
      this.setData({ draft: null, submittedServiceStatus: null, submittedParticipantCount: 0, loadedCustomerId: '', requestId: '', contactOverrides: { name: false, phone: false } })
    })
  },

  async onShow() {
    await this.refresh()
    if (this.data.view !== 'register' || this.data.loadError) return
    if (domain.blocksRegistration(this.data.registration)) {
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
    this.clearInvitation()
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
        const version = this.invitationVersion
        const registration = state.registrations[activity.id] || { activityId: activity.id, status: 'none', participants: [], serviceConsent: false }
        const self = registration.participants.find(person => person.id === 'person-self')
        const scope = business.identityScope ? business.identityScope() : null
        const reflectionAccess = await reflection.readEntry(() => business.campReflection(activity.id, scope), registration, activity)
        if (business.assertIdentity) business.assertIdentity(scope)
        if (version !== this.invitationVersion) return
        const today = domain.localDate()
        const todayDay = activity.days.find(day => String(day.activityDate).slice(0,10) === today) || null
        const phase = currentFeelingPhase(activity, registration.status)
        const record = (overview.experienceRecords || []).find(record => self && record.registrationId === self.registrationId && record.phase === phase && !(record.nodeKey || ''))
        const identityChanged = this.data.loadedCustomerId !== overview.customerId
        if (identityChanged) { this.invalidateQuote(); this.setData({ contactOverrides: { name: false, phone: false }, draft: null, sharePath: '', preparedInviteId: '', shareTitle: '', requestId: '', submittedServiceStatus: null, submittedParticipantCount: 0, loadedCustomerId: overview.customerId }) }
        const registrationKey = JSON.stringify(registration.participants.map(p => [p.registrationId, p.status, p.activeRegistration]))
        if (this.loadedRegistrationKey != null && this.loadedRegistrationKey !== registrationKey) {
          this.invalidateQuote(); this.setData({ draft: null, requestId: '', step: 1 })
        }
        this.loadedRegistrationKey = registrationKey
        let draft = this.data.draft || registrationHistory.draft(registration, state)
        const draftSelf = draft.participants.find(person => person.id === 'person-self')
        if (draftSelf) {
          const previous = this.data.state || {}
          if (!previous.profile || previous.profile.nickname !== state.profile.nickname) draftSelf.name = state.profile.nickname || ''
          if (previous.phone !== state.phone) draftSelf.phone = state.phone || ''
          draft = registrationDraft.syncContacts(draft, this.data.contactOverrides)
        }
        if (this.quoteKey && this.quoteKey !== registrationDraft.key(activity.id, draft, state)) this.invalidateQuote()
        const experienceTimeline = (overview.experienceRecords || []).filter(row => self && row.registrationId === self.registrationId).map(row => ({ ...row, label: row.phase === 'before' ? '活动前' : row.phase === 'after' ? '活动后' : ((activity.days.find(day => day.id === row.nodeKey) || {}).label || '活动期间') })).reverse()
        const selfConfirmed = reflection.activeSelf(registration) && ['confirmed','completed'].includes(self.status || registration.status)
        const canReview = selfConfirmed && phase === 'after'
        const reviewPatch = this.data.view === 'experience' ? experiencePresentation.refresh(this.data, JSON.stringify([overview.customerId, activity.id, self ? self.registrationId : '']), experienceTimeline, canReview) : {}
        this.setData({ activity, state, registration, draft, reflectionAccess, serviceStatus: domain.campServiceStatus(registration), overview, experienceTimeline, loading: false, routeActivityId: activity.id,
          selectedCount: (this.data.view === 'register' ? draft : registration).participants.filter(person => person.selected).length,
          isRegistered: domain.hasActiveRegistration(registration), hasSelfActiveRegistration: reflection.activeSelf(registration), canReapply: registrationHistory.canReapply(registration),
          cancelledHistory: (overview.registrations || []).filter(row => row.sessionId === activity.id && row.registrationStatus === 'cancelled').map(row => ({ ...row, reference: String(row.registrationId).slice(-8), linked: row.registrationId === this.data.linkedRegistrationId })).sort((a,b) => Number(b.linked) - Number(a.linked)), availability: domain.activityState(activity), todayDay,
          dayViews: activity.days.map(day => ({ ...day, date: String(day.activityDate).slice(0,10), today: day.id === (todayDay && todayDay.id), checked: (overview.attendance || []).some(row => self && row.registrationId === self.registrationId && row.sessionDayId === day.id && ['checked_in','late'].includes(row.attendanceStatus)) })),
          canReview, canWriteReview: !!self, canRecordFeeling: selfConfirmed && !!phase,
          feelingRegistrationId: self ? self.registrationId : '', feelingPhase: phase, feelingDayId: '',
          energy: record ? record.energy : null, relaxation: record ? record.relaxation : null, feelingNote: record ? record.note : '', feelingLength: Array.from(record && record.note || '').length, feelingSaved: !!record,
          reviewNote: record && phase === 'after' ? record.note : '', reviewSaved: !!record && phase === 'after',
          inviteActivities: context.activities.filter(item => item.id !== activity.id && domain.activityState(item).canRegister),
          ...this.detailPresentationPatch(activity, registration), ...reviewPatch })
      } catch (error) { this.setData({ loading: false, loadError: error.message }) }
      return
    }
    const state = store.getState()
    const activity = demo.activities.find((item) => item.id === state.selectedActivityId) || demo.activities[0]
    const registration = (state.registrations || {})[activity.id] || (state.registration.activityId === activity.id ? state.registration : { ...state.registration, status: 'none' })
    const draft = this.data.draft || JSON.parse(JSON.stringify({ ...state.registration, serviceConsent: true, contactName: state.profile.nickname || '', participants: state.registration.participants.map(person => person.id === 'person-self' ? { ...person, name: state.profile.nickname || '' } : person) }))
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
    const canWriteReview = registration.participants.some(person => person.selected && person.id === 'person-self')
    const reviewPatch = this.data.view === 'experience' ? experiencePresentation.refresh(this.data, JSON.stringify(['demo', activity.id, canWriteReview ? 'person-self' : '']), review && canWriteReview ? [{ phase: 'after', nodeKey: '', label: '活动后', note: review.note, updatedAt: review.savedAt, visibility: 'private' }] : [], canReview && canWriteReview) : {}
    this.setData({
      state,
      activity,
      selectedCount,
      registration, draft, serviceStatus: domain.campServiceStatus(registration), canWriteReview, availability: domain.activityState(activity), isRegistered: registration.status !== 'none', canReview,
      dayViews, todayDay: dayViews.find(day => day.today) || null,
      reviewNote: review && registration.participants.some(person => person.selected && person.id === 'person-self') ? review.note : this.data.reviewNote, reviewSaved: !!review,
      ...this.detailPresentationPatch(activity, registration), ...reviewPatch
    })
  },

  detailPresentationPatch(activity, registration) {
    const id = String(activity.id || ''), changed = id !== this.data.detailActivityId
    const failures = changed ? {} : this.data.detailFailedImages
    return { detail: detailPresentation.present(activity, registration, failures), detailActivityId: id,
      detailExpanded: changed ? false : this.data.detailExpanded, detailFailedImages: failures }
  },

  toggleDetailSchedule() { this.setData({ detailExpanded: !this.data.detailExpanded }) },

  detailImageFailed(event) {
    const url = event.currentTarget.dataset.url
    if (this.data.view !== 'detail' || !url || ![this.data.detail.cover, this.data.detail.guideImage].includes(url)) return
    const failures = { ...this.data.detailFailedImages, [url]: true }
    this.setData({ detailFailedImages: failures, detail: detailPresentation.present(this.data.activity, this.data.registration, failures) })
  },

  previewDetailImage(event) {
    const url = event.currentTarget.dataset.url
    if (this.data.view !== 'detail' || !url || ![this.data.detail.cover, this.data.detail.guideImage].includes(url) || this.data.detailFailedImages[url]) return
    const fail = () => wx.showToast({ title: '图片暂时无法查看', icon: 'none' })
    const preview = current => wx.previewImage({ current, urls: [current], fail })
    if (url.startsWith('/assets/')) wx.getImageInfo({ src: url, success: result => preview(result.path), fail })
    else preview(url)
  },

  async startRegistration() {
    if (domain.blocksRegistration(this.data.registration)) return this.openJourney()
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
    formFeedback.changeStep(this, wx, { view: 'register', step: 1, selectedCount: this.data.draft.participants.filter(person => person.selected).length })
  },

  openJourney() {
    this.setData({ view: 'journey' })
  },

  toggleParticipant(event) { formFeedback.clearError(this);
    const draft = JSON.parse(JSON.stringify(this.data.draft))
    const person = draft.participants.find(item => item.id === event.currentTarget.dataset.id)
    if (person && person.existingActive) return
    if (person) person.selected = !person.selected
    this.invalidateQuote()
    this.setData({ draft: registrationDraft.syncContacts(draft, this.data.contactOverrides), selectedCount: draft.participants.filter(item => item.selected).length })
  },
  onParticipantInput(event) {
    const { index, field } = event.currentTarget.dataset
    const person = this.data.draft.participants[index]
    if (!person || person.id === 'person-self' || !['name', 'phone'].includes(field)) return
    formFeedback.clearError(this)
    const draft = JSON.parse(JSON.stringify(this.data.draft))
    draft.participants[index][field] = event.detail.value
    this.invalidateQuote()
    this.setData({ draft })
  },

  chooseMotivation(event) {
    const value = event.currentTarget.dataset.value
    this.setData({ draft: { ...this.data.draft, motivation: value } })
  },

  toggleConsent() {
    formFeedback.clearError(this)
    this.setData({ draft: { ...this.data.draft, serviceConsent: !this.data.draft.serviceConsent } })
  },
  registrationError(target, message, step = 1) {
    formFeedback.showError(this, wx, { target, message }, { step })
  },

  participantError(index, message, field = 'phone') {
    const person = this.data.draft.participants[index]
    const target = this.data.state.backend && person.id === 'person-self' ? (field === 'name' ? 'camp-self-name' : 'camp-self-phone') : 'camp-participant-' + index
    this.registrationError(target, '第' + (index + 1) + '位参与者（' + (person.name || person.relation || '未填写姓名') + '）：' + message)
  },

  async nextStep() {
    if (this.data.step === 1 && this.data.selectedCount < 1) return this.registrationError('camp-participants', '请至少选择一位参与者')
    if (!this.data.draft.contactName) return this.registrationError('camp-contact-name', '请填写主要联系人姓名')
    if (!/^1\d{10}$/.test(this.data.draft.contactPhone || '')) return this.registrationError('camp-contact-phone', '请填写正确的主要联系人手机号')
    const missingName = this.data.draft.participants.findIndex(person => person.selected && !String(person.name || '').trim())
    if (missingName >= 0) return this.participantError(missingName, '请填写参与者的小名或称呼', 'name')
    const invalidPhone = this.data.draft.participants.findIndex(person => person.selected && person.phone && !/^1\d{10}$/.test(person.phone))
    if (invalidPhone >= 0) return this.participantError(invalidPhone, '请检查选填的本人手机号')
    if (this.data.step < 2) formFeedback.changeStep(this, wx, { step: this.data.step + 1 })
    if (business.enabled()) await this.loadQuote()
  },

  previousStep() {
    if (this.data.step > 1) formFeedback.changeStep(this, wx, { step: this.data.step - 1 })
  },

  onHide() { this.invalidateQuote(); this.clearInvitation() },
  onUnload() { this.invalidateQuote(); this.clearInvitation(); if (this.unwatchInvitation) this.unwatchInvitation(); formFeedback.cancel(this) },

  async submitRegistration() {
    if (this.data.submitting) return
    if (business.enabled()) {
      const participants = this.data.draft.participants.filter(person => person.selected)
      if (!this.data.draft.serviceConsent) return this.registrationError('camp-service-consent', '请确认服务必要信息授权', 2)
      if (!participants.length) return this.registrationError('camp-participants', '请至少选择一位参与者')
      if (!this.data.draft.contactName) return this.registrationError('camp-contact-name', '请填写主要联系人姓名')
      if (!/^1\d{10}$/.test(this.data.draft.contactPhone || '')) return this.registrationError('camp-contact-phone', '请填写正确的主要联系人手机号')
      const all = this.data.draft.participants
      const phoneOf = person => String(person.phone || '').trim()
      const invalidPhone = all.findIndex(person => person.selected && phoneOf(person) && !/^1\d{10}$/.test(phoneOf(person)))
      if (invalidPhone >= 0) return this.participantError(invalidPhone, '请检查选填的本人手机号')
      const duplicate = all.findIndex((person, index) => person.selected && phoneOf(person) && all.slice(0, index).some(other => other.selected && phoneOf(other) === phoneOf(person)))
      if (duplicate >= 0) {
        const first = all.findIndex(person => person.selected && phoneOf(person) === phoneOf(all[duplicate]))
        return this.participantError(duplicate, '手机号与第' + (first + 1) + '位参与者（' + (all[first].name || all[first].relation || '未填写姓名') + '）重复，不能共用同一手机号')
      }
      const contactReuse = all.findIndex(person => person.selected && person.id !== 'person-self' && phoneOf(person) === this.data.draft.contactPhone.trim())
      if (contactReuse >= 0) return this.participantError(contactReuse, '请勿复制主要联系人手机号，没有本人号码可留空')
      if (!this.data.requestId) this.setData({ requestId: 'registration-' + Date.now() + '-' + Math.random().toString(36).slice(2) })
      formFeedback.clearError(this)
      this.setData({ submitting: true })
      const submissionVersion = this.invitationVersion
      try {
        const scope = business.identityScope ? business.identityScope() : null
        const reviewed = this.data.quote.ready ? JSON.stringify(this.data.quote) : ''
        const currentKey = registrationDraft.key(this.data.activity.id, this.data.draft, this.data.state)
        await this.loadQuote()
        if (business.assertIdentity) business.assertIdentity(scope)
        if (submissionVersion !== this.invitationVersion) return
        if (currentKey !== registrationDraft.key(this.data.activity.id, this.data.draft, this.data.state)) return
        if (!this.data.quote.ready) return this.registrationError('camp-submit-error', '价格暂时无法核对，请重试', 2)
        if (this.data.quote.ready && reviewed !== JSON.stringify(this.data.quote)) return this.registrationError('camp-submit-error', '参考价格已更新，请核对后再次提交', 2)
        const submitted = await business.register({ sessionId: this.data.activity.id, clientRequestId: this.data.requestId, invitationCode: this.data.invitationCode || null, serviceConsent: true, motivation: this.data.draft.motivation || null, contactName: this.data.draft.contactName.trim(), contactPhone: this.data.draft.contactPhone.trim(),
          participants: participants.map(person => ({ self: person.id === 'person-self', customerId: person.id === 'person-self' ? null : person.customerId || null, name: person.name, relation: person.relation, minor: !!person.minor, phone: person.phone || (person.id === 'person-self' ? this.data.state.phone : '') })) }, scope)
        if (business.assertIdentity) business.assertIdentity(scope)
        if (submissionVersion !== this.invitationVersion) return
        this.setData({ view: 'success', submittedParticipantCount: participants.length,
          submittedServiceStatus: submitted && submitted.registrationStatus && submitted.paymentStatus
            ? domain.campServiceStatus({ status: submitted.registrationStatus, paymentStatus: submitted.paymentStatus })
            : { registration: '状态待核对', registrationNote: '请查看安排，核对本次申请状态' } })
        await this.refresh()
        wx.showToast({ title: '报名已收到', icon: 'none' })
      } catch (error) { if (submissionVersion === this.invitationVersion) formFeedback.showError(this, wx, { target: 'camp-submit-error', message: error.message || '报名提交失败，请重试' }) }
      finally { this.setData({ submitting: false }) }
      return
    }
    if (!this.data.state.phoneLinked) return navigation.navigateTo({ url: '/pages/mine-flow/index?view=auth&next=register' })
    if (!this.data.draft.participants.some(person => person.selected)) return this.registrationError('camp-participants', '请至少选择一位参与者')
    if (!this.data.draft.serviceConsent) {
      return this.registrationError('camp-service-consent', '请先同意服务必要信息处理', 2)
    }
    if (!domain.activityState(this.data.activity).canRegister) return this.registrationError('camp-submit-error', '本期暂不能报名', this.data.step)
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
    formFeedback.clearError(this)
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
  onReviewInput(event) { this.setData({ reviewNote: event.detail.value, reviewSaved: false, reviewDirty: event.detail.value !== this.data.reviewSavedNote, reviewError: '' }) },
  editReview() {
    if (!this.data.canReview || !this.data.canWriteReview || this.data.loading || this.data.loadError) return
    if (business.enabled()) return this.openReflection()
    this.setData({ reviewEditing: true }, () => {
      if (wx.pageScrollTo) wx.pageScrollTo({ selector: '#experience-editor', offsetTop: -110, duration: 200 })
    })
  },
  closeReview() {
    if (this.data.feelingSaving) return
    this.setData({ reviewEditing: false })
    if (this.data.reviewDirty) wx.showToast({ title: '未保存的修改保留在本页', icon: 'none' })
  },
  toggleExperienceRecord(event) {
    const key = event.currentTarget.dataset.key
    if (!this.data.canReview || !(this.data.experienceTimeline || []).some(row => row.displayKey === key && row.hasLongNote)) return
    this.setData({ reviewExpanded: { ...this.data.reviewExpanded, [key]: !this.data.reviewExpanded[key] } })
  },
  async saveReview() {
    if (!this.data.canReview || !this.data.canWriteReview || this.data.feelingSaving || this.data.loading || this.data.loadError) return
    if (business.enabled()) return this.openReflection()
    if (!this.data.registration.participants.some(person => person.selected && person.id === 'person-self')) return wx.showToast({ title: '请由实际参与者保存自己的感受', icon: 'none' })
    store.updateState(state => { state.experienceReviews = state.experienceReviews || {}; state.experienceReviews[this.data.activity.id] = { note: this.data.reviewNote.trim(), savedAt: new Date().toISOString(), private: true }; return state })
    this.setData({ reviewDirty: false, reviewEditing: false, reviewSavedNote: this.data.reviewNote.trim() })
    this.refresh()
    wx.showToast({ title: '感受已保存', icon: 'none' })
  },
  showAddPerson() { this.setData({ addingPerson: !this.data.addingPerson }) },
  onSelfPhone(event) { this.updateSelf('phone', event.detail.value) },
  onSelfName(event) { this.updateSelf('name', event.detail.value) },
  onContactName(event) { formFeedback.clearError(this); this.setData({ contactOverrides: { ...this.data.contactOverrides, name: true }, draft: { ...this.data.draft, contactName: event.detail.value } }) },
  onContactPhone(event) { formFeedback.clearError(this); this.setData({ contactOverrides: { ...this.data.contactOverrides, phone: true }, draft: { ...this.data.draft, contactPhone: event.detail.value } }) },
  onPersonName(event) { formFeedback.clearError(this); this.setData({ personName: event.detail.value }) },
  onPersonPhone(event) { formFeedback.clearError(this); this.setData({ personPhone: event.detail.value }) },
  onPersonRelation(event) {
    const value = this.data.relationOptions[Number(event.detail.value)]
    if (!value) return
    formFeedback.clearError(this); this.setData({ personRelation: value })
  },
  onParticipantRelation(event) {
    const index = Number(event.currentTarget.dataset.index), relation = this.data.relationOptions[Number(event.detail.value)]
    const draft = JSON.parse(JSON.stringify(this.data.draft)), person = draft.participants[index]
    if (!person || person.id === 'person-self' || !relation) return
    if (relation === '本人') return wx.showToast({ title: '本人请在上方参与者中选择', icon: 'none' })
    person.relation = relation; this.invalidateQuote(); this.setData({ draft })
  },
  toggleMinor() { this.setData({ personMinor: !this.data.personMinor }) },
  addPerson() {
    const name = this.data.personName.trim(), phone = this.data.personPhone.trim(), relation = this.data.personRelation.trim()
    if (!name) return this.registrationError('camp-person-name', '请填写新增参与者的姓名或称呼')
    if (!registrationDraft.relations.includes(relation)) return this.registrationError('camp-person-relation', '请选择与参与者的关系')
    if (relation === '本人') return this.registrationError('camp-person-relation', '本人请在上方参与者中选择，不重复添加')
    if (phone && !/^1\d{10}$/.test(phone)) return this.registrationError('camp-person-phone', '请检查新增参与者选填的本人手机号')
    const draft = JSON.parse(JSON.stringify(this.data.draft))
    this.invalidateQuote()
    draft.participants.push({ id: 'person-' + Date.now(), name, phone, relation, minor: this.data.personMinor, selected: true })
    this.setData({ draft })
    formFeedback.clearError(this)
    this.setData({ addingPerson: false, personName: '', personPhone: '', personRelation: '', personMinor: false })
    this.refresh()
  },
  updateSelf(field, value) {
    formFeedback.clearError(this)
    const draft = JSON.parse(JSON.stringify(this.data.draft)), self = draft.participants.find(p => p.id === 'person-self')
    if (!self) return
    self[field] = value; this.invalidateQuote()
    this.setData({ draft: registrationDraft.syncContacts(draft, this.data.contactOverrides) })
  },
  invalidateQuote() {
    this.quoteVersion = (this.quoteVersion || 0) + 1; this.quoteKey = ''
    this.setData({ quoteLoading: false, quote: { ready: false, rows: [], total: '', error: '' } })
  },
  async loadQuote() {
    const version = (this.quoteVersion || 0) + 1; this.quoteVersion = version
    const key = registrationDraft.key(this.data.activity.id, this.data.draft, this.data.state)
    const scope = business.identityScope ? business.identityScope() : null
    this.setData({ quoteLoading: true, quote: { ready: false, rows: [], total: '', error: '' } })
    try {
      if (!business.registrationQuote) throw Error('价格暂时无法确认，请联系工作人员核对')
      const result = await business.registrationQuote({ sessionId: this.data.activity.id, participants: registrationDraft.participants(this.data.draft, this.data.state) }, scope)
      if (business.assertIdentity) business.assertIdentity(scope)
      if (version !== this.quoteVersion || key !== registrationDraft.key(this.data.activity.id, this.data.draft, this.data.state)) return
      const quote = registrationDraft.quote(result, this.data.draft.participants.filter(p => p.selected))
      this.quoteKey = key; this.setData({ quote, quoteLoading: false })
    } catch (error) {
      if (version === this.quoteVersion) this.setData({ quoteLoading: false, quote: { ready: false, rows: [], total: '', error: error.message || '价格暂时无法确认，请重试' } })
    }
  },
  async openReflection() {
    if (!reflection.activeSelf(this.data.registration) || this.data.reflectionOpening || this.data.loading || this.data.loadError) return
    if (!this.data.reflectionAccess.allowed) return wx.showToast({ title: this.data.reflectionAccess.reason, icon: 'none' })
    const activity = this.data.activity, registration = this.data.registration, version = this.invitationVersion
    const scope = business.identityScope ? business.identityScope() : null
    this.setData({ reflectionOpening: true })
    try {
      const access = await reflection.readEntry(() => business.campReflection(activity.id, scope), registration, activity)
      if (business.assertIdentity) business.assertIdentity(scope)
      if (version !== this.invitationVersion) return
      this.setData({ reflectionAccess: access })
      if (!access.allowed) return wx.showToast({ title: access.reason, icon: 'none' })
      navigation.navigateTo({ url: '/pages/camp-reflection/index?id=' + encodeURIComponent(activity.id) })
    } catch (error) { if (version === this.invitationVersion) wx.showToast({ title: error.message, icon: 'none' }) }
    finally { if (version === this.invitationVersion) this.setData({ reflectionOpening: false }) }
  },
  openFeeling() { if (business.enabled() && this.data.feelingPhase === 'after') return this.openReflection(); this.setData({ view: 'feeling' }) },
  chooseScale(event) { const { field, value } = event.currentTarget.dataset; if (['energy','relaxation'].includes(field)) this.setData({ [field]: this.data[field] === Number(value) ? null : Number(value) }) },
  onFeelingInput(event) { this.setData({ feelingNote: event.detail.value, feelingLength: Array.from(event.detail.value).length, feelingSaved: false, feelingError: '', reviewSaved: false }) },
  async saveFeeling() {
    if (this.data.feelingSaving || !this.data.canRecordFeeling) return
    if (business.enabled() && this.data.feelingPhase === 'after') return this.openReflection()
    const note = this.data.feelingNote, phase = this.data.feelingPhase
    if (Array.from(note || '').length > 150) return this.setData({ feelingError: '新保存的感受最多150字；历史原文不会截断' })
    const reviewing = this.data.view === 'experience', scope = this.data.reviewScope
    const customerId = this.data.loadedCustomerId, registrationId = this.data.feelingRegistrationId
    const after = reviewing && (this.data.experienceTimeline || []).find(experiencePresentation.isAfter)
    const existing = after || ((this.data.overview || {}).experienceRecords || []).find(row => row.registrationId === registrationId && row.phase === phase && !row.nodeKey)
    const payload = { registrationId, phase, sessionDayId: null, energy: existing ? experiencePresentation.score(existing.energy) : null, relaxation: existing ? experiencePresentation.score(existing.relaxation) : null, note }
    this.setData({ feelingSaving: true, feelingError: '' })
    try {
      await business.saveExperience(payload)
      if (registrationId !== this.data.feelingRegistrationId || customerId !== this.data.loadedCustomerId || reviewing && scope !== this.data.reviewScope) return
      this.setData({ feelingSaved: this.data.feelingNote === note, reviewSaved: phase === 'after' && this.data.reviewNote === note })
      const draftChanged = reviewing ? this.data.reviewNote !== note : this.data.feelingNote !== note
      if (reviewing) this.setData({ experienceTimeline: experiencePresentation.saved(this.data.experienceTimeline, payload), reviewHasAfter: true, reviewSavedNote: note, reviewDirty: draftChanged, reviewEditing: draftChanged, reviewError: '' })
      wx.showToast({ title: draftChanged ? '提交内容已保存，新修改未保存' : '感受已保存，仅自己可见', icon: 'none' })
    } catch (error) {
      if (!reviewing && customerId === this.data.loadedCustomerId && registrationId === this.data.feelingRegistrationId) this.setData({ feelingError: error.message || '暂时无法保存，请重试。' })
      if (reviewing && scope === this.data.reviewScope && customerId === this.data.loadedCustomerId) this.setData({ reviewError: error.message || '暂时无法保存，请重试。' })
      wx.showToast({ title: error.message || '暂时无法保存，请重试。', icon: 'none' })
    }
    finally { this.setData({ feelingSaving: false }) }
  },
  clearInvitation() {
    this.invitationVersion = (this.invitationVersion || 0) + 1
    this.setData({ sharePath: '', preparedInviteId: '', shareTitle: '', invitePreparing: false, reflectionAccess: { allowed: false, reason: '正在读取心声资格', retry: false }, reflectionOpening: false, state: { ...this.data.state, invitationEligible: false } })
  },
  canInvite() { return !this.data.loading && !this.data.loadError && this.data.state.invitationEligible === true && !this.data.state.profile?.minor },
  openInvite() { if (this.canInvite()) this.setData({ view: 'invite' }) },
  async prepareInvitation(event) {
    if (!this.canInvite() || this.data.invitePreparing) return
    const id = event.currentTarget.dataset.id, version = this.invitationVersion
    if (!(this.data.inviteActivities || []).some(item => item.id === id)) return
    const scope = business.identityScope ? business.identityScope() : null
    this.setData({ invitePreparing: true, sharePath: '' })
    try {
      if (business.privateOverview) {
        const { overview } = await business.privateOverview(scope)
        if (version !== this.invitationVersion) return
        if (overview.invitationEligible !== true || overview.profile?.minor) { this.clearInvitation(); return }
      }
      const result = await business.createInvitation(id, scope)
      if (business.assertIdentity) business.assertIdentity(scope)
      if (version !== this.invitationVersion || !this.canInvite()) return
      this.setData({ sharePath: result.path, preparedInviteId: id, shareTitle: this.data.inviteActivities.find(item => item.id === id).name || '轻生活活动邀请' })
      wx.showToast({ title: '邀请已准备好，请自主分享', icon: 'none' })
    } catch (error) { if (version === this.invitationVersion) wx.showToast({ title: error.message, icon: 'none' }) }
    finally { if (version === this.invitationVersion) this.setData({ invitePreparing: false }) }
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
