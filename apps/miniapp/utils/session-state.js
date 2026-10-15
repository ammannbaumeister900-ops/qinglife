// Read contract only: admission rules and the Shanghai clock remain on the server.
const phases = { upcoming: '未开始', in_progress: '进行中', completed: '已结束', cancelled: '已取消', draft: '未发布', unknown: '状态待核对' }
const windows = ['open', 'not_open', 'closed', 'unknown']
function fromApi(raw) {
  if (raw.stateContractVersion == null) return {}
  const valid = raw.stateContractVersion === 1 && ['open', 'in_progress', 'closed', 'cancelled', 'draft', 'completed'].includes(raw.configuredStatus) &&
    (!raw.registrationAllowedByPolicy || raw.configuredStatus === 'open' && ['upcoming', 'in_progress'].includes(raw.activityState)) &&
    Object.prototype.hasOwnProperty.call(phases, raw.activityState) && windows.includes(raw.registrationWindowState) &&
    typeof raw.registrationAllowedByPolicy === 'boolean' && (!raw.registrationAllowedByPolicy || raw.registrationWindowState === 'open') &&
    typeof raw.businessTime === 'string' && /\+08:00$/.test(raw.businessTime) && Number.isFinite(Date.parse(raw.businessTime))
  return { stateContractReady: valid, status: phases[raw.activityState] || phases.unknown,
    registrationAllowedByPolicy: valid && raw.registrationAllowedByPolicy }
}
function availability(activity) {
  if (activity.stateContractVersion == null) return null
  if (!activity.stateContractReady) return { label: '报名信息待核对', tone: 'disabled', canRegister: false }
  if (['completed', 'cancelled', 'draft', 'unknown'].includes(activity.activityState)) {
    return { label: phases[activity.activityState], tone: 'disabled', canRegister: false }
  }
  if (!activity.registrationAllowedByPolicy) {
    const label = activity.configuredStatus === 'closed' ? '报名已关闭' : activity.registrationWindowState === 'closed' ? '报名已截止' :
      activity.registrationWindowState === 'not_open' && activity.configuredStatus === 'open' ? '尚未开放' : activity.registrationWindowState === 'unknown' ? '报名信息待核对' : '暂不接受报名'
    return { label, tone: 'disabled', canRegister: false }
  }
  if (activity.seatsLeft === 0) return { label: '已满', tone: 'disabled', canRegister: false }
  if (!Number.isFinite(activity.seatsLeft) || activity.seatsLeft < 0) return { label: '名额待核对', tone: 'disabled', canRegister: false }
  return { label: '余 ' + activity.seatsLeft + ' 席', tone: '', canRegister: true }
}
module.exports = { fromApi, availability }
