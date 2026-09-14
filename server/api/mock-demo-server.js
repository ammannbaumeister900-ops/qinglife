'use strict'

const http = require('http')
const { randomUUID } = require('crypto')

const customerFixtureRows = Array.from({ length: 100 }, (_, index) => ({
  sourceCandidateId: `synthetic-${String(index + 1).padStart(4, '0')}`,
  nickname: `演示轻友${String(index + 1).padStart(3, '0')}`,
  name: `演示轻友${String(index + 1).padStart(3, '0')}`,
  birthDate: null,
  phoneHint: null,
  mainlineParticipationCount: index % 4,
  firstMainlineSessionNumber: index % 4 ? 481 : null,
  latestMainlineSessionNumber: index % 4 ? 482 : null,
  starSource: 'synthetic_test_data',
  sourceRecordCount: 1
}))
const now = () => new Date().toISOString().replace('T', ' ').slice(0, 19)
const demoImage = `data:image/svg+xml;base64,${Buffer.from('<svg xmlns="http://www.w3.org/2000/svg" width="160" height="120"><rect width="160" height="120" fill="#e8f3ee"/><circle cx="80" cy="45" r="22" fill="#68a989"/><path d="M40 110c5-28 22-42 40-42s35 14 40 42" fill="#68a989"/></svg>').toString('base64')}`

const customers = customerFixtureRows.map((row, index) => ({
  id: `demo-customer-${String(index + 1).padStart(4, '0')}`,
  customerNo: `QY-MOCK-${String(index + 1).padStart(4, '0')}`,
  sourceCandidateId: row.sourceCandidateId,
  nickname: row.nickname,
  realName: row.name,
  birthDate: row.birthDate || null,
  gender: 'unknown',
  city: null,
  firstSource: '历史名单',
  dataSource: 'verified_clean_list_20260828',
  dataConfidence: 'verified',
  wechatNickname: null,
  wechatAvatar: null,
  accountStatus: 'unbound',
  lastLoginTime: null,
  phoneHint: row.phoneHint || null,
  idCardHint: null,
  mainlineParticipationCount: row.mainlineParticipationCount,
  firstMainlineSessionNumber: row.firstMainlineSessionNumber,
  latestMainlineSessionNumber: row.latestMainlineSessionNumber,
  starSource: row.starSource,
  sourceRecordCount: row.sourceRecordCount,
  status: 'active',
  revision: 0,
  createdAt: '2025-08-01 10:00:00',
  updatedAt: '2026-08-28 10:00:00'
}))

const sessions = [
  { id: 'demo-session-481', sessionNumber: 481, name: '轻生活三日体验营', intro: '三天重新感受身体与生活节奏。', startDate: '2025-08-19', endDate: '2025-08-21', capacity: 36, standardPrice: 2980, publicVenue: '杭州体验中心', city: '杭州', status: 'completed', registrationConfirmMode: 'manual', leaderName: '公主、大海', updatedAt: '2026-08-28 10:00:00' },
  { id: 'demo-session-482', sessionNumber: 482, name: '轻生活三日体验营', intro: '三天重新感受身体与生活节奏。', startDate: '2026-09-10', endDate: '2026-09-12', capacity: 30, standardPrice: 2980, publicVenue: '杭州体验中心', city: '杭州', status: 'open', registrationConfirmMode: 'manual', registrationOpenAt: '2026-08-20 00:00:00', registrationCloseAt: '2026-09-09 18:00:00', leaderName: '公主、大海', cancelPolicy: '如需调整请联系运营人员。', updatedAt: '2026-08-28 10:00:00' },
  { id: 'demo-session-483', sessionNumber: 483, name: '轻生活三日体验营', intro: '秋日轻生活体验。', startDate: '2026-09-24', endDate: '2026-09-26', capacity: 30, standardPrice: 2980, publicVenue: '待定', city: '杭州', status: 'draft', registrationConfirmMode: 'auto', leaderName: '待定', updatedAt: '2026-08-28 10:00:00' }
]

