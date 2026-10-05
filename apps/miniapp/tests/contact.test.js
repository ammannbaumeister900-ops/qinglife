const {test}=require('node:test')
const assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path'),vm=require('node:vm')
const contact=require('../utils/contact')
function page(api, clipboard) {
  let p,toast
  const wx={setClipboardData:clipboard,showToast:value=>toast=value}
  vm.runInNewContext(fs.readFileSync(path.join(__dirname,'../pages/mine/index.js'),'utf8'),{Page:value=>p=value,wx,require:name=>name.includes('utils/contact')?contact:api})
  p.data=JSON.parse(JSON.stringify(p.data));p.setData=value=>Object.assign(p.data,value)
  return {p,toast:()=>toast}
}
test('missing, blank and malformed public contact fields fall back; nonblank configuration survives',()=>{
  for(const value of [undefined,null,{}, {wechat:'  ',name:42}, {wechat:99,name:null}])assert.deepEqual(contact.resolveContact(value),{name:'桃子',wechat:'qinglife2014',configured:true})
  assert.deepEqual(contact.resolveContact({name:' 桃子客服 ',wechat:' support_id '}),{name:'桃子客服',wechat:'support_id',configured:true})
})
test('contact remains available without login in demo mode and when the public API fails',async()=>{
  for(const api of [{enabled:()=>false},{enabled:()=>true,contact:async()=>{throw Error('network')}}]){
    const {p}=page(api,()=>{});await p.refreshContact();assert.equal(p.data.contact.wechat,'qinglife2014');assert.equal(p.data.contact.configured,true)
  }
})
test('the copied ID follows the rendered operational contact and reports success',async()=>{
  let copied;const {p,toast}=page({enabled:()=>true,contact:async()=>({name:'桃子',wechat:' configured_id '})},value=>{copied=value.data;value.success()})
  await p.refreshContact();p.copyContact();assert.equal(copied,'configured_id');assert.equal(p.data.contact.wechat,copied);assert.equal(toast().title,'微信号已复制')
})
test('clipboard denial keeps the ID visible and offers manual copy instead of success',()=>{
  const {p,toast}=page({},value=>{assert.equal(value.data,'qinglife2014');value.fail()});p.copyContact();assert.equal(toast().icon,'none');assert(toast().title.includes('长按'));assert.equal(p.data.contact.wechat,'qinglife2014')
})
