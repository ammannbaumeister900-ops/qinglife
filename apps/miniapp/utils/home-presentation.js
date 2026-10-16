const discovery = require('./discovery')
const text = value => value == null ? '' : String(value).trim()
// Public endpoint membership survives derived status changes; private context rows stay excluded.
function camps(activities = [], selectedId = '', gatheringTimes = {}) {
  const rows = discovery.activities(activities.filter(a => a.publicListed === true || (a.publicListed == null && ['open', 'in_progress'].includes(a.sourceStatus))))
    .slice(0, 3).map(a => {
      const gathering = gatheringTimes[a.id]
      const confirmedTime = gathering && gathering.state === 'releaseReady' && gathering.evidenceRef && /^([01]\d|2[0-3]):[0-5]\d$/.test(gathering.time || '')
      return { ...a, title: a.name || '清体营', dateLabel: discovery.dateRange(a),
        periodLabel: a.sessionNumber == null ? '期次待公布' : '第' + a.sessionNumber + '期',
        timeLabel: confirmedTime ? '首日 ' + gathering.time + ' 集合' : '集合时间待公布',
        placeLabel: a.place || a.city || '地点待公布' }
    })
  const featured = rows.find(a => a.id === selectedId) || rows[0] || null
  return { camps: rows, selectedCampId: featured ? featured.id : '', featured }
}
function voiceGroups(records = []) {
  const approved = records.filter(v => v.state === 'releaseReady' && v.authorization === 'confirmed' && v.review === 'approved' &&
    text(v.id) && text(v.personKey) && text(v.author) && text(v.excerpt) && text(v.fullExcerpt) && v.source &&
    text(v.fullExcerpt).replace(/[。；;，,！!？?]+$/, '').includes(text(v.excerpt).replace(/[。；;，,！!？?]+$/, '')))
  const pending = approved.slice(), groups = [], seen = new Set()
  while (pending.length) {
    const group = [], people = new Set()
    for (let i = 0; i < pending.length && group.length < 4;) {
      const v = pending[i]
      if (seen.has(v.id)) { pending.splice(i, 1); continue }
      if (people.has(v.personKey)) { i++; continue }
      group.push(v); people.add(v.personKey); seen.add(v.id); pending.splice(i, 1)
    }
    if (!group.length) break
    groups.push(group)
  }
  return groups
}
function publicVoiceRecords(response, sessionId) {
  if (!response || response.contractVersion !== 1 || response.sessionId !== sessionId || !Array.isArray(response.list) || response.list.length > 16 || typeof response.hasMore !== 'boolean' || response.hasMore && !response.nextCursor) throw new Error('心声服务版本或分页数据无效')
  return response.list.map(v => {
    const note = typeof v.note === 'string' ? v.note : ''
    if (v.sessionId !== sessionId || !text(v.id) || !text(v.personKey) || !note.trim() || Array.from(note).length > 150) throw new Error('心声归属或正文无效')
    const excerpt = text(v.excerpt)
    const complete = /[。！？!?；;]$/.test(excerpt) && note.includes(excerpt)
    return { id: v.id, sessionId, personKey: v.personKey, author: text(v.author) || '本期轻友', excerpt: complete ? excerpt : note, fullExcerpt: note, state: 'releaseReady', authorization: 'confirmed', review: 'approved', source: { kind: 'camp-reflection', sessionId, voiceId: v.id } }
  })
}
module.exports = { camps, voiceGroups, publicVoiceRecords }
