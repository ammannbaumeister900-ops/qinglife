function dailyKey(state) {
  return state.stage === 'refeed' ? 'refeed-3' : `habit-${state.habit.currentDay}`
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
    savedAt: '刚刚',
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

module.exports = {
  dailyKey,
  registrationStatus,
  canPublish,
  privateRecord,
  publicPost
}

