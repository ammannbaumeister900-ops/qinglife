// Real compiled admin + authenticated API + isolated MySQL. No mocked business response.
// Tokens arrive only on stdin. One dropped response is deliberate transport fault injection.
const fs = require('node:fs')
const path = require('node:path')
const http = require('node:http')
const assert = require('node:assert/strict')
const { chromium } = require(process.env.QINGLIFE_PLAYWRIGHT_MODULE || 'playwright')

async function main(input) {
  assert.match(input.api, /^http:\/\/127\.0\.0\.1:\d+$/)
  const dist = path.resolve(__dirname, '../apps/admin/dist')
  assert.ok(fs.existsSync(path.join(dist, 'index.html')), 'Build the isolated admin before running browser validation')
  const artifacts = process.env.QINGLIFE_M2_UI_ARTIFACTS
  assert.ok(artifacts)
  fs.mkdirSync(artifacts, { recursive: true })
  let dropCaptureReply = true
  const captures = []
  const server = http.createServer((req, res) => {
    if (req.url.startsWith('/test-api/')) {
      const capture = req.method === 'POST' && req.url.endsWith('/offline-receipt')
      const chunks = []
      if (capture) req.on('data', b => chunks.push(b))
      req.on('end', () => { if (capture) captures.push(JSON.parse(Buffer.concat(chunks).toString())) })
      const upstream = http.request(input.api + req.url.slice('/test-api'.length), { method: req.method, headers: { ...req.headers, host: new URL(input.api).host } }, reply => {
        if (capture && dropCaptureReply) {
          dropCaptureReply = false
          reply.resume(); reply.on('end', () => { res.writeHead(502, { 'Content-Type': 'application/json' }); res.end(JSON.stringify({ code: 502, msg: 'Synthetic proxy fault: successful upstream reply lost' })) })
        } else { res.writeHead(reply.statusCode, reply.headers); reply.pipe(res) }
      })
      upstream.on('error', () => { res.writeHead(502); res.end('Isolated API unavailable') })
      req.pipe(upstream); return
    }
    const pathname = decodeURIComponent(new URL(req.url, 'http://127.0.0.1').pathname)
    let file = path.resolve(dist, '.' + pathname.replace(/^\/test-admin/, ''))
    if (!file.startsWith(dist + path.sep) || !fs.existsSync(file) || fs.statSync(file).isDirectory()) file = path.join(dist, 'index.html')
    res.setHeader('Content-Type', ({ '.js': 'application/javascript', '.css': 'text/css', '.html': 'text/html', '.svg': 'image/svg+xml', '.png': 'image/png' })[path.extname(file)] || 'application/octet-stream')
    fs.createReadStream(file).pipe(res)
  })
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve))
  const origin = 'http://127.0.0.1:' + server.address().port
  const browser = await chromium.launch({ headless: true, ...(process.env.QINGLIFE_BROWSER_EXECUTABLE ? { executablePath: process.env.QINGLIFE_BROWSER_EXECUTABLE } : {}) })
  const errors = [], checks = []
  let page
  try {
    const context = await browser.newContext({ viewport: { width: 1720, height: 1180 } })
    await context.addInitScript(token => sessionStorage.setItem('Admin-Token', token), input.token)
    page = await context.newPage()
    page.on('pageerror', error => errors.push(error.message))
    const go = async session => {
      await page.goto(origin + '/test-admin/activityOperations/registration?sessionNumber=' + session, { waitUntil: 'networkidle' })
      await page.locator('.app-main').getByRole('button', { name: '订单详情', exact: true }).first().click()
      await page.locator('.participant-order .people-table .el-table__body-wrapper tr').first().waitFor()
    }
    const order = () => page.locator('.el-dialog:visible').filter({ has: page.locator('.participant-order') })
    const operation = () => page.locator('.el-dialog:visible').filter({ has: page.locator('.operation-form') })
    const person = name => order().locator('.people-table .el-table__body-wrapper tr').filter({ hasText: name })
    const field = (label, selector = 'input') => operation().locator('.el-form-item').filter({ has: page.locator('label', { hasText: label }) }).locator(selector)
    const fill = (label, value, selector) => field(label, selector).fill(value)
    const reason = () => fill('操作原因', '合成运营验证：核实凭证与对象', 'textarea')
    const choose = async (label, name) => {
      await field(label).click()
      await page.locator('.el-select-dropdown:visible').getByText(name, { exact: true }).click()
    }
    const submit = async title => {
      await operation().getByRole('button', { name: title, exact: true }).click()
      await page.locator('.el-message-box:visible').getByRole('button', { name: '确认执行', exact: true }).click()
      await operation().getByText('操作已保存', { exact: true }).waitFor()
      await operation().getByRole('button', { name: '关闭并查看订单', exact: true }).click()
      await operation().waitFor({ state: 'hidden' })
      await order().locator('.order-heading button:not([disabled])').waitFor()
      await page.waitForLoadState('networkidle')
    }
    const shot = name => page.screenshot({ path: path.join(artifacts, name + '.png'), fullPage: true, animations: 'disabled' })
    const tab = name => order().getByRole('tab', { name, exact: true }).click()
    const refundRow = status => order().locator('#pane-refunds .el-table__body-wrapper tr').filter({ hasText: status }).last()
    await go(input.sessionNumber)
    assert.equal(await person('合成主报名人').getByRole('button', { name: '确认报名', exact: true }).count(), 0)
    await order().getByRole('button', { name: '登记实际到账', exact: true }).click()
    await fill('实际到账金额', '7600.00')
    await choose('到账方式', '转账')
    await fill('外部流水号', 'SYNTHETIC-BROWSER-CAPTURE-' + input.batchId)
    await fill('实际发生依据', '合成线下到账回单，仅用于隔离测试', 'textarea')
    await operation().getByText('我已核实该笔款项确已到账', { exact: false }).click()
    await operation().getByRole('button', { name: '登记实际到账', exact: true }).click()
    await page.locator('.el-message-box:visible').getByRole('button', { name: '确认执行', exact: true }).click()
    await operation().getByRole('button', { name: '按原请求重试', exact: true }).waitFor()
    await shot('01-uncertain-retry')
    await operation().getByRole('button', { name: '按原请求重试', exact: true }).click()
    await operation().getByText('操作已保存', { exact: true }).waitFor()
    await operation().getByRole('button', { name: '关闭并查看订单', exact: true }).click()
    await page.waitForLoadState('networkidle')
    assert.equal(captures.length, 2); assert.deepEqual(captures[0], captures[1]); checks.push('到账成功后丢失响应：原编号原报文重试，数据库仅一笔到账')

    await order().getByRole('button', { name: '分配给报名人', exact: true }).click()
    await operation().locator('tr').filter({ hasText: '合成主报名人' }).locator('input').fill('8000.00')
    await reason()
    await operation().getByRole('button', { name: '分配到账', exact: true }).click()
    await page.locator('.el-message-box:visible').getByRole('button', { name: '确认执行', exact: true }).click()
    await operation().getByText('分配金额超过该笔实际到账可用余额，到账事实已保留', { exact: true }).waitFor()
    await shot('02-allocation-conflict')
    await operation().getByRole('button', { name: '关闭并刷新', exact: true }).click()
    await page.waitForLoadState('networkidle')
    checks.push('超额分配被真实API拒绝，原到账保留，关闭刷新后重新分配')
    await order().getByRole('button', { name: '分配给报名人', exact: true }).click()
    for (const name of ['合成主报名人', '合成现金同行人']) await operation().locator('tr').filter({ hasText: name }).locator('input').fill('3800.00')
    await reason(); await submit('分配到账')
    for (const name of ['合成主报名人', '合成现金同行人']) {
      await person(name).getByRole('button', { name: '确认现金结清', exact: true }).click()
      await reason(); await submit('确认现金结清')
    }
    checks.push('一笔真实到账分配两人，分别结清并各占一席')

    await person('合成卡次同行人').getByRole('button', { name: '卡次使用核实', exact: true }).click()
    await field('卡次账户').click()
    await page.locator('.el-select-dropdown:visible .el-select-dropdown__item').first().click()
    await fill('运营核实依据', '已核实报名人自己的有效账户，仅合成测试', 'textarea')
    await operation().getByText('我已核实本人使用或同订单代用依据', { exact: true }).click()
    await submit('记录卡次核实')
    await tab('卡次核实与使用')
    await order().getByRole('button', { name: '撤销未用核实', exact: true }).click()
    await reason(); await submit('撤销未用核实')
    await person('合成卡次同行人').getByRole('button', { name: '卡次使用核实', exact: true }).click()
    await operation().getByText('同订单主报名人代用', { exact: true }).click()
    await field('卡次账户').click()
    await page.locator('.el-select-dropdown:visible').getByText(input.cardId, { exact: false }).click()
    await fill('运营核实依据', '已核实同单主报名人代用意愿及使用人，线下依据存档', 'textarea')
    await operation().getByText('我已核实本人使用或同订单代用依据', { exact: true }).click()
    await submit('记录卡次核实')
    await order().getByRole('button', { name: '确认扣次与报名', exact: true }).click()
    await reason(); await submit('确认扣次与报名')
    await person('合成卡次同行人').getByText('卡次结算 · 无现金欠款', { exact: true }).waitFor()
    await shot('03-participant-order')
    checks.push('本人卡次选项、核实撤销、同订单代用、扣次确认；卡次人员显示无现金欠款')

    for (const action of ['取消申请', '记录失败']) {
      await person('合成主报名人').getByRole('button', { name: '申请退款', exact: true }).click()
      await operation().locator('.el-table input').first().fill('100.00'); await reason(); await submit('申请逐人退款')
      await tab('退款申请与实际退款')
      await refundRow('申请中').getByRole('button', { name: action, exact: true }).click()
      await operation().getByText('我已核实没有发生任何实际退款', { exact: false }).click()
      await reason(); await submit(action === '取消申请' ? '取消退款申请' : '记录退款失败')
    }
    checks.push('未实际退付时明确确认，取消申请或记录失败后释放预留，可重新申请')
    for (const value of ['100.00', '3700.00']) {
      await person('合成主报名人').getByRole('button', { name: '申请退款', exact: true }).click()
      await operation().locator('.el-table input').first().fill(value)
      await reason(); await submit('申请逐人退款')
      await tab('退款申请与实际退款')
      if (value === '100.00') {
        await refundRow('申请中').getByRole('button', { name: '开始处理', exact: true }).click()
        await reason(); await submit('开始处理退款')
      }
      await refundRow(value === '100.00' ? '处理中' : '申请中').getByRole('button', { name: '登记实际退款', exact: true }).click()
      await fill('实际线下退款金额', value); await choose('退款方式', '转账')
      await fill('实际退款时间', '2026-10-18 10:30:00'); await field('实际退款时间').press('Tab')
      await fill('外部流水号', 'SYNTHETIC-BROWSER-REFUND-' + value + '-' + input.batchId)
      await fill('实际发生依据', '合成实际线下退款凭证', 'textarea'); await reason()
      await operation().getByText('我已核实该笔款项确已线下退付', { exact: false }).click()
      await submit('登记实际退款')
    }
    assert.ok((await person('合成主报名人').textContent()).includes('占用 1 席'))
    await shot('04-refund-history')
    checks.push('两次部分退款累计全退；实际退款后名额保持，同行人的结算不变')
    for (const name of ['合成主报名人', '合成卡次同行人']) {
      await person(name).getByRole('button', { name: '取消此人', exact: true }).click()
      await reason(); await submit('取消此人报名')
    }
    await person('合成卡次同行人').getByRole('button', { name: '原账户返卡', exact: true }).click()
    await reason(); await submit('返还原账户卡次')
    await person('合成主报名人').locator('.el-table__expand-icon').click()
    await person('合成卡次同行人').locator('.el-table__expand-icon').click()
    await shot('05-cancel-return-rebooking')
    assert.ok((await person('合成现金同行人').textContent()).includes('占用 1 席'))
    checks.push('逐人取消、原账户返卡、重报条件展示；另一名现金同行人仍占一席')
    await order().getByRole('button', { name: '关闭订单', exact: true }).click()

    await go(input.differenceSession)
    await order().getByRole('button', { name: '申请退款', exact: true }).click()
    await operation().locator('.el-table input').first().fill('100.00'); await reason(); await submit('申请逐人退款')
    await tab('退款申请与实际退款')
    await refundRow('申请中').getByRole('button', { name: '登记实际退款', exact: true }).click()
    await fill('实际线下退款金额', '90.00'); await choose('退款方式', '现金')
    await fill('实际退款时间', '2026-10-18 10:30:00'); await field('实际退款时间').press('Tab')
    await fill('实际发生依据', '合成退款金额存在差异，保留事实核对', 'textarea'); await reason()
    await operation().getByText('我已核实该笔款项确已线下退付', { exact: false }).click()
    await operation().getByRole('button', { name: '登记实际退款', exact: true }).click()
    await page.locator('.el-message-box:visible').getByRole('button', { name: '确认执行', exact: true }).click()
    await operation().getByText('实际退款已保存，差异待核对', { exact: true }).waitFor()
    await operation().getByRole('button', { name: '关闭并查看订单', exact: true }).click()
    await page.waitForLoadState('networkidle'); await shot('06-refund-reconciliation')
    assert.equal(await order().getByRole('button', { name: '登记实际退款', exact: true }).count(), 0)
    checks.push('实际退款与申请不一致：保存事实、显示待核对、禁止另记或自动改金额')

    const viewerContext = await browser.newContext({ viewport: { width: 1720, height: 1180 } })
    await viewerContext.addInitScript(token => sessionStorage.setItem('Admin-Token', token), input.viewer)
    const viewerPage = await viewerContext.newPage()
    await viewerPage.goto(origin + '/test-admin/activityOperations/registration?sessionNumber=' + input.sessionNumber, { waitUntil: 'networkidle' })
    await viewerPage.locator('.app-main').getByRole('button', { name: '订单详情', exact: true }).first().click()
    await viewerPage.getByText('当前账号只可查看。', { exact: false }).waitFor()
    assert.equal(await viewerPage.locator('.participant-order').getByRole('button', { name: '取消此人', exact: true }).count(), 0)
    assert.equal(await viewerPage.locator('.participant-order').getByRole('button', { name: '登记实际到账', exact: true }).count(), 0)
    await viewerPage.screenshot({ path: path.join(artifacts, '07-permissions-readonly.png'), fullPage: true })
    checks.push('真实受限角色只能查看，没有自动授予编辑、退款、取消或卡次权限')
    assert.deepEqual(errors, [])
    fs.writeFileSync(path.join(artifacts, 'browser-result.json'), JSON.stringify({ realBrowser: true, realApi: true, mockedBusinessResponses: false, transportFaultInjection: 'first successful capture reply replaced with proxy HTTP 502', checks, pageErrors: errors }, null, 2))
    console.log('REAL_PARTICIPANT_OPERATIONS_BROWSER=PASS; checks=' + checks.length)
  } catch (error) {
    if (page) {
      await page.screenshot({ path: path.join(artifacts, 'browser-failure.png'), fullPage: true }).catch(() => {})
      fs.writeFileSync(path.join(artifacts, 'browser-failure.txt'), error.message + '\n' + (await page.locator('.el-dialog:visible').allTextContents()).join('\n'))
    }
    throw error
  } finally { await browser.close(); await new Promise(resolve => server.close(resolve)) }
}
let input = ''
process.stdin.setEncoding('utf8')
process.stdin.on('data', b => { input += b })
process.stdin.on('end', () => main(JSON.parse(input)).catch(error => { console.error(error.stack); process.exitCode = 1 }))
