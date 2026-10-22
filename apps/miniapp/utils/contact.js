// Public service contact; operational configuration may override the published default.
const DEFAULT_CONTACT = { name: '桃子', wechat: 'qinglife2014', configured: true }
function resolveContact(value) {
  const supplied = value || {}
  const name = typeof supplied.name === 'string' && supplied.name.trim() ? supplied.name.trim() : DEFAULT_CONTACT.name
  const wechat = typeof supplied.wechat === 'string' && supplied.wechat.trim() ? supplied.wechat.trim() : DEFAULT_CONTACT.wechat
  return { name, wechat, configured: true }
}
module.exports = { resolveContact }