const registrations = customers.slice(0, 28).map((customer, index) => ({
  id: `demo-registration-${String(index + 1).padStart(4, '0')}`,
  customerId: customer.id,
  sessionId: index < 20 ? 'demo-session-481' : 'demo-session-482',
  registrationStatus: index % 7 === 0 ? 'pending' : 'confirmed',
  paymentStatus: index % 3 === 0 ? 'unpaid' : 'paid',
  registrationSource: index < 20 ? 'legacy_excel' : 'web_admin',
  registeredAt: `2026-08-${String((index % 20) + 1).padStart(2, '0')} 10:00:00`,
  sessionReferrerCustomerId: null,
  remark: index % 4 === 0 ? '待运营回访确认' : '',
  revision: 0
}))

registrations.slice(20).forEach((item, offset) => {
  const group = Math.floor(offset / 2)
  item.batchId = `demo-batch-${group + 1}`
  item.orderNo = `QL2026090${group + 1}DEMO`
  item.buyerCustomerId = customers[20 + group * 2].id
  item.buyerNickname = customers[20 + group * 2].nickname
  item.participantCount = Math.min(2, registrations.length - 20 - group * 2)
  item.payableAmount = 2980 * item.participantCount
  item.batchPaymentStatus = group % 2 === 0 ? 'unpaid' : 'paid'
  item.paymentStatus = item.batchPaymentStatus
})

const attendance = registrations.filter(item => item.sessionId === 'demo-session-482').flatMap((registration, index) =>
  [1, 2, 3].map(dayNo => ({
    id: `demo-attendance-${index + 1}-${dayNo}`,
    registrationId: registration.id,
    customerId: registration.customerId,
    sessionNumber: 482,
    sessionName: '轻生活三日体验营',
    dayNo,
    activityDate: `2026-09-${9 + dayNo}`,
    theme: ['慢下来', '感受生机', '带回生活'][dayNo - 1],
    attendanceStatus: dayNo === 1 && index < 3 ? 'checked_in' : 'not_arrived',
    checkedInAt: dayNo === 1 && index < 3 ? '2026-09-10 09:55:00' : null,
    checkInSource: dayNo === 1 && index < 3 ? 'web_admin' : null
  }))
)

const reports = [
  { id: 'demo-report-1', publishId: 1, reporterNo: customers[1].customerNo, reporterNickname: customers[1].nickname, postContent: legacyResourcesPlaceholder(), reasonCode: 'other', reasonNote: '内容需要运营核实', status: 'pending', handleResult: null, createdAt: '2026-09-02 18:30:00' }
]

function legacyResourcesPlaceholder() { return '今天完成了晨间打卡，状态很好。' }

const legacyResources = {
  userInfo: [
    { id: 1, nickName: '轻友小林', head: demoImage, gender: 1, status: '0', insertTime: '2025-08-18 09:30:00', lastLoginTime: '2026-08-28 19:20:00' },
    { id: 2, nickName: '轻友安安', head: demoImage, gender: 0, status: '0', insertTime: '2025-08-20 10:15:00', lastLoginTime: '2026-08-27 08:45:00' }
  ],
  essay: [
    { id: 1, title: '把健康习惯放进日常', content: '<p>从一件容易坚持的小事开始。</p>', Introduction: '轻生活习惯养成指南', titleUrl: demoImage, orderNum: 1, status: '0', authorNickName: '轻生活', label: '轻盈生活', labels: [1], insertTime: '2026-08-20 10:00:00' }
  ],
  collect: [
    { id: 1, userId: 1, essay: 1, nickName: '轻友小林', essayTitle: '把健康习惯放进日常', Introduction: '轻生活习惯养成指南', titleUrl: demoImage, insertTime: '2026-08-25 20:10:00' }
  ],
  label: [
    { id: 1, name: '轻盈生活', status: 1, level: 1, remark: '文章默认标签' },
    { id: 2, name: '营养记录', status: 1, level: 2, remark: '' }
  ],
  userPublish: [
    { id: 1, userId: 1, content: '今天完成了晨间打卡，状态很好。', images: demoImage, imageList: [demoImage], nickName: '轻友小林', tag: '每日打卡', praiseNum: 6, commentNum: 2, insertTime: '2026-08-28 08:20:00' }
  ],
  userComment: [
    { id: 1, userId: 2, nickName: '轻友安安', publishId: 1, commentId: null, content: '一起坚持！', insertTime: '2026-08-28 09:05:00' }
  ],
  tag: [
    { id: 1, name: '每日打卡', defaulted: 1, sort: 1, insertTime: '2025-08-01 10:00:00' },
    { id: 2, name: '轻友分享', defaulted: 0, sort: 2, insertTime: '2025-08-01 10:00:00' }
  ],
  publishTemplate: [
    { id: 1, templateName: '每日轻记录', status: 1, isDefault: 1, insertTime: '2025-08-01 10:00:00' }
  ],
  publishTemplateProject: [
    { id: 1, templateId: 1, templateName: '每日轻记录', projectName: '今日感受', placeholder: '用一句话记录今天', maxNum: 100, required: 1, status: 1, insertTime: '2025-08-01 10:00:00' }
  ],
  contact: [
    { id: 1, wechat: 'qinglife_demo', phone: '138****0000' }
  ],
  userMessage: [
    { id: 1, userId: 1, msgType: 1, msgFrom: 0, fromUserId: 2, fromNickName: '轻友安安', fromHead: demoImage, readed: 0, insertTime: '2026-08-28 09:05:00', insertUser: 'system', updateUser: 'system' }
  ]
}

