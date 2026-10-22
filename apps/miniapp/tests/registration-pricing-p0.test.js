const { test } = require('node:test')
const assert = require('node:assert/strict')
const { fixture } = require('./camp-flow-presentation.test')
test('failed initial quote never submits registration or invents a total', async () => {
  const f=await fixture('register','none'),p=f.page
  f.business.registrationQuote=async()=>{throw Error('服务暂时无法访问')}
  await p.nextStep();assert.equal(p.data.quote.ready,false);assert.equal(p.data.quote.total,'')
  await p.submitRegistration();assert.equal(f.writes.length,0);assert.equal(p.data.view,'register')
  assert.equal(p.data.formError.target,'camp-submit-error');assert.equal(p.data.quote.total,'')
})
test('changed participants require a fresh quote before submitting and retain entered details', async () => {
  const f=await fixture('register','none'),p=f.page;let requests=[]
  f.business.registrationQuote=async payload=>{requests.push(payload);return {participants:payload.participants.map((x,index)=>({index,friendType:'new',unitPrice:'3800.00'})),quotedAmount:3800*payload.participants.length}}
  await p.nextStep();assert.equal(p.data.quote.total,'3800.00')
  p.onSelfName({detail:{value:'最新报名名字'}});assert.equal(p.data.quote.ready,false)
  await p.submitRegistration();assert.equal(f.writes.length,0);assert.equal(p.data.quote.total,'3800.00')
  assert.equal(requests.at(-1).participants[0].name,'最新报名名字')
  await p.submitRegistration();assert.equal(f.writes.length,1);assert.equal(f.writes[0].participants[0].name,'最新报名名字')
})
