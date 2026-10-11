const { test } = require('node:test')
const assert = require('node:assert/strict')
const presentation = require('../utils/camp-list-presentation')

const activity = (id, extra = {}) => ({ id, name: '轻体营', sessionNumber: 10001, status: '开放报名', canRegister: true,
  startDate: '2099-01-01', endDate: '2099-01-17', city: '上海市', place: '上海市嘉定区活动场地', fee: 300, returningFee: 0, ...extra })

test('presentation preserves discovery order, IDs, admission and original fields without mutation', () => {
  const source = [activity('later', { canRegister: false, status: '报名已截止' }), activity('available')]
  const before = JSON.stringify(source), rows = presentation.presentRows(source)
  assert.deepEqual(rows.map(a => a.id), ['later', 'available'])
  assert.equal(rows[0].status, '报名已截止')
  assert.equal(rows[0].list.featured, false)
  assert.equal(rows[1].list.featured, false, 'do not reorder or promote a later row')
  assert.equal(JSON.stringify(source), before)
  for (let i = 0; i < rows.length; i++) for (const key of Object.keys(source[i])) assert.deepEqual(rows[i][key], source[i][key])
})

test('only the first available source row is featured, and unavailable lists remain compact', () => {
  assert.deepEqual(presentation.presentRows([activity('a'), activity('b')]).map(a => a.list.featured), [true, false])
  assert(presentation.presentRows([activity('closed', { canRegister: false })]).every(a => !a.list.featured))
})

test('template identity preserves short, very long and zero periods; missing is explicit', () => {
  for (const period of [0, 1, 12, 10001, '987654321', '123456789012345678901234567890']) {
    const row = presentation.presentRows([activity('id', { sessionNumber: period, cover: '' })])[0]
    assert.equal(row.list.period, String(period));assert.equal(row.list.hasPeriod, true)
  }
  assert.equal(presentation.presentRows([activity('id', { sessionNumber: null })])[0].list.hasPeriod, false)
})

test('public prices retain zero and distinguish unavailable values from free participation', () => {
  const free = presentation.presentRows([activity('free', { fee: 0, returningFee: 0 })])[0]
  assert(free.list.hasFee && free.list.hasReturningFee)
  for (const value of [null, undefined, '']) assert.equal(presentation.presentRows([activity('missing', { fee: value })])[0].list.hasFee, false)
  assert.equal(presentation.presentRows([activity('normal', { fee: '3800.50' })])[0].fee, '3800.50')
})

test('dates and complete locations are readable without repeating the same city prefix', () => {
  const a = presentation.presentRows([activity('id', { theme: '轻体营' })])[0]
  assert.equal(a.list.date, '2099年1月1日—17日');assert.equal(a.list.place, a.place);assert.equal(a.list.theme, '')
  assert.equal(presentation.presentRows([activity('id', { place: ' · 活动场地' })])[0].list.place, '上海市 · 活动场地')
  const sparse = presentation.presentRows([activity('id', { startDate: '', endDate: '', city: '', place: '', sessionNumber: '', fee: null })])[0]
  assert.equal(sparse.list.date, '时间待公布');assert.equal(sparse.list.place, '地点待公布')
})

test('filters consume actual states without conflating closed, full and ended or losing order', () => {
  const rows = [activity('open'), activity('wait', { status: '候补中' }), activity('planned', { status: '尚未开放', canRegister: false }),
    activity('ended', { status: '已结束', canRegister: false }), ...['已满','报名已截止','已取消','已关闭','进行中'].map(status => activity(status, { status, canRegister: false }))]
  assert.deepEqual(presentation.select(rows, 'open').map(a => a.id), ['open', 'wait'])
  assert.equal(presentation.select(rows, 'ended')[0].status, '已结束')
  assert.deepEqual(presentation.select(rows, 'other').map(a => a.status), ['已满','报名已截止','已取消','已关闭','进行中'])
  assert.equal(presentation.filters(rows)[0].count, rows.length)
  const emptyFilter = presentation.filters([activity('a')], 'planned').find(f => f.key === 'planned')
  assert.equal(emptyFilter.count, 0);assert.equal(presentation.select([activity('a')], 'planned').length, 0)
  assert.equal(presentation.select(rows, 'all'), rows)
})

test('public voice preview keeps original text, author and API order without cutting emoji',()=>{assert.equal(presentation.voicePreview([]),null);const note='😀'.repeat(70),voice={id:'first',note,excerpt:'不可重写为摘要',author:'公开署名',phase:'after'},voices=[voice,{id:'second',note:'短原文'}],before=JSON.stringify(voices),p=presentation.voicePreview(voices);assert.equal(p.preview,'😀'.repeat(64));assert.equal(p.text,note);assert.equal(p.isLong,true);assert.equal(p.author,voice.author);assert.equal(presentation.voicePreview(voices,1).id,'second');assert.equal(presentation.voicePreview(voices,1).isLong,false);assert.equal(presentation.voicePreview([{note:'一\n二\n三\n四'}]).preview,'一\n二\n三');assert.equal(presentation.voicePreview([{excerpt:'只有接口摘要'}]).text,'只有接口摘要');assert.equal(JSON.stringify(voices),before)})
