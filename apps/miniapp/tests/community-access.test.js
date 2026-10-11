const {test}=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm'),path=require('node:path');
const {fixture,tick}=require('./helpers/identity-fixture');
function setup(eligible,minor=false,phase='after'){
 const f=fixture(),saved={A:'私人原文',B:'B原文'},publicState={A:{note:'历史匿名公开',anonymous:1,status:'published',revision:3},B:{note:'历史匿名公开',anonymous:1,status:'published',revision:3}};let gate=eligible,minorFlag=minor,intercept;
 const context=a=>({registrationId:'reg-'+a,authorName:a,canAfter:phase==='after',canBefore:phase==='before',unifiedAfter:phase==='after',originalVersion:'v',experienceRecord:phase==='after'?{id:'e-'+a,note:saved[a],energy:4}:null,records:[{phase,note:saved[a],sharedNote:publicState[a].note,shareAnonymous:publicState[a].anonymous,shareConsent:1,status:publicState[a].status,revision:publicState[a].revision}]});
 const session=id=>({id,name:'测试期次',sessionNumber:id==='s'?1:2,status:'open',capacity:10,startDate:'2099-01-01',endDate:'2099-01-03',days:[]});
 f.intercept((o,a,r)=>{
  if(intercept&&intercept(o,a,r)===false)return false;
  if(r==='/me/overview'){f.response(o,{customerId:a,invitationEligible:gate,profile:{nickname:a,minor:minorFlag},registrations:[],experienceRecords:[]});return false;}
  if(r==='/reflections/s'){f.response(o,context(a));return false;}
  if(r==='/reflections'){saved[a]=o.data.note;if(o.data.action==='submit')publicState[a]={note:o.data.note,anonymous:0,status:'submitted',revision:4};f.response(o,phase==='after'?{context:context(a)}:{...o.data,revision:4,status:'private'});return false;}
  if(r.endsWith('/share')){f.response(o,null);return false;}
  if(r==='/sessions'){f.response(o,[session('s'),session('next')]);return false;}
  if(r==='/sessions/s'){f.response(o,session('s'));return false;}
  if(r==='/me/referrals'){f.response(o,[]);return false;}
  if(r.endsWith('/invitations')){f.response(o,{path:'/pages/camp-flow/index?id=next&invite=synthetic'});return false;}
 });
 return {f,saved,context,setGate:v=>{gate=v},intercept:fn=>{intercept=fn}};
}
async function editor(s){const p=s.f.page('camp-reflection','',{id:'s'});await tick();await p.load();assert.equal(p.data.error,'');return p;}
function referral(f){
 let p;const root=path.resolve(__dirname,'..');
 vm.runInNewContext(fs.readFileSync(path.join(root,'pages/referrals/index.js'),'utf8'),{Page:v=>p=v,require:id=>id.includes('business-api')?f.api:require(path.resolve(root,'pages/referrals',id)),wx:f.wx});
 p.data=JSON.parse(JSON.stringify(p.data));p.setData=patch=>Object.assign(p.data,patch);return p;
}
const input=(p,value)=>p.onInput({detail:{value}}),writes=f=>f.requests.filter(r=>r.method==='PUT'||r.method==='POST');
test('all ages and unknown eligibility retain the same intentional submit-for-review and private save',async()=>{
 for(const [gate,minor] of [[false,true],[false,false],[undefined,false],[null,false],['true',false],[1,false],[true,true]]){
  const s=setup(gate,minor),p=await editor(s);assert.equal(writes(s.f).length,0);
  input(p,'用户主动提交的原文');await p.save();const post=writes(s.f)[0].data;
  assert.equal(post.action,'submit');assert.equal(post.anonymous,false);assert.equal(post.shareConsent,true);
  assert.equal(p.data.status,'submitted');assert.equal(p.data.context.records[0].sharedNote,'用户主动提交的原文');
  input(p,'仅保存的修改');await p.savePrivate();assert.equal(s.saved.A,'仅保存的修改');
  assert.equal(p.data.context.records[0].sharedNote,'用户主动提交的原文');assert.equal(p.data.status,'submitted');assert.equal(p.data.context.experienceRecord.energy,4);
 }
});
test('before submit and private compatibility do not depend on invitation eligibility',async()=>{const s=setup(false,true,'before'),p=await editor(s);input(p,'活动前文字');await p.save();assert.equal(writes(s.f)[0].data.action,'submit');await p.savePrivate();assert.equal(writes(s.f)[1].data.phase,'before');assert.equal(s.saved.A,'活动前文字');});
test('invitation eligibility changing never changes the editor or authorized pending submission',async()=>{
 const s=setup(true),p=await editor(s);s.setGate(false);input(p,'普通轻友原稿');await p.save();assert.equal(writes(s.f).length,1);assert.equal(writes(s.f)[0].data.shareConsent,true);assert.equal(p.data.status,'submitted');
 input(p,'仅保存草稿');await p.savePrivate();assert.equal(s.saved.A,'仅保存草稿');assert.equal(p.data.context.records[0].sharedNote,'普通轻友原稿');
});
test('missing nickname and rejected submission retain input and never claim acceptance',async()=>{
 const s=setup(false,true),p=await editor(s);input(p,'保留我的输入');p.data.context.authorName='';await p.save();assert.equal(writes(s.f).length,0);
 p.data.context.authorName='A';s.intercept((o,a,r)=>{if(r==='/reflections'){o.success({statusCode:200,data:{code:403,msg:'必要授权未满足'}});return false;}});
 await p.save();assert.equal(p.data.note,'保留我的输入');assert.equal(p.data.saving,false);assert.equal(s.f.toasts.includes('已提交，等待审核'),false);assert.equal(s.saved.A,'私人原文');
});
test('identity switch clears private drafts; delayed A read cannot replace B before submission',async()=>{
 const s=setup(true),p=await editor(s);let pending;
 s.intercept((o,a,r)=>{if(a==='A'&&r==='/reflections/s'){pending=o;return false;}});
 const read=p.load();await tick();s.f.switchActor('B');assert.equal(p.data.note,'');s.setGate(false);await p.load();s.f.response(pending,s.context('A'));await read;assert.equal(p.data.context.registrationId,'reg-B');assert.equal(p.data.note,'B原文');
});
test('unknown age keeps historical anonymous content readable and retractable',async()=>{const s=setup(false),p=await editor(s);assert.equal(p.data.shared,true);assert.equal(p.data.note,'私人原文');const w=p.withdraw();s.f.modals.at(-1).success({confirm:true});await w;assert.equal(s.f.requests.filter(r=>r.method==='DELETE').length,1);assert.equal(s.saved.A,'私人原文');});
test('referral direct route and revoked eligibility cannot send invitation requests',async()=>{
 const s=setup(false),p=referral(s.f);await p.onShow();await p.prepare();assert.equal(p.data.eligible,false);assert.equal(writes(s.f).length,0);
 s.setGate(true);await p.refresh();assert.equal(p.data.eligible,true);s.setGate(false);await p.prepare();assert.equal(p.data.eligible,false);assert.equal(writes(s.f).length,0);assert.equal(p.onShareAppMessage().path,'/pages/home/index');p.onUnload();
});
test('late invitation after switching identity never installs or shares the previous owner path',async()=>{
 const s=setup(true),p=referral(s.f);await p.onShow();let pending;
 s.intercept((o,a,r)=>{if(r.endsWith('/invitations')){pending=o;return false;}});
 const prepare=p.prepare();await tick();assert.ok(pending);s.f.switchActor('B');s.setGate(false);await p.refresh();s.f.response(pending,{path:'/private-owner-A-invite'});await prepare;assert.equal(p.data.sharePath,'');assert.equal(p.data.eligible,false);assert.equal(p.onShareAppMessage().path,'/pages/home/index');p.onUnload();
});
test('old camp-flow invite action cannot bypass the same gate',async()=>{
 const s=setup(false),p=s.f.page('camp-flow','invite',{id:'s'});await p.refresh();assert.equal(p.data.view,'invite');await p.prepareInvitation({currentTarget:{dataset:{id:'next'}}});assert.equal(writes(s.f).length,0);
 s.setGate(true);await p.refresh();await p.prepareInvitation({currentTarget:{dataset:{id:'next'}}});assert.ok(p.data.sharePath);s.f.switchActor('B');assert.equal(p.data.sharePath,'');assert.equal(p.data.state.invitationEligible,false);p.onUnload();
});
