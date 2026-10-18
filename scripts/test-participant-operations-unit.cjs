const fs = require('node:fs')
const path = require('node:path')
const vm = require('node:vm')
const assert = require('node:assert/strict')
const crypto = require('node:crypto')
const root = path.resolve(__dirname, '../apps/admin/src/views/life/registration')
const helperSource = fs.readFileSync(path.join(root, 'participantFinance.js'), 'utf8').replace(/export /g, '')
const helpers = vm.runInNewContext(helperSource + '\n;({money,yuan,uuid,recovery,settlementLabel,labels})', { window: { crypto: crypto.webcrypto }, Uint8Array })
let writer, confirmations, events
const source = fs.readFileSync(path.join(root, 'ParticipantOperationDialog.vue'), 'utf8').match(/<script>([\s\S]*?)<\/script>/)[1].replace(/^import .*$/gm, '').replace('export default', 'module.exports =')
const sandbox = { module: { exports: {} }, ...helpers, ReceiptImages: {}, getParticipantPasses: async () => ({ data: [] }), writeParticipantFinance: (...args) => writer(...args), window: { addEventListener() {}, removeEventListener() {} } }
vm.runInNewContext(source, sandbox)
const definition = sandbox.module.exports
function component(kind = 'capture') {
  confirmations = 0; events = []
  const instance = { context: { kind, registrationId: 'synthetic-person' }, finance: { participants: [], allocations: [] }, $confirm: async () => { confirmations++ }, $emit: (...args) => events.push(args) }
  Object.assign(instance, definition.data.call(instance))
  for (const [name, fn] of Object.entries(definition.methods)) instance[name] = fn.bind(instance)
  for (const [name, fn] of Object.entries(definition.computed)) Object.defineProperty(instance, name, { get: () => fn.call(instance) })
  Object.assign(instance.form, { amount: '3800', method: 'transfer', evidence: 'synthetic receipt', actualConfirmed: true })
  return instance
}
async function main() {
  assert.equal(helpers.settlementLabel({ settlementStatus: 'confirmed', settlementType: 'pass', paymentStatus: 'unpaid' }), '卡次已结算')
  for (const bad of ['', '-1', 'NaN', '1.001', '1e3']) assert.throws(() => helpers.money(bad))
  const requests = []
  writer = async (...args) => { requests.push(JSON.parse(JSON.stringify(args))); if (requests.length === 1) throw Object.assign(new Error('synthetic timeout after server acceptance'), { uncertain: true }); return { data: { receiptId: 'one-fact' } } }
  const uncertain = component()
  await uncertain.submit(); assert.equal(uncertain.uncertain, true); assert.equal(uncertain.saved, false)
  uncertain.form.amount = '9900'; uncertain.form.evidence = 'changed UI after error'
  await uncertain.submit(); assert.deepEqual(requests[0], requests[1]); assert.equal(confirmations, 1); assert.equal(uncertain.saved, true)
  await uncertain.submit(); assert.equal(requests.length, 2)
  let release
  const concurrent = component(); concurrent.$confirm = () => new Promise(resolve => { release = resolve })
  let writes = 0; writer = async () => { writes++; return { data: {} } }
  const first = concurrent.submit(); await concurrent.submit(); assert.equal(writes, 0); release(); await first; assert.equal(writes, 1)
  const conflict = component(); writer = async () => { throw Object.assign(new Error('revision changed'), { code: 409 }) }
  await conflict.submit(); await conflict.submit(); assert.equal(confirmations, 1); assert.equal(conflict.saved, false); assert.match(conflict.guidance, /刷新/)
  const rejected = component(); rejected.$confirm = async () => { throw new Error('cancelled') }; writer = async () => { throw new Error('must not send') }
  await rejected.submit(); assert.equal(rejected.busy, false); assert.equal(rejected.submitted, null)
  const reconciliation = component('payment'); reconciliation.context.request = { requestId: 'request', revision: 2 }; reconciliation.form.occurredAt = '2026-10-18T10:00:00+08:00'; reconciliation.form.reason = 'verified outflow'
  writer = async () => ({ data: { reconciliationRequired: true, status: 'recorded_unreconciled' } })
  await reconciliation.submit(); assert.equal(reconciliation.saved, true); assert.equal(reconciliation.reconciliation, true)
  const invalid = component(); invalid.form.actualConfirmed = false; writer = async () => { throw new Error('must not send') }; await invalid.submit(); assert.equal(invalid.submitted, null)
  console.log('Participant operations UI unit checks: 8 scenarios PASS (state/retry/double-submit/validation/financial labels)')
}
main().catch(error => { console.error(error); process.exitCode = 1 })
