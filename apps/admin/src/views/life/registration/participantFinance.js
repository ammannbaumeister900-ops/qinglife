// Display helpers only. Balances, availability, permissions and write eligibility come from the API.
export function yuan(value) {
  return value == null ? '—' : '¥' + Number(value).toFixed(2)
}

export function money(value, positive = true) {
  const text = String(value == null ? '' : value).trim()
  if (!/^\d{1,8}(\.\d{1,2})?$/.test(text) || (positive && Number(text) <= 0)) throw new Error('请输入有效金额，最多两位小数')
  return Number(text).toFixed(2)
}

export function uuid() {
  if (!window.crypto || !window.crypto.getRandomValues) throw new Error('当前浏览器无法生成安全操作编号，请更换浏览器')
  const bytes = window.crypto.getRandomValues(new Uint8Array(16))
  bytes[6] = (bytes[6] & 15) | 64
  bytes[8] = (bytes[8] & 63) | 128
  return Array.from(bytes, (v, i) => ([4, 6, 8, 10].includes(i) ? '-' : '') + v.toString(16).padStart(2, '0')).join('')
}

export function settlementLabel(person) {
  if (person.settlementStatus === 'confirmed' && person.settlementType === 'pass') return '卡次已结算'
  if (person.settlementStatus === 'confirmed' && person.settlementType === 'money') return '现金已结清'
  if ((person.passUsage || []).some(use => use.returnedAt)) return '已返卡 · 待结算'
  if (person.registrationStatus === 'cancelled') return '已取消 · 资金另行处理'
  return Number(person.allocatedAmount) > 0 ? '已分配现金 · 待确认' : '待结算'
}

export function recovery(error) {
  if (error.uncertain) return '请保留此窗口，按原请求重试以确认结果；不要重新登记同一笔到账或退款。关闭浏览器后请先核对历史记录。'
  if (error.code === 409) return '其他人员操作或数据变化可能导致冲突。请关闭并刷新，核对最新记录后重新操作；已有到账事实会保留。'
  if (error.code === 403) return '当前账号权限不足，请联系管理员核对角色授权；系统不会自动授予权限。'
  if (error.code === 401) return '登录已过期，请重新登录后先核对记录，再继续操作。'
  if (error.code === 423) return '逐人结算写入尚未开放。可继续查看，开放须走独立验收流程。'
  return '请关闭并刷新，核对输入和历史记录后再操作。'
}

export const labels = {
  requested: '申请中 · 已预留', processing: '处理中 · 已预留', completed: '已完成', cancelled: '已取消', failed: '失败 · 已释放预留',
  reconciliation_required: '差异待核对', recorded_unreconciled: '事实已登记 · 待核对', applied: '已核对',
  verified: '已核实 · 未使用', used: '已使用', revoked: '已撤销', superseded: '已失效',
  pending: '待确认', confirmed: '已确认', waitlisted: '候补', paid: '已到账',
  cash: '现金', wechat_scan: '微信线下', alipay_scan: '支付宝线下', transfer: '转账', other: '其他'
}