const routePage = (name, path, component, title, icon) => ({
  name, path, hidden: false, component, meta: { title, icon, noCache: false }
})
const routeGroup = (name, path, title, icon, children) => ({
  name, path, hidden: false, redirect: 'noRedirect', component: 'Layout', alwaysShow: true,
  meta: { title, icon, noCache: false }, children
})

const routerData = [
  routeGroup('CustomerCenter', '/customer-center', '用户中心', 'peoples', [
    routePage('QlCustomer', 'customer', 'life/customer/index', '轻友档案', 'user')
  ]),
  routeGroup('ActivityOperations', '/activity-operations', '活动运营', 'date', [
    routePage('QlSession', 'session', 'life/session/index', '期次管理', 'date'),
    routePage('QlRegistration', 'registration', 'life/registration/index', '报名与付款', 'form'),
    routePage('QlAttendance', 'attendance', 'life/attendance/index', '活动签到', 'checkbox')
  ]),
  routeGroup('ContentPublishing', '/content-publishing', '内容发布', 'documentation', [
    routePage('ProductionEssay', 'essay', 'life/essay/index', '文章管理', 'documentation'),
    routePage('ProductionCollect', 'collect', 'life/collect/index', '用户收藏', 'star'),
    routePage('ProductionLabel', 'label', 'life/label/index', '文章标签', 'tag')
  ]),
  routeGroup('CustomerMoments', '/customer-moments', '轻友动态', 'message', [
    routePage('ProductionUserPublish', 'publish', 'life/userPublish/index', '用户动态', 'message'),
    routePage('ProductionUserComment', 'comment', 'life/userComment/index', '动态评论', 'edit'),
    routePage('ProductionTag', 'tag', 'life/tag/index', '动态标签', 'tag'),
    routePage('ProductionTemplate', 'template', 'life/publishTemplate/index', '动态模板', 'form'),
    routePage('ProductionTemplateProject', 'template-project', 'life/publishTemplateProject/index', '模板项目', 'list'),
    routePage('QlReport', 'report', 'life/report/index', '内容举报', 'warning')
  ]),
  routeGroup('OtherSettings', '/other-settings', '其他配置', 'setting', [
    routePage('ProductionContact', 'contact', 'life/contact/index', '联系信息', 'phone'),
    routePage('ProductionMessage', 'message', 'life/userMessage/index', '用户消息', 'email')
  ])
]

function send(res, payload, status = 200) {
  res.writeHead(status, { 'Content-Type': 'application/json; charset=utf-8', 'Access-Control-Allow-Origin': '*' })
  res.end(JSON.stringify(payload))
}

