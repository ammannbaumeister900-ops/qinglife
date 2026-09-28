'use strict'
const assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path'),vm=require('node:vm')
async function main(){
  let page,submitted=false;const storage=new Map(),toasts=[]
  const wx={getStorageSync:k=>storage.get(k),setStorageSync:(k,v)=>storage.set(k,v),removeStorageSync:k=>storage.delete(k),showToast:v=>toasts.push(v.title),navigateBack(){}}
  const business={friendProfile:async()=>({}),submitFriendAssessment:async()=>{submitted=true;return{assessmentCount:1}}}
  const file=path.resolve(__dirname,'../pages/friend-registration/index.js')
  const context={wx,console,Date,Math,Promise,Page:value=>{page=value}}
  vm.runInNewContext('(function(require){'+fs.readFileSync(file,'utf8')+'\n})',context)(target=>target.includes('business-api')?business:require(target))
  page.data=JSON.parse(JSON.stringify(page.data))
  page.setData=value=>{for(const [key,val] of Object.entries(value)){const parts=key.split('.');if(parts.length===2)page.data[parts[0]][parts[1]]=val;else page.data[key]=val}}
  page.data.form.healthConditions=['无']
  page.multi({currentTarget:{dataset:{field:'healthConditions',value:'高血压'}}})
  assert.deepEqual(Array.from(page.data.form.healthConditions),['高血压'])
  page.multi({currentTarget:{dataset:{field:'healthConditions',value:'无'}}})
  assert.deepEqual(Array.from(page.data.form.healthConditions),['无'])
  page.data.form.referralSource='朋友小雨';page.data.form.sensitiveConsent=false
  await page.submit()
  assert.equal(submitted,false);assert.equal(toasts.pop(),'请先阅读并同意敏感个人信息授权')
  console.log('PASS 轻友登记健康互斥与授权前置')
}
main().catch(error=>{console.error(error);process.exit(1)})
