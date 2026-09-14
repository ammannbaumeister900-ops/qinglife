function dailyKey(state) {
  return state.stage === 'refeed' ? 'refeed-3' : `habit-${state.habit.currentDay}`
}

function cleanDisplayTitle(value) {
  return String(value || '')
    .replace(/[\u{1F000}-\u{1FAFF}\u{2600}-\u{27BF}]/gu, '')
    .replace(/[❥♡♥★☆♪♫]/g, '')
    .replace(/\(\^[_-]?.*?\)/g, '')
    .replace(/^[【\[]+|[】\]]+$/g, '')
    .replace(/\s{2,}/g, ' ')
    .trim()
}

function registrationStatus(activity) {
  return activity.confirmMode === 'manual' ? 'pending' : 'confirmed'
}

function canPublish(state) {
  if (!state.loggedIn || !state.phoneLinked) return { allowed: false, reason: 'phone_required' }
  if (state.profile && state.profile.minor) return { allowed: false, reason: 'minor_blocked' }
  return { allowed: true, reason: '' }
}

function privateRecord(choice, note) {
  return {
    choice,
    note: String(note || '').trim(),
    savedAt: new Date().toISOString(),
    private: true
  }
}

function publicPost(state, content, sourcePrivateKey, now) {
  const name = state.profile.nickname || state.profile.name || '轻友'
  return {
    id: `post-${now}`,
    author: name,
    avatar: name.slice(0, 1),
    time: '刚刚',
    tag: '轻行动',
    content: String(content || '').trim(),
    likes: 0,
    liked: false,
    comments: [],
    sourcePrivateKey: sourcePrivateKey || ''
  }
}

// 日期只接受明确的年月日，不从期号或自然语言推测日期。
function localDate(now = new Date()) {
  return [now.getFullYear(), String(now.getMonth() + 1).padStart(2, '0'), String(now.getDate()).padStart(2, '0')].join('-')
}
function activityState(activity) {
  if (!activity) return { label: '暂无活动', tone: 'disabled', canRegister: false }
  const closed = ['已取消', '已结束', '已满'].includes(activity.status)
  return {
    label: activity.status === '已取消' || activity.status === '已结束' ? activity.status : activity.status === '候补中' ? '候补中' : activity.seatsLeft === 0 ? '已满' : activity.statusTone === 'planned' ? '尚未开放' : activity.seatsLeft > 0 ? '余 ' + activity.seatsLeft + ' 席' : activity.status,
    tone: closed ? 'disabled' : activity.status === '候补中' || activity.statusTone === 'planned' ? 'mist' : activity.status === '即将满员' ? 'warning' : '',
    canRegister: !closed && activity.statusTone !== 'planned' && (activity.seatsLeft > 0 || activity.status === '候补中')
  }
}
function homeTask(state, activities, now = new Date()) {
  const registrations = { ...(state.registrations || {}) }
  if (state.registration) registrations[state.registration.activityId] = state.registration
  const priority = reg => {
    const activity = activities.find(item => item.id === reg.activityId)
    if (!activity) return 99
    if (['cancelled', 'rescheduled'].includes(reg.status)) return 0
    if (reg.status === 'confirmed') return activity.endDate && localDate(now) > activity.endDate ? 4 : 1
    if (['pending', 'waitlist', 'waitlisted'].includes(reg.status)) return 2
    return 5
  }
  const candidates = Object.values(registrations).filter(reg => reg.status !== 'none').sort((a, b) => priority(a) - priority(b) || String((activities.find(item => item.id === a.activityId) || {}).startDate || '').localeCompare(String((activities.find(item => item.id === b.activityId) || {}).startDate || '')))
  const reg = candidates[0] || state.registration || {}
  const activity = activities.find(item => item.id === reg.activityId)
  const today = localDate(now)
  const task = (title, copy, action, route, activityId) => ({ title, copy, action, route, activityId: activityId || '', kicker: '现在的一件事' })
  if (state.loggedIn && reg.status !== 'none' && activity) {
    if (['cancelled', 'rescheduled'].includes(reg.status) || activity.status === '已取消') return task('本次活动安排有变化', '请查看当前安排，并联系工作人员核对。', '查看活动状态', 'journey', activity.id)
    if (reg.status === 'pending' || ['waitlist','waitlisted'].includes(reg.status)) return task(['waitlist','waitlisted'].includes(reg.status) ? '本次报名正在候补' : '报名已收到，等待确认', '名额尚未确认，可查看申请和活动信息。', '查看报名', 'journey', activity.id)
    if (reg.status === 'confirmed') return task(activity.startDate && today >= activity.startDate && today <= activity.endDate ? '查看今天的行程' : activity.endDate && today > activity.endDate ? '回看本次参与经历' : '为这次活动做一点准备', activity.date + ' · ' + activity.city + activity.place, activity.endDate && today > activity.endDate ? '查看本次经历' : '查看准备与行程', activity.endDate && today > activity.endDate ? 'experience' : 'journey', activity.id)
  }
  if (state.loggedIn && ['refeed', 'habit'].includes(state.stage)) {
    if (state.stage === 'habit' && state.habit.paused) return task('今天可以安心休息', '计划已暂停，之前的记录都还在。', '查看计划设置', 'habit')
    const record = state.dailyRecords[dailyKey(state)]
    return task(record ? '今天的记录已经保存' : '给今天留下一点感受', record ? '完成、轻量和休息，都是自己的节奏。' : '只记录实际情况，可以休息，也可以不写感受。', record ? '查看今天的记录' : '记录今天', 'daily')
  }
  if (state.loggedIn && reg.status === 'completed' && activity) return task('回看这一次的体验', '可以留下一点感受，也可以稍后再来。', '查看本次经历', 'experience', activity.id)
  const next = activities.find(item => activityState(item).canRegister)
  return next ? task(next.name, next.date + ' · ' + next.city + ' · ¥' + next.fee + '/人', '了解这一期', 'detail', next.id) : task('暂时没有要完成的事', '给自己一点放慢的时间。', '随便读读', 'reading')
}

module.exports = { localDate, activityState, homeTask,
  dailyKey,
  cleanDisplayTitle,
  registrationStatus,
  canPublish,
  privateRecord,
  publicPost
}
