const runtime = require('../config/runtime')
const store = require('../utils/store')
const BASE_KEY = 'qinglife_business_base_url'
const ENVIRONMENTS = ['demo', 'staging', 'production']
let loginPromise = null
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
function enabled() { return !!baseUrl() }
function tokenKey() { return 'qinglife_business_token:' + baseUrl() }
function ensureToken() {
  const token = wx.getStorageSync(tokenKey())
  if (token) return Promise.resolve(token)
  if (loginPromise) return loginPromise
  loginPromise = new Promise((resolve,reject) => wx.login({ success: result => {
    if (!result.code) return reject(new Error('微信登录未返回凭证'))
    request('/auth/login', 'POST', { code: result.code }, false).then(data => {
      if (!data || !data.token) throw new Error('登录未成功，请重试')
      wx.setStorageSync(tokenKey(),data.token); resolve(data.token)
    }).catch(reject)
  }, fail: reject })).finally(() => { loginPromise = null })
  return loginPromise
}
function request(path, method = 'GET', data, auth = true) {
  return Promise.resolve().then(async () => {
    const base = baseUrl()
    if (!base) throw new Error('业务接口尚未配置')
    const token = auth ? await ensureToken() : ''
    return new Promise((resolve, reject) => wx.request({
      url: base + '/app/qinglife' + path, method, data, timeout: 10000,
      header: { 'content-type': 'application/json', ...(token ? { token } : {}) },
      success(res) {
        if (res.statusCode >= 200 && res.statusCode < 300 && res.data && res.data.code === 200) resolve(res.data.data)
        else { const error = new Error(res.data && (res.data.msg || res.data.message) || '服务暂时无法访问'); error.code = res.data && res.data.code || res.statusCode; if (auth && error.code === 401) wx.removeStorageSync(tokenKey()); reject(error) }
      }, fail(error) { reject(new Error(error.errMsg || error.message || '网络暂时无法连接，请重试')) }
    }))
  })
}
function activity(raw) {
  const statuses = { open: '开放报名', in_progress: '进行中', completed: '已结束', cancelled: '已取消', closed: '已关闭', draft: '未发布' }
  const seats = Math.max(0, Number(raw.capacity) - Number(raw.registeredCount || 0))
  return { ...raw, date: String(raw.startDate).slice(0,10) + '—' + String(raw.endDate).slice(0,10), startDate: String(raw.startDate).slice(0,10), endDate: String(raw.endDate).slice(0,10), status: raw.status === 'open' && !seats ? '已满' : statuses[raw.status] || raw.status,
    statusTone: raw.status === 'open' ? seats ? 'open' : 'full' : raw.status === 'cancelled' ? 'cancelled' : 'planned', sourceStatus: raw.status,
    seatsLeft: seats, fee: raw.standardPrice, returningFee: raw.returningPrice, place: raw.venue || '', leader: raw.leaderName || '', theme: raw.theme || raw.name, cover: raw.coverUrl || '',
    suitedFor: [], talkFirst: ['如有身体或饮食方面的顾虑，请先与工作人员沟通。'],
    rules: { change: raw.cancelPolicy || '改期与取消请联系工作人员确认。', body: '出现不适可以暂停，并告知工作人员。', privacy: '个人感受仅自己可见，不会随邀请分享。' },
    days: (raw.days || []).map(day => ({ ...day, label: '第' + day.dayNo + '天', time: [day.startTime,day.endTime].filter(Boolean).join('—') })) }
}
async function sessions() { return (await request('/sessions', 'GET', null, false)).map(activity) }
function normalizeHabit(raw) {
  return raw ? { ...raw, paused: raw.status === 'paused', completedDays: raw.completedDays || [] }
    : { id: '', planLength: 14, currentDay: 1, status: 'none', paused: false, completedDays: [], canRecordToday: false }
}
function applyHabit(raw) {
  const habit = normalizeHabit(raw)
  store.updateState(state => { state.backend = true; state.habit = habit; if (habit.id) state.stage = 'habit'; else if (state.stage === 'habit') state.stage = 'journey'; return state })
  return habit
}
async function refreshHabit() { return applyHabit((await request('/me/overview')).habit) }async function context(id) {
  const list = await sessions()
  const selected = id ? activity(await request('/sessions/' + encodeURIComponent(id), 'GET', null, false)) : list[0] || null
  if (selected && !list.some(item => item.id === selected.id)) list.push(selected)
  const token = wx.getStorageSync(tokenKey())
  const overview = token ? await request('/me/overview') : { registrations: [], attendance: [], experienceRecords: [], passAccounts: [], passLedger: [] }
  const local = store.getState(), profile = overview.profile || {}
  for (const row of overview.registrations || []) {
    if (!list.some(item => item.id === row.sessionId)) list.push(activity(await request('/sessions/' + encodeURIComponent(row.sessionId), 'GET', null, false)))
  }
  const registrations = {}
  for (const row of overview.registrations || []) {
    const isSelf = row.isSelf === true || row.isSelf === 1 || row.isSelf === '1'
    const attended = row.participated === true || row.participated === 1 || row.participated === '1'
    const status = row.sessionStatus === 'completed' && row.registrationStatus === 'confirmed' && attended ? 'completed' : row.registrationStatus
    if (!registrations[row.sessionId]) { const account=(overview.passAccounts||[]).find(item=>item.id===row.passAccountId);registrations[row.sessionId] = { activityId: row.sessionId, status, participants: [], serviceConsent: true, quotedAmount: row.quotedAmount, finalAmount: row.finalAmount, settlementStatus: row.settlementStatus || 'pending', paymentStatus: row.orderPaymentStatus || row.paymentStatus, settlementType: row.settlementType, passUnits: row.passUnits, passAccountId:row.passAccountId, currentPassBalance:account?account.balance:null, contactName: row.contactName, contactPhone: row.contactPhone } }
    if (isSelf) registrations[row.sessionId].status = status
    registrations[row.sessionId].participants.push({ id: isSelf ? 'person-self' : row.customerId, customerId: row.customerId, registrationId: row.registrationId, name: row.participantName || (isSelf ? profile.name : '') || '参与者', relation: isSelf ? '本人' : row.relation || '同行', selected: true, minor: !!row.minor, status })
  }
  const self = { id: 'person-self', name: profile.name || '本人', relation: '本人', minor: !!profile.minor, phone: profile.phone || '', selected: true }
  const registration = registrations[id] || Object.values(registrations)[0] || { activityId: id || '', status: 'none', participants: [self], serviceConsent: false, contactName: profile.name || '', contactPhone: profile.phone || '' }
  const backendHabit = normalizeHabit(overview.habit)
  const passAccounts=overview.passAccounts||[],passLedger=overview.passLedger||[];const currentPassBalance=passAccounts.filter(item=>Number(item.usable)).reduce((sum,item)=>sum+Number(item.balance||0),0)
  const state = { ...local, backend: true, returningEligible: !!overview.returningEligible, invitationEligible: !!overview.invitationEligible, phone: profile.phone || '', loggedIn: !!overview.customerId, phoneLinked: !!overview.customerId, profile: { name: profile.name || '轻友', nickname: profile.name || '轻友', minor: !!profile.minor }, registrations, registration, passAccounts, passLedger, currentPassBalance, habit: backendHabit, checkedDays: [], dailyRecords: {}, experienceReviews: {}, arrivalConfirmations: {}, stage: overview.habit ? 'habit' : 'journey' }
  return { activities: list, activity: selected, overview, state }
}
module.exports = { normalizeHabit, applyHabit, refreshHabit, BASE_KEY, baseUrl, enabled, request, activity, sessions, context, login: ensureToken,
  featuredReadings: () => request('/readings/featured', 'GET', null, false),
  reading: id => request('/readings/' + encodeURIComponent(id), 'GET', null, false),
  contact: () => request('/contact', 'GET', null, false),
  resolveInvitation: code => request('/invitations/' + encodeURIComponent(code), 'GET', null, false),
  register: data => request('/registrations', 'POST', data),
  saveExperience: data => request('/experience-records', 'PUT', data),
  saveDailyRecord: data => request('/daily-records/today', 'PUT', data),
  startHabit: data => request('/habits', 'POST', data),
  changeHabit: (id, paused) => request('/habits/' + encodeURIComponent(id) + (paused ? '/pause' : '/resume'), 'PUT', {}),
  createInvitation: id => request('/sessions/' + encodeURIComponent(id) + '/invitations', 'POST', {}) }
