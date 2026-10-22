// Reading presentation only. Eligibility and ownership remain in camp-flow/business-api.
function score(value) {
  return ['number', 'string'].includes(typeof value) && value !== '' && Number.isInteger(Number(value)) && Number(value) >= 1 && Number(value) <= 5 ? Number(value) : null
}
function isAfter(row) { return row.phase === 'after' && !row.nodeKey }
function present(rows) {
  return (rows || []).map(row => {
    const note = typeof row.note === 'string' ? row.note : ''
    const date = typeof row.updatedAt === 'string' && row.updatedAt.match(/^\d{4}-(\d{2})-(\d{2})(?:[ T]|$)/)
    const validDate = date && Number(date[1]) >= 1 && Number(date[1]) <= 12 && Number(date[2]) >= 1 && Number(date[2]) <= new Date(Date.UTC(Number(row.updatedAt.slice(0,4)), Number(date[1]), 0)).getUTCDate()
    const values = [score(row.energy) !== null ? '精力 ' + score(row.energy) + '/5' : '', score(row.relaxation) !== null ? '放松 ' + score(row.relaxation) + '/5' : ''].filter(Boolean)
    return { ...row, note, displayKey: row.id || row.phase + ':' + (row.nodeKey || ''), isAfter: isAfter(row), hasLongNote: note.length > 220,
      updatedText: validDate ? '更新 ' + date[1] + '.' + date[2] : '', scoreText: values.join(' · ') }
  })
}
// The review is read-only; writing happens in the single 轻友心声 editor.
function refresh(previous, scope, rows, eligible) {
  const same = previous.reviewScope === scope && eligible
  return { reviewScope: scope, experienceTimeline: present(rows), reviewExpanded: same ? previous.reviewExpanded : {} }
}
module.exports = { present, refresh, score, isAfter }
