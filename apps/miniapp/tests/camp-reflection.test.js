const { test } = require('node:test'), assert = require('node:assert/strict'), fs = require('node:fs'), vm = require('node:vm'), path = require('node:path')
const reflection = require('../utils/reflection')
function page(api, wxOverrides = {}, timers = { setTimeout, clearTimeout }) { let page; vm.runInNewContext(fs.readFileSync(path.join(__dirname, '../pages/camp-reflection/index.js'), 'utf8'), { Page: p => { page = p }, require: p => p.includes('business-api') ? api : p.includes('reflection') ? reflection : {}, wx: { showToast() {}, ...wxOverrides }, Date, ...timers }); page.data = JSON.parse(JSON.stringify(page.data)); page.setData = patch => Object.assign(page.data, patch); return page }

test('new posts default to nickname while legacy records remain anonymous', () => { assert.equal(reflection.draft({ records: [] }, 'before').anonymous, false); const old = reflection.draft({ records: [{ phase: 'before', note: '期待', revision: 3, shareConsent: 1, status: 'published' }] }, 'before'); assert.equal(old.shared, true); assert.equal(old.anonymous, true); assert.equal(reflection.draft({ records: [{phase:'before',anonymous:0}] }, 'before').anonymous,false); const text = '轻'.repeat(150); assert.equal(reflection.count(reflection.voiceText(text, '草稿')), 153) })

test('the single submit action always enters review and uses the current nickname or anonymous choice', async () => { const calls = []; const p = page({ enabled: () => true, campReflection: async () => ({ registrationId: 'self', authorName: '小禾', canBefore: true, records: [] }), saveCampReflection: async data => { calls.push(data); return { ...data, status: data.action === 'submit' ? 'submitted' : 'private', revision: calls.length, shareConsent: data.shareConsent ? 1 : 0 } } }); p.data.sessionId = 'period'; await p.load(); p.onInput({ detail: { value: '想把一点轻带回日常' } }); await p.save(); assert.equal(calls[0].registrationId, 'self'); assert.equal(calls[0].action, 'submit'); assert.equal(calls[0].shareConsent, true); assert.equal(calls[0].anonymous, false); assert(!('author' in calls[0])); p.toggleAnonymous(); await p.save(); assert.equal(calls[1].action, 'submit'); assert.equal(calls[1].anonymous, true); assert.equal(calls[1].phase, 'before'); await p.save({currentTarget:{dataset:{action:'private'}}}); assert.equal(calls[2].action,'submit'); assert.equal(calls[2].shareConsent,true); assert.equal(calls[2].anonymous,true); assert.equal(p.data.anonymous,true) })

test('barrage and full text share the server supplied nickname or anonymous author', () => { let component, modal;vm.runInNewContext(fs.readFileSync(path.join(__dirname,'../components/camp-voices/index.js'),'utf8'),{Component:c=>{component=c},wx:{showModal:m=>{modal=m}}});const state={data:{voices:[{id:'named',phase:'before',author:'小禾',note:'来时'},{id:'anonymous',phase:'after',author:'本期轻友',note:'归去'}]},setData(){}};component.methods.read.call(state,{currentTarget:{dataset:{id:'named'}}});assert.equal(modal.title,'小禾 · 轻友心声');component.methods.read.call(state,{currentTarget:{dataset:{id:'anonymous'}}});assert.equal(modal.title,'本期轻友 · 轻友心声');const wxml=fs.readFileSync(path.join(__dirname,'../components/camp-voices/index.wxml'),'utf8');assert.equal((wxml.match(/item.author/g)||[]).length,2) })

test('failed backend does not pretend to save locally', async () => { const p = page({ enabled: () => true, campReflection: async () => { throw Error('离线') } }); await p.load(); assert.equal(p.data.error, '离线'); assert.equal(p.data.loading, false) })

