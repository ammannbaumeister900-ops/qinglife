const { test } = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs'), path = require('node:path'), vm = require('node:vm')
const { presentAbout, storyArticle } = require('../utils/about-presentation')
const content = require('../config/about-content')
const root = path.resolve(__dirname, '..'), clone = v => JSON.parse(JSON.stringify(v))
const approved = { state:'releaseReady', authorization:'confirmed', editorialReview:'approved', factCheck:'confirmed', source:{ document:'unit-test-only' } }
function person(name='单元人物甲', id='unit-person-a') { return { ...approved, id, personKey:id, name, identityCheck:'confirmed', history:['单元测试经历，不是业务事实'], intro:'单元测试简介', quote:'完整单元引语。', closingQuote:'单元收束引语。', attribution:name+' · 单元夹具', fullStory:{ ...approved, businessArticleId:91, personKey:id, publicVerified:true, evidenceRef:'unit-only', title:'单元文章，不是公开ID确认' } } }
function configured(story=person()) { return { ...clone(content), storySlot:{ heading:'单元通用故事标题', item:story } } }
function fixture(config=content, fontSize=16) {
  let page; const calls=[]; const navigation={ navigateTo:o=>calls.push({ method:'navigateTo', ...o }), switchTab:o=>calls.push({ method:'switchTab', ...o }) }
  const wx={ getSystemInfoSync:()=>({fontSizeSetting:fontSize}), showShareMenu:o=>calls.push({method:'showShareMenu',...o}) }
  vm.runInNewContext(fs.readFileSync(root+'/pages/about/index.js','utf8'), { Page:v=>page=v, wx, require:id=>id.includes('config/about-content')?config:id.includes('utils/navigation')?navigation:require(path.resolve(root+'/pages/about',id)) })
  page.data=clone(page.data);page.setData=patch=>Object.assign(page.data,clone(patch));return {page,calls}
}
test('public entry rejects candidate people/photos and ignores preview, person, session and article query injection',()=>{
  const f=fixture();f.page.onLoad({preview:'candidate',person:'琉璃光',articleId:'91',sessionId:'fake'});assert.equal(f.page.data.story,null);assert.equal(f.page.data.founders,null);assert.equal(f.page.data.everyday.photo,null);assert.equal(f.calls.filter(v=>v.method==='navigateTo').length,0);assert.equal(content.storySlot.item,null);assert.equal(content.founders,null);assert.deepEqual(content.assets,[])
  const source=fs.readFileSync(root+'/config/about-content.js','utf8');assert(!/琉璃光|luli-interview|camp-conversation|程文|胡海/.test(source));assert(!/getStorageSync|business-api|ensureToken|login/.test(fs.readFileSync(root+'/pages/about/index.js','utf8')))
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
  const f=fixture();f.page.onLoad();const share=clone(f.page.onShareAppMessage());assert.deepEqual(share,{title:'认识轻生活',path:'/pages/about/index',imageUrl:'/assets/brand/logo-sage.png'});const cold=fixture();cold.page.onLoad({from:'share',preview:'candidate'});assert.deepEqual(cold.page.data,f.page.data);assert.equal(cold.page.data.story,null);assert(fs.readFileSync(root+'/pages/about/index.wxml','utf8').includes('open-type="share"'))
})
test('registration action opens existing camp list; image failure and large fonts preserve narrative text',()=>{
  const f=fixture(configured(),20);f.page.onLoad();assert.equal(f.page.data.fontScale,1.25);const text=f.page.data.story.quote;f.page.imageFailed({currentTarget:{dataset:{id:'portrait-b'}}});assert.equal(f.page.data.failedImages['portrait-b'],true);assert.equal(f.page.data.story.quote,text);f.page.learnRegistration();assert.deepEqual(f.calls.find(v=>v.method==='switchTab'),{method:'switchTab',url:'/pages/camp/index'})
})
test('independent route uses shared safe return component and homepage is connected to it',()=>{
  const app=JSON.parse(fs.readFileSync(root+'/app.json'));assert.equal(app.pages.filter(v=>v==='pages/about/index').length,1);const config=JSON.parse(fs.readFileSync(root+'/pages/about/index.json'));assert.equal(config.navigationStyle,'custom');assert.equal(config.usingComponents['ql-nav-space'],'/components/ql-nav-space/index');assert(fs.readFileSync(root+'/pages/about/index.wxml','utf8').includes('title="了解轻生活" back'));let home;const calls=[];vm.runInNewContext(fs.readFileSync(root+'/pages/home/index.js','utf8'),{Page:v=>home=v,require:id=>id==='../../utils/navigation'?{navigateTo:o=>calls.push(o)}:require(path.resolve(root+'/pages/home',id))});home.about();assert.equal(calls[0].url,'/pages/about/index')
})

test('full-story entry is absent until an approved public target is configured; missing target preserves the story',()=>{
  const template=fs.readFileSync(root+'/pages/about/index.wxml','utf8'),tag=template.match(/<button[^>]*class="about-full-story"[^>]*>/)[0];
  const condition=tag.match(/wx:if="{{([^}]+)}}"/)[1];
  const p=person(),unconfigured=configured({...p,fullStory:{...p.fullStory,businessArticleId:null}});
  const empty=presentAbout(unconfigured);assert.equal(empty.story.name,p.name);assert.equal(empty.story.quote,p.quote);assert.equal(empty.story.articleUrl,'');
  assert.equal(!!vm.runInNewContext(condition,empty),false);assert.equal(!!vm.runInNewContext(condition,presentAbout(configured())),true);
  const f=fixture(unconfigured);f.page.onLoad();f.page.openFullStory();assert.equal(f.calls.filter(c=>c.method==='navigateTo').length,0);
})
