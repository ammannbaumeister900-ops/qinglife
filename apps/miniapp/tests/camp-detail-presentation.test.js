const { test } = require('node:test')
const assert = require('node:assert/strict')
const { present, datePresentation } = require('../utils/camp-detail-presentation')

test('duration uses inclusive civil dates independently of schedule records', () => {
  for (const [startDate, endDate, count] of [
    ['2026-10-01', '2026-10-01', 1], ['2026-10-01', '2026-10-03', 3],
    ['2026-10-01', '2026-10-17', 17], ['2026-10-01', '2027-03-29', 180],
    ['2026-12-31', '2027-01-02', 3], ['2024-02-28', '2024-03-01', 3]
  ]) assert.equal(present({ startDate, endDate, days: [] }).duration, count)
  assert.equal(datePresentation({ startDate: '2026-12-31', endDate: '2027-01-02' }).date, '12月31日—2027年1月2日')
  for (const [startDate,endDate] of [['2026-02-30','2026-03-01'],['2026-10-03','2026-10-01'],['10月1日','10月3日']])
    assert.equal(datePresentation({ startDate,endDate,date:'接口日期原文' }).duration,null)
})

test('date-only schedules are not previewed; all records remain available at bounded height', () => {
  const days=Array.from({length:180},(_,i)=>({id:'d'+i,dayNo:i+1,activityDate:new Date(Date.UTC(2026,9,1+i)).toISOString().slice(0,10)}))
  const dates=present({startDate:'2026-10-01',endDate:'2027-03-29',days})
  assert.equal(dates.previewDays.length,0);assert.equal(dates.fullDays.length,180);assert.equal(dates.scheduleHeight,312)
  assert.equal(dates.fullDays.at(-1).label,'第180天');assert.equal(dates.scheduleLabel,'查看已列日期（180条）')
  days[8].theme='真实第9天安排';days[179].time='10:00—17:00'
  const populated=present({days})
  assert.deepEqual(populated.previewDays.map(d=>d.key),['d8','d179'])
  assert.equal(populated.fullDays.length,180);assert.equal(populated.fullDays[8].theme,'真实第9天安排')
  assert.equal(populated.fullDays.at(-1).time,'10:00—17:00')
  const empty=present({days:[{}]});assert.equal(empty.fullDays.length,0);assert.equal(empty.canExpand,false)
})

test('source content and zero prices are preserved without mock opening or source mutations', () => {
  const activity={id:'real',name:'接口活动',intro:'接口简介原文',fee:0,returningFee:0,city:'上海市',place:'上海市嘉定区实际地点',leader:'另一位导游',rules:{body:'真实身体边界',privacy:'真实隐私规则',change:'真实改期规则'},talkFirst:['真实沟通条件','真实身体边界'],days:[]}
  const snapshot=JSON.stringify(activity),view=present(activity)
  assert.equal(view.intro,activity.intro);assert.equal(view.participation,'真实身体边界 真实沟通条件')
  assert.equal(view.place,activity.place);assert.equal(view.hasFee,true);assert.equal(view.hasReturningFee,true)
  assert.equal(view.guideImage,'');assert.equal(view.changeRule,activity.rules.change);assert.equal(view.privacyRule,activity.rules.privacy)
  assert.equal(JSON.stringify(activity),snapshot)
  const missing=present({});assert.equal(missing.intro,'');assert.equal(missing.showCover,false);assert.equal(missing.hasFee,false)
})

test('only the backend automatic poster is classified; images need exact guide membership', () => {
  for (const version of [1,2,3]) assert.equal(present({cover:'https://host/profile/public/session-covers/v'+version+'-101.png'}).isPoster,true)
  assert.equal(present({cover:'https://host/uploads/v3-101.png'}).isPoster,false)
  assert.equal(present({cover:'https://host/uploads/real-scene.png'}).isPoster,false)
  assert.equal(present({leader:'军军，公主，大海'}).showGuideImage,true)
  for(const leader of ['公主、大海','公主、大海、另一位','公主、大海、军军、另一位','公主、公主、大海']) assert.equal(present({leader}).showGuideImage,false)
  const a={cover:'https://host/uploads/real-scene.jpg',leader:'公主、大海、军军'},failed=present(a,{}, {[a.cover]:true,'/assets/brand/home-guides.jpg':true})
  assert.equal(failed.showCover,false);assert.equal(failed.showGuideImage,false);assert.equal(failed.intro,'')
})

test('registered labels preserve pending, waitlist, cancellation and unknown outcomes', () => {
  for(const [status,label] of [['pending','待确认'],['waitlist','候补中'],['confirmed','已确认'],['completed','已结束'],['cancelled','已取消'],['rescheduled','安排有调整'],['unknown','待核对']])
    assert.equal(present({}, {status}).registrationLabel,label)
  assert.ok(present({}, {status:'pending'}).registrationNote.includes('尚未占用名额'))
})