test('single form automatically uses the currently open window despite an old phase URL',async()=>{
 const p=page({enabled:()=>true,campReflection:async()=>({registrationId:'self',canBefore:false,canAfter:true,records:[{phase:'before',note:'来时的草稿',revision:2},{phase:'after',note:'现在的草稿',revision:4}]})})
 p.data.requestedPhase='before';await p.load();assert.equal(p.data.phase,'after');assert.equal(p.data.note,'现在的草稿');assert.equal(p.data.revision,4);assert.equal(p.choosePhase,undefined);assert.equal(p.data.pastShares[0].note,'来时的草稿');assert.equal(p.data.pastShares[0].shared,false)
 assert.equal(reflection.selectedPhase({canBefore:true,canAfter:false}),'before');assert.equal(reflection.selectedPhase({canBefore:true,canAfter:true}),'after')
})
test('past public posts remain retractable and withdrawing one preserves the current unsaved draft',async()=>{
 let withdrawn=false,phase
 const context=()=>({registrationId:'self',canAfter:true,records:[{phase:'before',note:'旧草稿',sharedNote:'实际公开的心声',shareConsent:withdrawn?0:1,revision:3,status:withdrawn?'withdrawn':'published'},{phase:'after',note:'结束时的草稿',revision:6}]})
 const p=page({enabled:()=>true,campReflection:async()=>context(),withdrawCampReflection:async(id,value)=>{phase=value;withdrawn=true}}, {showModal:options=>options.success({confirm:true})})
 p.data.sessionId='period';await p.load();assert.equal(p.data.pastShares[0].note,'实际公开的心声');p.onInput({detail:{value:'尚未保存的这一句'}})
 await p.withdraw({currentTarget:{dataset:{phase:'before'}}});assert.equal(phase,'before');assert.equal(p.data.pastShares.length,1);assert.equal(p.data.pastShares[0].shared,false);assert.equal(p.data.pastShares[0].note,'旧草稿');assert.equal(p.data.note,'尚未保存的这一句');assert.equal(p.data.revision,6);assert.equal(p.data.draftDirty,true)
})
test('overlong recognized text stays intact and editable until manually shortened', async () => {
 let saves = 0; const p = writable(page({ transcribeReflection: async () => ({ text: '新的一句' }), saveCampReflection: async () => saves++ }, microphone().wx))
 p.onInput({ detail: { value: '轻'.repeat(150) } }); await p.recognize({ tempFilePath: 'long.mp3', duration: 1000 })
 assert.equal(p.data.length, 155); assert(p.data.note.endsWith('新的一句')); await p.save(); assert.equal(saves, 0)
 p.onInput({ detail: { value: '精简后的文字' } }); assert.equal(p.data.length, 6)
})

function deferred() { let resolve, reject; const promise = new Promise((yes, no) => { resolve = yes; reject = no }); return { promise, resolve, reject } }
function writable(p) { p.data.context = { registrationId: 'self', authorName: '小禾', canBefore: true, voiceAvailable: true }; p.data.phase = 'before'; return p }
function clock() { let next = 0; const callbacks = new Map(); return { setTimeout: (fn, delay) => { callbacks.set(++next, { fn, delay }); return next }, clearTimeout: id => callbacks.delete(id), fire: delay => { for (const [id, value] of [...callbacks]) if (value.delay === delay) { callbacks.delete(id); value.fn() } }, count: () => callbacks.size } }
function microphone() {
 const handlers = {}, calls = { start: [], stop: 0, deleted: [] }
 const manager = { onStart: fn => handlers.start = fn, onStop: fn => handlers.stop = fn, onError: fn => handlers.error = fn, offStart: () => delete handlers.start, offStop: () => delete handlers.stop, offError: () => delete handlers.error, start: options => calls.start.push(options), stop: () => calls.stop++ }
 return { handlers, calls, wx: { getRecorderManager: () => manager, getFileSystemManager: () => ({ readFile: options => options.success({ data: 'test-base64' }), unlink: options => calls.deleted.push(options.filePath) }) } }
}
const allowVoice = { showModal: options => { assert(Array.from(options.confirmText).length <= 4); if (options.cancelText) assert(Array.from(options.cancelText).length <= 4); options.success({ confirm: true }) }, getPrivacySetting: options => options.success({ needAuthorization: false }), getSetting: options => options.success({ authSetting: { 'scope.record': true } }), authorize: options => options.success({}) }
const tick = () => new Promise(resolve => setImmediate(resolve))
function hold(p) { p.voiceTouchStart({ touches: [{ clientY: 400 }] }); return p.beginVoice() }
function ready(p) { p.voiceConsent = true; p.voiceReady = true; return p }

