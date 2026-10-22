const assert = require('assert')
const vm = require('vm')
const fs = require('fs')
const source = fs.readFileSync(require('path').join(__dirname, '../staff/workspace/index.js'), 'utf8')
function page(request) {
  let definition
  const routes=[],toasts=[]
  vm.runInNewContext(source,{Page:d=>definition=d,require:()=>({request}),wx:{navigateTo:o=>routes.push(o.url),redirectTo:o=>routes.push(o.url),switchTab:o=>routes.push(o.url),showToast:o=>toasts.push(o.title)},console})
  const p={...definition,data:JSON.parse(JSON.stringify(definition.data)),routes,toasts,setData(values){Object.assign(this.data,values)}}
  return p
}
const me={name:'验收员',can_operate:0,can_payment:1}

async function verifyManualEnrollment() {
  const calls=[]
  const sessions=[{id:'s-501',sessionNumber:501,name:'春日轻体营'},{id:'s-502',sessionNumber:' 502 ',name:'春日轻体营'}]
  const candidates=[{id:'same-name-1',nickname:'小林',realName:'林一',phone:'13800000001'},{id:'same-name-2',nickname:'小林',realName:'Alice林2',phone:'13800000002'}]
  const enroll=page(async (url,method='GET',body)=>{
    calls.push({url,method,body})
    if(url==='/me')return {...me,can_operate:1}
    if(url==='/sessions')return sessions
    if(url==='/people/same-name-2')return candidates[1]
    if(url.startsWith('/people?q='))return decodeURIComponent(url).includes('不存在')?[]:candidates
    if(url==='/registrations'&&method==='POST')return {id:'synthetic-registration'}
    throw Error('Unexpected enrollment request '+url)
  })
  enroll.onLoad({view:'enroll'});await enroll.refresh()
  assert.equal(enroll.data.selectedPerson,'','人工报名不能默认选中首位轻友')
  assert.equal(enroll.data.enrollPerson,null)
  assert(!calls.some(call=>call.url==='/people'),'人工报名不再只从首批轻友列表选人')
  assert.deepEqual(enroll.data.sessions.map(s=>s.sessionLabel),['【501】春日轻体营','【502】春日轻体营'])
  assert.equal(sessions[0].name,'春日轻体营','展示编号不得改写接口期次名称')
  await enroll.enroll();assert.equal(enroll.toasts.pop(),'请先搜索并选择轻友')
  assert(!calls.some(call=>call.method==='POST'),'未明确选人时不能提交报名')
  for(const query of ['林','Ali','13800000002','林2',' 张 小 ','%_']) {
    await enroll.searchPeople(query)
    assert.equal(calls[calls.length-1].url,'/people?q='+encodeURIComponent(query.trim())+'&limit=20')
    assert.equal(enroll.data.searching,false)
    assert.equal(enroll.data.selectedPerson,'')
    assert.equal(enroll.data.suggestions.length,2)
  }
  enroll.chooseEnrollPerson({currentTarget:{dataset:{id:'not-a-result'}}})
  assert.equal(enroll.data.selectedPerson,'','只能选择真实查询结果')
  enroll.chooseEnrollPerson({currentTarget:{dataset:{id:'same-name-2'}}})
  assert.equal(enroll.data.enrollPerson.phone,'13800000002','同名轻友必须保留可核对信息')
  assert.equal(enroll.data.selectedPerson,'same-name-2')
  assert.equal(enroll.data.suggestions.length,0)
  assert.equal(enroll.data.searchQuery,'')
  enroll.selectSession({detail:{value:1}});await enroll.refresh()
  assert.equal(enroll.data.enrollPerson.id,'same-name-2','返回页面不能重置已选轻友')
  assert.equal(enroll.data.selectedSession,'s-502','返回页面不能重置已选期次')
  assert.equal(enroll.data.sessionIndex,1)
  await enroll.enroll()
  const submitted=calls.find(call=>call.method==='POST')
  assert.deepEqual(submitted.body,{customerId:'same-name-2',sessionId:'s-502'})
  assert(enroll.routes.includes('/staff/workspace/index?view=roster&id=s-502'))
  await enroll.searchPeople('不存在')
  assert.equal(enroll.data.selectedPerson,'','重新查询必须撤销旧选择，避免报错人')
  assert.equal(enroll.data.enrollPerson,null)
  assert.equal(enroll.data.searchEmpty,true)
  await enroll.searchPeople('   ');assert.equal(enroll.data.searchEmpty,false)
  assert.equal(enroll.data.suggestions.length,0)
  const profile=page(async url=>url==='/me'?me:url==='/sessions'?sessions:url==='/people/older'?{id:'older',nickname:'档案轻友'}:[])
  profile.onLoad({view:'enroll',id:'older'});await profile.refresh()
  assert.equal(profile.data.selectedPerson,'older','从档案进入报名仍须带入该轻友')
  assert.equal(profile.data.enrollPerson.nickname,'档案轻友')
  await profile.searchPeople('');await profile.refresh()
  assert.equal(profile.data.selectedPerson,'','主动重新选择后不能从旧路由恢复轻友')
  profile.setData({selectedPerson:'older',enrollPerson:{id:'older'},selectedSession:''})
  await profile.enroll();assert.equal(profile.toasts.pop(),'请选择报名期次')
}
async function verifyEnrollmentSearchRaces() {
  const pending=[]
  const p=page(url=>new Promise((resolve,reject)=>pending.push({url,resolve,reject})))
  p.onLoad({view:'enroll'})
  const old=p.searchPeople('林'),latest=p.searchPeople('林2')
  pending[1].resolve([{id:'chosen',nickname:'林2'}]);await latest
  p.chooseEnrollPerson({currentTarget:{dataset:{id:'chosen'}}})
  pending[0].reject(Error('旧请求错误'));await old
  assert.equal(p.data.selectedPerson,'chosen','过期请求不能撤销明确选择')
  assert.equal(p.data.suggestions.length,0)
  assert.equal(p.toasts.length,0)
  const cleared=p.searchPeople('Alice');await p.searchPeople('')
  pending[2].resolve([{id:'stale-after-clear'}]);await cleared
  assert.equal(p.data.suggestions.length,0,'清空输入后旧结果不得恢复')
  const failed=p.searchPeople('138');pending[3].reject(Error('查询失败'));await failed
  assert.equal(p.data.searching,false);assert.equal(p.data.suggestions.length,0)
  assert.equal(p.data.selectedPerson,'');assert.equal(p.toasts.pop(),'查询失败')
  const unloaded=p.searchPeople('林');p.onUnload();const before=JSON.stringify(p.data)
  pending[4].resolve([{id:'after-unload'}]);await unloaded
  assert.equal(JSON.stringify(p.data),before,'页面离开后不得回写查询结果')
}
async function verifySessionTitles() {
  for(const view of ['desk','sessions','history']) {
    const p=page(async url=>url==='/me'?me:url.startsWith('/sessions?')?[{id:'501',sessionNumber:501,name:'轻体营'},{id:'legacy',name:'历史名称'}]:[])
    p.onLoad({view});await p.refresh()
    assert.equal(p.data.sessions[0].sessionLabel,'【501】轻体营',view+'必须显示编号与名称')
    assert.equal(p.data.sessions[1].sessionLabel,'历史名称','旧记录缺少编号不能显示undefined')
  }
}

