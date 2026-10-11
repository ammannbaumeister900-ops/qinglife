// Read projection only. Backend activeRegistration is authoritative; status fallback supports older API responses.
function active(row) {
  if (row.activeRegistration != null) return [true, 1, '1'].includes(row.activeRegistration)
  return !!row.registrationStatus && !['none', 'cancelled'].includes(row.registrationStatus)
}
function latest(rows = []) {
  const selected = new Map()
  for (const row of rows) {
    const key = JSON.stringify([row.sessionId, row.customerId || (row.isSelf ? 'self' : row.registrationId)])
    const previous = selected.get(key)
    if (!previous || Number(active(row)) > Number(active(previous)) ||
      active(row) === active(previous) && (String(row.registeredAt || '').localeCompare(String(previous.registeredAt || '')) || String(row.registrationId).localeCompare(String(previous.registrationId))) > 0) selected.set(key, row)
  }
  return [...selected.values()].sort((a,b) => Number(active(b)) - Number(active(a)) || Number([true,1,'1'].includes(b.isSelf)) - Number([true,1,'1'].includes(a.isSelf)))
}
function hasActive(reg) {
  if (!reg) return false
  const people = reg.participants || []
  return people.length ? people.some(p => p.activeRegistration != null ? [true,1,'1'].includes(p.activeRegistration) : !!(p.status || reg.status) && !['none','cancelled'].includes(p.status || reg.status))
    : !!reg.status && !['none','cancelled'].includes(reg.status)
}
function canReapply(reg) {
  return !!reg && ((reg.participants || []).some(p => p.status === 'cancelled' && !p.activeRegistration) || reg.status === 'cancelled' && !hasActive(reg))
}
function blocks(reg) { return hasActive(reg) && !canReapply(reg) }
function cancellation(state, activities, environment) {
  if (!state.backend || !state.loggedIn || !state.customerId) return null
  const dismissed = state.dismissedRegistrationCancellations || {}
  const events = Object.values(state.registrations || {}).flatMap(reg => (reg.participants || [])
    .filter(p => p.id === 'person-self' && p.status === 'cancelled' && !p.activeRegistration)
    .map(p => {
      const activity = activities.find(a => a.id === reg.activityId)
      return activity && p.registrationId ? { registrationId: p.registrationId, activityId: reg.activityId,
        label: activity.sessionNumber ? '第' + activity.sessionNumber + '期报名已取消' : '本次报名已取消',
        key: JSON.stringify([environment, state.customerId, p.registrationId]) } : null
    })).filter(Boolean)
  return events.find(event => !dismissed[event.key]) || null
}
function draft(reg, state) {
  const self = { id: 'person-self', name: state.profile.nickname || '', relation: '本人', minor: state.profile.minor, phone: state.phone || '', selected: true }
  let people = [self]
  if (canReapply(reg)) {
    people = (reg.participants || []).filter(p => !p.activeRegistration && p.status === 'cancelled').map(p => ({ ...p, selected: true, phone: p.phone || '', ...(p.id === 'person-self' ? self : {}) }))
    if (!people.some(p => p.id === 'person-self')) people.unshift({ ...self, selected: false, existingActive: (reg.participants || []).some(p => p.id === 'person-self' && p.activeRegistration) })
  }
  return { serviceConsent: true, contactName: state.profile.nickname || '', contactPhone: state.phone || '', participants: people }
}
module.exports = { active, latest, hasActive, canReapply, blocks, cancellation, draft }
