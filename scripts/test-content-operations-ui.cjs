// Compiled admin + real isolated Spring API. Tokens arrive on stdin only.
const fs = require('node:fs'), path = require('node:path'), http = require('node:http'), assert = require('node:assert/strict')
const { chromium } = require(process.env.QINGLIFE_PLAYWRIGHT_MODULE || 'playwright')
async function main(input) {
  assert.match(input.api, /^http:\/\/127\.0\.0\.1:\d+$/)
  const dist = path.resolve(__dirname, '../apps/admin/dist'), artifacts = process.env.QINGLIFE_CONTENT_ARTIFACTS
  assert.ok(artifacts && fs.existsSync(path.join(dist, 'index.html')))
  const server = http.createServer((req, res) => {
    if (req.url.startsWith('/test-api/')) {
      const upstream = http.request(input.api + req.url.slice(9), { method: req.method, headers: { ...req.headers, host: new URL(input.api).host } }, reply => { res.writeHead(reply.statusCode, reply.headers); reply.pipe(res) })
      upstream.on('error', () => { res.writeHead(502); res.end('Local API unavailable') }); req.pipe(upstream); return
    }
    let file = path.resolve(dist, '.' + decodeURIComponent(new URL(req.url, 'http://127.0.0.1').pathname).replace(/^\/test-admin/, ''))
    if (!file.startsWith(dist + path.sep) || !fs.existsSync(file) || fs.statSync(file).isDirectory()) file = path.join(dist, 'index.html')
    res.setHeader('Content-Type', ({ '.js': 'application/javascript', '.css': 'text/css', '.html': 'text/html', '.svg': 'image/svg+xml' })[path.extname(file)] || 'application/octet-stream'); fs.createReadStream(file).pipe(res)
  })
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve))
  const browser = await chromium.launch({ headless: true, executablePath: process.env.QINGLIFE_BROWSER_EXECUTABLE })
  let page; const errors = [], checks = []
  try {
    const context = await browser.newContext({ viewport: { width: 1440, height: 1100 } }); await context.addInitScript(token => sessionStorage.setItem('Admin-Token', token), input.token)
    page = await context.newPage(); page.on('pageerror', error => errors.push(error.message))
    const api = async (route, method = 'GET', body) => {
      const response = await fetch(input.api + route, { method, headers: { 'Content-Type': 'application/json', Authorization: 'Bearer ' + input.token }, ...(body ? { body: JSON.stringify(body) } : {}) }); const result = await response.json(); assert.equal(result.code, 200, JSON.stringify(result)); return result.data
    }
    await page.goto('http://127.0.0.1:' + server.address().port + '/test-admin/contentPublishing/essay', { waitUntil: 'networkidle' })
    const row = page.locator('.app-main .el-table__body-wrapper tr').filter({ hasText: (await api('/life/essay/' + input.id)).title })
    await row.getByRole('button', { name: /修改/ }).click()
    const editor = page.locator('.el-dialog:visible').filter({ has: page.locator('.ql-editor') })
    await editor.getByText('公众号录入', { exact: true }).waitFor()
    assert.equal((await (await fetch(input.api + '/app/qinglife/readings/' + input.id)).json()).code, 404)
    await editor.locator('.el-form-item').filter({ hasText: '原署名' }).locator('input').fill('合成公众号署名')
    await editor.locator('.ql-editor').fill('合成公众号正文，仅供隔离内容验收。')
    const save = page.waitForResponse(r => r.request().method() === 'PUT' && new URL(r.url()).pathname === '/test-api/life/essay')
    await editor.getByRole('button', { name: /确.*定/ }).click(); assert.equal((await (await save).json()).code, 200); await editor.waitFor({ state: 'hidden' })
    let article = await api('/life/essay/' + input.id); assert.equal(article.status, 0); assert.equal(article.authorName, '合成公众号署名'); assert.ok(article.content.includes('合成公众号正文'))
    checks.push('真实后台修改公众号正文与署名，保存草稿，不产生公开阅读')
    await row.getByRole('button', { name: '内容位置', exact: true }).click()
    const placements = page.locator('.el-dialog:visible').filter({ hasText: '认知引导页 · 文章关联' })
    const family = placements.locator('.placement').filter({ has: page.locator('h3', { hasText: '花花一家' }) })
    await family.getByRole('button', { name: '关联当前文章', exact: true }).click()
    await page.screenshot({ path: path.join(artifacts, 'content-draft-link.png'), fullPage: true, animations: 'disabled' })
    const publicLink = () => api('/app/qinglife/content-placements/about.huahua-family')
    assert.equal((await publicLink()).item, null)
    // Explicitly enable only the synthetic isolated article, then reload the real UI.
    await api('/life/essay/changeStatus', 'PUT', { id: input.id, status: 1 })
    await placements.getByRole('button', { name: '刷新配置', exact: true }).click()
    await family.getByRole('button', { name: '关联当前文章', exact: true }).click()
    await family.locator('.el-switch').click()
    for (const text of ['已核实文章确属此人物／故事', '已核实素材与公开使用授权', '全文已审核，可作为认知页延伸阅读']) await family.getByText(text, { exact: true }).click()
    await family.locator('textarea').fill('合成隔离测试授权与全文审核依据，不代表真实人物验收')
    await family.getByRole('button', { name: '保存关联', exact: true }).click()
    const saved = page.waitForResponse(r => r.request().method() === 'PUT' && r.url().includes('/placements/about.huahua-family'))
    await page.locator('.el-message-box:visible').getByRole('button', { name: '确定', exact: true }).click(); assert.equal((await (await saved).json()).code, 200)
    await placements.getByRole('button', { name: '刷新配置', exact: true }).waitFor()
    await family.getByRole('button', { name: '查看关联历史', exact: true }).click()
    const audit = page.locator('.el-dialog:visible').filter({ hasText: '关联历史（最多50条）' }); await audit.getByText('合成隔离测试授权与全文审核依据', { exact: false }).waitFor()
    await page.screenshot({ path: path.join(artifacts, 'content-review-audit.png'), fullPage: true, animations: 'disabled' })
    const link = await publicLink(); assert.equal(link.item.businessArticleId, input.id); assert.equal(link.item.personKey, 'huahua-family'); assert.ok(!JSON.stringify(link).includes('合成隔离测试授权'))
    checks.push('后台明确核实人物、授权与全文，关联后匿名读取正确目标，审核依据仅后台可见')
    const { withStoryContent } = require('../apps/miniapp/utils/story-content')
    const approved = { state: 'releaseReady', authorization: 'confirmed', editorialReview: 'approved', factCheck: 'confirmed', identityCheck: 'confirmed', source: { type: 'synthetic' }, personKey: 'huahua-family', intro: '合成原有故事编排' }
    const mini = withStoryContent({ data: { story: approved } }, { enabled: () => true, articlePlacement: key => api('/app/qinglife/content-placements/' + key) }); mini.setData = patch => Object.assign(mini.data, patch)
    await mini.loadStoryContent(); assert.ok(mini.data.story.articleUrl.includes('id=' + input.id)); assert.equal(mini.data.story.intro, approved.intro)
    article = await api('/life/essay/' + input.id); article.content = '<p>合成修订正文，原全文审核应失效。</p>'; await api('/life/essay', 'PUT', article)
    assert.equal((await publicLink()).item, null); await mini.loadStoryContent(); assert.equal(mini.data.story.articleUrl, '')
    checks.push('当前小程序数据钩子经真实HTTP关联，不换原故事；正文修改后入口失效，无旧ID或示例兜底')
    assert.deepEqual(errors, [])
    fs.writeFileSync(path.join(artifacts, 'browser-result.json'), JSON.stringify({ realBrowser: true, realApi: true, mockedBusinessResponses: false, checks, pageErrors: errors, miniappNativeDevice: false }, null, 2))
    console.log('REAL_CONTENT_OPERATIONS_BROWSER=PASS')
  } catch (error) {
    if (page) { await page.screenshot({ path: path.join(artifacts, 'browser-failure.png'), fullPage: true }).catch(() => {}); fs.writeFileSync(path.join(artifacts, 'browser-failure.txt'), error.message + '\n' + (await page.locator('.el-dialog:visible').allTextContents()).join('\n')) }
    throw error
  } finally { await browser.close(); await new Promise(resolve => server.close(resolve)) }
}
let input = ''; process.stdin.setEncoding('utf8'); process.stdin.on('data', b => { input += b }); process.stdin.on('end', () => main(JSON.parse(input)).catch(error => { console.error(error.stack); process.exitCode = 1 }))
