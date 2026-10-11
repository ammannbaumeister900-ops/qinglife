const {test}=require('node:test'),assert=require('node:assert/strict');
const {fixture,tick}=require('./helpers/identity-fixture');
function setup(){
 const f=fixture(),rows={A:[{phase:'after',note:'A原稿',anonymous:1,sharedNote:'历史匿名公开快照',shareAnonymous:1,shareConsent:1,status:'published',revision:3}],B:[]};
 const handle=(o,actor,route)=>{
  if(route==='/reflections/s') {f.response(o,{registrationId:'reg-'+actor,authorName:actor,canAfter:true,records:rows[actor]});return false;}
  if(route==='/reflections'&&o.method==='PUT') {const old=rows[actor][0]||{},data=o.data;const next={...old,note:data.note,phase:data.phase,anonymous:data.anonymous?1:0,revision:(old.revision||0)+1};if(data.action==='submit')Object.assign(next,{sharedNote:data.note,shareAnonymous:0,shareConsent:1,status:'submitted'});rows[actor]=[next];f.response(o,next);return false;}
  if(route==='/reflections/s/after/share'){rows[actor][0]={...rows[actor][0],shareConsent:0,status:'withdrawn',revision:rows[actor][0].revision+1};f.response(o,null);return false;}
 };f.intercept(handle);return{f,rows,handle};
}
async function open(f){const p=f.page('camp-reflection','',{id:'s'});await tick();await p.load();assert.equal(p.data.error,'');return p;}
const input=(p,note)=>p.onInput({detail:{value:note}});
test('same editor private save/readback keeps old anonymous public snapshot; new submit uses nickname',async()=>{
 const{f,rows}=setup(),p=await open(f);input(p,'修改后的私人原稿');await p.savePrivate();assert.equal(rows.A[0].sharedNote,'历史匿名公开快照');assert.equal(rows.A[0].shareAnonymous,1);assert.equal(rows.A[0].status,'published');await p.load();assert.equal(p.data.note,'修改后的私人原稿');await p.save();assert.equal(rows.A[0].note,rows.A[0].sharedNote);assert.equal(rows.A[0].status,'submitted');assert.equal(rows.A[0].shareAnonymous,0);assert.equal(p.toggleAnonymous,undefined);
});
test('A → B → A, hidden page and invalid identity never expose another draft',async()=>{
 const{f}=setup(),p=await open(f);input(p,'未保存A');f.switchActor('B');assert.equal(p.data.note,'');assert.equal(p.data.context.registrationId,undefined);await p.load();assert.equal(p.data.note,'');f.switchActor('A');await p.load();assert.equal(p.data.note,'A原稿');p.onHide();assert.equal(p.data.note,'');await p.onShow();assert.equal(p.data.note,'A原稿');f.switchActor(null);assert.equal(p.data.note,'');input(p,'不能操作');await p.save();assert.equal(p.data.note,'');
});
test('delayed A load cannot revive content or change loading in B',async()=>{
 const{f,handle}=setup(),p=await open(f);let old;f.intercept((o,actor,route)=>{if(route==='/reflections/s'&&actor==='A'){old=o;return false;}return handle(o,actor,route);});const pending=p.load();await tick();f.switchActor('B');await p.load();assert.equal(p.data.error,'');f.response(old,{registrationId:'reg-A',authorName:'A',canAfter:true,records:[{phase:'after',note:'迟到A',revision:1}]});await pending;assert.equal(p.data.note,'');assert.equal(p.data.error,'');assert.equal(p.data.loading,false);
});
test('pending A write cannot succeed visibly in B or write again with B credentials',async()=>{
 const{f,handle}=setup(),p=await open(f);let old;f.intercept((o,actor,route)=>{if(route==='/reflections'&&actor==='A'){old=o;return false;}return handle(o,actor,route);});input(p,'A提交');const pending=p.save();await tick();f.switchActor('B');await p.load();f.response(old,{phase:'after',note:'A提交',revision:4,status:'submitted',shareConsent:1});await pending;assert.equal(p.data.note,'');assert.equal(f.toasts.includes('已提交，等待审核'),false);await p.save();assert.equal(f.requests.filter(r=>r.route==='/reflections').length,1);
});
test('same-owner failure retains text and allows explicit retry; 150 Unicode limit never truncates history',async()=>{
 const{f,rows}=setup(),p=await open(f);rows.A[0].note='🌿'.repeat(1000);await p.load();assert.equal(p.data.note,rows.A[0].note);await p.savePrivate();assert.equal(f.requests.filter(r=>r.route==='/reflections').length,0);input(p,'🌿'.repeat(150));let fail=true;f.intercept((o,actor,route)=>{if(route==='/reflections'){if(fail)o.fail({errMsg:'isolated failure'});else f.response(o,{phase:'after',note:o.data.note,revision:4,status:'private'});return false;}});await p.savePrivate();assert.equal(p.data.note,'🌿'.repeat(150));assert.equal(p.data.saving,false);fail=false;await p.savePrivate();assert.equal(p.data.revision,4);
});
test('withdraw retains the original; changing identity during confirmation prevents the request',async()=>{
 const{f,rows}=setup(),p=await open(f);let pending=p.withdraw();f.modals.at(-1).success({confirm:true});await pending;assert.equal(rows.A[0].note,'A原稿');assert.equal(rows.A[0].shareConsent,0);assert.equal(p.data.note,'A原稿');pending=p.withdraw();f.switchActor('B');f.modals.at(-1).success({confirm:true});await pending;assert.equal(f.requests.filter(r=>r.route.endsWith('/share')).length,1);assert.equal(p.data.note,'');
});

