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
function fixture(options={}){
  let page
  const state={...plain(require('../utils/store').defaultState()),backend:true,loggedIn:true,phoneLinked:true,profile:{nickname:'小禾'},passAccounts:[],passLedger:[],currentPassBalance:0,...options.state}
  const context={state},links=[],copies=[],toasts=[],tabs=[],staffCalls=[]
  const business={enabled:()=>options.backend!==false,context:async()=>context,contact:async()=>({name:'桃子',wechat:'qinglife2014'}),login:async()=>{}}
  const staff={request:async p=>{staffCalls.push(p);if(!options.staff)throw Error('forbidden');return {}}}
  const mocks={'../../services/business-api':business,'../../services/staff-api':staff,'../../utils/store':{getState:()=>plain(state)},'../../utils/navigation':{navigateTo:x=>links.push(x.url),switchTab:x=>links.push(x.url)}}
  vm.runInNewContext(fs.readFileSync(path.join(root,'pages/mine/index.js'),'utf8'),{Page:p=>page=p,wx:{navigateTo:x=>links.push(x.url),showToast:x=>toasts.push(x.title),setClipboardData:x=>{copies.push(x.data);x.success()}},require:id=>mocks[id]||require(path.resolve(root,'pages/mine',id))})
  page.data=plain(page.data);page.setData=p=>Object.assign(page.data,p);page.getTabBar=()=>({setData:p=>tabs.push(p)})
  const nodes=()=>flatten(render(parse().children,page.data))
  return {page,context,business,staff,links,copies,toasts,tabs,staffCalls,nodes}
}
const event=view=>({currentTarget:{dataset:{view}}})

test('directory retains every service, prioritizes ordinary tasks and puts staff after public contact',async()=>{
  const f=fixture({staff:true,state:{passAccounts:[{id:1}],passLedger:[]}});await f.page.onShow()
  const nodes=f.nodes(),assets=nodes.filter(n=>has(n,'asset'))
  assert.deepEqual(assets.map(n=>text(flatten([n]).find(c=>has(c,'asset-name')))),['轻友登记','全部参与经历','私人记录','内容收藏','邀请轻友'])
  assert(nodes.indexOf(assets[0])<nodes.findIndex(n=>has(n,'pass-ledger')))
  assert(nodes.findIndex(n=>has(n,'staff-entry'))>nodes.findIndex(n=>has(n,'contact-section')))
  assert(!nodes.some(n=>has(n,'card')||has(n,'pill')||n.tag==='image'))
  assert(nodes.some(n=>n.attrs?.['data-view']==='privacy'));assert(nodes.some(n=>n.attrs?.['data-view']==='archive'))
})

test('identity keeps guest login, busy controls and missing nickname registration, without inviting a guest',async()=>{
  const f=fixture({state:{loggedIn:false}});await f.page.onShow()
  assert(f.nodes().some(n=>n.attrs?.bindtap==='login'))
  assert(!f.nodes().some(n=>n.attrs?.bindtap==='openReferrals'||n.attrs?.bindtap==='openStaff'))
  f.page.setData({loginLoading:true});const login=f.nodes().find(n=>n.attrs?.bindtap==='login');assert.equal(login.attrs.disabled,true);assert.equal(login.attrs.loading,true)
  f.context.state.loggedIn=true;f.context.state.profile.nickname='';await f.page.refresh()
  assert(f.nodes().some(n=>has(n,'profile-nickname-entry')))
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

test('loading and error hide ordinary tasks while refresh retains them and retry restores them',async()=>{
  const f=fixture();let resolve
  f.business.context=()=>new Promise(r=>resolve=r);const first=f.page.refresh()
  assert(!f.nodes().some(n=>has(n,'asset')));resolve(f.context);await first
  const pending=f.page.refresh();assert(f.nodes().some(n=>has(n,'asset')));assert(f.nodes().some(n=>has(n,'refresh-note')));resolve(f.context);await pending
  f.business.context=async()=>{throw Error('offline')};await f.page.refresh()
  assert(!f.nodes().some(n=>has(n,'asset')));assert(f.nodes().some(n=>n.attrs?.bindtap==='onShow'))
  f.business.context=async()=>f.context;await f.page.onShow();assert(f.nodes().some(n=>has(n,'asset')))
})

module.exports={parse,render,flatten,fixture,has,text}
