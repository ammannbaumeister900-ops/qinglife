const { test } = require('node:test')
const assert = require('node:assert/strict'), fs = require('node:fs'), vm = require('node:vm')
const path = require('node:path')
const file = path.resolve(__dirname, '../apps/admin/src/views/life/essay/index.vue')
function component() {
  const calls = [], source = fs.readFileSync(file, 'utf8').match(/<script>([\s\S]*?)<\/script>/)[1].replace(/^import .*$/gm, '').replace('export default', 'globalThis.component =')
  const api = name => form => { calls.push({ name, form: JSON.parse(JSON.stringify(form)) }); return Promise.resolve({ data: form }) }
  const validators = { setTimeout }; vm.runInNewContext(fs.readFileSync(path.resolve(__dirname, '../apps/admin/src/api/tool/validate.js'), 'utf8').replace(/export function/g, 'function'), validators)
  const sandbox = { ArticlePlacements: {}, isInteger: validators.isInteger, addEssay: api('create'), updateEssay: api('edit') }
  vm.runInNewContext(source, sandbox)
  const c = sandbox.component, context = { ...c.data(), ...c.methods, resetForm() {}, msgSuccess() {}, getList() {} }
  context.$refs = { form: { validate(cb) {
    // Exercise Element's actual required-field validator, not a hardcoded cover exception.
    const Schema = require(require.resolve('async-validator', { paths: [path.dirname(require.resolve('../apps/admin/node_modules/element-ui/package.json'))] }))
    new Schema(context.rules).validate(context.form, errors => { context.validationErrors = errors; cb(!errors) })
  } } }
  return { context, calls }
}
test('no-cover articles create and edit in published state; clearing a prior cover persists an empty value', async () => {
  for (const id of [undefined, 7]) for (const cover of [undefined, '', null]) {
    const { context: c, calls } = component(); c.reset()
    Object.assign(c.form, { id, status: '1', title: '隔离测试原文', Introduction: '摘要', orderNum: '1', content: '<video src="https://media.invalid/v.mp4" controls></video><p>原文</p>', titleUrl: cover })
    c.submitForm(); await new Promise(resolve => setTimeout(resolve, 20))
    assert.equal(calls.length, 1); assert.equal(calls[0].name, id ? 'edit' : 'create')
    assert.equal(calls[0].form.status, '1'); assert.equal(calls[0].form.titleUrl, cover)
    assert.equal(calls[0].form.content, c.form.content); assert.equal(c.buttonLoading, false)
  }
})
test('optional cover does not weaken title, introduction, order or body requirements', async () => {
  for (const missing of ['title', 'Introduction', 'orderNum', 'content']) {
    const { context: c, calls } = component(); c.reset()
    Object.assign(c.form, { title: '标题', Introduction: '简介', orderNum: '1', content: '<p>正文</p>' }); c.form[missing] = ''
    c.submitForm(); await new Promise(resolve => setTimeout(resolve, 20)); assert.equal(calls.length, 0)
  }
})
test('new articles start as private drafts rather than silently publishing', () => {
  const { context } = component(); context.reset(); assert.equal(context.form.status, '0'); assert.equal(context.form.homeFeatured, 0)
})
test('legacy zero order remains editable without losing the source order', async () => {
  const { context: c, calls } = component(); c.reset(); Object.assign(c.form, { id: '87', title: 'SYNTHETIC', Introduction: '简介', orderNum: 0, content: '<p>正文</p>' }); c.submitForm(); await new Promise(resolve => setTimeout(resolve, 20)); assert.equal(calls.length, 1, JSON.stringify(c.validationErrors)); assert.equal(calls[0].form.orderNum, 0)
})
