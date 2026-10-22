const discovery = require('./discovery')

const FILTERS = [
  { key: 'all', label: '全部' },
  { key: 'open', label: '可报名' },
  { key: 'planned', label: '未开放' },
  { key: 'ended', label: '已结束' },
  { key: 'other', label: '其他状态' }
]

// Consume discovery's result; do not decide admission or change its order.
function group(activity) {
  if (activity.canRegister) return 'open'
  if (activity.registrationWindowState === 'not_open' || activity.status === '尚未开放') return 'planned'
  if (activity.status === '已结束') return 'ended'
  return 'other'
}

function hasPrice(value) {
  return value != null && String(value).trim() !== '' && Number.isFinite(Number(value))
}

function presentRows(activities) {
  return activities.map((activity, index) => {
    const period = activity.sessionNumber == null ? '' : String(activity.sessionNumber).trim()
    const city = String(activity.city || '').trim(), place = String(activity.place || '').trim()
    return { ...activity, list: {
      period, hasPeriod: !!period,
      periodSize: period.length > 20 ? 'very-long' : period.length > 12 ? 'long' : period.length > 6 ? 'medium' : '',
      featured: index === 0 && !!activity.canRegister,
      date: discovery.dateRange(activity),
      place: place ? (!city || place.startsWith(city) ? place : city + ' · ' + place.replace(/^[\s·]+/, '')) : city || '地点待公布',
      theme: activity.theme && activity.theme !== activity.name ? activity.theme : '',
      hasFee: hasPrice(activity.fee), hasReturningFee: hasPrice(activity.returningFee),
      tone: activity.status === '候补中' || activity.status === '尚未开放' ? 'warning' : activity.canRegister ? 'open' : activity.status === '已取消' ? 'cancelled' : ''
    } }
  })
}

function filters(activities, active = 'all') {
  return FILTERS.map(filter => ({ ...filter, count: filter.key === 'all' ? activities.length : activities.filter(activity => group(activity) === filter.key).length }))
    .filter(filter => filter.key === 'all' || filter.count || filter.key === active)
}

function select(activities, filter) {
  return filter === 'all' ? activities : activities.filter(activity => group(activity) === filter)
}

function filterLabel(filter) {
  const item = FILTERS.find(item => item.key === filter)
  return item ? item.label : '全部'
}

// Preview is always a prefix of the public note, never a rewritten summary.
function voicePreview(voices, index = 0) {
  if (!voices.length) return null
  const voice = voices[index] || voices[0]
  const text = String(voice.note || voice.excerpt || '')
  const lines = text.split('\n').slice(0, 3).join('\n')
  const preview = Array.from(lines).slice(0, 64).join('')
  return { ...voice, text, preview, isLong: preview !== text, index: voices[index] ? index : 0, count: voices.length }
}

module.exports = { FILTERS, group, presentRows, filters, select, filterLabel, voicePreview }
