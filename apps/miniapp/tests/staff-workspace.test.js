const assert = require('assert')
const vm = require('vm')
const fs = require('fs')
const source = fs.readFileSync(require('path').join(__dirname, '../staff/workspace/index.js'), 'utf8')
function page(request) {
  let definition
  const routes=[]
  vm.runInNewContext(source,{Page:d=>definition=d,require:()=>({request}),wx:{navigateTo:o=>routes.push(o.url),redirectTo:o=>routes.push(o.url),switchTab:o=>routes.push(o.url),showToast:()=>{}},console})
  const p={...definition,data:JSON.parse(JSON.stringify(definition.data)),routes,setData(values){Object.assign(this.data,values)}}
  return p
}
const me={name:'验收员',can_operate:0,can_payment:1}
async function run() {
  const p=page(async path=>path==='/me'?me:path==='/people'?[{id:'first',nickname:'首批轻友'}]:path==='/people/older'?{id:'older',nickname:'历史轻友'}:[])
  p.onLoad({view:'interview',id:'older'});await p.refresh()
  assert.equal(p.data.people[p.data.personIndex].id,'older','档案入口必须带入正确轻友，即使不在首批列表')
  p.selectPerson({detail:{value:1}});await p.refresh()
  assert.equal(p.data.people[p.data.personIndex].id,'first','从相册返回不能重置所选轻友')
  assert.equal(p.data.canOperate,false);assert.equal(p.data.canPayment,true)
  p.go({currentTarget:{dataset:{view:'roster',id:'s1'}}})
  assert.equal(p.routes[0],'/staff/workspace/index?view=roster&id=s1&filter=all')
  let requests=[]
  const search=page(path=>new Promise((resolve,reject)=>requests.push({path,resolve,reject})))
  const first=search.search({detail:{value:'张'}}),second=search.search({detail:{value:'张小'}})
  requests[1].resolve([{id:'latest'}]);await second
  requests[0].reject(new Error('过期请求失败'));await first
  assert.equal(search.data.suggestions[0].id,'latest','过期失败不能清空最新建议')
  await search.search({detail:{value:''}})
  assert.equal(search.data.suggestions.length,0)
  assert(requests.every(r=>r.path.endsWith('limit=5')))
  const denied=page(async()=>{throw Object.assign(new Error('无权限'),{code:403})})
  denied.setData({profile:{realName:'不可继续显示'},people:[{id:'private'}],me})
  await denied.refresh()
  assert.equal(denied.data.me,null);assert.equal(denied.data.people.length,0)
  assert.equal(Object.keys(denied.data.profile).length,0)
  console.log('staff-workspace.test.js OK: selected customer, photo-return state, payment-only permission, origin navigation, stale search and revoked access')
}
run().catch(e=>{console.error(e);process.exitCode=1})
