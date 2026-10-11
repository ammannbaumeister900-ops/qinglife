const {test}=require('node:test'),assert=require('node:assert/strict'),present=require('../utils/camp-arrangements-presentation').present
const reg=(id,status='confirmed',extra={})=>({activityId:id,status,participants:[{id:'person-self',selected:true,status}],activity:{id,sessionNumber:id,name:'实际活动'+id,startDate:'2099-01-01',endDate:'2099-01-03',city:'上海',sourceStatus:'open',status:'开放报名'},...extra})
const rows=result=>result.arrangementGroups.flatMap(g=>g.rows)
test('only mixed current/history lists group; stable API order and original statuses survive',()=>{
  assert.deepEqual(present([]),{arrangementCount:0,arrangementGroups:[]})
  assert.equal(present([reg('single')]).arrangementGroups[0].label,'')
  assert.equal(present([reg('a'),reg('b','pending')]).arrangementGroups[0].label,'')
  const source=[reg('past-a','completed'),reg('now-b','pending'),reg('past-c','cancelled'),reg('now-d')],before=JSON.stringify(source),result=present(source)
  assert.deepEqual(result.arrangementGroups.map(g=>g.label),['当前安排','历史安排'])
  assert.deepEqual(result.arrangementGroups.map(g=>g.rows.map(r=>r.activityId)),[['now-b','now-d'],['past-a','past-c']])
  assert.equal(JSON.stringify(source),before);assert.deepEqual(rows(result).map(r=>r.status),['pending','confirmed','completed','cancelled'])
})
test('registration, explicit activity stage and primary experience condition remain independent',()=>{
  const a=reg('a'),ended=reg('b','confirmed',{activity:{...a.activity,id:'b',sourceStatus:'completed',status:'已结束'}}),ongoing=reg('c','confirmed',{activity:{...a.activity,id:'c',sourceStatus:'in_progress',status:'进行中'}})
  assert.equal(rows(present([a]))[0].arrangement.stage,'','dates do not imply ongoing/completed')
  const all=rows(present([a,ended,ongoing,reg('other','completed')],{status:'completed',activityId:'other'}))
  assert.equal(all.find(r=>r.activityId==='b').arrangement.label,'报名已确认');assert.equal(all.find(r=>r.activityId==='b').arrangement.stage,'活动已结束');assert.equal(all.find(r=>r.activityId==='c').arrangement.stage,'活动进行中')
  assert.deepEqual(all.filter(r=>r.arrangement.experience).map(r=>r.activityId),['other'])
  assert(rows(present([reg('a','completed')],{status:'confirmed',activityId:'a'})).every(r=>!r.arrangement.experience))
})
test('all existing registration actions still describe journey, never a new payment or check-in flow',()=>{
  for(const [status,action] of [['pending','查看报名'],['waitlist','查看候补'],['waitlisted','查看候补'],['cancelled','查看报名记录'],['rescheduled','查看当前状态'],['completed','查看报名与服务'],['confirmed','查看报名与服务'],['future-status','查看报名与服务']]){const row=rows(present([reg(status,status)]))[0];assert.equal(row.arrangement.action,action);if(status==='future-status')assert.equal(row.arrangement.label,'待核对')}
  const a=reg('cancelled-activity');a.activity.sourceStatus='cancelled';a.activity.status='已取消';assert.equal(rows(present([a]))[0].arrangement.action,'查看当前状态')
})
test('settlement summaries reuse canonical rules, retain zero/pass, and keep history pending work visible',()=>{
  const paid={settlementStatus:'confirmed',settlementType:'money',finalAmount:200,paymentStatus:'paid'}
  for(const [extra,expected] of [[{paymentStatus:'paid',quotedAmount:900,finalAmount:900},'金额待确认'],[paid,'已付款'],[{...paid,paymentStatus:'unpaid'},'待付款'],[{...paid,finalAmount:0},'无需付款'],[{...paid,settlementType:'pass',passUnits:2},'已用卡次'],[{...paid,finalAmount:null},'金额待确认']])assert.equal(rows(present([reg('a','confirmed',extra)]))[0].arrangement.payment,expected)
  const past=rows(present([reg('past','completed',{...paid,paymentStatus:'unpaid'})]))[0];assert(past.arrangement.history);assert.equal(past.arrangement.payment,'待付款');assert(past.arrangement.paymentAttention)
})
test('mixed participants are never called all confirmed and family-only rows identify their scope',()=>{
  const a=reg('a','confirmed',{participants:[{id:'person-self',status:'confirmed',selected:true},{id:'child',status:'pending',selected:true}]})
  let row=rows(present([a]))[0];assert.equal(row.arrangement.label,'本人报名已确认');assert.equal(row.arrangement.peopleNote,'参与者状态不同');assert.equal(row.arrangement.people,'2位参与者（含本人）')
  row=rows(present([{...a,participants:[{id:'one',status:'confirmed'},{id:'two',status:'cancelled'}]}]))[0];assert.equal(row.arrangement.label,'报名状态需分别核对');assert.equal(row.arrangement.people,'2位参与者（同行）')
  assert.equal(rows(present([reg('a','pending',{participants:[{id:'child',selected:true,status:'pending'}]})]))[0].arrangement.people,'为同行报名')
  const family=reg('family','cancelled',{participants:[{id:'person-self',status:'cancelled'},{id:'child',status:'confirmed'}]})
  assert.equal(rows(present([family]))[0].arrangement.history,false,'an active companion keeps the session current')
  family.activity.sourceStatus='completed';assert.equal(rows(present([family]))[0].arrangement.history,true,'explicit session end remains distinct from each registration')
})
test('multiple or incomplete orders cannot inherit a misleading paid summary from the first order',()=>{
  const a=reg('a','confirmed',{participants:[{id:'person-self',status:'confirmed'},{id:'child',status:'confirmed'}],settlementStatus:'confirmed',settlementType:'money',finalAmount:200,paymentStatus:'paid'})
  const raw=(batch,payment)=>({sessionId:'a',batchId:batch,settlementStatus:'confirmed',settlementType:'money',finalAmount:200,orderPaymentStatus:payment})
  assert.equal(rows(present([a],{}, {registrations:[raw('same','paid'),raw('same','paid')]}))[0].arrangement.payment,'已付款')
  assert.equal(rows(present([a],{}, {registrations:[raw('first','paid'),raw('other','unpaid')]}))[0].arrangement.payment,'存在待付款订单 · 费用分别核对')
  for(const source of [[raw('same','paid'),raw('same','unpaid')],[raw('only','paid')],[]])assert.notEqual(rows(present([a],{}, {registrations:source}))[0].arrangement.payment,'已付款')
})
test('missing activity, zero period, long text and no registration have honest fallbacks',()=>{
  assert.equal(present([reg('none','none')]).arrangementCount,0)
  const a=reg('long');a.activity.name='长名称'.repeat(80);a.activity.sessionNumber=0;assert.equal(rows(present([a]))[0].arrangement.period,'0');assert.equal(rows(present([a]))[0].arrangement.name,a.activity.name)
  const missing=rows(present([{...a,activity:null}]))[0];assert.equal(missing.arrangement.name,'活动信息待核对');assert.equal(missing.arrangement.date,'时间待公布');assert.equal(missing.arrangement.stage,'')
})
