const { test } = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const vm = require('node:vm')
const root = path.resolve(__dirname, '..')
const plain = value => JSON.parse(JSON.stringify(value))
const deferred = () => { let resolve, reject; const promise = new Promise((a,b) => { resolve=a; reject=b }); return { promise,resolve,reject } }
const tick = () => new Promise(resolve => setImmediate(resolve))

function fixture(name) {
  let page, state = require('../utils/store').defaultState()
  state.registration.status = 'none'; state.registrations = {}; state.backend = true
  const context = { state, activities: [{ id: 'session-test', name: '测试活动', date: '2099-01-01', startDate: '2099-01-01', endDate: '2099-01-03', status: '开放报名', sourceStatus: 'open', seatsLeft: 5 }], overview: {} }
  const toasts = [], copies = [], writes = [], links = []
  const privateScope = { token: 'synthetic' }
  let serverDailyRecords = null
  const toServerRecord = ([key, row]) => ({ ...row, id: key, recordDate: state.habit.serverDate,
    recordStage: 'habit', planDay: state.habit.currentDay, sessionId: state.habit.sessionId || null, choiceValue: row.choice })
  const business = {
    normalizeHabit: raw => raw, isDemo: () => false, identityScope: () => privateScope,
    assertIdentity: scope => { assert.equal(scope, privateScope) },
    privateOverview: async () => {
      await business.refreshHabit()
      if (!serverDailyRecords) serverDailyRecords = Object.entries(state.dailyRecords).map(toServerRecord)
      return { scope: privateScope, overview: { customerId: 'test-owner', habit: state.habit, dailyRecords: plain(serverDailyRecords) } }
    },
    recordDaily: value => { serverDailyRecords = [{ ...value, id: 'test-daily', choiceValue: value.choiceValue, updatedAt: '2026-10-06' }] },
    enabled: () => true, context: async () => context, featuredReadings: async () => [], homeReadings: async () => ({ stories: [], featured: await business.featuredReadings() }), campVoices: async () => [],
    readings: async filters => { const result = await legacy.getArticles(filters.pageNum); if(result.error) throw Error(result.error); return result }, readingTopics: async () => [],
    contact: async () => ({ name: '服务人员', wechat: 'test-wechat', configured: true }), login: async () => {},
    reading: async id => ({ id, title: '真实文章', content: '正文' }), refreshHabit: async () => state.habit,
    saveExperience: async () => {}, saveDailyRecord: async value => { writes.push(plain(value)) }
  }
  const legacy = { getArticle: async () => ({ item: {}, source: 'legacy' }), getArticles: async () => ({ list: [], hasMore: false }), getLegacyPosts: async () => ({ list: [], hasMore: false }) }
  const favorites = { isCollected: async () => false, getCollections: async () => ({ list: [], source: 'local' }), setCollected: async () => {} }
  const store = { getState: () => plain(state), updateState: fn => { state = fn(plain(state)); return plain(state) } }
  const wx = { showToast: value => toasts.push(value.title), setClipboardData: value => { copies.push(value.data); value.success() } }
  const mocks = { '../../services/business-api': business, '../../services/legacy-api': legacy, '../../services/article-favorites': favorites,
    '../../services/staff-api': { request: async () => ({}) }, '../../utils/store': store, '../../utils/navigation': { navigateTo: value => links.push(value.url) }, '../../config/runtime': { environment: 'production' } }
  vm.runInNewContext(fs.readFileSync(path.join(root, 'pages', name, 'index.js'), 'utf8'), {
    wx, Page: value => { page=value }, require: id => mocks[id] || require(path.resolve(root,'pages',name,id))
  })
  page.data = plain(page.data); page.setData = patch => Object.assign(page.data, patch)
  const wxml = fs.readFileSync(path.join(root, 'pages', name, 'index.wxml'), 'utf8')
  const condition = marker => {
    const match = [...wxml.matchAll(/wx:(?:if|elif)="{{([^}]+)}}"/g)].find(item => item[1].includes(marker))
    assert.ok(match, 'missing condition ' + marker)
    return !!vm.runInNewContext(match[1], page.data)
  }
  return { page, business, legacy, favorites, context, toasts, copies, writes, condition, links, state: () => state,
    run: () => page[name === 'mine' ? 'refresh' : 'onShow']() }
}

