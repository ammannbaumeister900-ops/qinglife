const {test}=require('node:test')
const assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path'),vm=require('node:vm')
function page(file,api,storage=new Map()) {
  let p;const toasts=[]
  const wx={getStorageSync:k=>storage.get(k),setStorageSync:(k,v)=>storage.set(k,JSON.parse(JSON.stringify(v))),removeStorageSync:k=>storage.delete(k),showToast:r=>toasts.push(r.title),switchTab:()=>{}}
  vm.runInNewContext(fs.readFileSync(path.resolve(__dirname,'../'+file),'utf8'),{Page:v=>p=v,require:name=>name.includes('discovery')?{activities:list=>list}:name.includes('invitation-presets')?require('../utils/invitation-presets'):api,wx,Date,Math,console,decodeURIComponent})
  p.data=JSON.parse(JSON.stringify(p.data));p.setData=patch=>{for(const [key,val] of Object.entries(patch)){const parts=key.split('.');if(parts.length===2)p.data[parts[0]][parts[1]]=val;else p.data[key]=val}}
  return {p,storage,toasts}
}
const code='0123456789abcdef0123456789abcdef',otherCode='abcdef0123456789abcdef0123456789'
const profile={id:'owner-a',name:'虚构轻友',nickname:'小雨',phone:'13800001234',birthDate:'1990-01-01',heightCm:165,weightKg:55.5,province:'上海市',city:'上海市'}
const fields={cleanBodyGoals:['调理'],dietPreference:'荤素各半',waterIntakeMl:1500,wakeTime:'07:00',sleepTime:'23:00',bowelStatus:'1次/天',energyStatus:'一般',exerciseStatus:'偶尔运动',emotionalStatus:['平静'],healthConditions:['无'],referralSource:'虚构测试',sensitiveConsent:true}
test('group form logs in before resolving the period; draft and submission preserve that link',async()=>{
  let saved,calls=[];const storage=new Map()
  const api={friendProfile:async()=>{calls.push('login-profile');return profile},assessmentInvitation:async value=>{calls.push(value);return {sessionId:'period-a',sessionNumber:101,sessionName:'本期'}},submitFriendAssessment:async payload=>{saved=payload;return {assessmentCount:1}}}
  const {p}=page('pages/friend-registration/index.js',api,storage);await p.onLoad({invite:code})
  assert.deepEqual(calls,['login-profile',code]);assert.equal(p.data.sessionContext.sessionNumber,101)
  p.data.form={...p.data.form,...fields};p.saveDraft();const retryId=p.data.form.clientRequestId;await p.submit()
  assert.equal(saved.invitationCode,code);assert.equal(saved.clientRequestId,retryId);assert.equal(saved.customerId,undefined);assert.equal(p.data.submitted,true);assert.equal(storage.size,0)
})
test('period drafts do not contaminate a different period or a different logged-in account',async()=>{
  const storage=new Map();const api={friendProfile:async()=>profile,assessmentInvitation:async()=>({sessionId:'period-a',sessionNumber:101})}
  const {p:a}=page('pages/friend-registration/index.js',api,storage);await a.onLoad({invite:code});a.data.form.name='甲的本期草稿';a.saveDraft()
  const {p:b}=page('pages/friend-registration/index.js',api,storage);await b.onLoad({invite:otherCode});assert.equal(b.data.form.name,profile.name)
  const {p:c}=page('pages/friend-registration/index.js',{...api,friendProfile:async()=>({...profile,id:'owner-b'})},storage);await c.onLoad({invite:code});assert.equal(c.data.form.name,profile.name)
  const {p:d}=page('pages/friend-registration/index.js',api,storage);await d.onLoad({invite:code});assert.equal(d.data.form.name,'甲的本期草稿')
})
test('expired link stays blocked on retry; an account switch resets rather than submitting the previous form',async()=>{
  let owner=profile,creates=0
  const api={friendProfile:async()=>owner,assessmentInvitation:async()=>({sessionId:'a',sessionNumber:101}),submitFriendAssessment:async()=>{creates++;return {assessmentCount:1}}}
  const {p,toasts}=page('pages/friend-registration/index.js',api);await p.onLoad({invite:code});p.data.form={...p.data.form,...fields};owner={...profile,id:'owner-b'};await p.submit()
  assert.equal(creates,0);assert.equal(p.customerId,'owner-b');assert.equal(p.data.form.sensitiveConsent,false);assert.equal(toasts.at(-1),'账号已切换，请重新确认登记内容')
  const {p:q}=page('pages/friend-registration/index.js',{friendProfile:async()=>profile,assessmentInvitation:async()=>{throw Error('本期登记已结束')}});await q.onLoad({invite:code});assert.equal(q.data.loadError,'本期登记已结束');await q.onLoad({currentTarget:{}});assert.equal(q.data.invitationCode,code);assert.equal(q.data.loadError,'本期登记已结束')
})
test('operator can choose any listed period; share payload contains only the prepared miniapp path',async()=>{
  const calls=[];let grant=1
  const api={request:async(p,m,b)=>{calls.push({p,m,b});if(p==='/me')return {name:'运营',can_operate:grant};if(p==='/assessment-sessions')return [{id:'next',sessionNumber:102}];if(p==='/sessions/next/assessment-invitations')return {path:'/pages/friend-registration/index?invite='+code,sessionNumber:102};return []}}
  const {p}=page('staff/workspace/index.js',api);p.onLoad({view:'assessment-share'});await p.refresh();await p.prepareAssessmentShare({currentTarget:{dataset:{id:'next'}}})
  assert.equal(p.data.assessmentSessions[0].id,'next');assert.equal(p.onShareAppMessage().path,'/pages/friend-registration/index?invite='+code);assert.equal(calls.at(-1).m,'POST');assert.deepEqual(Object.keys(calls.at(-1).b),[])
  grant=0;await p.refresh();assert.equal(p.data.assessmentSharePath,'');assert.equal(p.data.canOperate,false);assert.equal(p.onShareAppMessage().path,'/pages/home/index')
})
test('referral entry uses server eligibility, displays only returned records, and clears a stale share on failure',async()=>{
  let fail=false
  const api={context:async()=>{if(fail)throw Error('登录失效');return {state:{loggedIn:true,invitationEligible:true},activities:[{id:'a',sessionNumber:103,canRegister:true},{id:'closed',canRegister:false}]}},myReferrals:async()=>[{registrationId:'r',nickname:'小雨',registrationStatus:'pending',sessionNumber:103}],createInvitation:async id=>{assert.equal(id,'a');return {path:'/pages/camp-flow/index?invite='+code}}}
  const {p}=page('pages/referrals/index.js',api);await p.onShow();assert.equal(p.data.sessions.length,1);assert.equal(p.data.records[0].statusText,'待确认');await p.prepare({currentTarget:{dataset:{id:'a'}}});assert.ok(p.onShareAppMessage().path.includes(code))
  fail=true;await p.refresh();assert.equal(p.data.records.length,0);assert.equal(p.data.sharePath,'');assert.equal(p.onShareAppMessage().path,'/pages/home/index')
})
