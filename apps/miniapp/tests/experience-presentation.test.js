const { test } = require('node:test')
const assert = require('node:assert/strict')
const { fixture, has, content } = require('./camp-flow-presentation.test')
const presentation = require('../utils/experience-presentation')
const plain = x => JSON.parse(JSON.stringify(x))
const control = (f, handler) => f.nodes().find(n => n.attrs?.bindtap === handler || n.attrs?.bindinput === handler)
const input = value => ({ detail: { value } })
const row = (phase, extra = {}) => ({ id: phase, registrationId: 'self-reg', sessionId: 'session-live', phase, nodeKey: phase === 'during' ? 'day-1' : '', note: phase + '原文\n保留换行', energy: null, relaxation: null, visibility: 'private', updatedAt: '2026-10-03 16:20:00', ...extra })
async function setup(rows = [row('before'), row('during'), row('after')], status = 'completed') {
  const f = await fixture('experience', status)
  f.reflectionContext.canAfter = status === 'completed'
  f.context.overview.experienceRecords = [...rows].reverse().concat(row('after', { id: 'foreign', registrationId: 'foreign-reg', note: 'PRIVATE_FOREIGN_SENTINEL' }))
  await f.page.refresh()
  return f
}
test('private reading precedes services; only current after record can open editing', async () => {
  const f = await setup(), nodes = f.nodes()
  assert(nodes.findIndex(n => has(n, 'experience-records')) < nodes.findIndex(n => has(n, 'experience-services')))
  assert(!control(f, 'onReviewInput'));assert(!control(f, 'chooseScale'))
  assert.equal(f.nodes().filter(n => n.attrs?.bindtap === 'editReview').length, 1)
  assert.equal(nodes.find(n => n.tag === 'ql-nav-space').attrs.title, '体验回顾')
  assert.equal(content(nodes.find(n => has(n, 'experience-records'))).includes('PRIVATE_FOREIGN_SENTINEL'), false)
  assert.deepEqual(plain(f.page.data.experienceTimeline.map(r => r.phase)), ['before','during','after'])
  await f.page.editReview();f.flush()
  assert.equal(control(f, 'onReviewInput'), undefined)
  assert.equal(f.nodes().filter(n => has(n, 'experience-record')).length, 3)
  assert.equal(f.links.at(-1), '/pages/camp-reflection/index?id=session-live')
  assert.equal(f.writes.length, 0)
})
test('only actual scores appear; paragraphs, blank and long records keep their content', async () => {
  const f = await setup([row('during', { note: '', energy: 4 }), row('after', { note: '长文\n'.repeat(200) })])
  assert(content(f.nodes().find(n => has(n, 'experience-records'))).includes('精力 4/5'))
  assert(!content(f.nodes().find(n => has(n, 'experience-records'))).includes('未填'))
  assert(f.nodes().some(n => String(n.attrs?.class).includes('is-clamped')))
  f.page.toggleExperienceRecord({ currentTarget: { dataset: { key: 'after' } } })
  assert(!f.nodes().some(n => String(n.attrs?.class).includes('is-clamped')))
  assert.equal(f.page.data.experienceTimeline[1].note, '长文\n'.repeat(200))
  f.page.toggleExperienceRecord({ currentTarget: { dataset: { key: 'foreign' } } })
  assert(!f.page.data.reviewExpanded.foreign)
})
test('empty and partial records enter the unified editor without a second private write', async () => {
  for(const rows of [[],[row('before')]]){const f=await setup(rows);assert.equal(content(control(f,'editReview')),'写活动后感受＋');await f.page.editReview();await f.page.saveReview();assert.equal(f.links.at(-1),'/pages/camp-reflection/index?id=session-live');assert.equal(f.writes.length,0);assert.equal(f.page.data.experienceTimeline.length,rows.length);}
})

test('opening the unified editor preserves original ID text and optional ratings in reading view', async () => {
  const f=await setup([row('after',{energy:4,relaxation:2})]);await f.page.editReview();assert.equal(f.links.at(-1),'/pages/camp-reflection/index?id=session-live');assert.equal(f.writes.length,0);assert.equal(f.page.data.experienceTimeline[0].note,row('after').note);assert.equal(f.page.data.experienceTimeline[0].id,'after');assert.equal(f.page.data.experienceTimeline[0].energy,4);assert.equal(f.page.data.experienceTimeline[0].relaxation,2);
})

