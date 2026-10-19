const { test } = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs'), path = require('node:path'), vm = require('node:vm')
const { presentAbout, storyArticle } = require('../utils/about-presentation')
const content = require('../config/about-content')
const root = path.resolve(__dirname, '..'), clone = v => JSON.parse(JSON.stringify(v))
const approved = { state:'releaseReady', authorization:'confirmed', editorialReview:'approved', factCheck:'confirmed', source:{ document:'unit-test-only' } }
function person(name='单元人物甲', id='unit-person-a') { return { ...approved, id, personKey:id, name, identityCheck:'confirmed', history:['单元测试经历，不是业务事实'], intro:'单元测试简介', quote:'完整单元引语。', closingQuote:'单元收束引语。', attribution:name+' · 单元夹具', fullStory:{ ...approved, businessArticleId:91, personKey:id, publicVerified:true, evidenceRef:'unit-only', title:'单元文章，不是公开ID确认' } } }
function configured(story=person()) { return { ...clone(content), storySlot:{ heading:'单元通用故事标题', item:story } } }
function fixture(config=content, fontSize=16, business={ enabled:()=>true, sessions:async()=>[] }) {
  let page; const calls=[]; const navigation={ navigateTo:o=>calls.push({ method:'navigateTo', ...o }), switchTab:o=>calls.push({ method:'switchTab', ...o }) }
  const wx={ getSystemInfoSync:()=>({fontSizeSetting:fontSize}), showShareMenu:o=>calls.push({method:'showShareMenu',...o}) }
  vm.runInNewContext(fs.readFileSync(root+'/pages/about/index.js','utf8'), { Page:v=>page=v, wx, require:id=>id.includes('config/about-content')?config:id.includes('utils/navigation')?navigation:id.includes('services/business-api')?business:require(path.resolve(root+'/pages/about',id)) })
  page.data=clone(page.data);page.setData=patch=>Object.assign(page.data,clone(patch));return {page,calls}
}
test('public entry rejects candidate people/photos and ignores preview, person, session and article query injection',()=>{
  const f=fixture();f.page.onLoad({preview:'candidate',person:'琉璃光',articleId:'91',sessionId:'fake'});assert.equal(f.page.data.story.name,'花花一家');assert.equal(f.page.data.story.articleUrl,'');assert.equal(f.page.data.founders,null);const media=require('../config/visual-media');assert.equal(f.page.data.entry.photo&&f.page.data.entry.photo.url,media.aboutOpening||undefined);assert.equal(f.page.data.storyScene,null,'approved story hides #46');assert.equal(f.page.data.transparency.photo&&f.page.data.transparency.photo.url,media.aboutJuice||undefined);assert.equal(f.calls.filter(v=>v.method==='navigateTo').length,0);assert.equal(content.storySlot.item.personKey,'huahua-family');assert.equal(content.founders,null);assert.deepEqual(content.assets.map(a=>a.id+':'+a.state),['scene-50:releaseReady','scene-46:releaseReady','scene-67:releaseReady','family-huahua:releaseReady']);assert(content.assets.every(a=>a.url===''||a.url.startsWith('https://img.guijianju.cn/qinglife/visual/')),'只用已验证的 COS 地址或留空')
  const source=fs.readFileSync(root+'/config/about-content.js','utf8');assert(!/琉璃光|luli-interview|camp-conversation|程文|胡海/.test(source));const js=fs.readFileSync(root+'/pages/about/index.js','utf8');assert(!/getStorageSync|ensureToken|login|context\(|privateOverview/.test(js));assert.deepEqual(js.match(/business\.\w+/g).sort(),['business.enabled','business.sessions'])
})
test('all editorial readiness gates are required; unverified identity stays closed',()=>{
  for(const patch of [{state:'candidate'},{authorization:'pending'},{editorialReview:'pending'},{factCheck:'source-matched'},{identityCheck:'pending'},{source:null}])assert.equal(presentAbout(configured({...person(),...patch})).story,null)
  assert.equal(presentAbout(configured()).story.name,'单元人物甲')
})
test('replacing one story record changes name, portrait, experience, quotes and full-story target without changing layout',()=>{
  const a=person(),b={...person('单元人物乙','unit-person-b'),portraitAssetId:'portrait-b',intro:'替换简介',quote:'替换后的完整引语。',history:['替换的单元经历'],closingQuote:'替换的收束引语。'};b.fullStory={...b.fullStory,businessArticleId:92};const c=configured(b);c.assets=[{...approved,id:'portrait-b',identityCheck:'confirmed',url:'https://example.invalid/unit-portrait.jpg',caption:'单元肖像说明',alt:'单元人物乙'}];const result=presentAbout(c).story;assert.equal(result.name,b.name);assert.equal(result.portrait.url,c.assets[0].url);assert.equal(result.historyText,b.history[0]);assert.equal(result.quote,b.quote);assert.equal(result.closingQuote,b.closingQuote);assert.match(result.articleUrl,/id=92&source=business/);assert(!JSON.stringify(result).includes(a.name));assert(!JSON.stringify(result).includes(a.quote));const template=fs.readFileSync(root+'/pages/about/index.wxml','utf8');assert(!/琉璃光|2012|2021|59.9|676|405/.test(template));assert(template.includes('{{story.portrait.url}}'))
})
test('full story needs a matching person and verified public numeric business article; unknown/private/unreviewed targets never navigate',()=>{
  const p=person();for(const patch of [{businessArticleId:null},{businessArticleId:'../private'},{businessArticleId:'legacy-sample'},{publicVerified:false},{personKey:'different-person'},{editorialReview:'pending'},{authorization:'pending'},{evidenceRef:''}])assert.equal(storyArticle({...p,fullStory:{...p.fullStory,...patch}}),'')
  const f=fixture(configured());f.page.onLoad();f.page.openFullStory();assert.deepEqual(f.calls.find(v=>v.method==='navigateTo'),{method:'navigateTo',url:storyArticle(p)});f.page.setData({story:{...p,state:'candidate'}});const n=f.calls.length;f.page.openFullStory();assert.equal(f.calls.length,n)
})
test('statistics preserve confirmed participation denominator and definition; invalid data is not converted to a fake zero',()=>{
  const s=presentAbout(content).stats;assert.equal(s.percent,'59.9');assert.equal(s.campCount,18);assert.equal(s.participantOccurrences,676);assert.equal(s.returningOccurrences,405);assert.match(s.definition,/非独立人数复购率/);for(const patch of [{participantOccurrences:0},{returningOccurrences:677},{campCount:-1},{state:'candidate'}]){const c=clone(content);Object.assign(c.historicalEvidence.report,patch);assert.equal(presentAbout(c).stats,null)}
})
test('participation and full risk paragraphs remain readable content, with no truncation or missing restriction',()=>{
  const p=presentAbout(content).transparency;assert(p.participation[0].text.includes('纯蔬菜榨汁代替日常餐食'));assert.equal(p.participation[0].parts.map(v=>v.text).join(''),p.participation[0].text);assert(p.risks[0].includes('低血糖、脱水'));assert(p.risks[1].includes('不要因此停药'));const css=fs.readFileSync(root+'/pages/about/index.wxss','utf8');assert(!/line-clamp|text-overflow|overflow:\s*hidden/.test(css));assert(css.includes('16px * var(--about-font-scale'));assert(css.includes('safe-area-inset-bottom'))
})
test('visitor and share cold starts initialize identical safe public data without login or candidate state',()=>{
  const f=fixture();f.page.onLoad();const share=clone(f.page.onShareAppMessage());assert.deepEqual(share,{title:'一起来轻生活吧！',path:'/pages/about/index',imageUrl:'/assets/brand/share-about.jpg'});assert(fs.existsSync(root+'/assets/brand/share-about.jpg'));const cold=fixture();cold.page.onLoad({from:'share',preview:'candidate'});assert.deepEqual(cold.page.data,f.page.data);assert.equal(cold.page.data.story.name,'花花一家');assert(fs.readFileSync(root+'/pages/about/index.wxml','utf8').includes('open-type="share"'))
})
test('registration action opens existing camp list; image failure and large fonts preserve narrative text',()=>{
  const f=fixture(configured(),20);f.page.onLoad();assert.equal(f.page.data.fontScale,1.25);const text=f.page.data.story.quote;f.page.imageFailed({currentTarget:{dataset:{id:'portrait-b'}}});assert.equal(f.page.data.failedImages['portrait-b'],true);assert.equal(f.page.data.story.quote,text);f.page.learnRegistration();assert.deepEqual(f.calls.find(v=>v.url==='/pages/camp/index'),{method:'navigateTo',url:'/pages/camp/index'});assert(!f.calls.some(v=>v.method==='switchTab'))
})
test('independent route uses shared safe return component and homepage is connected to it',()=>{
  const app=JSON.parse(fs.readFileSync(root+'/app.json'));assert.equal(app.pages.filter(v=>v==='pages/about/index').length,1);const config=JSON.parse(fs.readFileSync(root+'/pages/about/index.json'));assert.equal(config.navigationStyle,'custom');assert.equal(config.usingComponents['ql-nav-space'],'/components/ql-nav-space/index');assert(fs.readFileSync(root+'/pages/about/index.wxml','utf8').includes('<ql-nav-space id="about-nav" title="了解轻生活" />'));let home;const calls=[];vm.runInNewContext(fs.readFileSync(root+'/pages/home/index.js','utf8'),{Page:v=>home=v,require:id=>id==='../../utils/navigation'?{navigateTo:o=>calls.push({method:'navigateTo',...o}),switchTab:o=>calls.push({method:'switchTab',...o})}:require(path.resolve(root+'/pages/home',id))});home.about();assert.deepEqual(calls[0],{method:'switchTab',url:'/pages/about/index'})
})

test('full-story entry is absent until an approved public target is configured; missing target preserves the story',()=>{
  const template=fs.readFileSync(root+'/pages/about/index.wxml','utf8'),tag=template.match(/<button[^>]*class="about-full-story"[^>]*>/)[0];
  const condition=tag.match(/wx:if="{{([^}]+)}}"/)[1];
  const p=person(),unconfigured=configured({...p,fullStory:{...p.fullStory,businessArticleId:null}});
  const empty=presentAbout(unconfigured);assert.equal(empty.story.name,p.name);assert.equal(empty.story.quote,p.quote);assert.equal(empty.story.articleUrl,'');
  assert.equal(!!vm.runInNewContext(condition,empty),false);assert.equal(!!vm.runInNewContext(condition,presentAbout(configured())),true);
  const f=fixture(unconfigured);f.page.onLoad();f.page.openFullStory();assert.equal(f.calls.filter(c=>c.method==='navigateTo').length,0);
})

test('#46 scene shows only after its own release and identity checks, and hides automatically when an approved person story is shown',()=>{
  const released=c=>{c.assets=c.assets.map(a=>a.id==='scene-46'?{...a,url:'https://cdn.example.invalid/qing-about-return-46.jpg',state:'releaseReady',authorization:'confirmed',identityCheck:'confirmed'}:a);return c}
  const noStory=c=>{c.storySlot={...c.storySlot,item:null};return c}
  assert.equal(presentAbout(content).storyScene,null)
  const pendingIdentity=noStory(clone(content));pendingIdentity.assets=pendingIdentity.assets.map(a=>a.id==='scene-46'?{...a,url:'https://cdn.example.invalid/x.jpg',state:'releaseReady',authorization:'confirmed',identityCheck:'pending'}:a);assert.equal(presentAbout(pendingIdentity).storyScene,null)
  const scene=presentAbout(released(noStory(clone(content)))).storyScene;assert.equal(scene.id,'scene-46');assert.equal(scene.caption,'导游军军在课程中协助轻友放松')
  const withStory=presentAbout(released(configured()));assert.equal(withStory.story.name,'单元人物甲');assert.equal(withStory.storyScene,null)
  const template=fs.readFileSync(root+'/pages/about/index.wxml','utf8');assert(template.includes('wx:if="{{storyScene && !failedImages[storyScene.id]}}"'))
})
test('latest period card reuses the public list only and disappears on failure, empty list or demo mode',async()=>{
  const camp={id:'s-1',name:'单元营期',sessionNumber:12,sourceStatus:'open',publicListed:true,status:'开放报名',startDate:'2099-03-01',endDate:'2099-03-03',place:'单元地点'}
  const ok=fixture(content,16,{enabled:()=>true,sessions:async()=>[camp]});ok.page.onLoad();await ok.page.loadLatestCamp();assert.deepEqual(clone(ok.page.data.latestCamp),{id:'s-1',periodLabel:'第12期',status:'开放报名',placeLabel:'单元地点',dateLabel:ok.page.data.latestCamp.dateLabel,dateParts:{start:'03.01',end:'03.03',year:'2099 · 3 天'}})
  ok.page.openCampList();assert.deepEqual(ok.calls.at(-1),{method:'navigateTo',url:'/pages/camp/index'})
  for(const business of [{enabled:()=>true,sessions:async()=>{throw Error('offline')}},{enabled:()=>true,sessions:async()=>[]},{enabled:()=>false,sessions:async()=>{throw Error('must not call')}}]){const f=fixture(content,16,business);f.page.onLoad();f.page.setData({latestCamp:{id:'stale'}});await f.page.loadLatestCamp();assert.equal(f.page.data.latestCamp,null)}
})
test('approved family story passes the real gate, hides #46 and carries no medication or contact claims',()=>{
  const item=content.storySlot.item,r=presentAbout(content);assert.equal(r.story.name,'花花一家');assert.equal(r.story.label,'花花一家的故事');assert.equal(r.storyScene,null)
  assert.equal(r.story.portrait,null,'CDN 地址填入前照片不显示');const withUrl=clone(content);withUrl.assets=withUrl.assets.map(a=>a.id==='family-huahua'?{...a,url:'https://cdn.example.invalid/story.jpg'}:a);assert.equal(presentAbout(withUrl).story.portrait.id,'family-huahua')
  const text=JSON.stringify(item).replace(/不要因此停药/,'');assert(!/停药|降糖药|不吃药|药箱|39\.6|13818065135|钟师姐|InBody/.test(text),'不含停药、疗效数字或联系人')
  assert(item.experienceNotice.includes('不代表课程疗效'));for(const patch of [{editorialReview:'pending'},{factCheck:'source-matched'},{identityCheck:'pending'}]){const c=clone(content);Object.assign(c.storySlot.item,patch);assert.equal(presentAbout(c).story,null)}
})
