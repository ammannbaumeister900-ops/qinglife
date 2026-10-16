// A route selects one service. A failed read must never retry another source.
function routeText(value) {
  if (typeof value !== 'string') return ''
  try { return decodeURIComponent(value) } catch (error) { return value }
}

function resolveSource(source, runtime, businessEnabled, sample) {
  if (source && !['business', 'legacy', 'demo'].includes(source)) throw new Error('文章来源无效')
  if (source === 'business') return 'business'
  // The existing legacy adapter owns environment/address validation and token isolation.
  if (source === 'legacy') return 'legacy'
  if (source === 'demo') {
    if (runtime.environment !== 'demo' || !sample) throw new Error('演示内容不可在当前环境读取')
    return 'demo'
  }
  if (runtime.environment === 'demo' && sample) return 'demo'
  if (businessEnabled()) return 'business'
  if (runtime.legacyEnabled && runtime.environment !== 'staging') return 'legacy'
  throw new Error('文章服务尚未配置')
}

module.exports = { routeText, resolveSource }
