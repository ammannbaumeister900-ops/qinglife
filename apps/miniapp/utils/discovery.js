const domain = require('./domain')
function activities(list) {
  const today = domain.localDate()
  return list.filter(a => a.sourceStatus !== 'draft').map(a => {
    const beforeOpen = a.registrationOpenAt && today < String(a.registrationOpenAt).slice(0, 10)
    const afterClose = a.registrationCloseAt && today > String(a.registrationCloseAt).slice(0, 10)
    const ended = a.endDate && today > a.endDate
    return { ...a, canRegister: domain.activityState(a).canRegister && !beforeOpen && !afterClose && !ended,
      status: beforeOpen ? '尚未开放' : afterClose ? '报名已截止' : ended ? '已结束' : a.status }
  })
    .sort((a, b) => Number(b.canRegister) - Number(a.canRegister) || String(a.startDate || '').localeCompare(String(b.startDate || '')))
}
function returning(state) {
  if (typeof state.returningEligible === 'boolean') return !!state.loggedIn && state.returningEligible
  return !!state.loggedIn && Object.values(state.registrations || {}).some(r => r.status === 'completed' && (r.participants || []).some(p => p.id === 'person-self' && (!p.status || p.status === 'completed')))
}
module.exports = { activities, returning }
