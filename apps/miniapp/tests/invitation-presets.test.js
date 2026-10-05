const {test}=require('node:test')
const assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path'),vm=require('node:vm')
const presets=require('../utils/invitation-presets')
function page(api) {
  let p;const wx={hideShareMenu(){},showShareMenu(){},showToast(){}}
  vm.runInNewContext(fs.readFileSync(path.join(__dirname,'../pages/referrals/index.js'),'utf8'),{Page:x=>p=x,require:n=>n.includes('discovery')?{activities:x=>x}:n.includes('invitation-presets')?presets:api,wx})
  p.data=JSON.parse(JSON.stringify(p.data));p.setData=x=>Object.assign(p.data,x);return p
}
function jpegSize(buffer) {
  assert.equal(buffer.subarray(0,2).toString('hex'),'ffd8')
  let offset=2
  while(offset<buffer.length) {
    assert.equal(buffer[offset++],255)
    while(buffer[offset]===255)offset++
    const marker=buffer[offset++],length=buffer.readUInt16BE(offset)
    if([192,193,194].includes(marker))return {height:buffer.readUInt16BE(offset+3),width:buffer.readUInt16BE(offset+5)}
    assert(length>=2);offset+=length
  }
  throw Error('Missing JPEG dimensions')
}
const session={id:'period',sessionNumber:108,canRegister:true}
const context=async()=>({state:{loggedIn:true,invitationEligible:true},activities:[session]})
test('three fresh-produce share images fit 5:4 and the selected artwork travels with the prepared invitation',async()=>{
  assert.equal(presets.length,3)
  for(const preset of presets){
    assert(preset.image.endsWith('-fresh.jpg'))
    const b=fs.readFileSync(path.join(__dirname,'..',preset.image)),size=jpegSize(b)
    assert(Math.abs(size.width/size.height-1.25)<.002,'rounding tolerance under one image pixel')
    assert(b.length<250000,'share artwork should not inflate the miniapp bundle')
  }
  const p=page({context,myReferrals:async()=>[],createInvitation:async()=>({path:'/pages/camp-flow/index?invite=opaque'})});await p.refresh();await p.prepare()
  p.choosePreset({currentTarget:{dataset:{id:'bloom'}}});assert.equal(p.onShareAppMessage().imageUrl,presets[2].image);assert.ok(p.onShareAppMessage().title.includes('108'));assert.equal(p.onShareAppMessage().path,'/pages/camp-flow/index?invite=opaque')
})
test('period selection invalidates its previous invitation, and stale responses cannot restore it after login failure',async()=>{
  let finish,failed=false,creates=0
  const p=page({context:async()=>{if(failed)throw Error('登录失效');return {state:{loggedIn:true,invitationEligible:true},activities:[session,{...session,id:'other'}]}},myReferrals:async()=>[],createInvitation:()=>{creates++;return new Promise(r=>finish=r)}})
  await p.refresh();const preparing=p.prepare();failed=true;await p.refresh();finish({path:'stale'});await preparing
  assert.equal(p.data.sharePath,'');assert.equal(p.data.eligible,false);assert.equal(p.data.preparing,false);assert.equal(p.onShareAppMessage().path,'/pages/home/index');assert.equal(creates,1)
  failed=false;await p.refresh();p.data.sharePath='previous';p.chooseSession({currentTarget:{dataset:{id:'other'}}});assert.equal(p.data.sharePath,'');assert.equal(p.data.selectedSessionId,'other')
  p.chooseSession({currentTarget:{dataset:{id:'unknown'}}});assert.equal(p.data.selectedSessionId,'other')
})
test('a guest cannot create an invitation even if a stale eligibility flag survives',async()=>{
  let created=false
  const p=page({context:async()=>({state:{loggedIn:false,invitationEligible:true},activities:[session]}),myReferrals:async()=>[],createInvitation:async()=>{created=true}});await p.refresh();await p.prepare();assert.equal(created,false)
})
