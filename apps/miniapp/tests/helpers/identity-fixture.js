const fs = require('node:fs'), path = require('node:path'), vm = require('node:vm')
const root = path.resolve(__dirname, '../..')
const plain = value => JSON.parse(JSON.stringify(value))
const tick = () => new Promise(resolve => setImmediate(resolve))
function fixture(options = {}) {
  const runtime = { environment: 'staging', businessBaseUrl: 'https://isolated.invalid', allowedBusinessOrigins: ['https://isolated.invalid'], ...options.runtime }
  const storage = options.storage || new Map(), cache = new Map(), requests = [], toasts = [], nav = [], modals = []
  const records = options.records || { A: [], B: [] }
  let capture, intercept = null, loginCalls = 0
  const key = 'qinglife_business_token:' + runtime.businessBaseUrl
  if (!options.cold) storage.set(key, 'synthetic-A')
  const habit = actor => ({ id: 'habit-' + actor, sessionId: 'session-' + actor, status: 'active', planLength: 14, currentDay: 1,
    completedDays: [], serverDate: '2026-10-10', canRecordToday: true })
  const response = (o, data, code = 200) => o.success({ statusCode: code, data: { code, data, msg: code === 401 ? '身份失效' : '' } })
  const wx = {
    getStorageSync: k => storage.has(k) ? plain(storage.get(k)) : '',
    setStorageSync: (k, v) => storage.set(k, plain(v)), removeStorageSync: k => storage.delete(k),
    showToast: o => toasts.push(o.title), showModal: o => modals.push(o), hideLoading() {},
    redirectTo: o => nav.push(o.url), navigateTo: o => nav.push(o.url), switchTab: o => nav.push(o.url),
    login: o => { loginCalls++; o.success({ code: 'synthetic-code' }) },
    request(o) {
      const actor = String(o.header.token || '').replace('synthetic-', '')
      const route = o.url.split('/app/qinglife')[1]
      requests.push({ actor, route, method: o.method, data: o.data && plain(o.data) })
      if (intercept && intercept(o, actor, route) === false) return
      if (route === '/auth/login') return response(o, { token: 'synthetic-A' })
      if (route === '/me/overview') return response(o, { invitationEligible: options.invitationEligible === true, customerId: actor, profile: { nickname: actor, phone: actor === 'A' ? '19900000001' : '' }, habit: habit(actor), dailyRecords: plain(records[actor] || []) })
      if (route === '/daily-records/today' && o.method === 'PUT') {
        records[actor] = [{ ...o.data, id: 'record-' + actor, updatedAt: '2026-10-10 12:00:00' }]
        return response(o, null)
      }
      throw new Error('Unexpected intercepted route: ' + route)
    }
  }
  function load(file, fresh = false) {
    file = path.resolve(file); if (!path.extname(file)) file += '.js'
    if (file === path.join(root, 'config/runtime.js')) return runtime
    if (!fresh && cache.has(file)) return cache.get(file).exports
    const module = { exports: {} }; cache.set(file, module)
    const source = fs.readFileSync(file, 'utf8')
    vm.runInNewContext('(function(require,module,exports){' + source + '\n})', {
      wx, Page: p => { capture = p }, Date, Math, Promise, Set,
      setTimeout: fn => { fn(); return 1 }, clearTimeout() {}, getApp: () => ({ globalData: {} })
    })(id => id.startsWith('.') ? load(path.resolve(path.dirname(file), id)) : require(id), module, module.exports)
    return module.exports
  }
  const api = load(path.join(root, 'services/business-api.js'))
  const store = load(path.join(root, 'utils/store.js'))
  function page(name = 'friend-flow', view = 'daily', extra = {}) {
    load(path.join(root, 'pages', name, 'index.js'), true)
    const p = capture; p.data = plain(p.data); p.setData = patch => Object.assign(p.data, patch)
    p.onLoad({ view, ...extra }); return p
  }
  return { api, store, wx, page, records, requests, storage, key, nav, toasts, modals, habit, response,
    intercept: fn => { intercept = fn }, loginCalls: () => loginCalls,
    switchActor: actor => { if (actor) storage.set(key, 'synthetic-' + actor); else storage.delete(key); api.identityScope() } }
}
module.exports = { fixture, tick, plain }
