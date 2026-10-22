const { test } = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const vm = require('node:vm')
const root = path.resolve(__dirname, '..')
const plain = value => JSON.parse(JSON.stringify(value))

function fixture(records = [], options = {}) {
  const context = {
    state: { loggedIn: options.loggedIn !== false, registrations: {}, registration: { status: 'none' },
      dailyRecords: options.dailyRecords || {}, experienceReviews: options.experienceReviews || {} },
    activities: [{ id: 'session-1', name: '同一期活动' }, { id: 'session-2', name: '另一期活动' }],
    overview: { experienceRecords: records, registrations: options.registrations || [{ registrationId: 'self', isSelf: 1, sessionId: 'session-1' }] }
  }
  const writes = [], links = [], business = {
    enabled: () => options.backend !== false, context: async () => context,
    saveExperience: () => { throw new Error('Display must not write records') }
  }
  const store = { getState: () => context.state, updateState: value => writes.push(value) }
  let page
  vm.runInNewContext(fs.readFileSync(path.join(root, 'pages/personal/index.js'), 'utf8'), {
    Page: value => { page = value }, require: name => ({ '../../services/business-api': business, '../../utils/store': store,
      '../../utils/domain': require('../utils/domain'), '../../data/demo': { activities: context.activities }, '../../utils/navigation': { navigateTo: x => links.push(x.url), switchTab: x => links.push(x.url) } })[name]
  })
  page.data = plain(page.data)
  page.setData = patch => Object.assign(page.data, patch)
  page.onLoad({ view: 'records', id: options.sessionId || '' })
  return { page, business, context, writes, links }
}
const record = (id, updatedAt, extra = {}) => ({ id, registrationId: 'self', phase: 'during', note: '私人正文 ' + id, visibility: 'private', updatedAt, ...extra })

test('same-session same-phase records are distinguished by their own save date and second', async () => {
  const source = [record('day-1', '2026-09-07 09:30:01'), record('day-2', '2026-09-08 09:30:01'), record('day-3', '2026-09-08 09:30:02')]
  const f = fixture(source), before = plain(f.context)
  await f.page.onShow()
  assert.deepEqual(plain(f.page.data.records).map(row => row.recordTimeText), ['2026年9月7日 09:30:01', '2026年9月8日 09:30:01', '2026年9月8日 09:30:02'])
  assert.equal(new Set(f.page.data.records.map(row => row.recordTimeText)).size, 3)
  assert.ok(f.page.data.records.every(row => row.label === '活动期间' && row.name === '同一期活动' && row.visibility === 'private'))
  assert.deepEqual(plain(f.context), before, 'response data must not be mutated')
  assert.deepEqual(f.writes, [])
})

test('updatedAt represents latest save rather than creation or a guessed activity date', async () => {
  const f = fixture([record('edited', '2026-10-06 10:11:12', { createdAt: '2026-09-07 08:00:00', savedAt: '2026-09-08 08:00:00' }), record('missing', null, { createdAt: '2026-09-07 08:00:00' })])
  await f.page.refresh()
  assert.equal(f.page.data.records[0].recordTimeText, '2026年10月6日 10:11:12')
  assert.equal(f.page.data.records[1].recordTimeText, '')
})

test('timezone-free server values retain their calendar time; valid date-only history does not invent a clock', async () => {
  const f = fixture([record('sql', '2026-02-28 23:59:59.123'), record('local-iso', '2026-02-28T23:59:59'), record('date', '2024-02-29'), record('minute', '2026-10-06 09:30')])
  await f.page.refresh()
  assert.deepEqual(plain(f.page.data.records).map(row => row.recordTimeText), ['2026年2月28日 23:59:59', '2026年2月28日 23:59:59', '2024年2月29日', '2026年10月6日 09:30'])
})

test('ISO timestamps with zones display using the device local clock, including midnight boundaries', async () => {
  const morning = new Date(2026, 9, 6, 0, 1, 2).toISOString()
  const evening = new Date(2026, 9, 6, 23, 59, 58).toISOString()
  const f = fixture([record('morning', morning), record('evening', evening)])
  await f.page.refresh()
  assert.deepEqual(plain(f.page.data.records).map(row => row.recordTimeText), ['2026年10月6日 00:01:02', '2026年10月6日 23:59:58'])
})

