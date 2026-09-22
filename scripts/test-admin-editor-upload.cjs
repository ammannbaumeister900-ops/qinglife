const assert = require('node:assert/strict')
const fs = require('node:fs')
const vm = require('node:vm')
const path = require('node:path')
for (const component of ['Editor', 'EditorLocal']) {
  const source = fs.readFileSync(path.join(__dirname, '../apps/admin/src/components', component, 'index.vue'), 'utf8')
    .match(/<script>([\s\S]*?)<\/script>/)[1]
    .replace(/^import .*$/gm, '').replace('export default', 'globalThis.component =')
  const sandbox = { process: { env: { VUE_APP_BASE_API: '/test-api' } } }
  vm.runInNewContext(source, sandbox)
  const calls = [], errors = []
  const context = { Quill: { getSelection: () => null, getLength: () => 4,
    insertEmbed: (...args) => calls.push(args), setSelection: index => calls.push(index) },
    $message: { error: message => errors.push(message) } }
  const handler = sandbox.component.methods.handleUploadSuccess
  handler.call(context, { code: 200, data: { url: 'https://media.invalid/profile/public/test.png', fileName: 'public/test.png', fileType: 'image' } })
  assert.deepEqual(calls[0], [3, 'image', 'https://media.invalid/profile/public/test.png'])
  assert.equal(calls[1], 4)
  handler.call(context, { code: 400 })
  assert.equal(calls.length, 2)
  assert.equal(errors.length, 1)
}
console.log('Editor uploads: returned public URL, lost-focus insertion, and failed upload handling passed')