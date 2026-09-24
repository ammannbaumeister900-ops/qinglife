'use strict'
// Only the existing loopback Mock uses this module. No production data or tokens are accepted.
const { randomUUID, createHash } = require('crypto')
module.exports = function journeyRoutes({ customers, sessions, registrations, attendance, send, readBody }) {
  const actors = new Map(), invitations = [], records = [], batches = new Map()
  let testNow = null
  const today = () => (testNow || new Date().toISOString()).slice(0, 10)
  const stamp = () => testNow || new Date().toISOString()
  const fail = (message, status = 400) => { const error = new Error(message); error.status = status; throw error }
  const actor = req => actors.get(req.headers.token) || fail('请先登录或重新登录', 401)
  const admin = req => req.headers.authorization === 'Bearer ql-demo-token-30-days' || fail('需要工作人员权限', 403)
  const days = session => {
    const result = []
    for (let value = new Date(session.startDate + 'T12:00:00Z'), i = 1; value.toISOString().slice(0, 10) <= session.endDate; value.setUTCDate(value.getUTCDate() + 1), i++) {
      result.push({ id: session.id + '-day-' + i, dayNo: i, activityDate: value.toISOString().slice(0, 10), startTime: '10:00', endTime: '17:00', theme: '第' + i + '天', status: 'scheduled' })
    }
    return result
  }
  const sessionView = session => ({ ...session, venue: session.publicVenue || session.venue || '', confirmMode: session.registrationConfirmMode, registeredCount: registrations.filter(r => r.sessionId === session.id && ['pending', 'confirmed'].includes(r.registrationStatus)).length, days: days(session) })
  const openSession = id => {
    const s = sessions.find(item => item.id === id)
    if (!s || s.status !== 'open') fail('当前期次未开放报名')
    if (s.registrationOpenAt && stamp().replace('T', ' ').slice(0, 19) < s.registrationOpenAt) fail('报名尚未开始')
    if ((s.registrationCloseAt && stamp().replace('T', ' ').slice(0, 19) > s.registrationCloseAt) || today() > s.endDate) fail('报名已经截止')
    return s
  }
  const invite = (sessionId, ownerCustomerId) => {
    const s = openSession(sessionId)
    const item = { code: randomUUID().replace(/-/g, ''), sessionId, ownerCustomerId, createdAt: stamp() }
    invitations.push(item)
    return { code: item.code, sessionId, path: '/pages/camp-flow/index?view=detail&id=' + encodeURIComponent(s.id) + '&invite=' + item.code }
  }
  const registrationView = (r, me) => {
    const s = sessions.find(item => item.id === r.sessionId), person = customers.find(item => item.id === r.customerId)
    return { registrationId: r.id, customerId: r.customerId, participantName: person.nickname, isSelf: r.customerId === me.id, registrationStatus: r.registrationStatus, paymentStatus: r.paymentStatus, sessionId: s.id, sessionName: s.name, sessionNumber: s.sessionNumber, sessionStatus: s.status, startDate: s.startDate, endDate: s.endDate, batchId: r.batchId, contactName: r.contactName, contactPhone: r.contactPhone, quotedAmount: r.quotedAmount, finalAmount: r.finalAmount, settlementStatus: r.settlementStatus, relation: r.relation || '本人' }
  }
  return async function handle(req, res, pathname) {
    const url = new URL(req.url, 'http://127.0.0.1')
    const reply = data => { send(res, { code: 200, data }); return true }
    try {
      if (pathname.startsWith('/__test/')) {
        if (process.env.QL_E2E !== '1') fail('Not found', 404)
        admin(req)
        const body = await readBody(req)
        if (pathname === '/__test/clock') { if (!/^\d{4}-\d{2}-\d{2}T/.test(body.now)) fail('需要明确测试时间'); testNow = body.now; return reply({ now: stamp() }) }
        if (pathname === '/__test/actors') {
          const id = randomUUID(), token = 'test-' + randomUUID()
          const person = { id, customerNo: 'TEST-' + id.slice(0, 8), nickname: body.name, realName: body.name, phoneHint: '测试身份', firstReferrerCustomerId: null, minor: !!body.minor }
          customers.push(person); actors.set(token, person)
          return reply({ token, customerId: id })
        }
        fail('Not found', 404)
      }
      if (pathname.startsWith('/life/')) admin(req)
      const staffInvite = pathname.match(/^\/life\/session\/([^/]+)\/invitation$/)
      if (staffInvite && req.method === 'POST') { admin(req); return reply(invite(staffInvite[1], null)) }
      if (pathname === '/life/session' && ['POST', 'PUT'].includes(req.method)) {
        admin(req)
        const body = await readBody(req)
        if (!Number.isInteger(body.sessionNumber) || body.sessionNumber < 1) fail('期次必须为正整数')
        if (sessions.some(s => s.sessionNumber === body.sessionNumber && s.id !== body.id)) fail('期次已存在')
        if (!body.name || !/^\d{4}-\d{2}-\d{2}$/.test(body.startDate) || !/^\d{4}-\d{2}-\d{2}$/.test(body.endDate) || body.endDate < body.startDate) fail('活动日期或名称无效')
        if ((new Date(body.endDate) - new Date(body.startDate)) / 86400000 > 30) fail('活动日期跨度过大')
        if (!Number.isInteger(body.capacity) || body.capacity < 1 || Number(body.standardPrice) < 0) fail('名额或费用无效')
        if (!['draft','open','closed','in_progress','completed','cancelled'].includes(body.status)) fail('活动状态无效')
        if (req.method === 'POST') { const s = { ...body, id: randomUUID(), updatedAt: stamp() }; sessions.push(s); return reply(s) }
        const s = sessions.find(item => item.id === body.id) || fail('期次不存在', 404)
        Object.assign(s, body, { updatedAt: stamp() }); return reply(s)
      }
      if (!pathname.startsWith('/app/qinglife/')) return false
      if (pathname === '/app/qinglife/sessions' && req.method === 'GET') return reply(sessions.filter(s => ['open', 'in_progress'].includes(s.status)).map(sessionView))
      const detail = pathname.match(/^\/app\/qinglife\/sessions\/([^/]+)$/)
      if (detail && req.method === 'GET') { const s = sessions.find(s => s.id === detail[1] && s.status !== 'draft') || fail('活动不存在或未发布', 404); return reply(sessionView(s)) }
      const resolveInvite = pathname.match(/^\/app\/qinglife\/invitations\/([^/]+)$/)
      if (resolveInvite && req.method === 'GET') {
        const item = invitations.find(item => item.code === resolveInvite[1]) || fail('邀请无效', 404)
        openSession(item.sessionId)
        return reply({ sessionId: item.sessionId })
      }
      const me = actor(req)
      if (pathname === '/app/qinglife/me/overview') {
        const mine = registrations.filter(r => r.customerId === me.id || r.buyerCustomerId === me.id)
        return reply({ customerId: me.id, profile: { name: me.nickname, minor: me.minor }, registrations: mine.map(r => registrationView(r, me)), attendance: attendance.filter(a => a.customerId === me.id), experienceRecords: records.filter(r => r.customerId === me.id), dailyRecords: [], habit: null })
      }
      const appInvite = pathname.match(/^\/app\/qinglife\/sessions\/([^/]+)\/invitations$/)
      if (appInvite && req.method === 'POST') {
        if (me.minor || !registrations.some(r => r.customerId === me.id && r.registrationStatus === 'confirmed' && sessions.some(s => s.id === r.sessionId && s.status === 'completed'))) fail('完成本人体验后可生成邀请', 403)
        return reply(invite(appInvite[1], me.id))
      }
      if (pathname === '/app/qinglife/registrations' && req.method === 'POST') {
        const body = await readBody(req)
        if (!body.clientRequestId || body.clientRequestId.length > 64) fail('请求编号无效')
        const key = me.id + ':' + body.clientRequestId
        const fingerprint = createHash('sha256').update(JSON.stringify(body)).digest('hex')
        if (batches.has(key)) { const saved = batches.get(key); if (saved.fingerprint !== fingerprint) fail('请求编号已用于不同报名', 409); return reply(saved.result) }
        if (!body.serviceConsent || !body.contactName || !/^1\d{10}$/.test(body.contactPhone || '') || !Array.isArray(body.participants) || !body.participants.length || body.participants.length > 10) fail('请确认主要联系人、参与人和服务必要授权')
        const s = openSession(body.sessionId)
        const inv = body.invitationCode ? invitations.find(i => i.code === body.invitationCode && i.sessionId === s.id) : null
        if (body.invitationCode && !inv) fail('邀请与当前活动不匹配')
        if (sessionView(s).registeredCount + body.participants.length > s.capacity) fail('剩余名额不足')
        let selfCount = 0
        const participantIds = new Set(), participantPhones = new Set()
        const people = body.participants.map(p => {
          if (!p.name || p.phone && !/^1\d{10}$/.test(p.phone)) fail('请填写参与人姓名并检查选填手机号')
          if (p.phone && participantPhones.has(p.phone)) fail('同一手机号不能作为多名参与人的独立身份')
          if (p.phone) participantPhones.add(p.phone)
          if (p.self && ++selfCount > 1) fail('本人不能重复报名')
          let person = p.self ? me : p.customerId ? customers.find(c => c.id === p.customerId && registrations.some(r => r.customerId === c.id && r.buyerCustomerId === me.id)) : null
          if (p.customerId && !person) fail('无权使用该参与人', 403)
          if (!person) person = { id: randomUUID(), customerNo: 'TEST-PARTICIPANT', nickname: p.name, minor: !!p.minor, firstReferrerCustomerId: null }
          if (participantIds.has(person.id)) fail('参与人重复')
          participantIds.add(person.id)
          if (registrations.some(r => r.sessionId === s.id && r.customerId === person.id)) fail('该参与人已报名本期', 409)
          return { person, input: p }
        })
        const batchId = randomUUID(), result = { id: batchId, orderNo: 'TEST-' + batchId.slice(0,8), registrationStatus: s.registrationConfirmMode === 'auto' ? 'confirmed' : 'pending', paymentStatus: 'unpaid', quotedAmount: body.participants.length * 199, finalAmount: null, settlementStatus: 'pending', registrations: [] }
        for (const { person, input } of people) {
          if (!customers.some(c => c.id === person.id)) customers.push(person)
          const referrer = inv && inv.ownerCustomerId !== person.id ? inv.ownerCustomerId : null
          if (referrer && !person.firstReferrerCustomerId && !registrations.some(r => r.customerId === person.id)) person.firstReferrerCustomerId = referrer
          const r = { id: randomUUID(), sessionId: s.id, customerId: person.id, buyerCustomerId: me.id, batchId, orderNo: result.orderNo, registrationStatus: result.registrationStatus, paymentStatus: 'unpaid', contactName: body.contactName, contactPhone: body.contactPhone, quotedAmount: result.quotedAmount, finalAmount: null, settlementStatus: 'pending', sessionReferrerCustomerId: referrer, registrationSource: 'mini_program', relation: input.relation || '本人', revision: 0, registeredAt: stamp() }
          registrations.push(r)
          days(s).forEach(day => attendance.push({ registrationId: r.id, customerId: person.id, sessionDayId: day.id, sessionNumber: s.sessionNumber, sessionName: s.name, ...day, id: randomUUID(), attendanceStatus: 'not_arrived' }))
          result.registrations.push({ registrationId: r.id, customerId: person.id, name: input.name, status: r.registrationStatus })
        }
        batches.set(key, { fingerprint, result }); return reply(result)
      }
      if (pathname === '/app/qinglife/experience-records' && req.method === 'PUT') {
        const body = await readBody(req)
        const r = registrations.find(r => r.id === body.registrationId && r.customerId === me.id) || fail('只能记录本人感受', 403)
        const s = sessions.find(s => s.id === r.sessionId)
        if (r.registrationStatus !== 'confirmed' || s.status === 'cancelled') fail('报名尚未确认或已取消')
        if (!['before','during','after'].includes(body.phase)) fail('记录阶段无效')
        if (body.phase === 'before' && (today() !== s.startDate || stamp().slice(11,19) >= days(s)[0].startTime + ':00')) fail('请在活动当天正式开始前记录')
        if (body.phase === 'during' && !days(s).some(day => day.id === body.sessionDayId && day.activityDate === today())) fail('只能保存当天活动感受')
        if (body.phase === 'after' && s.status !== 'completed' && (today() < s.endDate || today() === s.endDate && stamp().slice(11,19) < days(s).slice(-1)[0].endTime + ':00')) fail('活动尚未结束')
        if ([body.energy, body.relaxation].some(value => value != null && (!Number.isInteger(value) || value < 1 || value > 5)) || String(body.note || '').length > 1000) fail('感受刻度或文字长度无效')
        const nodeKey = body.phase === 'during' ? body.sessionDayId : ''
        let record = records.find(item => item.registrationId === r.id && item.phase === body.phase && item.nodeKey === nodeKey)
        if (!record) { record = { id: randomUUID(), registrationId: r.id, sessionId: s.id, customerId: me.id, phase: body.phase, nodeKey }; records.push(record) }
        Object.assign(record, { energy: body.energy ?? null, relaxation: body.relaxation ?? null, note: body.note || '', visibility: 'private', updatedAt: stamp() })
        return reply(record)
      }
      fail('未实现的模拟业务接口', 404)
    } catch (error) { send(res, { code: error.status || 400, msg: error.message }, error.status || 400); return true }
  }
}
