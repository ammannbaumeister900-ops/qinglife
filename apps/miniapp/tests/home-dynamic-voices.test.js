const {test}=require('node:test'),assert=require('node:assert/strict'),fs=require('fs'),path=require('path'),vm=require('vm')
const root=path.resolve(__dirname,'..'),defer=()=>{let resolve,reject;const promise=new Promise((a,b)=>{resolve=a;reject=b});return{promise,resolve,reject}}
const camp=id=>({id,name:'真实接口营期 '+id,sessionNumber:id==='A'?1:2,sourceStatus:'open',publicListed:true,status:'开放报名',startDate:'2099-01-01',endDate:'2099-01-03',seatsLeft:9})
const row=(i,sessionId='A',extra={})=>({id:'voice-'+i,sessionId,personKey:'person-'+i,author:i%2?'本期轻友':'公开小名',excerpt:'不完整摘句',note:'完整公开原文 '+i+'。🌿 <特殊字符> & 换行\n仍是原文。',...extra})
const response=(sessionId,list,more=false,cursor=null)=>({contractVersion:1,sessionId,list,hasMore:more,nextCursor:cursor})
function world(records=[],options={}){let page;const calls=[],timers=new Map();let timer=0;
 const api={enabled:()=>true,context:async()=>({state:{loggedIn:false,registration:{status:'none'},registrations:{}},activities:[camp('A'),camp('B')]}),homeReadings:async()=>({featured:[{id:'article-1',title:'固定精选'}],stories:[]}),
 campVoicePage:async(sessionId,cursor)=>{calls.push({sessionId,cursor});const list=records.filter(r=>r.sessionId===sessionId),offset=cursor?Number(cursor.split(':')[0]):0;return response(sessionId,list.slice(offset,offset+16),list.length>offset+16,list.length>offset+16?(offset+16)+':next':null)},
 campVoice:async(sessionId,id)=>{calls.push({detail:id,sessionId});return{contractVersion:1,sessionId,item:records.find(r=>r.id===id&&r.sessionId===sessionId)}}};
 const mocks={'../../services/business-api':api,'../../utils/domain':{homeTask:()=>({})},'../../config/brand-content':{...require('../config/brand-content'),voices:[{id:'hardcoded-forbidden'}]}};
 vm.runInNewContext(fs.readFileSync(root+'/pages/home/index.js','utf8'),{Page:p=>page=p,require:id=>mocks[id]||require(path.resolve(root,'pages/home',id)),setInterval:(fn,ms)=>{timers.set(++timer,fn);return timer},clearInterval:id=>timers.delete(id)});
 page.data=JSON.parse(JSON.stringify(page.data));page.setData=p=>Object.assign(page.data,p);return{p:page,api,calls,timers,records};}
