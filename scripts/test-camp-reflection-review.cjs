'use strict'
const { test } = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const vm = require('node:vm')

function fixture() {
  const calls = []
  let publicationError = null
  let history = async () => ({ data: { revision: 3, customerId: 'A', rows: [], availableReferences: [{ type: 'registration', id: '00000000-0000-0000-0000-000000000001' }] } })
  const warnings = []
  const request = (kind) => async (...args) => {
    calls.push({ kind, args })
    if (kind === 'display' && publicationError) throw publicationError
  }
  const script = fs.readFileSync(path.join(__dirname, '../apps/admin/src/views/life/campReflection/index.vue'), 'utf8').match(/<script>([\s\S]*?)<\/script>/)[1]
  const sandbox = {
    CustomerDossier: {},
    reviewReflection: request('review'), displayReflection: request('display'), deferReflection: request('defer'),
    reflectionReviewHistory: async (id) => { calls.push({ kind: 'history', id }); return history(id) },
    approveAndPublishReflection() { throw new Error('Content review must not publish') },
    checkPermi() { throw new Error('Publish permission must not silently change the action') },
    listReflections: async () => { calls.push({ kind: 'read' }); return { data: { rows: [], total: 0 } } }
  }
  vm.runInNewContext(script.replace(/^import .*$/gm, '').replace('export default', 'component ='), sandbox)
  const p = Object.assign(sandbox.component.data.call({ $route: { query: {} } }), sandbox.component.methods)
  p.current = { id: 'f', revision: 3, note: 'User original' }
  p.displayNote = 'Approved display text'
  p.$message = { warning(message) { warnings.push(message) } }
  return { p, calls, warnings, setHistory: fn => { history = fn }, denyPublication: () => { publicationError = new Error('Necessary authorization evidence missing') } }
}
test('content review always approves for later checks, even when the operator can publish', async () => {
  const { p, calls } = fixture()
  await p.submitReview()
  assert.equal(calls[0].kind, 'review')
  assert.equal(calls[0].args[1], 'approve')
  assert.equal(calls[0].args[2].revision, 3)
  assert.equal(calls[0].args[2].displayNote, 'Approved display text')
  assert.equal(p.current.note, 'User original')
  assert.equal(p.acting, false)
  assert.equal(calls.filter(c => c.kind === 'display').length, 0)
})
test('editing keeps the existing edit operation without publishing', async () => {
  const { p, calls } = fixture()
  p.editing = true
  await p.submitReview()
  assert.equal(calls[0].args[1], 'edit')
  assert.equal(calls.filter(c => c.kind === 'display').length, 0)
})
test('operational rejection keeps the separate review route and concurrency guard', async () => {
  const { p, calls } = fixture()
  p.acting = true
  await p.act(p.current, 'reject')
  assert.equal(calls.length, 0)
  p.acting = false
  await p.act(p.current, 'reject')
  assert.equal(calls[0].kind, 'review')
  assert.equal(calls[0].args[1], 'reject')
})
test('final publication prerequisites failing never become a successful review or refresh', async () => {
  const { p, calls, denyPublication } = fixture()
  denyPublication()
  await assert.rejects(p.act(p.current, 'publish'), /authorization evidence/)
  assert.equal(calls.length, 1)
  assert.equal(calls[0].kind, 'display')
  assert.equal(p.acting, false)
})

function fillPublication(p) {
  p.publication.contentSafetyConclusion = 'Checked the actual display and content safety'
  p.publication.identityConclusion = 'Confirmed the author against existing business evidence'
  p.publication.authorizationConclusion = 'not_applicable'
  p.publication.basisSummary = 'Reviewed the linked material and recorded applicable reasons'
  p.publication.referenceKeys = ['registration:00000000-0000-0000-0000-000000000001']
}
test('opening publication reads evidence but neither preauthorizes nor publishes', async () => {
  const { p, calls } = fixture()
  await p.openPublication(p.current)
  assert.equal(p.publicationOpen, true)
  assert.equal(p.publication.identityConclusion, '')
  assert.equal(p.publication.authorizationConclusion, 'insufficient')
  assert.equal(calls.length, 1)
  assert.equal(calls[0].kind, 'history')
})
test('missing actual conclusions never becomes ready publication', async () => {
  const { p, calls, warnings } = fixture()
  await p.openPublication(p.current)
  p.publication.basisSummary = 'Offline evidence remains missing'
  await p.submitPublication('ready')
  assert.equal(warnings.length, 1)
  assert.equal(calls.filter(c => c.kind === 'display').length, 0)
})
test('defer retains an auditable missing-evidence conclusion without publication', async () => {
  const { p, calls } = fixture()
  await p.openPublication(p.current)
  p.publication.basisSummary = 'Necessary offline authorization source remains missing'
  await p.submitPublication('deferred')
  const write = calls.find(c => c.kind === 'defer')
  assert.equal(write.args[1].revision, 3)
  assert.equal(write.args[1].publicationReview.decision, 'deferred')
  assert.equal(calls.filter(c => c.kind === 'display').length, 0)
})
test('explicit ready publication sends only controlled references and current revision', async () => {
  const { p, calls } = fixture()
  await p.openPublication(p.current)
  fillPublication(p)
  await p.submitPublication('ready')
  const write = calls.find(c => c.kind === 'display')
  assert.equal(write.args[1], 'publish')
  assert.equal(write.args[2].revision, 3)
  assert.equal(write.args[2].publicationReview.decision, 'ready')
  assert.deepEqual(JSON.parse(JSON.stringify(write.args[2].publicationReview.references)), [{ type: 'registration', id: '00000000-0000-0000-0000-000000000001' }])
  assert.equal('operatorId' in write.args[2], false)
  assert.equal(p.publicationOpen, false)
})
test('publication denial preserves the entered conclusions and never refreshes as success', async () => {
  const { p, calls, denyPublication } = fixture()
  await p.openPublication(p.current)
  fillPublication(p)
  denyPublication()
  await assert.rejects(p.submitPublication('ready'), /authorization evidence/)
  assert.equal(p.publicationOpen, true)
  assert.equal(p.acting, false)
  assert.equal(p.publication.identityConclusion, 'Confirmed the author against existing business evidence')
  assert.equal(calls.filter(c => c.kind === 'read').length, 0)
})
test('unavailable or cross-subject reference keys are not silently sent', async () => {
  const { p, calls } = fixture()
  await p.openPublication(p.current)
  fillPublication(p)
  p.publication.referenceKeys = ['registration:another-owner']
  await p.submitPublication('ready')
  assert.equal(calls.filter(c => c.kind === 'display').length, 0)
})
test('late A review context cannot overwrite the newly selected B context', async () => {
  const { p, setHistory } = fixture()
  let resolveA
  setHistory(id => id === 'A' ? new Promise(resolve => { resolveA = resolve }) : Promise.resolve({ data: { revision: 3, customerId: 'B', rows: [], availableReferences: [] } }))
  const a = p.openPublication({ id: 'A', revision: 3 })
  const b = p.openPublication({ id: 'B', revision: 3 })
  await b
  resolveA({ data: { revision: 3, customerId: 'A', rows: [], availableReferences: [] } })
  await a
  assert.equal(p.reviewContext.customerId, 'B')
  assert.equal(p.current.id, 'B')
})
test('stale review revision does not open a publication form', async () => {
  const { p, calls, setHistory } = fixture()
  setHistory(async () => ({ data: { revision: 4, customerId: 'A' } }))
  await p.openPublication(p.current)
  assert.equal(p.publicationOpen, false)
  assert.equal(calls.filter(c => c.kind === 'display').length, 0)
})