test('first-use native consent meets the actual four-character limit and never records through the dialog', async () => {
 let modal; const mic = microphone(), p = writable(page({}, { ...allowVoice, ...mic.wx, showModal: options => { modal = options; allowVoice.showModal(options) } }))
 await hold(p); assert.equal(modal.confirmText, '允许录音'); assert.equal(Array.from(modal.confirmText).length, 4); assert.equal(p.voiceReady, true); assert.equal(p.data.voiceStarting, false); assert.equal(mic.calls.start.length, 0)
 p.endVoice(); await hold(p); assert.equal(mic.calls.start.length, 1); mic.handlers.start(); p.cancelVoice(); mic.handlers.stop({ tempFilePath: 'permission.mp3', duration: 1000 })
})

test('a short tap does not ask permission or start recording', () => {
 let modal = 0; const mic = microphone(), p = writable(page({}, { ...mic.wx, showModal: () => modal++ }))
 p.voiceTouchStart({ touches: [{ clientY: 400 }] }); p.endVoice(); assert.equal(p.voiceHeld, false); assert.equal(p.data.voicePressing, false); assert.equal(modal, 0); assert.equal(mic.calls.start.length, 0)
})

test('hold records mp3; release transcribes directly into the same editable draft without submission', async () => {
 const mic = microphone(), calls = [], p = ready(writable(page({ transcribeReflection: async data => { calls.push(data); return { text: '今天慢慢来' } }, saveCampReflection: async () => { throw Error('must not save automatically') } }, mic.wx)))
 p.onInput({ detail: { value: '原有文字' } }); await hold(p); mic.handlers.start(); assert.equal(p.data.recording, true); assert.equal(p.data.voiceStarting, false); assert.equal(mic.calls.start[0].format, 'mp3'); assert.equal(mic.calls.start[0].duration, 45000)
 p.endVoice(); assert.equal(mic.calls.stop, 1); mic.handlers.stop({ tempFilePath: 'hold.mp3', duration: 2000 }); await tick()
 assert.equal(calls[0].voiceConsent, true); assert.equal(calls[0].registrationId, 'self'); assert.equal(p.data.note, '原有文字\n今天慢慢来'); assert.equal(p.data.length, 10); assert.equal(p.data.draftDirty, true); assert.equal(p.data.transcribing, false); assert.deepEqual(mic.calls.deleted, ['hold.mp3'])
 p.onInput({ detail: { value: '可以修正识别错字' } }); p.toggleAnonymous(); assert.equal(p.data.note, '可以修正识别错字'); assert.equal(p.data.anonymous, true)
})

test('release before recorder onStart immediately stops the delayed recording', async () => {
 const mic = microphone(), p = ready(writable(page({ transcribeReflection: async () => ({ text: '短句' }) }, mic.wx)))
 await hold(p); p.endVoice(); assert.equal(mic.calls.stop, 0); mic.handlers.start(); assert.equal(mic.calls.stop, 1)
 mic.handlers.stop({ tempFilePath: 'early-release.mp3', duration: 300 }); await tick(); assert.equal(p.data.recording, false); assert.equal(p.data.voiceStarting, false); assert.equal(p.data.note, ''); assert.match(p.data.voiceError, /太短/); assert.deepEqual(mic.calls.deleted, ['early-release.mp3'])
})

