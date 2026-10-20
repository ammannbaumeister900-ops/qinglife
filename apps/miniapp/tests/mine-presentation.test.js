const {test}=require('node:test')
const assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path'),vm=require('node:vm')
const root=path.resolve(__dirname,'..'),plain=x=>JSON.parse(JSON.stringify(x))
function parse(xml=fs.readFileSync(path.join(root,'pages/mine/index.wxml'),'utf8')) {
  const tree={children:[]},stack=[tree]
  for(const token of xml.match(/<(?:[^<>"']|"[^"]*"|'[^']*')+>|[^<]+/g)||[]) {
    if(token.startsWith('</')){assert.equal(stack.pop().tag,token.match(/^<\/([\w-]+)/)[1]);continue}
    if(!token.startsWith('<')){stack.at(-1).children.push({text:token});continue}
    const tag=token.match(/^<([\w-]+)/)[1],attrs={}
    for(const a of token.slice(tag.length+1).replace(/\/?\s*>$/,'').matchAll(/([\w:-]+)(?:\s*=\s*(?:"([^"]*)"|'([^']*)'))?/g))attrs[a[1]]=a[2]??a[3]??true
    const node={tag,attrs,children:[]};stack.at(-1).children.push(node);if(!token.endsWith('/>'))stack.push(node)
  }
  assert.equal(stack.length,1);return tree
}
function value(template,scope){
  if(template===true)return true
  const evaluate=code=>{try{return vm.runInNewContext(code,scope)}catch(e){if(e.name==='TypeError'||e.name==='ReferenceError')return undefined;throw e}}
  const binding=template.match(/^{{([\s\S]*?)}}$/)
  if(binding&&!binding[1].includes('}}'))return evaluate(binding[1])
  return template.replace(/{{([\s\S]*?)}}/g,(_,code)=>evaluate(code)??'')
}
function render(children,scope){
  const result=[];let branch=false
  for(const n of children){
    if('text'in n){result.push({text:String(value(n.text,scope)??'')});continue}
    const a=n.attrs,scopes=a['wx:for']?(value(a['wx:for'],scope)||[]).map((item,index)=>({...scope,item,index})):[scope]
    for(const current of scopes){
      let show=true
      if('wx:if'in a){show=!!value(a['wx:if'],current);branch=show}
      else if('wx:elif'in a){show=!branch&&!!value(a['wx:elif'],current);branch ||= show}
      else if('wx:else'in a){show=!branch;branch=true}else branch=false
      if(show)result.push({tag:n.tag,attrs:Object.fromEntries(Object.entries(a).filter(([k])=>!k.startsWith('wx:')).map(([k,v])=>[k,value(v,current)])),children:render(n.children,current)})
    }
  }
  return result
}
const flatten=nodes=>nodes.flatMap(n=>[n,...flatten(n.children||[])])
const has=(n,c)=>String(n.attrs?.class||'').split(' ').includes(c)
const text=n=>flatten([n]).filter(x=>'text'in x).map(x=>x.text).join('').trim()
// The service keeps the credential; this fixture only models whether one exists on this device.
function fixture(options={}){
  let page
  const guest=options.state?.loggedIn===false
  const state={...plain(require('../utils/store').defaultState()),backend:true,loggedIn:true,phoneLinked:true,profile:{nickname:'小禾'},passAccounts:[],passLedger:[],currentPassBalance:0,...options.state}
  const context={state},links=[],copies=[],toasts=[],tabs=[],staffCalls=[],modals=[],identity={scope:{token:guest?'':'synthetic'},watchers:[]}
  let contextCalls=0
  const signIn=()=>{identity.scope={token:'synthetic'}},signOut=()=>{identity.scope={token:''}}
  const business={enabled:()=>options.backend!==false,identityScope:()=>identity.scope,watchIdentity:fn=>{identity.watchers.push(fn);return()=>{}},
    context:async()=>{contextCalls++;return context},contact:async()=>({name:'桃子',wechat:'qinglife2014'}),login:async()=>signIn(),logout:async()=>signOut()}
  const staff={request:async p=>{staffCalls.push(p);if(!options.staff)throw Error('forbidden');return {}}}
  const mocks={'../../services/business-api':business,'../../services/staff-api':staff,'../../utils/store':{getState:()=>plain(state)},'../../utils/navigation':{navigateTo:x=>links.push(x.url),switchTab:x=>links.push(x.url)}}
  vm.runInNewContext(fs.readFileSync(path.join(root,'pages/mine/index.js'),'utf8'),{Page:p=>page=p,wx:{navigateTo:x=>links.push(x.url),showToast:x=>toasts.push(x.title),showModal:x=>modals.push(x),setClipboardData:x=>{copies.push(x.data);x.success()}},require:id=>mocks[id]||require(path.resolve(root,'pages/mine',id))})
  page.data=plain(page.data);page.setData=p=>Object.assign(page.data,p);page.getTabBar=()=>({setData:p=>tabs.push(p)})
  const nodes=()=>flatten(render(parse().children,page.data))
  return {page,context,business,staff,links,copies,toasts,tabs,staffCalls,modals,nodes,signIn,signOut,identity,contextCalls:()=>contextCalls}
}
const event=view=>({currentTarget:{dataset:{view}}})
const names=(nodes,row,label)=>nodes.filter(n=>has(n,row)).map(n=>text(flatten([n]).find(c=>has(c,label))))
const pageText=f=>f.nodes().filter(n=>'text'in n).map(n=>n.text).join('')
const tapCount=(f,handler)=>f.nodes().filter(n=>n.attrs?.bindtap===handler).length
const directoryVisible=f=>names(f.nodes(),'asset','asset-name').length===3&&f.nodes().some(n=>has(n,'contact-section'))&&f.nodes().some(n=>n.attrs?.['data-view']==='privacy')
const rejected=props=>Object.assign(new Error(props.message||''),props)

test('directory separates personal participation from secondary services; staff and logout stay after contact',async()=>{
  const f=fixture({staff:true,state:{passAccounts:[{id:1}],passLedger:[]}});await f.page.onShow()
  const nodes=f.nodes()
  assert.deepEqual(names(nodes,'asset','asset-name'),['轻友登记','全部参与经历','私人记录'])
  assert.deepEqual(names(nodes,'service-entry','service-name').slice(0,4),['内容收藏','邀请轻友','历史打卡','设置与隐私'])
  const at=c=>nodes.findIndex(n=>has(n,c))
  assert(at('asset')<at('pass-ledger')&&at('pass-ledger')<at('service-section'))
  assert(at('contact-section')<at('staff-entry')&&at('staff-entry')<at('logout-area'))
  assert(!nodes.some(n=>has(n,'card')||has(n,'pill')||n.tag==='image'))
})

test('guest is an ordinary state: one login control, full directory, no private request or error',async()=>{
  const f=fixture({staff:true,state:{loggedIn:false}});await f.page.onShow()
  assert.equal(f.contextCalls(),0);assert.deepEqual(f.staffCalls,[])
  assert.equal(text(f.nodes().find(n=>has(n,'profile-title'))),'未登录')
  assert.equal(f.page.data.avatarText,'轻')
  assert.equal(tapCount(f,'login'),1);assert(directoryVisible(f))
  assert(!f.nodes().some(n=>has(n,'identity-notice')||has(n,'logout-area')||has(n,'invite-entry')||has(n,'staff-entry')))
  f.page.setData({loginLoading:true});const login=f.nodes().find(n=>n.attrs?.bindtap==='login')
  assert.equal(login.attrs.disabled,true);assert.equal(login.attrs.loading,true);assert.equal(text(login),'正在登录…')
})

test('a private entry explains login in place and continues to the original destination afterwards',async()=>{
  const f=fixture({state:{loggedIn:false}});await f.page.onShow()
  f.page.openFlow(event('records'));assert.deepEqual(f.links,[])
  assert(pageText(f).includes('使用「私人记录」需要先登录'))
  assert.equal(text(f.nodes().find(n=>n.attrs?.bindtap==='login')),'微信登录并继续')
  f.page.dismissPending();assert(!f.nodes().some(n=>has(n,'identity-notice')));assert.equal(tapCount(f,'dismissPending'),0)
  f.page.openFlow(event('records'));f.context.state.loggedIn=true;await f.page.login()
  assert.deepEqual(f.links,['/pages/personal/index?view=records&id=']);assert.equal(f.toasts.at(-1),'登录成功')
  assert.equal(f.page.data.pendingEntry,null);assert.equal(text(f.nodes().find(n=>has(n,'profile-title'))),'小禾')
  const g=fixture({state:{loggedIn:false}});await g.page.onShow();g.page.openFriendRegistration();assert.deepEqual(g.links,[])
  g.context.state.loggedIn=true;await g.page.login();assert.deepEqual(g.links,['/pages/friend-registration/index'])
  for(const view of ['experiences','collections']){const h=fixture({state:{loggedIn:false}});await h.page.onShow();h.page.openFlow(event(view));assert.deepEqual(h.links,[]);assert.equal(h.page.data.pendingEntry.key,view)}
  const open=fixture({state:{loggedIn:false}});await open.page.onShow();open.page.openFlow(event('archive'));open.page.openFlow(event('privacy'))
  assert.deepEqual(open.links,['/pages/friend-flow/index?view=archive','/pages/mine-flow/index?view=privacy'])
})

test('login cancellation, network failure and server failure are distinct and never show raw messages',async()=>{
  const cases=[[{errMsg:'login:fail cancel'},'info','已取消登录','微信登录'],[{message:'request:fail timeout'},'error','网络不太稳定，登录没有完成','重新登录'],[{message:'Internal Server Error #42',code:500},'error','登录没有完成，请重试','重新登录']]
  for(const [error,kind,copy,action] of cases){
    const f=fixture({state:{loggedIn:false}});await f.page.onShow();f.page.openFlow(event('records'))
    f.business.login=async()=>{throw rejected(error)};await f.page.login()
    assert.equal(f.page.data.notice.kind,kind);assert(pageText(f).includes(copy));assert(!pageText(f).includes(error.errMsg||error.message))
    assert.equal(f.page.data.loginLoading,false);assert.deepEqual(f.toasts,[]);assert.deepEqual(f.links,[])
    assert.equal(f.page.data.pendingEntry.key,'records');assert(directoryVisible(f))
    f.page.dismissPending();assert.equal(text(f.nodes().find(n=>n.attrs?.bindtap==='login')),action)
  }
})

test('WeChat login without a readable profile is not announced as success and keeps a retry',async()=>{
  const f=fixture({state:{loggedIn:false}});await f.page.onShow();f.page.openFlow(event('records'))
  f.business.context=async()=>{throw Error('request:fail timeout')};await f.page.login()
  assert(!f.toasts.includes('登录成功'));assert.deepEqual(f.links,[]);assert.equal(f.page.data.signedIn,true)
  assert.equal(text(f.nodes().find(n=>has(n,'profile-title'))),'已登录')
  assert(pageText(f).includes('网络不太稳定，个人信息暂时没能读取'));assert.equal(text(f.nodes().find(n=>n.attrs?.bindtap==='onShow')),'重试')
  assert.equal(tapCount(f,'login'),0);assert.equal(tapCount(f,'logout'),1);assert(directoryVisible(f))
  f.context.state.loggedIn=true;f.business.context=async()=>f.context;await f.page.onShow()
  assert.equal(f.page.data.profileError,'');assert.equal(text(f.nodes().find(n=>has(n,'profile-title'))),'小禾')
})

test('signed-in without a nickname offers registration without claiming it is complete',async()=>{
  const f=fixture({state:{profile:{nickname:''}}});await f.page.onShow()
  assert.equal(text(f.nodes().find(n=>has(n,'profile-title'))),'还没有登记小名');assert(pageText(f).includes('已用微信登录'))
  const entry=f.nodes().find(n=>has(n,'profile-nickname-entry'));assert.equal(text(entry),'登记小名')
  f.page.openFriendRegistration();assert.deepEqual(f.links,['/pages/friend-registration/index'])
  const long=fixture({state:{profile:{nickname:'一个特别特别长的轻友小名用于检查换行与对齐是否仍然清楚'}}});await long.page.onShow()
  assert.equal(long.page.data.avatarText,'一');assert(long.nodes().some(n=>n.text?.includes('一个特别特别长')))
})

test('expired login returns to the full guest directory with an in-place message and no private data',async()=>{
  const f=fixture({staff:true,state:{passAccounts:[{id:1}],currentPassBalance:3}});await f.page.onShow()
  assert(f.nodes().some(n=>has(n,'pass-ledger')))
  // The service drops the credential and notifies watchers before rejecting with 401.
  f.business.context=async()=>{f.signOut();f.identity.watchers.forEach(fn=>fn());throw rejected({message:'token expired: invalid jwt',code:401})}
  await f.page.onShow()
  assert.equal(f.page.data.signedIn,false);assert.equal(f.page.data.state.loggedIn,false);assert.equal(f.page.data.staffAccess,false)
  assert(pageText(f).includes('登录已过期，请重新登录'));assert(!pageText(f).includes('jwt'))
  assert.equal(text(f.nodes().find(n=>n.attrs?.bindtap==='login')),'重新登录')
  assert(!f.nodes().some(n=>has(n,'pass-ledger')||has(n,'logout-area')||has(n,'staff-entry')));assert(directoryVisible(f))
  f.context.state.loggedIn=true;f.business.context=async()=>f.context;await f.page.login()
  assert.equal(f.page.data.notice,null);assert.equal(f.toasts.at(-1),'登录成功')
})

test('first-load and refresh failures keep the public directory; refresh keeps the last good identity',async()=>{
  const f=fixture();let resolve
  f.business.context=()=>new Promise(r=>resolve=r);const first=f.page.refresh()
  assert(directoryVisible(f));assert(pageText(f).includes('正在读取个人信息'));resolve(f.context);await first
  const pending=f.page.refresh();assert(f.nodes().some(n=>has(n,'refresh-note')));assert.equal(text(f.nodes().find(n=>has(n,'profile-title'))),'小禾');resolve(f.context);await pending
  f.business.context=async()=>{throw Error('<html>502 Bad Gateway</html>')};await f.page.refresh()
  assert.equal(text(f.nodes().find(n=>has(n,'profile-title'))),'小禾');assert(pageText(f).includes('个人信息暂时没能读取'));assert(!pageText(f).includes('502'))
  assert(directoryVisible(f));assert.equal(tapCount(f,'onShow'),1)
  const cold=fixture();cold.business.context=async()=>{throw Error('offline')};await cold.page.onShow()
  assert(directoryVisible(cold));assert.equal(tapCount(cold,'logout'),1)
  cold.business.context=async()=>cold.context;await cold.page.onShow();assert.equal(cold.page.data.profileError,'');assert.equal(cold.page.data.state.loggedIn,true)
})

test('rendered directory preserves all destinations and existing phone authorization routing',async()=>{
  const f=fixture({staff:true});await f.page.onShow()
  for(const view of ['experiences','records','collections','privacy','archive']){
    assert(f.nodes().some(n=>n.attrs?.['data-view']===view));f.page.openFlow(event(view))
  }
  f.page.openFriendRegistration();f.page.openReferrals();f.page.openStaff()
  assert.deepEqual(f.links,['/pages/personal/index?view=experiences&id=','/pages/personal/index?view=records&id=','/pages/mine-flow/index?view=collections','/pages/mine-flow/index?view=privacy','/pages/friend-flow/index?view=archive','/pages/friend-registration/index','/pages/referrals/index','/staff/workspace/index?view=desk'])
  f.page.data.state.phoneLinked=false;f.page.openFlow(event('collections'));assert.equal(f.links.at(-1),'/pages/mine-flow/index?view=auth&next=collections')
  assert.equal(f.tabs.at(-1).selected,3)
  const demo=fixture({backend:false,state:{loggedIn:false,phoneLinked:false}});await demo.page.onShow()
  demo.page.openFlow(event('collections'));assert.equal(demo.links.at(-1),'/pages/mine-flow/index?view=auth&next=collections')
  await demo.page.login();assert.equal(demo.links.at(-1),'/pages/mine-flow/index?view=auth');assert.equal(tapCount(demo,'logout'),0)
})

test('staff permission failure hides its directory row; success uses the same access request',async()=>{
  const f=fixture();await f.page.onShow();assert(!f.nodes().some(n=>has(n,'staff-entry')))
  f.staff.request=async()=>({});await f.page.onShow();assert(f.nodes().some(n=>has(n,'staff-entry')))
  f.staff.request=async()=>{throw Error('expired')};await f.page.onShow();assert(!f.nodes().some(n=>has(n,'staff-entry')))
})

test('pass summary retains zero balances and only the existing three latest consumption records',async()=>{
  const f=fixture({state:{passAccounts:[{id:1}],currentPassBalance:0,passLedger:[{id:'credit',entryType:'credit'},...[1,2,3,4].map(id=>({id,entryType:'consume',quantityDelta:-1,balanceAfter:4-id,sessionNumber:100+id,occurredAt:'2026-10-07 09:00:00'}))]}})
  await f.page.refresh();assert.equal(f.page.data.passConsumptions.length,3)
  assert.equal(text(f.nodes().find(n=>has(n,'pass-balance'))),'0')
  assert.equal(f.nodes().filter(n=>has(n,'pass-record')).length,3)
  f.context.state.passLedger=[];await f.page.refresh();assert(f.nodes().some(n=>n.text?.includes('暂无卡次消耗记录')))
})

test('contact API fallback remains copyable; an absent rendered ID has no actionable copy control',async()=>{
  const f=fixture();await f.page.refresh();f.business.contact=async()=>{throw Error('offline')};await f.page.refreshContact()
  const copy=f.nodes().find(n=>n.attrs?.bindtap==='copyContact');assert(copy)
  assert.equal(f.nodes().find(n=>has(n,'contact-wechat')).attrs['user-select'],'true')
  f.page.copyContact();assert.deepEqual(f.copies,['qinglife2014']);assert.equal(f.toasts.at(-1),'微信号已复制')
  f.page.setData({contact:{name:'桃子',wechat:''}})
  assert(!f.nodes().some(n=>n.attrs?.bindtap==='copyContact'))
  assert(f.nodes().some(n=>has(n,'contact-unavailable')))
  assert(!f.nodes().some(n=>n.text?.includes('复制后，在微信中搜索添加好友')))
})

test('logout confirms once; cancel keeps everything and success lands on the complete guest directory',async()=>{
  const f=fixture({staff:true,state:{passAccounts:[{id:1}]}});await f.page.onShow();let calls=0;f.business.logout=async()=>{calls++;f.signOut()}
  const cancelled=f.page.logout();assert.equal(f.modals.length,1);assert.equal(f.modals[0].cancelText,'取消');f.modals.at(-1).success({confirm:false});await cancelled
  assert.equal(calls,0);assert.equal(f.page.data.state.loggedIn,true);assert(f.nodes().some(n=>has(n,'pass-ledger')));assert.equal(f.page.data.staffAccess,true)
  const pending=f.page.logout();f.modals.at(-1).success({confirm:true});await pending
  assert.equal(calls,1);assert.equal(f.modals.length,2);assert.equal(f.page.data.state.loggedIn,false);assert.equal(f.page.data.staffAccess,false)
  assert.deepEqual(plain(f.page.data.passConsumptions),[]);assert.equal(f.toasts.at(-1),'已退出登录');assert.equal(f.page.data.avatarText,'轻')
  assert.equal(text(f.nodes().find(n=>has(n,'profile-title'))),'未登录');assert(directoryVisible(f));assert(!pageText(f).includes('小禾'))
  assert(!f.nodes().some(n=>has(n,'logout-area')||has(n,'pass-ledger')||has(n,'invite-entry')||has(n,'staff-entry')||has(n,'identity-notice')))
  assert.equal(tapCount(f,'login'),1)
})

test('logout blocks duplicate taps; a failed revocation reports only the local result',async()=>{
  const f=fixture();await f.page.onShow();let fail,calls=0
  f.business.logout=()=>{calls++;f.signOut();return new Promise((resolve,reject)=>fail=reject)}
  const pending=f.page.logout();f.modals.at(-1).success({confirm:true});await Promise.resolve()
  assert.equal(f.page.data.logoutLoading,true)
  const button=f.nodes().find(n=>n.attrs?.bindtap==='logout');assert.equal(button.attrs.disabled,true);assert.equal(text(button),'正在退出…')
  await f.page.logout();assert.equal(calls,1);assert.equal(f.modals.length,1)
  fail(Error('request:fail timeout'));await pending
  assert.equal(f.page.data.logoutLoading,false);assert.equal(f.page.data.state.loggedIn,false);assert.equal(f.page.data.signedIn,false)
  assert(pageText(f).includes('已在本机退出登录，但未能确认服务器端同步注销'));assert(!f.toasts.includes('已退出登录'));assert(!pageText(f).includes('timeout'))
  assert(directoryVisible(f));assert.equal(tapCount(f,'login'),1)
  const kept=fixture();await kept.page.onShow();kept.business.logout=async()=>{throw Error('offline')}
  const retry=kept.page.logout();kept.modals.at(-1).success({confirm:true});await retry
  assert(pageText(kept).includes('退出没有完成，请重试'));assert.equal(kept.page.data.signedIn,true);assert.equal(tapCount(kept,'logout'),1);assert(!kept.toasts.length)
})

test('late mine refresh and staff permission replies cannot repopulate personal data after logout',async()=>{
  const f=fixture({staff:true});await f.page.onShow();let finishContext,finishStaff
  f.business.context=()=>new Promise(resolve=>finishContext=resolve)
  f.staff.request=()=>new Promise(resolve=>finishStaff=resolve)
  const refresh=f.page.refresh(),staff=f.page.refreshStaffAccess()
  const pending=f.page.logout();f.modals.at(-1).success({confirm:true});await pending
  finishContext(f.context);finishStaff({});await Promise.all([refresh,staff])
  assert.equal(f.page.data.state.loggedIn,false);assert.equal(f.page.data.staffAccess,false)
  assert.equal(f.page.data.avatarText,'轻');assert.deepEqual(plain(f.page.data.passConsumptions),[]);assert(!pageText(f).includes('小禾'))
})

test('identity change clears mine immediately and a confirmation from a hidden page cannot log out',async()=>{
  const f=fixture({staff:true});let calls=0
  f.business.logout=async()=>calls++;await f.page.onShow()
  const pending=f.page.logout();f.identity.scope={token:'other'};f.identity.watchers.forEach(fn=>fn());f.modals.at(-1).success({confirm:true});await pending
  assert.equal(calls,0);assert.equal(f.page.data.state.loggedIn,undefined);assert.equal(f.page.data.staffAccess,false)
  await f.page.onShow();const hidden=f.page.logout();f.page.onHide();f.modals.at(-1).success({confirm:true});await hidden
  assert.equal(calls,0);assert.equal(f.page.data.state.loggedIn,undefined)
})

module.exports={parse,render,flatten,fixture,has,text}
