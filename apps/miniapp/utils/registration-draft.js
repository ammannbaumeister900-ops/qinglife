// Form state and display only; participant identity and prices come from the server.
const relations = ['本人', '父母', '夫妻', '子女', '朋友', '同事', '同学', '兄弟姐妹', '其他亲属']
function syncContacts(draft, overrides = {}) {
  const self = (draft.participants || []).find(person => person.id === 'person-self' && person.selected)
  if (!self) return draft
  return { ...draft, contactName: overrides.name ? draft.contactName : self.name || '',
    contactPhone: overrides.phone ? draft.contactPhone : self.phone || '' }
}
function participants(draft = {}, state = {}) {
  return (draft.participants || []).filter(p => p.selected).map(p => ({
    self: p.id === 'person-self', customerId: p.id === 'person-self' ? null : p.customerId || null,
    name: p.name, relation: p.relation, minor: !!p.minor,
    phone: p.phone || (p.id === 'person-self' ? state.phone : '') || ''
  }))
}
function key(sessionId, draft, state) { return JSON.stringify([sessionId, participants(draft, state)]) }
function money(value) {
  if (value == null || String(value).trim() === '' || !/^\d+(\.\d{1,2})?$/.test(String(value))) return null
  const cents = Math.round(Number(value) * 100)
  return Number.isSafeInteger(cents) ? cents : null
}
function quote(result, selected) {
  const rows = result && result.participants
  if (!Array.isArray(rows) || rows.length !== selected.length || !rows.length) throw Error('价格暂时无法确认')
  let sum = 0
  const details = selected.map((p, index) => {
    const row = rows[index], cents = money(row.unitPrice)
    if (row.index !== index || !['new', 'returning'].includes(row.friendType) || cents == null) throw Error('价格暂时无法确认')
    sum += cents
    return { id: p.id, name: p.name, friendLabel: row.friendType === 'returning' ? '老轻友' : '新轻友', price: (cents / 100).toFixed(2) }
  })
  if (!Number.isSafeInteger(sum) || money(result.quotedAmount) !== sum) throw Error('价格暂时无法确认')
  return { ready: true, rows: details, total: (sum / 100).toFixed(2), error: '' }
}
module.exports = { relations, syncContacts, participants, key, quote }
