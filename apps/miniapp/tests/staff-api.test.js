const assert = require('assert')
const path = require('path')
const businessPath = path.join(__dirname, '../services/business-api.js')
const staffPath = path.join(__dirname, '../services/staff-api.js')

async function verify(name, base) {
  let request
  global.wx = {
    downloadFile: options => { request = options; options.success({ statusCode: 200, tempFilePath: '/tmp/' + name + '.jpg' }) },
    getFileSystemManager: () => ({ readFile: () => {} })
  }
  delete require.cache[require.resolve(staffPath)]
  require.cache[require.resolve(businessPath)] = {
    id: businessPath,
    filename: businessPath,
    loaded: true,
    exports: { baseUrl: () => base, login: async () => 'token:' + base, request: () => {} }
  }
  const staff = require(staffPath)
  const imagePath = await staff.image('photo / 1')
  assert.equal(imagePath, '/tmp/' + name + '.jpg')
  assert.equal(request.url, base + '/app/qinglife/staff/images/photo%20%2F%201')
  assert.deepEqual(request.header, { token: 'token:' + base })
}

async function run() {
  await verify('development', 'https://dev-api.example.test')
  await verify('staging', 'https://staging-api.example.test')
  await verify('production', 'https://api.example.test')
  console.log('staff-api.test.js OK: image URL and token use the same resolved business origin')
}
run().catch(error => { console.error(error); process.exitCode = 1 })