test('sliding up then releasing discards audio and never sends it to Tencent', async () => {
 let transcribed = 0; const mic = microphone(), p = ready(writable(page({ transcribeReflection: async () => transcribed++ }, mic.wx)))
 p.onInput({ detail: { value: '正文保留' } }); await hold(p); mic.handlers.start(); p.voiceTouchMove({ touches: [{ clientY: 310 }] }); assert.equal(p.data.cancelOnRelease, true)
 p.endVoice(); mic.handlers.stop({ tempFilePath: 'discard.mp3', duration: 2000 }); await tick()
 assert.equal(transcribed, 0); assert.equal(p.data.note, '正文保留'); assert.equal(p.data.recording, false); assert.equal(p.data.cancelOnRelease, false); assert.deepEqual(mic.calls.deleted, ['discard.mp3'])
})

test('moving back down keeps the held recording before release', async () => {
 const mic = microphone(), p = ready(writable(page({ transcribeReflection: async () => ({ text: '保留下来' }) }, mic.wx)))
 await hold(p); mic.handlers.start(); p.voiceTouchMove({ touches: [{ clientY: 300 }] }); p.voiceTouchMove({ touches: [{ clientY: 395 }] }); assert.equal(p.data.cancelOnRelease, false)
 p.endVoice(); mic.handlers.stop({ tempFilePath: 'keep.mp3', duration: 1000 }); await tick(); assert.equal(p.data.note, '保留下来')
})

test('declining or failing consent never requests the microphone and does not lock edits', async () => {
 for (const mode of ['decline', 'fail', 'throw']) {
  let authorized = 0; const p = writable(page({}, { showModal: options => { if (mode === 'throw') throw Error('dialog failure'); mode === 'decline' ? options.success({ confirm: false }) : options.fail({ errMsg: 'cancel' }) }, authorize: () => authorized++ }))
  await hold(p); assert.equal(authorized, 0); assert.equal(p.data.voiceStarting, false); p.onInput({ detail: { value: '继续修改' } }); p.toggleAnonymous(); assert.equal(p.data.note, '继续修改'); assert.equal(p.data.anonymous, true)
 }
})

test('waiting for microphone permission never auto-records after release or a late grant', async () => {
 let permission; const mic = microphone(), p = writable(page({}, { ...allowVoice, ...mic.wx, getSetting: options => options.success({ authSetting: {} }), authorize: options => permission = options }))
 p.voiceConsent = true; const pending = hold(p); await tick(); p.endVoice(); p.onInput({ detail: { value: '等待时修改' } }); p.toggleAnonymous(); permission.success({}); await pending
 assert.equal(mic.calls.start.length, 0); assert.equal(p.voiceReady, true); assert.equal(p.data.voiceStarting, false); assert.equal(p.data.note, '等待时修改'); assert.equal(p.data.anonymous, true)
 await hold(p); mic.handlers.start(); p.cancelVoice(); mic.handlers.stop({ tempFilePath: 'granted.mp3', duration: 1000 })
})

test('cancelled permission cannot start a recording when its stale success arrives', async () => {
 let permission; const mic = microphone(), p = writable(page({}, { ...allowVoice, ...mic.wx, getSetting: options => options.success({ authSetting: {} }), authorize: options => permission = options }))
 const pending = hold(p); await tick(); p.cancelVoice(); await pending; permission.success({}); await tick(); assert.equal(mic.calls.start.length, 0); assert.equal(p.data.voiceStarting, false)
})

test('platform privacy consent remains explicit and microphone is not used before agreement', async () => {
 let authorized = 0; const mic = microphone(), p = writable(page({}, { ...allowVoice, ...mic.wx, getPrivacySetting: options => options.success({ needAuthorization: true, privacyContractName: '轻生活隐私保护指引' }), getSetting: options => options.success({ authSetting: {} }), authorize: options => { authorized++; options.success({}) } }))
 await hold(p); assert.equal(p.data.showVoicePrivacy, true); assert.equal(p.data.voiceStarting, false); assert.equal(authorized, 0); assert.equal(mic.calls.start.length, 0)
 p.toggleAnonymous(); await p.agreeVoicePrivacy(); assert.equal(authorized, 1); assert.equal(mic.calls.start.length, 0); assert.equal(p.voiceReady, true); assert.equal(p.data.showVoicePrivacy, false)
 const markup = fs.readFileSync(path.join(__dirname, '../pages/camp-reflection/index.wxml'), 'utf8'); assert.match(markup, /open-type="agreePrivacyAuthorization"/)
 await hold(p); mic.handlers.start(); p.cancelVoice(); mic.handlers.stop({ tempFilePath: 'privacy.mp3', duration: 1000 })
})

