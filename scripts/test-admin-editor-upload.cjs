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
const coverSource = fs.readFileSync(path.join(__dirname, '../apps/admin/src/components/ImageLocalUpload/index.vue'), 'utf8').match(/<script>([\s\S]*?)<\/script>/)[1].replace(/^import .*$/gm, '').replace('export default', 'globalThis.component =')
const coverSandbox = { process: { env: { VUE_APP_BASE_API: '/test-api' } } }; vm.runInNewContext(coverSource, coverSandbox)
const cover = coverSandbox.component, publicUrl = 'https://media.invalid/profile/public/cover.jpg'
const context = { value: publicUrl, baseUrl: '/test-api', fileList: [], loading: { close() {} }, listToString: cover.methods.listToString, $emit: (name, value) => { context.emitted = value } }
cover.watch.value.handler.call(context, publicUrl); assert.equal(context.fileList[0].url, publicUrl)
context.value = 'old.jpg'; cover.watch.value.handler.call(context, 'old.jpg'); assert.equal(context.fileList[0].url, '/test-api/data/static/old.jpg')
context.fileList = []; cover.methods.handleUploadSuccess.call(context, { code: 200, data: { url: publicUrl, fileName: 'public/cover.jpg' } }); assert.equal(context.fileList[0].url, publicUrl); assert.equal(context.emitted, publicUrl)
console.log('Article cover: public URL preview/storage and legacy relative compatibility passed')

context.fileList = [{ name: publicUrl, url: publicUrl }, { name: "other.jpg", url: "other.jpg" }]; cover.methods.handleRemove.call(context, { name: publicUrl }); assert.equal(context.fileList.length, 1); assert.equal(context.fileList[0].name, "other.jpg"); cover.methods.handleRemove.call(context, { name: "missing.jpg" }); assert.equal(context.fileList.length, 1)
console.log("Article cover: remove selected image without deleting an unrelated image passed")

const localEditorSource = fs.readFileSync(path.join(__dirname, "../apps/admin/src/components/EditorLocal/index.vue"), "utf8"); const initSource = localEditorSource.slice(localEditorSource.indexOf("    init() {")); assert.ok(initSource.indexOf("Quill.register(VideoBlot)") < initSource.indexOf("new Quill(editor")); assert.ok(initSource.indexOf("Quill.register(VideoBlot)") < initSource.indexOf("this.Quill.pasteHTML")); console.log("Existing video format registered before initial editor import passed")
