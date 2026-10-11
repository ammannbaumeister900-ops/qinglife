const domain = require('./domain')
const discovery = require('./discovery')

// A session aggregate may contain several orders. Do not call its first order
// the payment state of the whole session unless the existing data agrees.
function paymentSummary(registration, overview) {
  const source = (overview && overview.registrations || []).filter(row => row.sessionId === registration.activityId)
  const participants = registration.participants || []
  const complete = !source.length || source.length === participants.length
  const sameOrder = source.length <= 1 ? source.length === 1 || participants.length === 1
    : source.every(row => row.batchId && row.batchId === source[0].batchId)
  const signature = row => JSON.stringify([row.settlementStatus, row.settlementType, row.finalAmount == null ? null : String(row.finalAmount), row.orderPaymentStatus || row.paymentStatus, row.passUnits == null ? null : String(row.passUnits)])
  const agrees = !source.length || source.every(row => signature(row) === signature(source[0]))
  if (!complete || !sameOrder || !agrees) {
    const pending = source.some(row => domain.campServiceStatus({ ...row, paymentStatus: row.orderPaymentStatus || row.paymentStatus }).payment === '待付款')
    return { label: pending ? '存在待付款订单 · 费用分别核对' : '费用需分别核对', attention: true }
  }
  const payment = domain.campServiceStatus(registration).payment
  return { label: payment, attention: ['待付款', '金额待确认'].includes(payment) }
}

function present(registrations, primaryRegistration = {}, overview) {
  const rows = registrations.filter(reg => domain.hasRegistration(reg)).map(reg => {
    const activity = reg.activity || {}, service = domain.campServiceStatus(reg)
    const participants = (reg.participants || []).filter(person => person.selected !== false)
    const self = participants.find(person => person.id === 'person-self')
    const statuses = participants.map(person => person.status || '')
    const mixed = new Set(statuses).size > 1
    const incomplete = participants.length > 1 && statuses.some(status => !status)
    let label = ['pending', 'confirmed', 'cancelled'].includes(reg.status) ? '报名' + service.registration : reg.status === 'completed' ? '参与已结束' : service.registration
    if (participants.length > 1 && self) label = '本人' + label
    if (participants.length > 1 && !self && (mixed || incomplete)) label = '报名状态需分别核对'
    const stage = { in_progress: '活动进行中', completed: '活动已结束', cancelled: '活动已取消', closed: '活动已关闭' }[activity.sourceStatus]
      || { '进行中': '活动进行中', '已结束': '活动已结束', '已取消': '活动已取消', '已关闭': '活动已关闭' }[activity.status] || ''
    const otherCurrent = incomplete || participants.some(person => person.status && !['completed', 'cancelled'].includes(person.status))
    const history = reg.status !== 'rescheduled' && (stage === '活动已结束' || ['completed', 'cancelled'].includes(reg.status) && !otherCurrent)
    const attention = service.operationalRisk || (overview && overview.registrations || []).some(row => row.sessionId === reg.activityId && domain.campServiceStatus({ ...row, status: row.registrationStatus, paymentStatus: row.orderPaymentStatus || row.paymentStatus }).operationalRisk) || ['pending', 'waitlist', 'waitlisted', 'rescheduled'].includes(reg.status) || stage === '活动已取消' && reg.status !== 'cancelled'
    const payment = paymentSummary(reg, overview)
    const period = activity.sessionNumber == null ? '' : String(activity.sessionNumber).trim()
    const people = participants.length > 1 ? participants.length + '位参与者' + (self ? '（含本人）' : '（同行）') : participants.length === 1 && !self ? '为同行报名' : ''
    let action = { pending: '查看报名', waitlist: '查看候补', waitlisted: '查看候补', cancelled: '查看报名记录', rescheduled: '查看当前状态' }[reg.status] || '查看报名与服务'
    if (stage === '活动已取消' && reg.status !== 'cancelled') action = '查看当前状态'
    return { ...reg, arrangement: {
      period, name: activity.name || '活动信息待核对', label, tone: attention ? 'attention' : reg.status === 'confirmed' ? 'confirmed' : 'muted',
      date: discovery.dateRange(activity), city: String(activity.city || '').trim(), stage, people,
      peopleNote: incomplete ? '参与者状态需核对' : mixed ? '参与者状态不同' : '',
      note: attention ? service.registrationNote || (stage === '活动已取消' ? '请与工作人员核对当前安排' : '') : '',
      payment: payment.label, paymentAttention: payment.attention, action, history,
      // Keep the original completed-primary-registration condition and route.
      experience: primaryRegistration.status === 'completed' && reg.activityId === primaryRegistration.activityId
    } }
  })
  const current = rows.filter(row => !row.arrangement.history), history = rows.filter(row => row.arrangement.history)
  const grouped = rows.length > 1 && current.length > 0 && history.length > 0
  return { arrangementCount: rows.length, arrangementGroups: grouped
    ? [{ key: 'current', label: '当前安排', rows: current }, { key: 'history', label: '历史安排', rows: history }]
    : rows.length ? [{ key: 'all', label: '', rows }] : [] }
}

module.exports = { present }