test('empty, malformed and impossible historical timestamps remain readable without invalid date text', async () => {
  const values = [undefined, null, '', ' ', 'Invalid Date', '2026-02-30 10:00:00', '2026-13-01', '2026-04-31', '2026-10-06 24:00:00', '2026-10-06 10:60:00', '2026-10-06 10:00:60', '2026-10-06T10:00:00+99:99', 0, {}, true]
  const f = fixture(values.map((value,index) => record(String(index), value)))
  await f.page.refresh()
  assert.equal(f.page.data.error, '')
  assert.equal(f.page.data.records.length, values.length)
  assert.ok(f.page.data.records.every(row => row.recordTimeText === '' && row.note.startsWith('私人正文')))
})

test('self ownership, selected session, logout and read failure still exclude private records', async () => {
  const f = fixture([record('mine', '2026-09-07 09:00:00'), record('companion', '2026-09-07 09:01:00', { registrationId: 'other' }), record('different-session', '2026-09-07 09:02:00', { registrationId: 'self-2' })], {
    sessionId: 'session-1', registrations: [{ registrationId: 'self', isSelf: '1', sessionId: 'session-1' }, { registrationId: 'other', isSelf: 0, sessionId: 'session-1' }, { registrationId: 'self-2', isSelf: true, sessionId: 'session-2' }]
  })
  await f.page.refresh()
  assert.deepEqual(f.page.data.records.map(row => row.id), ['mine'])
  f.page.onHide()
  assert.equal(f.page.data.records.length, 0)
  f.context.state.loggedIn = false
  await f.page.refresh()
  assert.equal(f.page.data.records.length, 0)
  f.context.state.loggedIn = true
  await f.page.refresh()
  f.business.context = async () => { throw new Error('offline') }
  await f.page.refresh()
  assert.equal(f.page.data.records.length, 0)
  assert.equal(f.page.data.error, 'offline')
})

test('local daily and activity-review records reuse savedAt without changing stored records', async () => {
  const savedAt = new Date(2026, 9, 6, 10, 11, 12).toISOString()
  const f = fixture([], { backend: false, dailyRecords: { daily: { note: '日常正文', savedAt }, old: { note: '旧记录' }, malformed: { note: '旧数值时间', savedAt: 123 } }, experienceReviews: { 'session-1': { note: '活动后正文', savedAt } } })
  const before = plain(f.context.state)
  await f.page.refresh()
  assert.equal(f.page.data.error, '')
  const rows = plain(f.page.data.records)
  assert.equal(rows[0].recordTimeText, '2026年10月6日 10:11:12')
  assert.equal(rows[1].recordTimeText, '')
  assert.equal(rows[2].recordTimeText, '')
  assert.equal(rows[3].recordTimeText, '2026年10月6日 10:11:12')
  assert.equal(rows[3].label, '活动后')
  assert.deepEqual(plain(f.context.state), before)
  assert.deepEqual(f.writes, [])
})

test('time is conditional secondary metadata and does not replace the stage or private record body', () => {
  const wxml = fs.readFileSync(path.join(root,'pages/personal/index.wxml'),'utf8')
  const css = fs.readFileSync(path.join(root,'pages/personal/index.wxss'),'utf8')
  assert.ok(wxml.includes('{{item.label}} · 仅自己可见'))
  assert.ok(wxml.includes('wx:if="{{item.recordTimeText}}" class="record-time">最近保存 · {{item.recordTimeText}}'))
  assert.ok(wxml.includes('wx:if="{{item.note}}" class="record-body">{{item.note}}'))
  assert.ok(!wxml.includes('这一次，留下了一份状态记录。'))
  assert.match(css, /\.record-time[^}]*font-size:\s*28rpx/)
  assert.ok(wxml.indexOf('class="record-body"') < wxml.indexOf('class="record-time"'))
})

