const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const vm = require('node:vm')
const axios = require(process.env.QINGLIFE_ADMIN_DEPENDENCIES ? path.join(process.env.QINGLIFE_ADMIN_DEPENDENCIES, 'axios') : '../apps/admin/node_modules/axios')
const source = fs.readFileSync(path.join(__dirname, '../apps/admin/src/utils/request.js'), 'utf8')
  .replace(/^import .*$/gm, '').replace('export default service', 'globalThis.service = service')

async function main() {
  const calls = []
  const sandbox = {
    axios, process: { env: { VUE_APP_BASE_API: '/test-api' } },
    getToken: () => 'synthetic-token', errorCode: {},
    console: { log() {} }, Message() {}, Notification: { error() {} },
    MessageBox: { confirm: () => Promise.reject(new Error('cancel')) },
    store: { dispatch: () => Promise.resolve() }
  }
  vm.createContext(sandbox)
  vm.runInContext(source, sandbox)
  const service = sandbox.service
  service.defaults.adapter = async config => {
    calls.push(config)
    return { data: { code: 200, ok: true }, status: 200, headers: {}, config }
  }
  await service.get('/life/customer/list', { params: { name: '轻友 A' } })
  assert.equal(calls[0].headers.Authorization, 'Bearer synthetic-token')
  assert.equal(calls[0].baseURL, '/test-api')
  assert.match(calls[0].url, /name=%E8%BD%BB%E5%8F%8B%20A/)
  await service.post('/login', {}, { headers: { isToken: false } })
  assert.equal(calls[1].headers.Authorization, undefined)
  for (const url of ['https://evil.invalid/a', '//evil.invalid/a', '/\\evil.invalid/a', ' /login', '/\n/evil.invalid/a']) {
    await assert.rejects(service.get(url), /接口地址不合法/)
  }
  await assert.rejects(service.get('/login', { baseURL: 'https://evil.invalid' }), /接口地址不合法/)
  assert.equal(calls.length, 2, 'blocked URLs must never reach the transport')
  const failure = new Error('synthetic request failure')
  service.interceptors.request.use(() => { throw failure })
  await assert.rejects(service.get('/getInfo'), error => error === failure)
  assert.equal(calls.length, 2)
  console.log('Admin request: trusted relative paths, token opt-out, blocked external URLs, and rejection propagation passed')
}
main().catch(error => { console.error(error); process.exitCode = 1 })