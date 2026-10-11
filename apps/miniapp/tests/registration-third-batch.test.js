const { test } = require('node:test')
const assert = require('node:assert/strict')
const { fixture, has, content } = require('./camp-flow-presentation.test')
const draft = require('../utils/registration-draft')
const domain = require('../utils/domain')
const input = value => ({ detail: { value } })
function priced(f, rows) {
  f.business.registrationQuote = async payload => ({ participants: payload.participants.map((p,index) => ({index,friendType: index ? 'new' : 'returning',unitPrice: rows ? rows[index] : index ? '3800.00' : '2500.00'})),
    quotedAmount: rows ? rows.reduce((a,b)=>a+Number(b),0) : payload.participants.length===1?'2500.00':'6300.00' })
}
test('self edits sync each untouched contact field; manual clearing is also an override', async () => {
  const f=await fixture('register','none'),p=f.page
  p.onSelfName(input('最新小名'));p.onSelfPhone(input('19900000001'))
  assert.equal(p.data.draft.contactName,'最新小名');assert.equal(p.data.draft.contactPhone,'19900000001')
  p.onContactName(input('联系人'));p.onSelfName(input('再次变化'));p.onSelfPhone(input('19900000002'))
  assert.equal(p.data.draft.contactName,'联系人');assert.equal(p.data.draft.contactPhone,'19900000002')
  p.onContactPhone(input(''));p.onSelfPhone(input('19900000003'));assert.equal(p.data.draft.contactPhone,'')
})
test('latest profile sync survives refresh and excludes deselected self', async () => {
  const f=await fixture('register','none'),p=f.page
  f.context.state={...f.context.state,profile:{nickname:'档案新名'},phone:'19900000007'}
  await p.refresh();assert.equal(p.data.draft.contactName,'档案新名');assert.equal(p.data.draft.contactPhone,'19900000007')
  p.onContactName(input('手动联系人'))
  f.context.state={...f.context.state,profile:{nickname:'第二次新名'},phone:'19900000008'}
  await p.refresh();assert.equal(p.data.draft.contactName,'手动联系人');assert.equal(p.data.draft.contactPhone,'19900000008')
  p.data.draft.participants[0].selected=false;p.onSelfPhone(input('19900000009'));assert.equal(p.data.draft.contactPhone,'19900000008')
})
test('relationships use one choice and preserve historic text until explicitly changed', async () => {
  const f=await fixture('register','none'),p=f.page
  p.data.draft.participants.push({id:'family',name:'家人',relation:'历史自由文本',selected:true})
  assert.equal(p.data.draft.participants[1].relation,'历史自由文本')
  p.onParticipantRelation({currentTarget:{dataset:{index:1}},detail:{value:1}})
  assert.equal(p.data.draft.participants[1].relation,'父母')
  assert.deepEqual([...p.data.relationOptions],['本人','父母','夫妻','子女','朋友','同事','同学','兄弟姐妹','其他亲属'])
  p.setData({addingPerson:true,personName:'合成参与者'});p.onPersonRelation(input(0));p.addPerson()
  assert.equal(p.data.formError.target,'camp-person-relation')
})
test('server identities and prices form one exact reference total, including zero', async () => {
  const f=await fixture('register','none'),p=f.page
  p.data.draft.participants.push({id:'companion',name:'同事',relation:'同事',selected:true})
  p.setData({selectedCount:2});priced(f);await p.nextStep()
  assert.equal(p.data.quote.total,'6300.00')
  assert.deepEqual(JSON.parse(JSON.stringify(p.data.quote.rows.map(r=>r.friendLabel))),['老轻友','新轻友'])
  const text=content(f.nodes().find(n=>has(n,'summary-price')))
  assert(text.includes('6300.00'));assert(text.includes('同事 · 新轻友'));assert(!text.includes('公开参考价'))
  priced(f,['0','0']);await p.loadQuote();assert.equal(p.data.quote.total,'0.00')
})
test('participant edits invalidate prices and stale or hidden responses cannot restore them', async () => {
  const f=await fixture('register','none'),p=f.page;priced(f);await p.loadQuote()
  p.onSelfName(input('改名'));assert.equal(p.data.quote.ready,false)
  let resolve;f.business.registrationQuote=()=>new Promise(r=>resolve=r)
  const task=p.loadQuote();p.onSelfName(input('再改名'));resolve({participants:[{index:0,friendType:'returning',unitPrice:2500}],quotedAmount:2500});await task
  assert.equal(p.data.quote.ready,false)
  const hidden=p.loadQuote();p.onHide();resolve({participants:[{index:0,friendType:'returning',unitPrice:2500}],quotedAmount:2500});await hidden
  assert.equal(p.data.quote.ready,false)
})
test('missing identity, malformed amount or mismatched total never becomes a certain price', () => {
  const people=[{id:'self',name:'本人'}]
  for(const result of [{}, {participants:[{index:0,friendType:'new',unitPrice:null}],quotedAmount:0},
    {participants:[{index:0,friendType:'new',unitPrice:3800}],quotedAmount:2500},
    {participants:[{index:0,unitPrice:3800}],quotedAmount:3800}]) assert.throws(()=>draft.quote(result,people))
})
test('changed server price requires review before writing registration; API failure keeps an unknown total', async () => {
  const f=await fixture('register','none'),p=f.page;priced(f);await p.nextStep()
  priced(f,['3000']);await p.submitRegistration();assert.equal(f.writes.length,0);assert.equal(p.data.quote.total,'3000.00')
  assert.equal(p.data.formError.target,'camp-submit-error')
  await p.submitRegistration();assert.equal(f.writes.length,1);assert.equal(p.data.view,'success')
  f.business.registrationQuote=async()=>{throw Error('价格未确认')};await p.loadQuote()
  assert.equal(p.data.quote.ready,false);assert.equal(p.data.quote.total,'');assert.equal(p.data.quote.rows.length,0)
})
test('new pending, historical unpaid and paid/pass outcomes remain distinct without mutation', () => {
  const pending={status:'pending',paymentStatus:'unpaid'},old={status:'confirmed',paymentStatus:'unpaid',settlementStatus:'confirmed',settlementType:'money',finalAmount:3800}
  assert.equal(domain.campServiceStatus(pending).registration,'待缴费 / 待确认')
  assert.match(domain.campServiceStatus(pending).registrationNote,/尚未占用名额/)
  const snapshot=JSON.stringify(old),service=domain.campServiceStatus(old)
  assert.equal(service.registration,'已确认');assert.equal(service.operationalRisk,true);assert.match(service.registrationNote,/运营核查/)
  assert.equal(JSON.stringify(old),snapshot)
  const paidPending=domain.campServiceStatus({...old,status:'pending',paymentStatus:'paid'})
  assert.equal(paidPending.registration,'待确认');assert.equal(paidPending.payment,'已付款');assert(!paidPending.registrationNote.includes('待缴费'))
  assert.equal(domain.campServiceStatus({...old,paymentStatus:'paid'}).operationalRisk,false)
  assert.equal(domain.campServiceStatus({...old,settlementType:'pass',passUnits:1}).operationalRisk,false)
})
test('success retains arrange navigation and a disabled reflection button with the server reason', async () => {
  const f=await fixture('success','pending')
  assert(!content(f.nodes().find(n=>has(n,'result-title'))).includes('待缴费'), 'missing historical payment facts remain unknown')
  const registration={...f.page.data.registration,paymentStatus:'unpaid'}
  f.page.setData({registration,serviceStatus:domain.campServiceStatus(registration)})
  assert(f.nodes().find(n=>n.attrs?.bindtap==='openJourney'))
  const button=f.nodes().find(n=>n.attrs?.bindtap==='openReflection')
  assert.equal(button.attrs.disabled,true);assert(!content(button).includes('↗'))
  assert(content(f.nodes().find(n=>has(n,'result-title'))).includes('待缴费'))
  f.page.setData({view:'journey'});assert.equal(f.nodes().find(n=>n.attrs?.bindtap==='openReflection').attrs.disabled,true)
})