// Evaluate the current WXML so feedback tests assert visible content, not just page data.
function parse(xml = fs.readFileSync(path.join(root, 'pages/personal/index.wxml'), 'utf8')) {
  const tree = { children: [] }, stack = [tree]
  for (const token of xml.match(/<(?:[^<>"']|"[^"]*"|'[^']*')+>|[^<]+/g) || []) {
    if (token.startsWith('</')) { assert.equal(stack.pop().tag, token.match(/^<\/([\w-]+)/)[1]); continue }
    if (!token.startsWith('<')) { stack.at(-1).children.push({ text: token }); continue }
    const tag = token.match(/^<([\w-]+)/)[1], attrs = {}
    for (const a of token.slice(tag.length + 1).replace(/\/?\s*>$/, '').matchAll(/([\w:-]+)(?:\s*=\s*(?:"([^"]*)"|'([^']*)'))?/g)) attrs[a[1]] = a[2] ?? a[3] ?? true
    const node = { tag, attrs, children: [] }; stack.at(-1).children.push(node)
    if (!token.endsWith('/>')) stack.push(node)
  }
  assert.equal(stack.length, 1); return tree
}
function value(template, scope) {
  if (template === true) return true
  const evaluate = code => { try { return vm.runInNewContext(code, scope) } catch (e) { if (e.name === 'ReferenceError' || e.name === 'TypeError') return undefined; throw e } }
  const binding = template.match(/^{{([\s\S]*?)}}$/)
  if (binding && !binding[1].includes('}}')) return evaluate(binding[1])
  return template.replace(/{{([\s\S]*?)}}/g, (_, code) => evaluate(code) ?? '')
}
function render(children, scope) {
  const nodes = []; let branch = false
  for (const node of children) {
    if ('text' in node) { nodes.push({ text: String(value(node.text, scope) ?? '') }); continue }
    const a = node.attrs
    const scopes = a['wx:for'] ? (value(a['wx:for'], scope) || []).map((item, index) => ({ ...scope, item, index })) : [scope]
    for (const current of scopes) {
      let visible = true
      if ('wx:if' in a) { visible = !!value(a['wx:if'], current); branch = visible }
      else if ('wx:elif' in a) { visible = !branch && !!value(a['wx:elif'], current); branch ||= visible }
      else if ('wx:else' in a) { visible = !branch; branch = true }
      else branch = false
      if (visible) nodes.push({ tag: node.tag, attrs: Object.fromEntries(Object.entries(a).filter(([k]) => !k.startsWith('wx:')).map(([k,v]) => [k,value(v,current)])), children: render(node.children,current) })
    }
  }
  return nodes
}
const flatten = nodes => nodes.flatMap(n => [n, ...flatten(n.children || [])])
const has = (node, name) => String(node.attrs?.class || '').split(' ').includes(name)
const text = node => flatten([node]).filter(n => 'text' in n).map(n => n.text).join('').trim()
const visible = page => flatten(render(parse().children,page.data))

test('rendered records retain individual stage, complete multiline body and conditional save times', async () => {
  const f = fixture([record('one', '2026-10-06 09:10:01', { note: '第一段感受\n第二段感受', energy: 3 }),record('two','2026-10-06 09:10:02'),record('old',null)])
  await f.page.refresh()
  const rows = visible(f.page).filter(n => has(n,'record-row'))
  assert.equal(rows.length,3)
  for (const row of rows) assert(text(row).includes('活动期间 · 仅自己可见'))
  assert.equal(text(flatten([rows[0]]).find(n => has(n,'record-body'))),'第一段感受\n第二段感受')
  assert(text(rows[0]).includes('精力 3/5'))
  assert(!text(rows[0]).includes('放松'))
  assert(!text(rows[0]).includes('未填'))
  assert(text(rows[0]).includes('09:10:01')); assert(text(rows[1]).includes('09:10:02'))
  assert(!flatten([rows[2]]).some(n => has(n,'record-time')))
})

test('participation renders registration and settlement independently and preserves the session action', async () => {
  const f = fixture()
  f.page.onLoad({view:'experiences'})
  f.context.state.registrations = { first: {activityId:'session-1',status:'pending',participants:[{name:'自己'},{name:'家人'}],settlementStatus:'confirmed',settlementType:'cash',paymentStatus:'paid'} }
  await f.page.refresh()
  const nodes=visible(f.page), rows=nodes.filter(n=>has(n,'participation-state'))
  assert.deepEqual(rows.map(text),['报名待确认','结算现金已收款'])
  assert(!nodes.some(n=>has(n,'record-row')))
  const action=nodes.find(n=>n.attrs?.bindtap==='openSession')
  assert.equal(action.attrs['data-id'],'session-1')
  f.page.openSession({currentTarget:{dataset:{id:'session & 2'}}})
  assert.equal(f.links[0],'/pages/camp-flow/index?view=journey&id=session%20%26%202')
  f.context.state.registrations.first.status='completed'
  f.context.state.registrations.first.settlementType='pass'
  await f.page.refresh()
  assert.deepEqual(visible(f.page).filter(n=>has(n,'participation-state')).map(text),['报名已完成','结算卡次已使用'])
})

test('loading, error and logged-out branches never expose private rows or false empty states', async () => {
  const f=fixture([record('private','2026-10-06 09:00:01')])
  await f.page.refresh()
  for(const patch of [{loading:true},{loading:false,error:'offline'},{error:'',loggedIn:false}]) {
    f.page.setData(patch)
    const nodes=visible(f.page)
    assert(!nodes.some(n=>has(n,'record-row')||has(n,'experience-row')))
    assert(!nodes.some(n=>n.text?.includes('还没有私人记录')))
    if(patch.error) assert(nodes.some(n=>n.attrs?.bindtap==='refresh'))
    if(patch.loggedIn===false) assert(nodes.some(n=>n.attrs?.bindtap==='login'))
  }
  await f.page.refresh()
  assert.equal(visible(f.page).filter(n=>has(n,'record-row')).length,1)
  f.page.onHide(); assert(!visible(f.page).some(n=>has(n,'record-row')))
})

test('empty views remain specific, keep privacy explanation and preserve the camp recovery action', async () => {
  const f=fixture([])
  await f.page.refresh()
  assert(visible(f.page).some(n=>n.text?.includes('还没有私人记录')))
  assert(visible(f.page).some(n=>n.text?.includes('替家人报名不会看到对方的私人记录')))
  f.page.onLoad({view:'experiences'})
  assert(visible(f.page).some(n=>n.attrs?.bindtap==='openCamp'))
  f.page.openCamp(); assert.equal(f.links[0],'/pages/camp/index')
  assert(!visible(f.page).some(n=>n.text?.includes('还没有私人记录')))
})

test('combined session view distinguishes arrangements from records without adding headings to single-purpose views', async () => {
  const f=fixture([record('one','2026-10-06 09:00:01')])
  await f.page.refresh()
  assert(!visible(f.page).some(n=>has(n,'personal-section-title')))
  f.page.onLoad({view:'experience'})
  assert.deepEqual(visible(f.page).filter(n=>has(n,'personal-section-title')).map(text),['参与安排','私人记录'])
  f.page.onLoad({view:'experiences'})
  assert(!visible(f.page).some(n=>has(n,'personal-section-title')))
})

test('participation keeps historical unpaid state and flags operational review without writing it', async () => {
  const f=fixture();f.page.onLoad({view:'experiences'})
  const row={activityId:'session-1',status:'confirmed',paymentStatus:'unpaid',settlementStatus:'confirmed',settlementType:'money',finalAmount:3800,participants:[]}
  f.context.state.registrations={first:row};const before=plain(f.context.state)
  await f.page.refresh()
  assert.deepEqual(visible(f.page).filter(n=>has(n,'participation-state')).map(text),['报名已确认','结算缴费和名额待运营核查'])
  assert.deepEqual(plain(f.context.state),before);assert.equal(f.writes.length,0)
  row.status='pending';await f.page.refresh()
  assert.equal(f.page.data.rows[0].status,'待缴费 / 待确认')
  row.finalAmount=0;await f.page.refresh()
  assert.equal(f.page.data.rows[0].settlement,'费用待核对')
})

module.exports={parse,render,flatten,fixture,has,text}

test('missing body and invalid optional scores never become invented record content', async () => {
  const f=fixture([record('blank','2026-10-06 09:10:01',{note:'',energy:10,relaxation:null}),record('scored',null,{note:'',energy:5,relaxation:1})]);
  await f.page.refresh();const rows=visible(f.page).filter(n=>has(n,'record-row'));
  assert.equal(rows.length,2);assert(!flatten([rows[0]]).some(n=>has(n,'record-body')||has(n,'record-values')));
  assert(text(rows[0]).includes('活动期间 · 仅自己可见'));assert(text(rows[0]).includes('09:10:01'));
  assert(text(rows[1]).includes('精力 5/5'));assert(text(rows[1]).includes('放松 1/5'));
  assert.equal(f.writes.length,0);
});
