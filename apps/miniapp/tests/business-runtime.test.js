const assert = require('assert')
const path = require('path')

const runtimePath = path.resolve(__dirname, '../config/runtime.js')
const businessPath = path.resolve(__dirname, '../services/business-api.js')
const storePath = path.resolve(__dirname, '../utils/store.js')

function load(runtime, wx) {
  global.wx = wx
  delete require.cache[businessPath]
  delete require.cache[storePath]
  require.cache[runtimePath] = { id: runtimePath, filename: runtimePath, loaded: true, exports: runtime }
  return require(businessPath)
}

async function run() {
  const storage = new Map([['qinglife_business_base_url', 'https://attacker.example']])
  const wx = {
    getStorageSync: key => storage.get(key) || '',
    setStorageSync: (key, value) => storage.set(key, value),
    removeStorageSync: key => storage.delete(key)
  }
  let api = load({ environment: 'production', businessBaseUrl: 'https://api.example.test', allowedBusinessOrigins: ['https://api.example.test'] }, wx)
  assert.equal(api.baseUrl(), 'https://api.example.test', 'production ignores local storage origin overrides')

  api = load({ environment: 'production', businessBaseUrl: '', allowedBusinessOrigins: [] }, wx)
  assert.throws(() => api.baseUrl(), /生产业务接口尚未配置/)
  api = load({ environment: 'production', businessBaseUrl: 'http://api.example.test', allowedBusinessOrigins: ['http://api.example.test'] }, wx)
  assert.throws(() => api.baseUrl(), /业务接口地址无效/)

  storage.set('qinglife_business_base_url', 'http://127.0.0.1:18081')
  api = load({ environment: 'demo', businessBaseUrl: '', allowedBusinessOrigins: [] }, wx)
  assert.equal(api.baseUrl(), 'http://127.0.0.1:18081')

  storage.clear()
  let logins = 0
  const loginWx = {
    getStorageSync: key => storage.get(key) || '',
    setStorageSync: (key, value) => storage.set(key, value),
    removeStorageSync: key => storage.delete(key),
    login: options => { logins++; setTimeout(() => options.success({ code: 'synthetic-code' }), 5) },
    request: options => setTimeout(() => options.success({ statusCode: 200, data: { code: 200, data: { token: 'synthetic-token' } } }), 5)
  }
  api = load({ environment: 'production', businessBaseUrl: 'https://api.example.test', allowedBusinessOrigins: ['https://api.example.test'] }, loginWx)
  const tokens = await Promise.all(Array.from({ length: 8 }, () => api.login()))
  assert.deepEqual(new Set(tokens), new Set(['synthetic-token']))
  assert.equal(logins, 1, 'concurrent callers share one wx.login request')
  console.log('business-runtime.test.js OK: environment contract and shared login promise')
}

run().catch(error => { console.error(error); process.exitCode = 1 })
