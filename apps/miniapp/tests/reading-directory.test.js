const { test } = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs'), path = require('node:path'), vm = require('node:vm')
function page(api, navigation = {}) {
  let result
  vm.runInNewContext(fs.readFileSync(path.join(__dirname,'../pages/content/index.js'),'utf8'), {
    Page: value => { result=value }, setTimeout,clearTimeout,
    require: id => id.includes('reading-samples') ? [{id:'demo-only',title:'Demo',summary:'Demo',category:'Demo'}]
      : id.includes('runtime') ? {environment:'staging'} : id.includes('business-api') ? api : navigation
  })
  result.data=JSON.parse(JSON.stringify(result.data));result.setData=patch=>Object.assign(result.data,patch);return result
}
test('staging reads real articles, paginates and sends tag/search filters to the server',async()=>{
  const calls=[]
  const content=page({enabled:()=>true,readings:async filters=>{calls.push({...filters});return {list:[{id:'9007199254741000',title:'Real',summary:'Story',category:'故事'}],hasMore:filters.pageNum===1}},readingTopics:async()=>[{id:'7',name:'故事'}]})
  await content.loadArticles()
  assert.equal(content.data.sampleMode,false);assert.equal(content.data.visibleArticles[0].id,'9007199254741000')
  await content.onReachBottom();assert.equal(calls.at(-1).pageNum,2);assert.equal(content.data.articles.length,1)
  content.data.query='慢';await content.chooseTopic({currentTarget:{dataset:{topic:'7'}}})
  assert.equal(calls.at(-1).labelId,'7');assert.equal(calls.at(-1).query,'慢');assert.equal(calls.at(-1).pageNum,1)
})
test('failed real API never falls back to demo stories',async()=>{
  const content=page({enabled:()=>true,readings:async()=>{throw Error('offline')},readingTopics:async()=>[]})
  await content.loadArticles();assert(content.data.error);assert.equal(content.data.sampleMode,false);assert.equal(content.data.articles.length,0)
})
test('switching tags ignores stale responses and accepts the newest result',async()=>{
  let finishOld
  const content=page({enabled:()=>true,readings:filters=>filters.labelId?Promise.resolve({list:[{id:'new',title:'New'}],hasMore:false}):new Promise(resolve=>{finishOld=resolve}),readingTopics:async()=>[]})
  const old=content.loadArticles();await content.chooseTopic({currentTarget:{dataset:{topic:'7'}}})
  finishOld({list:[{id:'old',title:'Old'}],hasMore:false});await old
  assert.equal(content.data.articles.length,1);assert.equal(content.data.articles[0].id,'new');assert.equal(content.data.loading,false)
})
test('home more-stories intent resets an existing tab and requests all tagged stories',async()=>{
 let filter=true;const calls=[]
 const content=page({enabled:()=>true,readings:async query=>{calls.push(query);return {list:[{id:'unfeatured-story'}],hasMore:false}},readingTopics:async()=>[]}, {consumeReadingFilter:()=>{const next=filter;filter=null;return next}})
 content.data.loaded=true;content.data.query='old';content.data.topic='old-tag';content.data.page=4
 await content.onShow();assert.equal(calls[0].stories,true);assert.equal(calls[0].pageNum,1);assert.equal(calls[0].query,undefined);assert.equal(calls[0].labelId,undefined)
 assert.equal(content.data.visibleArticles[0].id,'unfeatured-story');await content.onShow();assert.equal(calls.length,1)
})

test('story chip keeps search text, uses the canonical stories filter and clears all selection', async () => {
  const calls = []
  const content = page({ enabled: () => true, readings: async filters => {
    calls.push({ ...filters }); return { list: [], hasMore: false }
  }, readingTopics: async () => [{ id: '7', name: '轻友故事' }, { id: '8', name: '日常' }] })
  content.data.query = '慢'; content.data.topic = '8'; content.data.page = 4
  await content.chooseStories()
  assert.equal(content.data.stories, true); assert.equal(content.data.topic, '')
  assert.equal(calls.at(-1).stories, true); assert.equal(calls.at(-1).query, '慢')
  assert.equal(calls.at(-1).pageNum, 1); assert.equal(calls.at(-1).labelId, undefined)
  await content.chooseTopic({ currentTarget: { dataset: { topic: '8' } } })
  assert.equal(content.data.stories, false); assert.equal(calls.at(-1).labelId, '8')
  await content.chooseStories(); await content.clearSearch()
  assert.equal(content.data.query, ''); assert.equal(content.data.topic, '')
  assert.equal(content.data.stories, false); assert.equal(calls.at(-1).stories, false)
  assert.equal(calls.at(-1).labelId, undefined); assert.equal(calls.at(-1).query, undefined)
})
