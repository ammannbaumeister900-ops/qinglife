const assert = require('assert')
const fs = require('fs')
const path = require('path')
const vm = require('vm')
const { createRequire } = require('module')

const filename = path.resolve(__dirname, '../pages/camp-flow/index.js')
let page
vm.runInNewContext(fs.readFileSync(filename, 'utf8'), {
  require: createRequire(filename),
  Page: value => { page = value },
  wx: {}, Date, setTimeout, clearTimeout, decodeURIComponent
})
page.data = JSON.parse(JSON.stringify(page.data))
page.setData = values => Object.assign(page.data, values)

const code = '0123456789abcdef0123456789abcdef'
page.onLoad({ scene: encodeURIComponent(code), view: 'detail' })
assert.equal(page.data.invitationCode, code)
page.onLoad({ scene: encodeURIComponent('customer=private-data'), view: 'detail' })
assert.equal(page.data.invitationCode, '', 'scene only accepts an opaque invitation code')
console.log('invitation-scene.test.js OK: opaque scene restoration without customer data')
