const { test } = require('node:test')
const assert = require('node:assert/strict'), fs = require('node:fs'), path = require('node:path'), vm = require('node:vm')
const mini = path.resolve(__dirname, '..')
const clone = value => JSON.parse(JSON.stringify(value))
function fixture(changeDuringRead = false) {
  let page, business; const cache = new Map(), memory = {}, requests = []
  const raw = { id:'unit-guest-session', sessionNumber:7, name:'单元测试营期，非业务数据', startDate:'2099-01-01', endDate:'2099-01-03', city:'单元城市', venue:'单元公开地点', status:'open', capacity:8, registeredCount:0, standardPrice:1, returningPrice:1, days:[] }
  const wx = { getStorageSync:key=>memory[key], setStorageSync:(key,value)=>{memory[key]=value}, removeStorageSync:key=>{delete memory[key]},
    request:options=>{assert.equal(options.method,'GET');assert.equal(options.header.token,undefined);const route=options.url.split('/app/qinglife')[1];requests.push(route);assert(['/sessions','/sessions/'+raw.id].includes(route));options.success({statusCode:200,data:{code:200,data:route==='/sessions'?[raw]:raw}})},
    login:()=>assert.fail('public detail must not log in'), showToast:()=>{}, hideLoading:()=>{} }
  const context = vm.createContext({ wx, Page:value=>{page=value}, setTimeout, clearTimeout, Date, console })
  function load(file) {
    file=path.resolve(file);if(!path.extname(file))file+='.js';if(cache.has(file))return cache.get(file).exports
    const module={exports:{}};cache.set(file,module)
    const requireLocal=id=>{if(id==='../../utils/reflection'&&changeDuringRead){const real=load(path.resolve(path.dirname(file),id));return {...real,readEntry:async()=>{memory['qinglife_business_token:'+business.baseUrl()]='unit-only-identity';business.identityScope();return {allowed:false,retry:false}}}}return id.startsWith('.')?load(path.resolve(path.dirname(file),id)):require(id)}
    vm.runInContext('(function(module,exports,require){'+fs.readFileSync(file,'utf8')+'\n})',context,{filename:file})(module,module.exports,requireLocal);return module.exports
  }
  business=load(mini+'/services/business-api.js');load(mini+'/pages/camp-flow/index.js')
  page.data=clone(page.data);page.setData=patch=>Object.assign(page.data,clone(patch));page.onLoad({view:'detail',id:raw.id});return {page,business,requests,raw}
}
test('first anonymous identity adoption renders public camp detail with exact ID and ends loading without auth/private requests',async()=>{
  const f=fixture();await f.page.onShow();assert.equal(f.page.data.loading,false);assert.equal(f.page.data.loadError,'');assert.equal(f.page.data.activity.id,f.raw.id);assert.equal(f.page.data.state.loggedIn,false);assert.equal(f.page.data.detail.ready,true);assert.equal(f.page.data.isRegistered,false);assert.deepEqual(f.requests,['/sessions','/sessions/'+f.raw.id]);f.page.onUnload()
})
test('stable anonymous context supports returning and refreshing without treating normal state as registration',async()=>{
  const f=fixture();await f.page.onShow();await f.page.onShow();assert.equal(f.page.data.loading,false);assert.equal(f.page.data.activity.id,f.raw.id);assert.equal(f.page.data.registration.status,'none');assert.equal(f.page.data.view,'detail');assert.equal(f.page.data.isRegistered,false);f.page.onUnload()
})
test('a real identity change during the awaited read still fails closed and clears loading',async()=>{
  const f=fixture(true);await f.page.onShow();assert.equal(f.page.data.loading,false);assert.match(f.page.data.loadError,/身份已变化/);assert.notEqual(f.page.data.activity.id,f.raw.id);assert.equal(f.requests.length,2);f.page.onUnload()
})