async function run() {
  const p=page(async path=>path==='/me'?me:path==='/people'?[{id:'first',nickname:'首批轻友'}]:path==='/people/older'?{id:'older',nickname:'历史轻友'}:[])
  p.onLoad({view:'interview',id:'older'});await p.refresh()
  assert.equal(p.data.people[p.data.personIndex].id,'older','档案入口必须带入正确轻友，即使不在首批列表')
  p.selectPerson({detail:{value:1}});await p.refresh()
  assert.equal(p.data.people[p.data.personIndex].id,'first','从相册返回不能重置所选轻友')
  assert.equal(p.data.canOperate,false);assert.equal(p.data.canPayment,true)
  p.go({currentTarget:{dataset:{view:'roster',id:'s1'}}})
  assert.equal(p.routes[0],'/staff/workspace/index?view=roster&id=s1&filter=all')
  // Native input uses the handler return value to replace the input content.
  // Returning an async Promise can corrupt the value on device runtimes.
  const nativeInput=page(async()=>[])
  const inputValues=[]
  nativeInput.searchPeople=value=>{inputValues.push(value);return Promise.resolve()}
  for(const value of ['张','Alice','138','', ' 张 ']){
    assert.equal(nativeInput.search({detail:{value}}),undefined,'bindinput must not return a Promise')
  }
  assert.deepEqual(inputValues,['张','Alice','138','', ' 张 '])
  let requests=[]
  const search=page(path=>new Promise((resolve,reject)=>requests.push({path,resolve,reject})))
  const first=search.searchPeople('张'),second=search.searchPeople('张小')
  requests[1].resolve([{id:'latest'}]);await second
  requests[0].reject(new Error('过期请求失败'));await first
  assert.equal(search.data.suggestions[0].id,'latest','过期失败不能清空最新建议')
  await search.searchPeople('')
  assert.equal(search.data.suggestions.length,0)
  assert(requests.every(r=>r.path.endsWith('limit=5')))
  const denied=page(async()=>{throw Object.assign(new Error('无权限'),{code:403})})
  denied.setData({profile:{realName:'不可继续显示'},people:[{id:'private'}],me})
  await denied.refresh()
  assert.equal(denied.data.me,null);assert.equal(denied.data.people.length,0)
  assert.equal(Object.keys(denied.data.profile).length,0)
  const sessionCounts=page(async path=>{
    if(path==='/me')return me
    if(path==='/sessions?history=false')return [{id:'first-session'},{id:'second-session'}]
    if(path==='/sessions/first-session/registrations')return [{id:'r1',registrationStatus:'pending',paymentStatus:'unpaid'},{id:'r2',registrationStatus:'confirmed',paymentStatus:'paid'}]
    if(path==='/sessions/second-session/registrations')return [{id:'r3',registrationStatus:'confirmed',paymentStatus:'unpaid'},{id:'r4',registrationStatus:'cancelled',paymentStatus:'unpaid'}]
    if(path==='/interviews')return []
    throw new Error('unexpected request '+path)
  })
  sessionCounts.onLoad({view:'desk'});await sessionCounts.refresh()
  assert.deepEqual(sessionCounts.data.sessions.map(s=>[s.allCount,s.pendingCount,s.unpaidCount]),[[2,1,1],[1,0,1]],'每个期次卡片必须使用自己的报名汇总')
  assert.equal(sessionCounts.data.roster[0].id,'r1','期次页默认名单继续显示首个期次，不改变既有导航契约')
  await verifyManualEnrollment();await verifyEnrollmentSearchRaces();await verifySessionTitles()
  console.log('staff-workspace.test.js OK: session number labels, explicit enrollment search/selection, submission guards,  selected customer, photo-return state, payment-only permission, origin navigation, synchronous native input, stale search, per-session summaries and revoked access')
}
run().catch(e=>{console.error(e);process.exitCode=1})