// Mine is a directory whose public entries survive private read failures; see the mine-specific contract below.
for (const name of ['home','camp']) {
  test(name + ': first loading, failure, retry and successful empty response do not overlap normal content', async () => {
    const f = fixture(name), first = deferred()
    f.business.context = () => first.promise
    const pending = f.run()
    assert.equal(f.page.data.loading, true)
    assert.equal(f.condition('!loading && !loadError'), false)
    first.reject(new Error('offline')); await pending
    assert.equal(f.page.data.loading, false)
    assert.equal(f.page.data.loadError, 'offline')
    assert.ok(!f.page.data.ready, 'failed first read must not mark the page ready')
    assert.equal(f.condition('!loading && !loadError'), false)
    const retry = deferred(); f.business.context = () => retry.promise
    const retrying = f.run()
    assert.equal(f.page.data.loading, true)
    assert.equal(f.page.data.refreshing, false)
    assert.equal(f.page.data.loadError, '')
    f.context.activities = []
    retry.resolve(f.context); await retrying
    assert.equal(f.page.data.loading, false)
    assert.equal(f.page.data.ready, true)
    assert.equal(f.page.data.loadError, '')
    assert.equal(f.condition('!loading && !loadError'), true)
    assert.ok(f.page.data.visibleActivities ? !f.page.data.visibleActivities.length : !f.page.data.featured)
  })
  test(name + ': refresh keeps the last good content while pending, then exposes failure and recovers', async () => {
    const f = fixture(name)
    await f.run()
    const oldState = plain(f.page.data.state), refresh = deferred()
    f.business.context = () => refresh.promise
    const pending = f.run()
    assert.equal(f.page.data.refreshing, true)
    assert.equal(f.page.data.loading, false)
    assert.equal(f.condition('!loading && !loadError'), true)
    assert.deepEqual(plain(f.page.data.state), oldState)
    refresh.reject(new Error('refresh offline')); await pending
    assert.equal(f.page.data.refreshing, false)
    assert.equal(f.condition('!loading && !loadError'), false)
    f.business.context = async () => f.context
    await f.run()
    assert.equal(f.page.data.loadError, '')
    assert.equal(f.condition('!loading && !loadError'), true)
  })
}

test('mine: private read failures stay local, keep the directory and recover by retry', async () => {
  const f = fixture('mine'), first = deferred()
  f.business.context = () => first.promise
  const pending = f.run()
  assert.equal(f.page.data.loading, true)
  assert.equal(f.page.data.signedIn, true)
  first.reject(new Error('offline')); await pending
  assert.equal(f.page.data.loading, false)
  assert.equal(f.page.data.profileError, '网络不太稳定，个人信息暂时没能读取。')
  assert.ok(!f.page.data.ready, 'failed first read must not mark the page ready')
  assert.equal(f.condition('profileError'), true)
  f.business.context = async () => f.context
  await f.run()
  assert.equal(f.page.data.profileError, '')
  assert.equal(f.page.data.ready, true)
  const oldState = plain(f.page.data.state), refresh = deferred()
  f.business.context = () => refresh.promise
  const refreshing = f.run()
  assert.equal(f.page.data.refreshing, true)
  assert.deepEqual(plain(f.page.data.state), oldState)
  refresh.reject(new Error('')); await refreshing
  assert.equal(f.page.data.profileError, '个人信息暂时没能读取。')
  assert.deepEqual(plain(f.page.data.state), oldState)
})

test('mine: an expired credential becomes the guest directory with an expiry notice, never an error page', async () => {
  const f = fixture('mine'); await f.run()
  let token = 'synthetic'
  f.business.identityScope = () => ({ token })
  f.business.context = async () => { token = ''; const error = new Error(''); error.code=401; throw error }
  await f.run()
  assert.deepEqual(plain(f.page.data.state), { backend: true, loggedIn: false })
  assert.equal(f.page.data.signedIn, false)
  assert.equal(f.page.data.profileError, '')
  assert.equal(f.page.data.notice.text, '登录已过期，请重新登录后查看个人信息。')
})

