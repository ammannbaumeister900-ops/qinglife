const runtime = require('../config/runtime')
const store = require('../utils/store')
const BASE_KEY = 'qinglife_business_base_url'
const ENVIRONMENTS = ['demo', 'staging', 'production']
let loginPromise = null
let logoutPromise = null
let identity = null
let identityRevision = 0
const identityListeners = new Set()
function isDemo() { return runtime.environment === 'demo' }
// Credentials stay inside the service; pages keep this opaque reference off setData.
function identityScope() {
  const base = baseUrl(), token = wx.getStorageSync('qinglife_business_token:' + base) || ''
  if (!identity || identity.base !== base || identity.token !== token) {
    identity = { base, token, revision: ++identityRevision, customerId: '' }
    identityListeners.forEach(listener => listener())
  }
  return identity
}
function identityError() {
  const error = new Error('身份已变化，请重新读取本人记录')
  error.code = 'IDENTITY_CHANGED'
  return error
}
function assertIdentity(scope) {
  if (!scope || scope !== identityScope()) throw identityError()
}
function watchIdentity(listener) {
  identityListeners.add(listener)
  return () => identityListeners.delete(listener)
}
async function privateOverview(scope = identityScope()) {
  assertIdentity(scope)
  if (!scope.token) throw new Error('请先通过微信登录，再读取本人记录')
  const overview = await request('/me/overview', 'GET', undefined, true, 10000, scope)
  assertIdentity(scope)
  if (!overview || !overview.customerId) {
    wx.removeStorageSync('qinglife_business_token:' + scope.base)
    identityScope()
    throw new Error('本人身份未确认，请重新微信登录')
  }
  if (scope.customerId && scope.customerId !== overview.customerId) {
    identity = { ...scope, customerId: overview.customerId, revision: ++identityRevision }
    identityListeners.forEach(listener => listener())
    throw identityError()
  }
  scope.customerId = overview.customerId
  return { scope, overview }
}
function baseUrl() {
  if (!ENVIRONMENTS.includes(runtime.environment)) throw new Error('小程序运行环境无效')
  const override = runtime.environment === 'demo' && typeof wx !== 'undefined' && wx.getStorageSync
    ? wx.getStorageSync(BASE_KEY) : ''
  const value = String(override || runtime.businessBaseUrl || '').replace(/\/$/, '')
  if (!value) {
    if (runtime.environment === 'production') throw new Error('生产业务接口尚未配置')
    return ''
  }
  const https = /^https:\/\/[^/]+(?:\/[^?#]*)?$/.test(value)
  const loopback = /^http:\/\/127\.0\.0\.1:\d+$/.test(value)
  if (!https && !(runtime.environment === 'demo' && loopback)) throw new Error('业务接口地址无效')
  if (runtime.environment === 'production') {
    const origin = value.match(/^https:\/\/[^/]+/)[0]
    if (!Array.isArray(runtime.allowedBusinessOrigins) || !runtime.allowedBusinessOrigins.includes(origin)) {
      throw new Error('生产业务接口不在允许源站中')
    }
  }
  return value
}
function enabled() {
  const base = baseUrl()
  if (!base && !isDemo()) throw new Error('业务接口尚未配置')
  return !!base
}
function tokenKey() { return 'qinglife_business_token:' + baseUrl() }
function ensureToken() {
  const scope = identityScope()
  if (!scope.base) return Promise.reject(new Error('业务接口尚未配置'))
  if (scope.token) return Promise.resolve(scope.token)
  if (loginPromise && loginPromise.scope === scope) return loginPromise.promise
  const attempt = { scope }
  attempt.promise = new Promise((resolve, reject) => wx.login({ success: result => {
    if (!result.code) return reject(new Error('微信登录未返回凭证'))
    request('/auth/login', 'POST', { code: result.code }, false, 10000, scope).then(data => {
      assertIdentity(scope)
      if (!data || !data.token) throw new Error('登录未成功，请重试')
      wx.setStorageSync('qinglife_business_token:' + scope.base, data.token)
      identityScope()
      resolve(data.token)
    }).catch(error => { try { assertIdentity(scope); reject(error) } catch (changed) { reject(changed) } })
  }, fail: error => { try { assertIdentity(scope); reject(error) } catch (changed) { reject(changed) } } })).finally(() => { if (loginPromise === attempt) loginPromise = null })
  loginPromise = attempt
  return attempt.promise
}
function logout() {
  if (logoutPromise) return logoutPromise
  const scope = identityScope()
  logoutPromise = (async () => {
    try {
      if (scope.token) await request('/auth/logout', 'POST', {}, true, 10000, scope)
    } catch (error) { if (error.code !== 401) throw error }
    finally {
      if (scope === identityScope()) {
        wx.removeStorageSync('qinglife_business_token:' + scope.base)
        loginPromise = null
        // Invalidate even an empty scope so an outstanding wx.login cannot restore it.
        identity = null
        identityScope()
      }
    }
  })().finally(() => { logoutPromise = null })
  return logoutPromise
}
function request(path, method = 'GET', data, auth = true, timeout = 10000, expectedScope) {
  return Promise.resolve().then(async () => {
    if (expectedScope) assertIdentity(expectedScope)
    const base = baseUrl()
    if (!base) throw new Error('业务接口尚未配置')
    const token = auth ? await ensureToken() : ''
    const scope = auth ? identityScope() : null
    if (auth && (scope.base !== base || scope.token !== token)) throw identityError()
    if (expectedScope) assertIdentity(expectedScope)
    return new Promise((resolve, reject) => wx.request({
      url: base + '/app/qinglife' + path, method, data, timeout,
      header: { 'content-type': 'application/json', ...(token ? { token } : {}) },
      success(res) {
        try {
          if (auth) assertIdentity(scope)
          if (res.statusCode >= 200 && res.statusCode < 300 && res.data && res.data.code === 200) return resolve(res.data.data)
          const error = new Error(res.data && (res.data.msg || res.data.message) || '服务暂时无法访问')
          error.code = res.data && res.data.code || res.statusCode
          if (auth && (error.code === 401 || res.statusCode === 401)) {
            wx.removeStorageSync('qinglife_business_token:' + base)
            identityScope()
          }
          reject(error)
        } catch (error) { reject(error) }
      }, fail(error) {
        try { if (auth) assertIdentity(scope) } catch (changed) { return reject(changed) }
        reject(new Error(error.errMsg || error.message || '网络暂时无法连接，请重试'))
      }
    }))
  })
}
const sessionState = require('../utils/session-state')
function activity(raw) {
  const statuses = { open: '开放报名', in_progress: '进行中', completed: '已结束', cancelled: '已取消', closed: '已关闭', draft: '未发布' }
  const seats = Math.max(0, Number(raw.capacity) - Number(raw.registeredCount || 0))
  return { ...raw, date: String(raw.startDate).slice(0,10) + '—' + String(raw.endDate).slice(0,10), startDate: String(raw.startDate).slice(0,10), endDate: String(raw.endDate).slice(0,10), status: raw.status === 'open' && !seats ? '已满' : statuses[raw.status] || raw.status,
    statusTone: raw.status === 'open' ? seats ? 'open' : 'full' : raw.status === 'cancelled' ? 'cancelled' : 'planned', sourceStatus: raw.status,
    seatsLeft: seats, fee: raw.standardPrice, returningFee: raw.returningPrice, place: raw.venue || '', leader: raw.leaderName || '', theme: raw.theme || raw.name, cover: raw.coverUrl || '',
    suitedFor: [], talkFirst: ['如有身体或饮食方面的顾虑，请先与工作人员沟通。'],
    rules: { change: raw.cancelPolicy || '改期与取消请联系工作人员确认。', body: '出现不适可以暂停，并告知工作人员。', privacy: '个人感受仅自己可见，不会随邀请分享。' },
    days: (raw.days || []).map(day => ({ ...day, label: '第' + day.dayNo + '天', time: [day.startTime,day.endTime].filter(Boolean).join('—') })),
    publicListed: false, ...sessionState.fromApi(raw) }
}
async function sessions() { return (await request('/sessions', 'GET', null, false)).map(raw => ({ ...activity(raw), publicListed: true })) }
function normalizeHabit(raw) {
  return raw ? { ...raw, paused: raw.status === 'paused', completedDays: raw.completedDays || [] }
    : { id: '', planLength: 14, currentDay: 1, status: 'none', paused: false, completedDays: [], canRecordToday: false }
}
function applyHabit(raw) {
  const habit = normalizeHabit(raw)
  store.updateState(state => { state.backend = true; state.habit = habit; if (habit.id) state.stage = 'habit'; else if (state.stage === 'habit') state.stage = 'journey'; return state })
  return habit
}
async function refreshHabit() {
  const result = await privateOverview()
  assertIdentity(result.scope)
  return applyHabit(result.overview.habit)
}
async function context(id, { allowGuest = false } = {}) {
  const list = await sessions()
  const selected = id ? activity(await request('/sessions/' + encodeURIComponent(id), 'GET', null, false)) : list[0] || null
  if (selected && !list.some(item => item.id === selected.id)) list.push(selected)
  const token = wx.getStorageSync(tokenKey())
  let scoped = null
  if (token) {
    try { scoped = await privateOverview() }
    catch (error) {
      // A public page can retain public content after an expired login. Private callers still fail.
      if (!allowGuest || error.code !== 401) throw error
    }
  }
  const overview = scoped ? scoped.overview : { registrations: [], attendance: [], experienceRecords: [], passAccounts: [], passLedger: [] }
  const local = store.getState(), profile = overview.profile || {}
  for (const row of overview.registrations || []) {
    if (!list.some(item => item.id === row.sessionId)) list.push(activity(await request('/sessions/' + encodeURIComponent(row.sessionId), 'GET', null, false)))
  }
  const registrations = {}
  const history = require('../utils/registration-history')
  const currentRows = history.latest(overview.registrations || [])
  for (const row of currentRows) {
    const isSelf = row.isSelf === true || row.isSelf === 1 || row.isSelf === '1'
    const attended = row.participated === true || row.participated === 1 || row.participated === '1'
    const status = row.sessionStatus === 'completed' && row.registrationStatus === 'confirmed' && attended ? 'completed' : row.registrationStatus
    if (!registrations[row.sessionId]) { const account=(overview.passAccounts||[]).find(item=>item.id===row.passAccountId);registrations[row.sessionId] = { activityId: row.sessionId, status, participants: [], serviceConsent: true, quotedAmount: row.quotedAmount, finalAmount: row.finalAmount, settlementStatus: row.settlementStatus || 'pending', paymentStatus: row.orderPaymentStatus || row.paymentStatus, settlementType: row.settlementType, passUnits: row.passUnits, passAccountId:row.passAccountId, currentPassBalance:account?account.balance:null, contactName: row.contactName, contactPhone: row.contactPhone } }
    if (['confirmed', 'completed'].includes(status) && (row.orderPaymentStatus || row.paymentStatus) !== 'paid' && !(row.settlementType === 'pass' && row.settlementStatus === 'confirmed')) registrations[row.sessionId].operationalRisk = true
    if (isSelf) registrations[row.sessionId].status = status
    registrations[row.sessionId].participants.push({ id: isSelf ? 'person-self' : row.customerId, customerId: row.customerId, registrationId: row.registrationId, name: isSelf ? row.participantNameSnapshot || String(profile.nickname || '').trim() || '本人' : row.participantName || '参与者', relation: isSelf ? '本人' : row.relation || '同行', selected: true, activeRegistration: history.active(row), phone: row.participantPhone == null ? (isSelf ? profile.phone || '' : '') : row.participantPhone, minor: !!row.minor, status, unitPrice: row.unitPrice, paymentStatus: row.paymentStatus, settlementStatus: row.settlementStatus, settlementType: row.settlementType })
  }
  for (const registration of Object.values(registrations)) {
    registration.canReapply = history.canReapply(registration)
    const ids = new Set(currentRows.filter(row => row.sessionId === registration.activityId).map(row => row.batchId || row.registrationId).filter(Boolean))
    registration.mixedOrders = ids.size > 1 || new Set(registration.participants.map(person => person.status)).size > 1
  }
  const self = { id: 'person-self', name: String(profile.nickname || '').trim(), relation: '本人', minor: !!profile.minor, phone: profile.phone || '', selected: true }
  const registration = registrations[id] || Object.values(registrations)[0] || { activityId: id || '', status: 'none', participants: [self], serviceConsent: false, contactName: String(profile.nickname || '').trim(), contactPhone: profile.phone || '' }
  const backendHabit = normalizeHabit(overview.habit)
  const passAccounts=overview.passAccounts||[],passLedger=overview.passLedger||[];const currentPassBalance=passAccounts.filter(item=>Number(item.usable)).reduce((sum,item)=>sum+Number(item.balance||0),0)
  const state = { ...local, backend: true, customerId: overview.customerId || '', returningEligible: !!overview.returningEligible, invitationEligible: !!overview.invitationEligible, phone: profile.phone || '', loggedIn: !!overview.customerId, phoneLinked: !!overview.customerId, profile: { name: profile.name || '轻友', nickname: String(profile.nickname || '').trim(), minor: !!profile.minor }, registrations, registration, passAccounts, passLedger, currentPassBalance, habit: backendHabit, checkedDays: [], dailyRecords: {}, experienceReviews: {}, arrivalConfirmations: {}, stage: overview.habit ? 'habit' : 'journey' }
  if (scoped) assertIdentity(scoped.scope)
  return { activities: list, activity: selected, overview, state }
}
module.exports = { isDemo, identityScope, assertIdentity, watchIdentity, privateOverview, normalizeHabit, applyHabit, refreshHabit, BASE_KEY, baseUrl, enabled, request, activity, sessions, context, login: ensureToken, logout,
  featuredReadings: () => request('/readings/featured', 'GET', null, false),
  articlePlacement: key => request('/content-placements/' + encodeURIComponent(key), 'GET', null, false),
  readings: (filters = {}) => request('/readings', 'GET', filters, false),
  readingTopics: () => request('/readings/topics', 'GET', null, false),
  homeReadings: () => request('/readings/home', 'GET', null, false),
  reading: id => request('/readings/' + encodeURIComponent(id), 'GET', null, false),
  contact: () => request('/contact', 'GET', null, false),
  resolveInvitation: code => request('/invitations/' + encodeURIComponent(code), 'GET', null, false),
  register: (data, scope) => request('/registrations', 'POST', data, true, 10000, scope),
  registrationQuote: (data, scope) => request('/registrations/quote', 'POST', data, true, 10000, scope),
  saveExperience: data => request('/experience-records', 'PUT', data),
  campVoices: id => request('/camp-voices/' + encodeURIComponent(id), 'GET', null, false),
  campVoicePage: (id, cursor = '') => request('/camp-voices/' + encodeURIComponent(id), 'GET', { pageSize: 16, ...(cursor ? { cursor } : {}) }, false),
  campVoice: (id, voiceId) => request('/camp-voices/' + encodeURIComponent(id), 'GET', { voiceId }, false),
  campReflection: (id, scope) => request('/reflections/' + encodeURIComponent(id), 'GET', undefined, true, 10000, scope),
  saveCampReflection: (data, scope) => request('/reflections', 'PUT', data, true, 10000, scope),
  withdrawCampReflection: (id, phase, scope) => request('/reflections/' + encodeURIComponent(id) + '/' + encodeURIComponent(phase) + '/share', 'DELETE', undefined, true, 10000, scope),
  transcribeReflection: (data, scope) => request('/reflections/voice', 'POST', data, true, 45000, scope),
  saveDailyRecord: (data, scope) => request('/daily-records/today', 'PUT', data, true, 10000, scope),
  startHabit: data => request('/habits', 'POST', data),
  changeHabit: (id, paused) => request('/habits/' + encodeURIComponent(id) + (paused ? '/pause' : '/resume'), 'PUT', {}),
  createInvitation: (id, scope) => request('/sessions/' + encodeURIComponent(id) + '/invitations', 'POST', {}, true, 10000, scope),
  myReferrals: () => request('/me/referrals'),
  assessmentInvitation: code => request('/friend/assessment-invitations/' + encodeURIComponent(code)),
  friendProfile: () => request('/friend/profile'),
  friendAssessments: () => request('/friend/assessments'),
  amendFriendAssessment: (id, data) => request('/friend/assessments/' + encodeURIComponent(id), 'PUT', data),
  submitFriendAssessment: data => request('/friend/assessments', 'POST', data) }
