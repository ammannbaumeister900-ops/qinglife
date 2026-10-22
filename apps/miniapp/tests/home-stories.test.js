const { test } = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs'), path = require('node:path'), vm = require('node:vm')
const root = path.resolve(__dirname,'..')
function home(api) {
 let result
 vm.runInNewContext(fs.readFileSync(path.join(root,'pages/home/index.js'),'utf8'), {
  Page: value => {result=value}, require: id => id.includes('business-api') ? api : id.includes('domain') ? {homeTask:()=>({})} : require(path.resolve(root,'pages/home',id))
 })
 result.data=JSON.parse(JSON.stringify(result.data));result.setData=patch=>Object.assign(result.data,patch);return result
}
const context={state:{loggedIn:false,registration:{status:'none'},registrations:{}},activities:[{id:'s',sessionNumber:10001,sourceStatus:'open',status:'开放报名',startDate:'2099-01-01',endDate:'2099-01-03',seatsLeft:5}]}
test('home consumes real story/general selection and keeps the registerable camp',async()=>{
 const page=home({enabled:()=>true,context:async()=>context,homeReadings:async()=>({stories:[{id:'9007199254741000',title:'Story'}],featured:[{id:'general'}]})})
 await page.onShow();assert.equal(page.data.stories[0].id,'9007199254741000');assert.equal(page.data.featuredReadings[0].id,'general');assert.equal(page.data.featured.sessionNumber,10001)
 page.onHide();assert.equal(page.data.visible,false)
})
test('story API failure leaves camp available and never substitutes demo stories',async()=>{
 const page=home({enabled:()=>true,context:async()=>context,homeReadings:async()=>{throw Error('offline')}})
 await page.onShow();assert.equal(page.data.featured.id,'s');assert.equal(page.data.stories.length,0);assert.equal(page.data.featuredReadings.length,0);assert.ok(page.data.readingError)
})
test('a late home refresh cannot replace a newer result or resume hidden autoplay',async()=>{
 let first;let calls=0
 const page=home({enabled:()=>true,context:async()=>context,homeReadings:()=>++calls===1?new Promise(resolve=>{first=resolve}):Promise.resolve({stories:[{id:'new'}],featured:[]})})
 const old=page.onShow();await page.onShow();page.onHide();first({stories:[{id:'old'}],featured:[]});await old
 assert.equal(page.data.stories[0].id,'new');assert.equal(page.data.visible,false)
})
test('story-tab intent is consumed once and cleared when navigation fails',()=>{
 let options
 const module={exports:{}}
 vm.runInNewContext(fs.readFileSync(path.join(root,'utils/navigation.js'),'utf8'),{module,wx:{switchTab:value=>{options=value},hideLoading:()=>{}}})
 module.exports.openReadingList(true);assert.equal(options.url,'/pages/content/index');assert.equal(module.exports.consumeReadingFilter(),true);assert.equal(module.exports.consumeReadingFilter(),null)
 module.exports.openReadingList(false);assert.equal(module.exports.consumeReadingFilter(),false)
 module.exports.openReadingList(true);options.fail({});assert.equal(module.exports.consumeReadingFilter(),null)
})
