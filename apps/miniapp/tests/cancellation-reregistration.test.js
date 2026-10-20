const {test}=require('node:test'),assert=require('node:assert/strict')
const history=require('../utils/registration-history'),domain=require('../utils/domain')
const {fixture,has,content}=require('./camp-flow-presentation.test')
const {home,state,activity,has:homeHas}=require('./home-presentation.test')
test('server active row wins regardless of history order; server inactive overrides status',()=>{
 const old={sessionId:'s',customerId:'a',registrationId:'old',registrationStatus:'cancelled',activeRegistration:0,registeredAt:'2026-10-11'}
 const current={...old,registrationId:'new',registrationStatus:'pending',activeRegistration:1,registeredAt:'2026-10-10'}
 for(const rows of [[old,current],[current,old]]) assert.equal(history.latest(rows)[0].registrationId,'new')
 assert.equal(history.active({...old,registrationStatus:'confirmed'}),false)
 assert.equal(history.active({...old,activeRegistration:undefined}),false)
})
test('partial cancellation selects cancelled identities only; active self stays unselected',()=>{
 const reg={status:'confirmed',participants:[{id:'person-self',status:'confirmed',activeRegistration:true},{id:'family',customerId:'c',name:'家人',relation:'历史关系',status:'cancelled',activeRegistration:false}]}
 assert.equal(history.blocks(reg),false);assert.equal(history.hasActive(reg),true)
 const d=history.draft(reg,{profile:{nickname:'当前本人'},phone:'19900000001'})
 assert.equal(d.participants[0].selected,false);assert.equal(d.participants[0].existingActive,true)
 assert.deepEqual(d.participants.filter(p=>p.selected).map(p=>p.customerId),['c']);assert.equal(d.participants[1].relation,'历史关系')
})
test('cancel refresh resets old request and price; reapply submits a new pending record without old ID',async()=>{
 const f=await fixture('detail','pending'),p=f.page
 p.setData({requestId:'old-request',quote:{ready:true,rows:[],total:'2500.00'}})
 const reg=f.context.state.registrations[f.context.activity.id]
 reg.status='cancelled';reg.participants=[{id:'person-self',registrationId:'old',status:'cancelled',activeRegistration:false,selected:true}]
 f.context.overview.registrations=[{sessionId:f.context.activity.id,registrationId:'old',registrationStatus:'cancelled',isSelf:1}]
 await p.refresh();assert.equal(p.data.requestId,'');assert.equal(p.data.quote.ready,false);assert.equal(p.data.isRegistered,false)
 assert(f.nodes().some(n=>n.attrs?.bindtap==='startRegistration'&&content(n).includes('重新报名')))
 p.openJourney();assert(!f.nodes().some(n=>has(n,'service-reflection')));assert(!f.nodes().some(n=>content(n).includes('无法填写心声')))
 assert(!f.nodes().some(n=>content(n).includes('已取消记录')))
 await p.startRegistration();assert.equal(p.data.view,'register')
 f.business.registrationQuote=async()=>({participants:[{index:0,friendType:'new',unitPrice:3800}],quotedAmount:3800})
 f.business.register=async payload=>{f.writes.push(payload);reg.status='pending';reg.participants=[{...reg.participants[0],registrationId:'fresh',status:'pending',activeRegistration:true}]}
 await p.nextStep();await p.submitRegistration()
 assert.equal(f.writes.length,1);assert.notEqual(f.writes[0].clientRequestId,'old-request');assert(!('registrationId' in f.writes[0].participants[0]))
 assert.equal(p.data.registration.participants[0].registrationId,'fresh');assert.equal(f.context.overview.registrations[0].registrationStatus,'cancelled')
 assert(!f.nodes().some(n=>content(n).includes('已取消记录')))
 assert.equal(p.data.serviceStatus.registration,'待确认')
})
test('closed full and whole-session cancellation keep reapplication closed',async()=>{
 for(const status of ['已取消','已满','报名已截止']) {
  const f=await fixture('register','cancelled');f.context.activity.status=status;f.context.activity.statusTone='closed';f.context.activity.seatsLeft=0
  await f.page.onShow();assert.equal(f.page.data.view,'detail');await f.page.startRegistration();assert.equal(f.page.data.view,'detail')
 }
})
test('personal notice is dynamic, dismissed per identity/event, cleared on new active registration',async()=>{
 const s=state('cancelled');s.backend=true;s.customerId='A';s.registration.participants=[{id:'person-self',registrationId:'cancel-1',status:'cancelled',activeRegistration:false}]
 const f=await home(s,undefined,[{...activity,sessionNumber:504}])
 assert.equal(f.p.data.cancellationNotice.label,'第504期报名已取消')
 assert(f.nodes().some(n=>homeHas(n,'welcome-block')));assert(!f.nodes().some(n=>homeHas(n,'home-task')))
 f.p.openCancellation();assert(f.routes[0].includes('registrationId=cancel-1'))
 f.p.dismissCancellation();await f.p.onShow();assert.equal(f.p.data.cancellationNotice,null)
 s.registration.participants[0].registrationId='cancel-2';await f.p.onShow();assert(f.p.data.cancellationNotice)
 s.customerId='B';s.registration.participants[0].registrationId='cancel-1';await f.p.onShow();assert(f.p.data.cancellationNotice)
 s.registration.status='pending';s.registration.participants[0]={id:'person-self',registrationId:'new',status:'pending',activeRegistration:true}
 await f.p.onShow();assert.equal(f.p.data.cancellationNotice,null)
 s.registration.status='cancelled';s.registration.participants[0].id='family';s.registration.participants[0].status='cancelled';await f.p.onShow();assert.equal(f.p.data.cancellationNotice,null)
})
test('partial self cancellation never borrows a valid companion reflection or attendance',async()=>{
 const f=await fixture('experience','confirmed'),p=f.page,reg=f.context.state.registrations[f.context.activity.id]
 reg.participants=[{id:'person-self',registrationId:'cancelled-self',status:'cancelled',activeRegistration:false,selected:true},{id:'family',registrationId:'valid-family',status:'confirmed',activeRegistration:true,selected:true}]
 f.context.activity.startDate='2020-01-01';f.context.activity.endDate='2020-01-03';f.reflectionContext.canAfter=true
 f.context.activity.days=[{id:'day'}];f.context.overview.attendance=[{registrationId:'other-old-self',sessionDayId:'day',attendanceStatus:'checked_in'}]
 await p.refresh();assert.equal(p.data.hasSelfActiveRegistration,false);assert.equal(p.data.canReview,false);assert.equal(p.data.dayViews[0].checked,false)
 assert(!f.nodes().some(n=>n.attrs?.bindtap==='openReflection'))
 assert.equal(domain.campServiceStatus({status:'confirmed',mixedOrders:true,paymentStatus:'paid',settlementStatus:'confirmed',settlementType:'money',finalAmount:3800}).payment,'费用分别核对')
})
test('real API context keeps own cancellation separate from active companions and newer histories',async()=>{
 const fs=require('node:fs'),path=require('node:path'),vm=require('node:vm'),root=path.resolve(__dirname,'..'),module={exports:{}}
 const session={id:'s',sessionNumber:504,name:'生机清体营',startDate:'2099-01-01',endDate:'2099-01-03',capacity:50,status:'open'}
 const rows=[{registrationId:'old-self',customerId:'owner',isSelf:1,sessionId:'s',batchId:'old-order',registrationStatus:'cancelled',activeRegistration:0,registeredAt:'2026-10-10'},
 {registrationId:'valid-family',customerId:'family',isSelf:0,sessionId:'s',batchId:'old-order',registrationStatus:'pending',activeRegistration:1,registeredAt:'2026-10-10'}]
 const wx={getStorageSync:()=> 'synthetic-token',request:o=>o.success({statusCode:200,data:{code:200,data:o.url.endsWith('/sessions')?[session]:o.url.endsWith('/sessions/s')?session:{customerId:'owner',profile:{nickname:'本人'},registrations:rows}}})}
 const factory=vm.runInNewContext('(function(require,module,exports){'+fs.readFileSync(path.join(root,'services/business-api.js'),'utf8')+'\n})',{wx})
 factory(id=>id.includes('runtime')?{environment:'staging',businessBaseUrl:'https://synthetic.invalid'}:id.includes('session-state')?require('../utils/session-state'):id.includes('registration-history')?history:{getState:()=>({})},module,module.exports)
 let context=await module.exports.context('s'),reg=context.state.registrations.s
 assert.equal(reg.status,'cancelled');assert.equal(reg.participants.find(p=>p.id==='family').status,'pending');assert.equal(reg.canReapply,true);assert.equal(reg.mixedOrders,true)
 const view=require('../utils/camp-arrangements-presentation').present([{...reg,activity:context.activity}],reg,context.overview)
 assert.match(view.arrangementGroups[0].rows[0].arrangement.label,/本人报名已取消/);assert.equal(view.arrangementGroups[0].rows[0].arrangement.history,false)
 rows.push({...rows[0],registrationId:'new-self',batchId:'new-order',registrationStatus:'pending',activeRegistration:1,registeredAt:'2026-10-11'})
 context=await module.exports.context('s');reg=context.state.registrations.s
 assert.equal(reg.status,'pending');assert.equal(reg.participants.length,2);assert.equal(context.overview.registrations.length,3)
 assert.equal(history.cancellation(context.state,context.activities,'test'),null)
})