test('privacy declaration failure is visible and never bypasses authorization', async () => {
 let authorized = 0; const p = writable(page({}, { ...allowVoice, getPrivacySetting: options => options.fail({ errMsg: 'getPrivacySetting:fail api scope is not declared in the privacy agreement', errCode: 112 }), authorize: () => authorized++ }))
 await hold(p); assert.equal(authorized, 0); assert.equal(p.data.voiceStarting, false); assert.equal(p.data.voiceError, '小程序录音隐私声明未配置'); assert.equal(p.lastVoiceFailure.stage, 'privacy'); assert.equal(p.lastVoiceFailure.code, 112)
})

test('permission denial has a settings path and timeout restores editing', async () => {
 const timers = clock(), p = writable(page({}, { ...allowVoice, getSetting: options => options.success({ authSetting: {} }), authorize: () => {} }, timers))
 const pending = hold(p); await tick(); timers.fire(60000); await pending; assert.equal(p.data.voiceStarting, false); assert.equal(p.data.microphoneDenied, true); assert.equal(timers.count(), 0)
 const denied = writable(page({}, { ...allowVoice, getSetting: options => options.success({ authSetting: { 'scope.record': false } }) }))
 await hold(denied); assert.equal(denied.data.microphoneDenied, true); assert.equal(denied.data.voiceStarting, false); assert.equal(denied.voiceReady, false)
})

test('recording startup watchdog cancels stalled starts and discards their late callbacks', async () => {
 const timers = clock(), mic = microphone(); let transcribed = 0; const p = ready(writable(page({ transcribeReflection: async () => transcribed++ }, mic.wx, timers)))
 await hold(p); timers.fire(15000); assert.equal(p.data.voiceStarting, false); mic.handlers.start(); assert.equal(p.data.recording, false); mic.handlers.stop({ tempFilePath: 'stalled.mp3', duration: 1000 }); assert.equal(transcribed, 0); assert.deepEqual(mic.calls.deleted, ['stalled.mp3']); assert.equal(timers.count(), 0)
})

test('recorder errors clear recording and allow a retry without losing the draft', async () => {
 const mic = microphone(), p = ready(writable(page({}, { ...allowVoice, ...mic.wx })))
 p.onInput({ detail: { value: '原文' } }); await hold(p); mic.handlers.error({ errMsg: 'record fail' }); assert.equal(p.data.recording, false); assert.equal(p.data.voiceStarting, false); assert.equal(p.voiceReady, false); assert.equal(p.data.note, '原文')
 await hold(p); assert.equal(mic.calls.start.length, 2); mic.handlers.start(); p.cancelVoice(); mic.handlers.stop({ tempFilePath: 'retry.mp3', duration: 1000 })
})

test('recording at the 45 second limit transcribes without needing a release event', async () => {
 const mic = microphone(), p = ready(writable(page({ transcribeReflection: async () => ({ text: '自动结束' }) }, mic.wx)))
 await hold(p); mic.handlers.start(); mic.handlers.stop({ tempFilePath: 'limit.mp3', duration: 45000 }); await tick(); assert.equal(p.data.note, '自动结束'); assert.equal(p.voiceHeld, false); assert.equal(p.data.recording, false)
})