test('expired identity clears the editor; explicit retry restores the legitimate owner through existing login',async()=>{
 const{f,handle}=setup(),p=await open(f);let expired=true;
 f.intercept((o,actor,route)=>{if(route==='/reflections/s'&&expired){expired=false;f.response(o,null,401);return false;}return handle(o,actor,route);});
 await p.load();assert.equal(p.data.note,'');assert.equal(p.data.context.registrationId,undefined);assert.equal(f.storage.has(f.key),false);
 await p.load();assert.equal(p.data.error,'');assert.equal(p.data.note,'A原稿');assert.equal(f.loginCalls(),1);
});
test('a pending read cannot restore private content after hiding the editor',async()=>{
 const{f,handle}=setup(),p=await open(f);let delayed;
 f.intercept((o,actor,route)=>{if(route==='/reflections/s'){delayed=o;return false;}return handle(o,actor,route);});
 const pending=p.load();await tick();p.onHide();f.response(delayed,{registrationId:'reg-A',canAfter:true,records:[{phase:'after',note:'隐藏后不能恢复',revision:3}]});await pending;
 assert.equal(p.data.note,'');assert.equal(p.data.context.registrationId,undefined);assert.equal(p.data.saving,false);
});

test('identity switch during owner preflight prevents transmitting the previous draft',async()=>{
 const{f,handle}=setup(),p=await open(f);let ownerRead;
 f.intercept((o,actor,route)=>{if(route==='/me/overview'&&actor==='A'){ownerRead=o;return false;}return handle(o,actor,route);});
 input(p,'不能发给B的A原稿');const pending=p.save();await tick();f.switchActor('B');
 f.response(ownerRead,{customerId:'A',profile:{nickname:'A'},dailyRecords:[]});await pending;
 assert.equal(p.data.note,'');assert.equal(f.requests.filter(r=>r.route==='/reflections'&&r.method==='PUT').length,0);
});
test('unknown or changed server owner under the same credential blocks private read and submission',async()=>{
 for(const customerId of ['', 'B']) {
  const{f,handle}=setup(),p=await open(f);input(p,'原身份的未保存内容');
  f.intercept((o,actor,route)=>{if(route==='/me/overview'){f.response(o,{customerId,profile:{nickname:'synthetic'},dailyRecords:[]});return false;}return handle(o,actor,route);});
  await p.save();assert.equal(p.data.note,'');assert.equal(p.data.context.registrationId,undefined);assert.equal(f.requests.filter(r=>r.route==='/reflections'&&r.method==='PUT').length,0);
 }
});

test('withdraw readback interrupted by an identity switch cannot restore the old unsaved original',async()=>{
 const{f,handle}=setup(),p=await open(f);let readback;
 f.intercept((o,actor,route)=>{if(route==='/reflections/s'&&actor==='A'){readback=o;return false;}return handle(o,actor,route);});
 input(p,'撤回前A的未保存私人原稿');const pending=p.withdraw();f.modals.at(-1).success({confirm:true});await tick();assert.ok(readback);
 f.switchActor('B');await p.load();assert.equal(p.data.note,'');assert.equal(p.data.context.registrationId,'reg-B');
 const restored=[],apply=p.setData;p.setData=patch=>{if(patch.note)restored.push(patch.note);apply(patch);};
 f.response(readback,{registrationId:'reg-A',authorName:'A',canAfter:true,records:[{phase:'after',note:'A原稿',revision:4,status:'withdrawn'}]});await pending;
 assert.equal(restored.includes('撤回前A的未保存私人原稿'),false,'no transient stale private draft in setData');assert.equal(p.data.note,'');assert.equal(p.data.context.registrationId,'reg-B');assert.equal(p.data.loading,false);
});