test('reapplying companion success reflects this new submission while valid self keeps its real state',async()=>{
 const f=await fixture('detail','confirmed'),p=f.page,reg=f.context.state.registrations[f.context.activity.id]
 reg.participants=[{id:'person-self',registrationId:'valid-self',status:'confirmed',activeRegistration:true,selected:true},{id:'family',customerId:'family',name:'家人',relation:'父母',registrationId:'old-family',status:'cancelled',activeRegistration:false,selected:true}]
 await p.refresh();assert.equal(p.data.isRegistered,true);assert.equal(p.data.canReapply,true)
 assert(f.nodes().some(n=>n.attrs?.bindtap==='startRegistration'&&content(n).includes('重新报名')))
 await p.startRegistration();assert.equal(p.data.selectedCount,1)
 f.business.registrationQuote=async()=>({participants:[{index:0,friendType:'new',unitPrice:3800}],quotedAmount:3800})
 f.business.register=async payload=>{f.writes.push(payload);reg.participants[1]={...reg.participants[1],registrationId:'fresh-family',status:'pending',activeRegistration:true};return {registrationStatus:'pending',paymentStatus:'unpaid'}}
 await p.nextStep();await p.submitRegistration();assert.equal(f.writes.length,1);assert.equal(f.writes[0].participants.length,1);assert.equal(f.writes[0].participants[0].customerId,'family')
 assert.equal(p.data.registration.status,'confirmed');assert.equal(p.data.submittedServiceStatus.registration,'待缴费 / 待确认');assert.equal(p.data.submittedParticipantCount,1)
 const result=f.nodes().find(n=>has(n,'registration-result'));assert(content(result).includes('待缴费 / 待确认'));assert(content(result).includes('共 1 位参与者'))
})
test('reschedule and whole-session cancellation keep their original task semantics',()=>{
 const s=state('rescheduled');assert.equal(domain.homeTask(s,[activity]).title,'本次活动安排有变化')
 s.registration.status='confirmed';assert.equal(domain.homeTask(s,[{...activity,status:'已取消'}]).title,'本次活动安排有变化')
})