test('0/1/3/4/5/37 voices: all actual eligible records reachable with one bounded page, no padding or other-session borrowing',async()=>{
 for(const count of [0,1,3,4,5,37]){const w=world(Array.from({length:count},(_,i)=>row(i)).concat([row(99,'B')]));await w.p.onShow();assert.equal(w.p.data.voiceCards.length,Math.min(4,count));
 const seen=new Set();for(let guard=0;guard<20;guard++){for(const v of w.p.data.voiceCards){seen.add(v.id);assert.equal(v.sessionId,'A');assert.ok(v.fullExcerpt.includes('<特殊字符>'));assert.equal(v.excerpt,v.fullExcerpt)}assert.ok(w.p._voiceRows.length<=16);if(seen.size===count)break;await w.p.nextVoices()}assert.equal(seen.size,count);assert.ok(!seen.has('voice-99'));w.p.onHide();assert.equal(w.timers.size,0)}
})
test('switch is schedule context only; rapid A/B/A rejects expired page replies and preserves tasks/readings',async()=>{
 const w=world([row(1)]);await w.p.onShow();const reading=w.p.data.featuredReadings[0],task=w.p.data.view,old=defer();w.api.campVoicePage=(id)=>id==='B'?old.promise:Promise.resolve(response('A',[row(2)]));
 const b=w.p.chooseCamp({currentTarget:{dataset:{id:'B'}}});assert.equal(w.p.data.voiceCards.length,0);await w.p.chooseCamp({currentTarget:{dataset:{id:'A'}}});old.resolve(response('B',[row(3,'B')]));await b;
 assert.equal(w.p.data.voiceCards[0].id,'voice-2');assert.equal(w.p.data.featuredReadings[0],reading);assert.equal(w.p.data.view,task);w.p.onHide();
})
test('refresh removes revoked content; full text is reread with exact session/id and late detail cannot reopen after switching',async()=>{
 const w=world([row(1)]);await w.p.onShow();await w.p.openVoice({currentTarget:{dataset:{id:'voice-1'}}});assert.equal(w.p.data.expandedVoice.fullExcerpt,row(1).note);assert.equal(w.calls.at(-1).sessionId,'A');w.p.closeVoice();
 const delayed=defer();w.api.campVoice=()=>delayed.promise;const old=w.p.openVoice({currentTarget:{dataset:{id:'voice-1'}}});await w.p.chooseCamp({currentTarget:{dataset:{id:'B'}}});delayed.resolve({contractVersion:1,sessionId:'A',item:row(1)});await old;assert.equal(w.p.data.expandedVoice,null);
 await w.p.chooseCamp({currentTarget:{dataset:{id:'A'}}});w.api.campVoice=async()=>{const e=Error('withdrawn');e.code=404;throw e};await w.p.openVoice({currentTarget:{dataset:{id:'voice-1'}}});assert.equal(w.p.data.voiceCards.length,0);
 w.records.splice(0);await w.p.onShow();assert.equal(w.p.data.voiceCards.length,0);w.p.onHide();
})
test('hide, return, failures and legacy truncated responses never leave stale public cards or restart hidden timers',async()=>{
 const w=world([row(1)]);await w.p.onShow();const d=defer();w.api.campVoicePage=()=>d.promise;const old=w.p.onShow();w.p.onHide();d.resolve(response('A',[row(2)]));await old;assert.equal(w.p.data.voiceCards.length,0);assert.equal(w.timers.size,0);
 w.api.campVoicePage=async()=>{throw Error('offline')};await w.p.onShow();assert.ok(w.p.data.voiceError);assert.equal(w.p.data.voiceCards.length,0);assert.equal(w.p.data.loadError,'');
 w.api.campVoicePage=async()=>[row(1)];await w.p.retryVoices();assert.ok(w.p.data.voiceError);assert.equal(w.p.data.voiceCards.length,0);w.p.onUnload();
})
test('malformed/mixed session payload fails closed; focus, modal, manual pause and lifecycle retain rotation control',async()=>{
 const w=world(Array.from({length:5},(_,i)=>row(i)));await w.p.onShow();assert.equal(w.timers.size,1);w.p.pauseVoiceReading();assert.equal(w.timers.size,0);await w.p.openVoice({currentTarget:{dataset:{id:'voice-0'}}});assert.equal(w.timers.size,0);w.p.closeVoice();assert.equal(w.timers.size,1);w.p.toggleVoicePause();assert.equal(w.timers.size,0);
 w.api.campVoicePage=async()=>response('A',[row(2,'B')]);await w.p.onShow();assert.equal(w.p.data.voiceCards.length,0);assert.ok(w.p.data.voiceError);w.p.onHide();
})

test('150 Unicode points stay intact with anonymous fallback; 151 is rejected without truncation',()=>{
 const presentation=require('../utils/home-presentation'),note='🌿'.repeat(149)+'。';
 const records=presentation.publicVoiceRecords(response('A',[row(1,'A',{note,author:'',excerpt:'不完整摘句'})]),'A');
 assert.equal(records[0].author,'本期轻友');assert.equal(records[0].excerpt,note);assert.equal(Array.from(records[0].fullExcerpt).length,150);
 assert.throws(()=>presentation.publicVoiceRecords(response('A',[row(1,'A',{note:note+'多'})]),'A'));
})