test('ASR errors or empty results preserve the latest typed text', async () => {
 for (const result of [null, 'empty']) {
  const p = writable(page({ transcribeReflection: async () => { if (!result) throw Error('转换暂不可用'); return { text: '  ' } } }, microphone().wx))
  p.onInput({ detail: { value: '不丢失的正文' } }); await p.recognize({ tempFilePath: 'failure.mp3', duration: 1000 }); assert.equal(p.data.note, '不丢失的正文'); assert.equal(p.data.transcribing, false); assert(p.data.voiceError)
 }
})

test('typing during recognition is preserved and recognized text appends to the latest draft', async () => {
 const conversion = deferred(), mic = microphone(), p = writable(page({ transcribeReflection: () => conversion.promise }, mic.wx))
 const pending = p.recognize({ tempFilePath: 'edit.mp3', duration: 1000 }); await tick(); p.onInput({ detail: { value: '后来修改的正文' } }); p.toggleAnonymous(); conversion.resolve({ text: '语音文字' }); await pending
 assert.equal(p.data.note, '后来修改的正文\n语音文字'); assert.equal(p.data.anonymous, true); assert.equal(p.data.transcribing, false); assert.equal(p.data.draftDirty, true); assert.deepEqual(mic.calls.deleted, ['edit.mp3'])
})

test('text submission during recognition sends only the visible draft and ignores late ASR', async () => {
 const conversion = deferred(), saved = deferred(), mic = microphone(), requests = []
 const p = writable(page({ transcribeReflection: () => conversion.promise, saveCampReflection: data => { requests.push(data); return saved.promise } }, mic.wx))
 const recognition = p.recognize({ tempFilePath: 'submit.mp3', duration: 1000 }); await tick(); p.onInput({ detail: { value: '明确提交的文字' } }); p.toggleAnonymous(); const submission = p.save(); await p.save(); p.toggleAnonymous(); p.onInput({ detail: { value: '请求期间不混入修改' } }); assert.equal(requests.length, 1); assert.equal(requests[0].anonymous, true); assert.equal(requests[0].action, 'submit'); assert.equal(requests[0].shareConsent, true)
 saved.resolve({ ...requests[0], shareConsent: 1, status: 'submitted', revision: 1 }); await submission; conversion.resolve({ text: '迟到的文字' }); await recognition; assert.equal(p.data.note, '明确提交的文字'); assert.equal(p.data.status, 'submitted'); assert.equal(p.data.saving, false); assert.equal(p.data.transcribing, false)
})

test('a cancelled old conversion cannot clear or alter a newer held recording', async () => {
 const conversion = deferred(), mic = microphone(), p = ready(writable(page({ transcribeReflection: () => conversion.promise }, mic.wx)))
 const previous = p.recognize({ tempFilePath: 'old.mp3', duration: 1000 }); await tick(); p.cancelVoice(); await hold(p); mic.handlers.start(); conversion.resolve({ text: '已取消' }); await previous; assert.equal(p.data.recording, true); assert.equal(p.data.note, ''); p.cancelVoice(); mic.handlers.stop({ tempFilePath: 'new.mp3', duration: 1000 })
})

test('hiding or unloading cancels pending permission and cleans final audio without publishing', async () => {
 let permission; const mic = microphone(), p = writable(page({}, { ...allowVoice, ...mic.wx, getSetting: options => options.success({ authSetting: {} }), authorize: options => permission = options }))
 const pending = hold(p); await tick(); p.onHide(); await pending; permission.success({}); assert.equal(mic.calls.start.length, 0); assert.equal(p.data.voiceStarting, false)
 const recording = ready(writable(page({}, mic.wx))); await hold(recording); mic.handlers.start(); recording.onUnload(); mic.handlers.stop({ tempFilePath: 'unload.mp3', duration: 1000 }); assert.deepEqual(mic.calls.deleted, ['unload.mp3']); assert.equal(mic.handlers.stop, undefined)
})

