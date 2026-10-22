// Detail-only presentation. Admission, pricing and registration stay in domain/business-api.
const domain = require('./domain')
// The existing brand asset is authorized for these three guides, not for arbitrary leaders.
const guidePhoto = { names: ['公主', '大海', '军军'], url: '/assets/brand/home-guides.jpg' }
const text = value => value == null ? '' : String(value).trim()

function calendarDate(value) {
  const match = text(value).match(/^(\d{4})-(\d{2})-(\d{2})(?:$|T|\s)/)
  if (!match) return null
  const year = Number(match[1]), month = Number(match[2]), day = Number(match[3])
  const stamp = Date.UTC(year, month - 1, day), date = new Date(stamp)
  return date.getUTCFullYear() === year && date.getUTCMonth() === month - 1 && date.getUTCDate() === day
    ? { year, month, day, stamp } : null
}

function datePresentation(activity) {
  const start = calendarDate(activity.startDate), end = calendarDate(activity.endDate)
  if (!start || !end || end.stamp < start.stamp) return { year: '', date: text(activity.date), duration: null }
  const from = start.month + '月' + start.day
  const date = start.year === end.year && start.month === end.month
    ? from + '—' + end.day + '日'
    : from + '日—' + (start.year === end.year ? '' : end.year + '年') + end.month + '月' + end.day + '日'
  return { year: String(start.year), date, duration: (end.stamp - start.stamp) / 86400000 + 1 }
}

function schedulePresentation(days) {
  return (Array.isArray(days) ? days : []).map((day, index) => {
    const date = calendarDate(day.activityDate), theme = text(day.theme), time = text(day.time)
    return { key: text(day.id) || 'detail-day-' + index,
      date: date ? date.month + '月' + date.day + '日' : text(day.label),
      label: day.dayNo != null ? '第' + day.dayNo + '天' : '', theme, time, hasContent: !!(theme || time) }
  }).filter(day => day.date || day.label || day.hasContent)
}

function guideImage(leader) {
  const names = text(leader).split(/[、,，\/\s]+/).filter(Boolean).sort(), expected = guidePhoto.names.slice().sort()
  return names.length === expected.length && names.every((name, i) => name === expected[i]) ? guidePhoto.url : ''
}

function present(activity = {}, registration = {}, failedImages = {}) {
  const dates = datePresentation(activity), days = schedulePresentation(activity.days)
  const scheduleDays = days.filter(day => day.hasContent)
  const cover = text(activity.cover), guide = guideImage(activity.leader), rules = activity.rules || {}
  const terms = [...new Set([rules.body, ...(activity.talkFirst || [])].map(text).filter(Boolean))]
  const city = text(activity.city), place = text(activity.place), service = domain.campServiceStatus(registration)
  return { ready: !!(activity.id || activity.name), ...dates,
    place: place && city && !place.includes(city) ? city + ' · ' + place : place || city,
    cover, showCover: !!cover && !failedImages[cover],
    // Identifies versioned backend automatic posters, not arbitrary PNG photos.
    isPoster: /(?:^|\/)public\/session-covers\/v[123]-\d+\.png(?:[?#]|$)/.test(cover),
    guideImage: guide, showGuideImage: !!guide && !failedImages[guide],
    intro: text(activity.intro), participation: terms.join(' '), suitedFor: (activity.suitedFor || []).map(text).filter(Boolean),
    hasFee: activity.fee != null && text(activity.fee) !== '',
    hasReturningFee: activity.returningFee != null && text(activity.returningFee) !== '',
    hasCapacity: activity.capacity != null && text(activity.capacity) !== '',
    scheduleDays, hasScheduleContent: scheduleDays.length > 0,
    changeRule: text(rules.change), privacyRule: text(rules.privacy),
    registrationLabel: service.registration,
    registrationNote: service.registrationNote,
    registrationTone: ['confirmed', 'completed'].includes(registration.status) ? 'confirmed' : 'warning' }
}

module.exports = { present, datePresentation, schedulePresentation, guideImage }
