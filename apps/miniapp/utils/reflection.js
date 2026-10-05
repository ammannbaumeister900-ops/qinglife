function count(text) { return Array.from(String(text || '')).length }
function phase(context) { return context.canAfter ? 'after' : context.canBefore ? 'before' : '' }
function draft(context, selected) { const record = (context.records || []).find(row => row.phase === selected); return { note: record ? record.note : '', revision: record ? record.revision : 0, status: record ? record.status : 'private', shared: !!(record && Number(record.shareConsent)), anonymous: record ? record.anonymous == null || Number(record.anonymous) === 1 : false } }
function voiceText(current, transcript) { return current ? current + '\n' + transcript : transcript }
function selectedPhase(context) {
  const active = phase(context)
  if (active) return active
  const records = context.records || []
  return records.some(row => row.phase === 'after') ? 'after' : records.some(row => row.phase === 'before') ? 'before' : 'after'
}
function pastShares(context, selected) { return (context.records || []).filter(row => row.phase !== selected && row.note).map(row => ({ phase: row.phase, note: Number(row.shareConsent) ? row.sharedNote || row.note : row.note, shared: !!Number(row.shareConsent), status: row.status })) }
module.exports = { count, phase, draft, voiceText, selectedPhase, pastShares }