function readBody(req) {
  return new Promise((resolve, reject) => {
    let raw = ''
    req.on('data', chunk => { raw += chunk })
    req.on('end', () => {
      try { resolve(raw ? JSON.parse(raw) : {}) } catch (error) { reject(error) }
    })
    req.on('error', reject)
  })
}

function page(items, query) {
  const pageNum = Math.max(Number(query.get('pageNum')) || 1, 1)
  const pageSize = Math.max(Number(query.get('pageSize')) || 10, 1)
  const start = (pageNum - 1) * pageSize
  return { code: 200, msg: '查询成功', rows: items.slice(start, start + pageSize), total: items.length }
}

function registrationView(item) {
  const customer = customers.find(value => value.id === item.customerId) || {}
  const session = sessions.find(value => value.id === item.sessionId) || {}
  return { ...item, customerNo: customer.customerNo, nickname: customer.nickname, realName: customer.realName, sessionNumber: session.sessionNumber, sessionName: session.name, standardPrice: session.standardPrice }
}

function filterCustomers(query) {
  const keyword = (query.get('nickname') || '').toLowerCase()
  return customers.filter(item => (!keyword || [item.nickname, item.realName, item.customerNo, item.wechatNickname].some(value => (value || '').toLowerCase().includes(keyword))) && (!query.get('status') || item.status === query.get('status')))
}

function filterSessions(query) {
  return sessions.filter(item => (!query.get('sessionNumber') || item.sessionNumber === Number(query.get('sessionNumber'))) && (!query.get('name') || item.name.includes(query.get('name'))) && (!query.get('status') || item.status === query.get('status')))
}

function filterRegistrations(query) {
  const keyword = query.get('nickname') || ''
  return registrations.map(registrationView).filter(item => (!keyword || [item.nickname, item.realName, item.customerNo].some(value => (value || '').includes(keyword))) && (!query.get('sessionNumber') || item.sessionNumber === Number(query.get('sessionNumber'))) && (!query.get('registrationStatus') || item.registrationStatus === query.get('registrationStatus')) && (!query.get('paymentStatus') || item.paymentStatus === query.get('paymentStatus')))
}

function filterAttendance(query) {
  const keyword = query.get('keyword') || ''
  return attendance.map(item => {
    const customer = customers.find(value => value.id === item.customerId) || {}
    return { ...item, customerNo: customer.customerNo, nickname: customer.nickname, realName: customer.realName }
  }).filter(item => (!keyword || [item.nickname, item.realName, item.customerNo].some(value => (value || '').includes(keyword))) && (!query.get('sessionNumber') || item.sessionNumber === Number(query.get('sessionNumber'))) && (!query.get('attendanceStatus') || item.attendanceStatus === query.get('attendanceStatus')))
}

function filterLegacy(items, query) {
  const ignored = new Set(['pageNum', 'pageSize', 'orderByColumn', 'isAsc'])
  return items.filter(item => Array.from(query.entries()).every(([key, value]) => {
    if (ignored.has(key) || value === '' || !(key in item)) return true
    return String(item[key] ?? '').toLowerCase().includes(value.toLowerCase())
  }))
}

function nextLegacyId(items) {
  return items.reduce((max, item) => Math.max(max, Number(item.id) || 0), 0) + 1
}

const handleJourney = require('./mock-journey-routes')({ customers, sessions, registrations, attendance, send, readBody })

