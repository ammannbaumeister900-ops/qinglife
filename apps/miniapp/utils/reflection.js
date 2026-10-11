function count(text) { return Array.from(String(text || '')).length }
function phase(context) { return context.canAfter === true ? 'after' : context.canBefore === true ? 'before' : '' }
function legacyDraft(context, selected) { const record = (context.records || []).find(row => row.phase === selected); return { note: record ? record.note : '', revision: record ? record.revision : 0, status: record ? record.status : 'private', shared: !!(record && Number(record.shareConsent)), anonymous: record ? record.anonymous == null || Number(record.anonymous) === 1 : false } }
function draft(context, selected, chosen) {
 const result = legacyDraft(context, selected)
 if (selected !== 'after' || !context.unifiedAfter) return result
 const original = context.experienceRecord, legacy = (context.records || []).find(row => row.phase === 'after')
 const sources = []
 if (original) sources.push({ source: 'experience', label: '私人体验记录', note: original.note || '' })
 if (legacy && legacy.note) sources.push({ source: 'reflection', label: '此前心声原稿', note: legacy.note })
 const conflict = sources.length === 2 && sources[0].note !== sources[1].note
 const source = chosen || (conflict ? '' : original ? 'experience' : sources.length ? 'reflection' : 'new')
 const selectedSource = sources.find(row => row.source === source)
 return { ...result, note: selectedSource ? selectedSource.note : '', source, sourceConflict: conflict && !source, sources, originalVersion: context.originalVersion,
  scoreText: require('./experience-presentation').present(original ? [original] : [])[0]?.scoreText || '' }
}
function voiceText(current, transcript) { return current ? current + '\n' + transcript : transcript }
function selectedPhase(context) {
  const active = phase(context)
  if (active) return active
  const records = context.records || []
  return records.some(row => row.phase === 'after') ? 'after' : records.some(row => row.phase === 'before') ? 'before' : 'after'
}
function pastShares(context, selected) { return (context.records || []).filter(row => row.phase !== selected && row.note).map(row => ({ phase: row.phase, note: Number(row.shareConsent) ? row.sharedNote || row.note : row.note, shared: !!Number(row.shareConsent), status: row.status })) }
// Entry presentation consumes the existing server window; dates only explain a denial.
function activeSelf(registration) {
 const self = (registration && registration.participants || []).find(person => person.id === 'person-self')
 return !!self && require('./registration-history').hasActive({ ...registration, participants: [self] })
}
function confirmedSelf(registration) {
 const self = (registration && registration.participants || []).find(person => person.id === 'person-self')
 return !!self && ['confirmed', 'completed'].includes(self.status || registration.status)
}
function entry(context, registration, activity = {}) {
 if (registration && !confirmedSelf(registration)) {
  const self = (registration.participants || []).find(person => person.id === 'person-self')
  return { allowed: false, reason: !self ? '请由本期实际参与者填写' : (self.status || registration.status) === 'cancelled' ? '报名已取消，无法填写心声' : '本人报名确认后可填写心声', retry: false }
 }
 if (context && phase(context)) return { allowed: true, reason: '', retry: false }
 const status = activity.sourceStatus || activity.status
 const reason = ['cancelled', '已取消'].includes(status) ? '本期已取消，无法填写心声'
  : ['draft', '未发布'].includes(status) ? '本期尚未开放心声'
  : activity.startDate && require('./domain').localDate() < activity.startDate ? '开营当天起可填写心声'
  : '当前阶段暂不可填写心声'
 return { allowed: false, reason, retry: false }
}
async function readEntry(readContext, registration, activity) {
 if (!confirmedSelf(registration)) return entry(null, registration, activity)
 try { return entry(await readContext(), registration, activity) }
 catch (error) { return { allowed: false, reason: error.code === 403 ? error.message : '心声资格读取失败，请重试', retry: error.code !== 403 } }
}
module.exports = { activeSelf, count, phase, draft, voiceText, selectedPhase, pastShares, entry, readEntry }