test('expired identity invalidates ready state; even an empty error message cannot expose fake normal content', async () => {
  for (const name of ['home','camp']) {
    const f = fixture(name); await f.run()
    f.business.context = async () => { const error = new Error(''); error.code=401; throw error }
    await f.run()
    assert.ok(f.page.data.loadError)
    assert.equal(f.page.data.ready, false)
    assert.equal(f.condition('!loading && !loadError'), false)
    assert.deepEqual(plain(f.page.data.state), {})
  }
})

test('featured reading failures have their own recoverable error rather than becoming empty content', async () => {
  const f = fixture('home')
  f.business.featuredReadings = async () => { throw new Error('featured offline') }
  await f.run()
  assert.equal(f.page.data.loadError, '')
  assert.equal(f.page.data.readingError, '轻读暂时未能读取，请稍后重试。')
  assert.equal(f.condition('!loading && !loadError'), true)
  assert.equal(f.condition('readingError'), true)
  f.business.featuredReadings = async () => [{ id:'reading-1', title:'已发布文章' }]
  await f.page.onShow()
  assert.equal(f.page.data.readingError, '')
  assert.equal(f.page.data.featuredReadings.length, 1)
  assert.equal(f.condition('readingError'), false)
})

test('article distinguishes request failure, successful empty response and valid content, and always ends loading', async () => {
  const f = fixture('article')
  f.page.setData({ id:'remote-1', routeSource:'business', article:{id:'old'}, collected:true })
  f.business.reading = async () => { throw new Error('网络断开') }
  await f.page.loadArticle()
  assert.equal(f.page.data.loading, false)
  assert.equal(f.page.data.error, '网络断开')
  assert.deepEqual(plain(f.page.data.article), {})
  assert.equal(f.page.data.collected, false)
  f.business.reading = async () => ({})
  await f.page.loadArticle()
  assert.equal(f.page.data.error, '')
  assert.ok(!f.page.data.article.id)
  f.business.reading = async id => ({ id, title:'文章', content:'完整正文' })
  f.favorites.isCollected = async () => true
  await f.page.loadArticle()
  assert.equal(f.page.data.error, '')
  assert.equal(f.page.data.article.id, 'remote-1')
  assert.equal(f.page.data.collected, true)
  assert.equal(f.page.data.loading, false)
})

test('legacy article errors and rejected favorite metadata have a retry path without indefinite loading', async () => {
  const f = fixture('article'); f.page.setData({id:'legacy-1',routeSource:'legacy'})
  f.legacy.getArticle = async () => ({item:{},source:'legacy',error:'历史服务未连接'})
  await f.page.loadArticle()
  assert.equal(f.page.data.error,'历史服务未连接')
  assert.equal(f.page.data.loading,false)
  f.legacy.getArticle = async () => ({item:{id:'legacy-1',title:'未命名文章'},source:'legacy'})
  f.favorites.isCollected = async () => { throw new Error('storage unavailable') }
  await f.page.loadArticle()
  assert.equal(f.page.data.error,'storage unavailable')
  assert.equal(f.page.data.loading,false)
  f.favorites.isCollected = async () => false; f.page.data.routeTitle='列表标题'
  await f.page.loadArticle()
  assert.equal(f.page.data.error,'')
  assert.equal(f.page.data.article.title,'列表标题')
})

test('repeated article retry taps share the pending read and cannot finish loading out of order', async () => {
  const f=fixture('article'), reading=deferred(); let calls=0
  f.page.setData({id:'remote-1',routeSource:'business'})
  f.business.reading=()=>{calls++;return reading.promise}
  const first=f.page.loadArticle(), second=f.page.loadArticle()
  assert.equal(calls,1)
  assert.equal(f.page.data.loading,true)
  reading.resolve({id:'remote-1',title:'文章'}); await Promise.all([first,second])
  assert.equal(f.page.data.loading,false)
  assert.equal(f.page.data.error,'')
})

test('latest public contact fallback remains usable after API failure', async () => {
  const f=fixture('mine');f.business.contact=async()=>{throw Error('offline')}
  await f.page.refreshContact();f.page.copyContact()
  assert.deepEqual(f.copies,['qinglife2014']);assert.equal(f.toasts.at(-1),'微信号已复制')
})