const server = http.createServer(async (req, res) => {
  if (req.method === 'OPTIONS') return send(res, {})
  const url = new URL(req.url, 'http://127.0.0.1')
  const pathname = url.pathname.replace(/^\/dev/, '')

  try {
    if (await handleJourney(req, res, pathname)) return
    if (pathname === '/captchaImage' && req.method === 'GET') {
      const svg = '<svg xmlns="http://www.w3.org/2000/svg" width="120" height="38"><rect width="120" height="38" fill="#f4f4f5"/><text x="22" y="27" font-size="24" font-family="Arial" fill="#409EFF">8888</text></svg>'
      return send(res, { code: 200, data: { captchaOnOff: true, uuid: 'demo-captcha', imgMime: 'image/svg+xml', img: Buffer.from(svg).toString('base64') } })
    }
    if (pathname === '/login' && req.method === 'POST') {
      const body = await readBody(req)
      if (!body.username || !body.password || body.code !== '8888') return send(res, { code: 500, msg: '演示验证码为 8888' })
      return send(res, { code: 200, data: { token: 'ql-demo-token-30-days' } })
    }
    if (pathname === '/getInfo' && req.method === 'GET') return send(res, { code: 200, data: { user: { userName: '轻生活验收账号', avatar: '' }, roles: ['ql_admin'], permissions: ['*:*:*'] } })
    if (pathname === '/getRouters' && req.method === 'GET') return send(res, { code: 200, data: routerData })
    if (pathname === '/logout' && req.method === 'POST') return send(res, { code: 200 })

    if (pathname === '/life/label/getLabelList' && req.method === 'GET') return send(res, { code: 200, data: legacyResources.label })
    if (pathname === '/life/publishTemplate/getTemplateList' && req.method === 'GET') return send(res, { code: 200, data: legacyResources.publishTemplate })

    const legacyListMatch = pathname.match(/^\/life\/([^/]+)\/list$/)
    const legacyActionMatch = pathname.match(/^\/life\/([^/]+)\/(changeStatus|export)$/)
    const legacyItemMatch = pathname.match(/^\/life\/([^/]+)\/([^/]+)$/)
    const legacyRootMatch = pathname.match(/^\/life\/([^/]+)$/)
    if (legacyListMatch && legacyResources[legacyListMatch[1]] && req.method === 'GET') {
      const items = filterLegacy(legacyResources[legacyListMatch[1]], url.searchParams)
      return send(res, page(items, url.searchParams))
    }
    if (legacyActionMatch && legacyResources[legacyActionMatch[1]]) {
      if (legacyActionMatch[2] === 'export') return send(res, { code: 200, msg: '演示环境不生成导出文件' })
      const body = await readBody(req)
      const items = legacyResources[legacyActionMatch[1]]
      const item = items.find(value => String(value.id) === String(body.id || body.userId || body.essayId))
      if (item) Object.assign(item, body)
      return send(res, { code: 200, msg: '状态更新成功' })
    }
    if (legacyItemMatch && legacyResources[legacyItemMatch[1]]) {
      const items = legacyResources[legacyItemMatch[1]]
      if (req.method === 'GET') return send(res, { code: 200, data: items.find(item => String(item.id) === legacyItemMatch[2]) })
      if (req.method === 'DELETE') {
        const ids = new Set(legacyItemMatch[2].split(','))
        legacyResources[legacyItemMatch[1]] = items.filter(item => !ids.has(String(item.id)))
        return send(res, { code: 200, msg: '删除成功' })
      }
    }
    if (legacyRootMatch && legacyResources[legacyRootMatch[1]] && ['POST', 'PUT'].includes(req.method)) {
      const body = await readBody(req)
      const items = legacyResources[legacyRootMatch[1]]
      if (req.method === 'POST') items.unshift({ ...body, id: nextLegacyId(items), insertTime: body.insertTime || now() })
      else Object.assign(items.find(item => String(item.id) === String(body.id)), body)
      return send(res, { code: 200, msg: '操作成功' })
    }

    if (pathname === '/life/customer/list' && req.method === 'GET') return send(res, page(filterCustomers(url.searchParams), url.searchParams))
    if (pathname === '/life/session/list' && req.method === 'GET') return send(res, page(filterSessions(url.searchParams), url.searchParams))
    if (pathname === '/life/registration/list' && req.method === 'GET') return send(res, page(filterRegistrations(url.searchParams), url.searchParams))
    if (pathname === '/life/attendance/list' && req.method === 'GET') return send(res, page(filterAttendance(url.searchParams), url.searchParams))
    if (pathname === '/life/report/list' && req.method === 'GET') return send(res, page(reports.filter(item => !url.searchParams.get('status') || item.status === url.searchParams.get('status')), url.searchParams))

    const customerMatch = pathname.match(/^\/life\/customer\/([^/]+)$/)
    const sessionMatch = pathname.match(/^\/life\/session\/([^/]+)$/)
    const registrationMatch = pathname.match(/^\/life\/registration\/([^/]+)$/)
    const paymentMatch = pathname.match(/^\/life\/registration\/([^/]+)\/payment$/)
    const batchPaymentMatch = pathname.match(/^\/life\/registration\/batch\/([^/]+)\/payment$/)
    const attendanceMatch = pathname.match(/^\/life\/attendance\/([^/]+)\/status$/)
    const reportMatch = pathname.match(/^\/life\/report\/([^/]+)$/)
    if (customerMatch && req.method === 'GET') return send(res, { code: 200, data: customers.find(item => item.id === customerMatch[1]) })
    if (sessionMatch && req.method === 'GET') return send(res, { code: 200, data: sessions.find(item => item.id === sessionMatch[1]) })
    if (registrationMatch && req.method === 'GET') return send(res, { code: 200, data: registrationView(registrations.find(item => item.id === registrationMatch[1])) })

    if (pathname === '/life/customer' && ['POST', 'PUT'].includes(req.method)) {
      const body = await readBody(req)
      if (req.method === 'POST') customers.unshift({ ...body, id: randomUUID(), customerNo: `QY-DEMO-${String(customers.length + 1).padStart(4, '0')}`, revision: 0, createdAt: now(), updatedAt: now() })
      else Object.assign(customers.find(item => item.id === body.id), body, { updatedAt: now() })
      return send(res, { code: 200, msg: '操作成功' })
    }
    if (pathname === '/life/session' && ['POST', 'PUT'].includes(req.method)) {
      const body = await readBody(req)
      if (req.method === 'POST') sessions.unshift({ ...body, id: randomUUID(), updatedAt: now() })
      else Object.assign(sessions.find(item => item.id === body.id), body, { updatedAt: now() })
      return send(res, { code: 200, msg: '操作成功' })
    }
    if (pathname === '/life/registration' && ['POST', 'PUT'].includes(req.method)) {
      const body = await readBody(req)
      if (req.method === 'POST') registrations.unshift({ ...body, id: randomUUID(), paymentStatus: 'unpaid', registeredAt: now(), revision: 0 })
      else Object.assign(registrations.find(item => item.id === body.id), body)
      return send(res, { code: 200, msg: '操作成功' })
    }
    if (paymentMatch && req.method === 'PUT') {
      const body = await readBody(req)
      const item = registrations.find(value => value.id === paymentMatch[1])
      item.paymentStatus = body.paymentStatus
      item.revision += 1
      return send(res, { code: 200, msg: '付款状态更新成功' })
    }
    if (batchPaymentMatch && req.method === 'PUT') {
      const body = await readBody(req)
      registrations.filter(item => item.batchId === batchPaymentMatch[1]).forEach(item => { item.batchPaymentStatus = body.paymentStatus; item.paymentStatus = body.paymentStatus; item.revision += 1 })
      return send(res, { code: 200, msg: '订单付款状态更新成功' })
    }
    if (attendanceMatch && req.method === 'PUT') {
      const body = await readBody(req)
      const item = attendance.find(value => value.id === attendanceMatch[1])
      Object.assign(item, { attendanceStatus: body.attendanceStatus, changeReason: body.changeReason || '', checkInSource: 'web_admin', checkedInAt: ['checked_in', 'late', 'left_early'].includes(body.attendanceStatus) ? now() : item.checkedInAt })
      return send(res, { code: 200, msg: '签到状态更新成功' })
    }
    if (reportMatch && req.method === 'PUT') {
      const body = await readBody(req)
      Object.assign(reports.find(item => item.id === reportMatch[1]), body, { handledAt: now(), handlerName: '轻生活验收账号' })
      return send(res, { code: 200, msg: '举报已处理' })
    }

    return send(res, { code: 404, msg: `未实现的演示接口：${pathname}` }, 404)
  } catch (error) {
    return send(res, { code: 500, msg: error.message })
  }
})

if (require.main === module) server.listen(Number(process.env.QL_MOCK_PORT || 8081), '127.0.0.1', () => {
  process.stdout.write('轻生活原后台 Mock API: http://127.0.0.1:' + server.address().port + '\n')
})

module.exports = server
