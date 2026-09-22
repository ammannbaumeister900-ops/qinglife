const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const vm = require('node:vm')

const authPath = path.join(__dirname, '../apps/admin/src/utils/auth.js')
const loginPath = path.join(__dirname, '../apps/admin/src/views/login.vue')
const authSource = fs.readFileSync(authPath, 'utf8')
  .replace(/export function /g, 'function ')
  .concat('\nglobalThis.auth = { getToken, setToken, removeToken }')

const values = new Map()
const sandbox = {
  window: {
    sessionStorage: {
      getItem: key => values.has(key) ? values.get(key) : null,
      setItem: (key, value) => values.set(key, String(value)),
      removeItem: key => values.delete(key)
    }
  }
}
vm.createContext(sandbox)
vm.runInContext(authSource, sandbox)

assert.equal(sandbox.auth.getToken(), null)
assert.equal(sandbox.auth.setToken('short-lived-token'), undefined)
assert.equal(sandbox.auth.getToken(), 'short-lived-token')
assert.equal(sandbox.auth.removeToken(), undefined)
assert.equal(sandbox.auth.getToken(), null)

const loginSource = fs.readFileSync(loginPath, 'utf8')
assert.doesNotMatch(loginSource, /Cookies\.(?:get|set)\(["']password["']/)
assert.doesNotMatch(loginSource, /rememberMe/)

console.log('Admin auth: session-scoped token storage and no remembered password cookie passed')