test('failed identity overview after login never announces login success', async () => {
  const f=fixture('mine')
  f.business.context=async()=>{const error=new Error('身份已失效');error.code=401;throw error}
  await f.page.login()
  assert.ok(!f.toasts.includes('登录成功'))
  assert.equal(f.page.data.loginLoading,false)
  f.business.context=async()=>f.context
  await f.page.login()
  assert.equal(f.toasts.at(-1),'登录成功')
})

test('collection retry clears the prior error, reports partial reads and recovers from thrown reads', async () => {
  const f=fixture('mine-flow'), read=deferred()
  f.page.data.collectionsError='旧错误';f.favorites.getCollections=()=>read.promise
  const pending=f.page.loadCollections()
  assert.equal(f.page.data.collectionsLoading,true)
  assert.equal(f.page.data.collectionsError,'')
  read.resolve({list:[{id:'saved',title:'已收藏内容'}],source:'local',error:'partial offline'});await pending
  assert.match(f.page.data.collectionsError,/部分收藏/)
  assert.equal(f.page.data.collections.length,1)
  f.favorites.getCollections=async()=>{throw new Error('read failed')}
  await f.page.loadCollections()
  assert.equal(f.page.data.collectionsLoading,false)
  assert.match(f.page.data.collectionsError,/保留上次读取/)
  assert.equal(f.page.data.collections[0].id,'saved')
  f.favorites.getCollections=async()=>({list:[],source:'local'})
  await f.page.loadCollections()
  assert.equal(f.page.data.collectionsError,'')
  assert.equal(f.page.data.collections.length,0)
})

test('content and archive pagination retain good rows and retry the failed page rather than show a false empty state', async () => {
  for(const name of ['content','friend-flow']){
    const f=fixture(name), pages=[];let fail=false
    const method=name==='content'?'getArticles':'getLegacyPosts'
    f.legacy[method]=async page=>{pages.push(page);return fail?{list:[],error:'offline'}:{list:[{id:String(page),title:'正文',summary:'',category:'主题'}],hasMore:true}}
    const load=()=>f.page[name==='content'?'loadArticles':'loadArchive']()
    await load();fail=true;await load()
    const list=name==='content'?f.page.data.articles:f.page.data.archivePosts
    assert.equal(list.length,1)
    assert.ok(name==='content'?f.page.data.error:f.page.data.archiveError)
    const retry=deferred();f.legacy[method]=page=>{pages.push(page);return retry.promise}
    const pending=load()
    assert.equal(name==='content'?f.page.data.error:f.page.data.archiveError,'')
    assert.equal(f.condition(name==='content'?'loaded &&':'archiveLoaded &&'),false)
    retry.resolve({list:[{id:'2',title:'正文',summary:'',category:'主题'}],hasMore:false});await pending
    assert.deepEqual(pages,[1,2,2])
    assert.equal(name==='content'?f.page.data.articles.length:f.page.data.archivePosts.length,2)
  }
})

test('empty list retries show loading without a simultaneous empty message', async () => {
  for(const name of ['content','friend-flow']){
    const f=fixture(name),read=deferred()
    if(name==='content'){f.page.data.loaded=true;f.legacy.getArticles=()=>read.promise}
    else{f.page.data.archiveLoaded=true;f.legacy.getLegacyPosts=()=>read.promise}
    const pending=f.page[name==='content'?'loadArticles':'loadArchive']()
    assert.equal(f.condition(name==='content'?'loaded &&':'archiveLoaded &&'),false)
    read.resolve({list:[],hasMore:false});await pending
    assert.equal(f.condition(name==='content'?'loaded &&':'archiveLoaded &&'),true)
  }
})

test('the review never writes through the old private API; its one entry opens the 轻友心声 editor', async () => {
 const f=fixture('camp-flow');f.business.campReflection=async()=>({canAfter:true});f.page.setData({reflectionAccess:{allowed:true},registration:{status:'confirmed',participants:[{id:'person-self'}]},canReview:true,canWriteReview:true});
 for(const h of ['onFeelingInput','saveFeeling','onReviewInput','saveReview'])assert.equal(f.page[h],undefined,h);
 f.business.saveExperience=()=>{throw Error('the review must not write through the old API')};await f.page.openReflection();assert.match(f.links.at(-1),/camp-reflection/);
})