test('formal reading never opens the obsolete inline editor or mutates records on entry', async () => {
 const f=await setup();await f.page.editReview();f.page.closeReview();await f.page.refresh();assert.equal(f.page.data.experienceTimeline.at(-1).note,row('after').note);assert.equal(f.page.data.reviewEditing,false);assert.equal(control(f,'onReviewInput'),undefined);assert.equal(f.writes.length,0);
})

test('obsolete formal save action navigates to one editor and never invokes the legacy writer', async () => {
 const f=await setup();f.business.saveExperience=()=>{throw Error('obsolete writer must not run')};await f.page.saveReview();assert.equal(f.links.at(-1),'/pages/camp-reflection/index?id=session-live');assert.equal(f.writes.length,0);assert.equal(f.page.data.reviewEditing,false);assert.equal(f.page.data.experienceTimeline.at(-1).note,row('after').note);
})

test('identity and participant changes discard prior private records on reading refresh', async () => {
 for(const change of ['customer','participant','activity']){const f=await setup();await f.page.editReview();if(change==='customer')f.context.overview.customerId='other-owner';if(change==='participant')f.context.state.registrations[f.context.activity.id].participants[0].registrationId='other-reg';if(change==='activity'){const old=f.context.activity.id;f.context.activity.id='other-session';f.context.state.registrations['other-session']=f.context.state.registrations[old];}f.context.overview.experienceRecords=[];await f.page.refresh();assert.equal(f.page.data.reviewNote,'');assert.equal(f.page.data.reviewEditing,false);assert.equal(f.page.data.experienceTimeline.length,0);assert.equal(f.writes.length,0);}
})

test('pending, waitlist, cancelled, no self and pre-review stages cannot open or save review', async () => {
  for(const status of ['pending','waitlist','cancelled','none','confirmed']){
    const f=await setup([],status);await f.page.editReview();await f.page.saveReview()
    assert(!f.page.data.reviewEditing);assert(!control(f,'onReviewInput'));assert.equal(f.writes.length,0)
  }
  const f=await setup();f.context.state.registration.participants=[];await f.page.refresh();await f.page.editReview();await f.page.saveReview()
  assert(!control(f,'openReflection'));assert.equal(f.writes.length,0);assert.equal(f.page.data.experienceTimeline.length,0)
})
test('minor and adult reflection/invitation gates and exact service navigation remain independent', async()=>{
  const f=await setup();assert(control(f,'openReflection'));assert(control(f,'openInvite'))
  f.context.state.profile.minor=true;await f.page.refresh();assert(control(f,'openReflection'));assert(!control(f,'openInvite'))
  await f.page.openReflection();assert.equal(f.links.at(-1),'/pages/camp-reflection/index?id=session-live')
  f.page.backToCamp();assert.equal(f.links.at(-1),'/pages/camp/index')
  assert(!f.page.onShareAppMessage().path.includes('原文'))
})
test('before feeling keeps its existing text-only payload; during has no new UI write path', async()=>{
  const f=await fixture('feeling');f.page.setData({canRecordFeeling:true,feelingPhase:'before',feelingRegistrationId:'self-reg',feelingNote:'活动前原文',energy:4,relaxation:2})
  await f.page.saveFeeling();assert.deepEqual(f.writes[0],{registrationId:'self-reg',phase:'before',sessionDayId:null,energy:null,relaxation:null,note:'活动前原文'})
  assert(!control(f,'chooseScale'));assert(!control(f,'editReview'))
})
test('invalid optional ratings never masquerade as actual scores',()=>{
  assert.equal(presentation.score(null),null);assert.equal(presentation.score(''),null)
  assert.equal(presentation.score(0),null);assert.equal(presentation.score(6),null);assert.equal(presentation.score(1.5),null)
  assert.equal(presentation.score('4'),4);assert.equal(presentation.score(true),null)
  for(const updatedAt of ['', 'not-a-date', '2026-02-30 12:00:00', '2026-13-01']) assert.equal(presentation.present([row('after',{updatedAt})])[0].updatedText,'')
})

test('before body-only edit preserves actual stored ratings without enabling during writes',async()=>{const f=await fixture('feeling');f.page.setData({canRecordFeeling:true,feelingPhase:'before',feelingRegistrationId:'self-reg',feelingNote:'修改前感受',overview:{experienceRecords:[{registrationId:'self-reg',phase:'before',nodeKey:'',energy:4,relaxation:2}]}});await f.page.saveFeeling();assert.equal(f.writes[0].energy,4);assert.equal(f.writes[0].relaxation,2);f.page.setData({canRecordFeeling:false,feelingPhase:''});assert.equal(control(f,'onFeelingInput').attrs.disabled,true);await f.page.saveFeeling();assert.equal(f.writes.length,1);});