test('a failed text submission keeps edits and anonymity available for retry', async () => {
 const p = writable(page({ saveCampReflection: async () => { throw Error('网络异常') } }))
 p.onInput({ detail: { value: '保留的心声' } }); await p.save(); assert.equal(p.data.saving, false); assert.equal(p.data.note, '保留的心声'); p.onInput({ detail: { value: '修改后重试' } }); p.toggleAnonymous(); assert.equal(p.data.note, '修改后重试'); assert.equal(p.data.anonymous, true)
})

test('one in-box hold control and one submit remain; recording never disables editing or anonymity', () => {
 const markup = fs.readFileSync(path.join(__dirname, '../pages/camp-reflection/index.wxml'), 'utf8')
 assert.equal((markup.match(/<textarea /g) || []).length, 1); assert.equal((markup.match(/bindtap="save"/g) || []).length, 1); assert(!/voice-review|voice-entry|加入草稿|仅自己保存/.test(markup)); assert.match(markup, /bindlongpress="beginVoice"/); assert.match(markup, /bindtouchend="endVoice"/); assert.match(markup, /bindtouchcancel="cancelVoice"/)
 const input = markup.match(/<textarea class="reflection-input"[^>]+/)[0], anonymous = markup.match(/<button class="reflection-anonymous"[^>]+/)[0], mic = markup.match(/<button class="voice-hold[^>]+/)[0]
 assert(!/voiceStarting|recording|transcribing/.test(input)); assert(!/voiceStarting|recording|transcribing/.test(anonymous)); assert(!/disabled="[^\"]*(voiceStarting|recording|transcribing)/.test(mic))
})

test('reset requires confirmation, changes only the local draft and preserves anonymity and revision', async () => {
 let modal, calls=0; const p=writable(page({ saveCampReflection:async()=>calls++ }, {showModal:options=>{modal=options;assert(Array.from(options.confirmText).length<=4)}}))
 p.data.revision=8;p.data.status='published';p.data.shared=true;p.toggleAnonymous();p.onInput({detail:{value:'仍然保留的文字'}})
 const cancelled=p.resetDraft();assert.equal(p.data.note,'仍然保留的文字');modal.success({confirm:false});await cancelled;assert.equal(p.data.note,'仍然保留的文字')
 const confirmed=p.resetDraft();modal.success({confirm:true});await confirmed;assert.equal(p.data.note,'');assert.equal(p.data.length,0);assert.equal(p.data.anonymous,true);assert.equal(p.data.revision,8);assert.equal(p.data.shared,true);assert.equal(p.data.status,'published');assert.equal(p.data.draftDirty,true);assert.equal(calls,0)
})
test('reset cancels pending transcription so a late response cannot refill the cleared draft', async () => {
 const response=deferred(),mic=microphone(),p=writable(page({transcribeReflection:()=>response.promise},{...mic.wx,showModal:options=>options.success({confirm:true})}))
 p.onInput({detail:{value:'旧文字'}});const recognizing=p.recognize({tempFilePath:'reset.mp3',duration:1000});await tick();assert.equal(p.data.transcribing,true)
 await p.resetDraft();p.onInput({detail:{value:'重写的一句'}});response.resolve({text:'旧录音'});await recognizing;assert.equal(p.data.note,'重写的一句');assert.equal(p.data.transcribing,false);assert.deepEqual(mic.calls.deleted,['reset.mp3'])
})
test('reset ignores duplicate taps and does not clear if submitting, leaving, or the native dialog fails', async () => {
 let modal,count=0;const p=writable(page({}, {showModal:options=>{modal=options;count++}}));p.onInput({detail:{value:'保留'}})
 const pending=p.resetDraft();await p.resetDraft();assert.equal(count,1);p.data.saving=true;modal.success({confirm:true});await pending;assert.equal(p.data.note,'保留');assert.equal(p.resetPending,false)
 p.data.saving=false;const failed=p.resetDraft();modal.fail();await failed;assert.equal(p.data.note,'保留')
 const leaving=p.resetDraft();p.leftPage=true;modal.success({confirm:true});await leaving;assert.equal(p.data.note,'保留');assert.equal(p.resetPending,false)
})