test('daily edited drafts survive a failed preflight/write and do not claim to be saved', async () => {
  const f=fixture('friend-flow'),state=f.context.state
  state.stage='habit';state.habit={...state.habit,id:'plan-test',serverDate:'2026-10-06',canRecordToday:true}
  const key='habit-plan-test-2026-10-06';state.dailyRecords={[key]:{choice:'rest',note:'已保存正文'}}
  f.business.refreshHabit=async()=>state.habit
  await f.page.refresh()
  assert.equal(f.condition('savedRecord &&'),true)
  f.page.chooseDaily({currentTarget:{dataset:{value:'done'}}});f.page.onDailyNote({detail:{value:'  新的草稿  '}})
  assert.equal(f.condition('savedRecord &&'),false)
  f.business.saveDailyRecord=async()=>{throw new Error('save offline')}
  await f.page.saveDaily()
  assert.equal(f.page.data.dailyNote,'  新的草稿  ')
  assert.equal(f.page.data.dailyChoice,'done')
  assert.equal(f.condition('savedRecord &&'),false)
  assert.equal(f.state().dailyRecords[key].note,'已保存正文')
  f.business.saveDailyRecord=async value=>{f.writes.push(plain(value));f.business.recordDaily(value)}
  await f.page.saveDaily();await tick()
  assert.equal(f.condition('savedRecord &&'),true)
  assert.equal(f.writes[0].note,'新的草稿')
  assert.equal(f.toasts.at(-1),'私人记录已保存')
})

test('error templates retain actionable retries and avoid unsupported update/configuration explanations', () => {
  const article=fs.readFileSync(path.join(root,'pages/article/index.wxml'),'utf8')
  assert.ok(article.includes('wx:elif="{{error}}"'))
  assert.ok(article.includes('wx:elif="{{!article.id}}"'))
  assert.ok(article.includes('bindtap="loadArticle"'))
  assert.ok(!article.includes('可能正在更新'))
  const mine=fs.readFileSync(path.join(root,'pages/mine/index.wxml'),'utf8')
  assert.ok(mine.includes('user-select="true"'))
  assert.ok(mine.includes('bindtap="copyContact"'))
  for(const name of ['home','camp','mine'])assert.ok(fs.readFileSync(path.join(root,'pages',name,'index.wxml'),'utf8').includes('bindtap="onShow">重试'))
})


test('daily edits across preflight, write and background refresh remain editable without duplicate saves', async () => {
  const f=fixture('friend-flow'),state=f.context.state,preflight=deferred(),write=deferred();let checks=0
  state.stage='habit';state.habit={...state.habit,id:'plan-test',serverDate:'2026-10-06',canRecordToday:true}
  state.dailyRecords={'habit-plan-test-2026-10-06':{choice:'rest',note:'旧记录'}}
  await f.page.refresh()
  f.business.refreshHabit=()=>++checks===1?preflight.promise:Promise.resolve(state.habit)
  f.business.saveDailyRecord=value=>{f.writes.push(plain(value));return write.promise.then(()=>f.business.recordDaily(value))}
  f.page.chooseDaily({currentTarget:{dataset:{value:'done'}}});f.page.onDailyNote({detail:{value:'提交时的正文'}})
  const pending=f.page.saveDaily()
  const repeated=f.page.saveDaily()
  assert.equal(checks,1,'pending save must not start another preflight')
  f.page.onDailyNote({detail:{value:'预检期间继续编辑'}})
  preflight.resolve(state.habit);await tick()
  assert.equal(f.page.data.dailyNote,'预检期间继续编辑')
  assert.equal(f.writes[0].note,'提交时的正文')
  f.page.onDailyNote({detail:{value:'写入期间的新修改'}})
  write.resolve();await Promise.all([pending,repeated]);await tick()
  assert.equal(f.page.data.dailyNote,'写入期间的新修改')
  assert.equal(f.condition('savedRecord &&'),false)
  assert.equal(f.page.data.dailySaving,false)
  assert.equal(f.writes.length,1)
  assert.match(f.toasts.at(-1),/新修改未保存/)
})
