'use strict'
// Headless native-page adapter; every business request reaches the actual Spring HTTP server.
// Only wx UI/storage primitives and local runtime selection are adapted; no API result is mocked.
const fs=require('node:fs'),path=require('node:path'),vm=require('node:vm'),assert=require('node:assert/strict')
const root=path.resolve(__dirname,'../..'),input=JSON.parse(fs.readFileSync(0,'utf8')),cache=new Map(),storage=new Map()
if(!/^http:\/\/127\.0\.0\.1:\d+$/.test(input.base))throw Error('Fresh loopback HTTP server required')
storage.set('qinglife_business_token:'+input.base,input.token)
let page
const wx={
 getStorageSync:key=>storage.get(key)||'',setStorageSync:(key,value)=>storage.set(key,value),removeStorageSync:key=>storage.delete(key),
 pageScrollTo(){},showToast(){},login(){throw Error('Synthetic test session must already exist')},
 request(options){
  assert(options.url.startsWith(input.base+'/app/qinglife/'),'Unexpected remote request')
  fetch(options.url,{method:options.method||'GET',headers:options.header,
   ...(['POST','PUT'].includes(options.method)?{body:JSON.stringify(options.data)}:{})})
   .then(async res=>options.success({statusCode:res.status,data:await res.json()})).catch(options.fail)
 }
}
function load(filename){
 filename=path.resolve(filename);if(!path.extname(filename))filename+='.js'
 if(filename===path.join(root,'config/runtime.js'))return {environment:'demo',businessBaseUrl:input.base,allowedBusinessOrigins:[],legacyEnabled:false}
 if(cache.has(filename))return cache.get(filename).exports
 const module={exports:{}};cache.set(filename,module)
 const requireLocal=id=>id.startsWith('.')?load(path.resolve(path.dirname(filename),id)):require(id)
 vm.runInNewContext('(function(require,module,exports){'+fs.readFileSync(filename,'utf8')+'\n})',{wx,Page:p=>{page=p},getApp:()=>({globalData:{}}),console,setTimeout,clearTimeout,Promise},{filename})(requireLocal,module,module.exports)
 return module.exports
}
async function main(){
 load(path.join(root,'pages/camp-flow/index.js'));page.data=JSON.parse(JSON.stringify(page.data));page.setData=(patch,callback)=>{Object.assign(page.data,patch);if(callback)callback()}
 page.onLoad({view:'register',id:input.session});await page.onShow();assert.equal(page.data.loadError,'');assert.equal(page.data.view,'register')
 page.onSelfName({detail:{value:input.name}});page.onSelfPhone({detail:{value:input.phone}})
 page.setData({personName:'New companion via page',personPhone:'',personRelation:'朋友',personMinor:false})
 page.addPerson();await page.refresh();await page.nextStep()
 assert.equal(page.data.quote.ready,true,page.data.quote.error);assert.equal(page.data.quote.total,input.total)
 const quote=JSON.parse(JSON.stringify(page.data.quote));assert.deepEqual(quote.rows.map(r=>r.friendLabel),['老轻友','新轻友'])
 await page.submitRegistration();assert.equal(page.data.view,'success',JSON.stringify(page.data.formError))
 assert.equal(page.data.submittedParticipantCount,2);assert.equal(page.data.submittedServiceStatus.registration,'待缴费 / 待确认')
 const context=await load(path.join(root,'services/business-api.js')).context(input.session)
 const current=context.state.registrations[input.session];assert.equal(current.participants.length,2)
 assert.equal(current.participants.find(p=>p.id==='person-self').name,input.name)
 for(const person of current.participants){assert.equal(person.status,'pending');assert.equal(person.paymentStatus,'unpaid')}
 const result={total:quote.total,prices:quote.rows.map(r=>({name:r.name,friendLabel:r.friendLabel,price:r.price})),submittedParticipants:2,status:current.status,actualHttp:true,mockedBusinessResponses:false,physicalDevice:false}
 process.stdout.write(JSON.stringify(result))
 page.onUnload()
}
main().catch(error=>{process.stderr.write(error.message+'\n');process.exitCode=1})
