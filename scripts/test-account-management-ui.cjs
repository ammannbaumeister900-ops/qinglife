// Real local admin build + real isolated API. Session input is supplied over stdin, never command arguments.
const fs = require('node:fs')
const path = require('node:path')
const http = require('node:http')
const assert = require('node:assert/strict')
const { chromium } = require(process.env.QINGLIFE_PLAYWRIGHT_MODULE || 'playwright')

async function main(input) {
  assert.match(input.api, /^http:\/\/127\.0\.0\.1:\d+$/)
  assert.match(input.name, /^ui-account-[a-z0-9]+$/)
  const root = path.resolve(__dirname, '..')
  const dist = path.join(root, 'apps/admin/dist')
  const artifacts = process.env.QINGLIFE_ACCOUNT_UI_ARTIFACTS || path.join(root, '.tools/account-management-20261012')
  fs.mkdirSync(artifacts, { recursive: true })
  const server = http.createServer((req, res) => {
    if (req.url.startsWith('/test-api/')) {
      const upstream = http.request(input.api + req.url.slice('/test-api'.length), {
        method: req.method, headers: { ...req.headers, host: new URL(input.api).host }
      }, reply => { res.writeHead(reply.statusCode, reply.headers); reply.pipe(res) })
      upstream.on('error', () => { res.writeHead(502); res.end('Local test API unavailable') })
      req.pipe(upstream)
      return
    }
    const pathname = decodeURIComponent(new URL(req.url, 'http://127.0.0.1').pathname)
    let file = path.resolve(dist, '.' + pathname.replace(/^\/test-admin/, ''))
    if (!file.startsWith(dist + path.sep)) file = path.join(dist, 'index.html')
    if (!fs.existsSync(file) || fs.statSync(file).isDirectory()) file = path.join(dist, 'index.html')
    const mime = { '.js': 'application/javascript', '.css': 'text/css', '.html': 'text/html', '.svg': 'image/svg+xml' }
    res.setHeader('Content-Type', mime[path.extname(file)] || 'application/octet-stream')
    fs.createReadStream(file).pipe(res)
  })
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve))
  const origin = 'http://127.0.0.1:' + server.address().port
  const browser = await chromium.launch({ headless: true, ...(process.env.QINGLIFE_BROWSER_EXECUTABLE ? { executablePath: process.env.QINGLIFE_BROWSER_EXECUTABLE } : {}) })
  const errors = []
  let page
  try {
    const context = await browser.newContext({ viewport: { width: 1440, height: 1000 } })
    await context.addInitScript(token => sessionStorage.setItem('Admin-Token', token), input.token)
    page = await context.newPage()
    page.on('pageerror', error => errors.push(error.message))
    const go = route => page.goto(origin + '/test-admin' + route, { waitUntil: 'networkidle' })
    const submit = async (dialog, method, endpoint, button = /确.*定/) => {
      const response = page.waitForResponse(r => r.request().method() === method && new URL(r.url()).pathname === '/test-api' + endpoint)
      response.catch(() => {})
      await dialog.getByRole('button', { name: button }).click()
      assert.equal((await (await response).json()).code, 200)
      await dialog.waitFor({ state: 'hidden' })
    }
    await go('/system/role')
    await page.locator('.app-main button').filter({ hasText: '新增' }).first().click()
    let dialog = page.locator('.el-dialog:visible')
    await dialog.getByPlaceholder('请输入角色名称').fill(input.name)
    await dialog.getByPlaceholder('请输入权限字符').fill(input.name)
    await dialog.locator('.el-tree-node__content').filter({ hasText: '权限管理' }).click()
    await dialog.locator('.el-tree-node__content').filter({ hasText: '后台账号' }).locator('.el-checkbox').click()
    await submit(dialog, 'POST', '/system/role')
    await page.screenshot({ path: path.join(artifacts, 'roles.png'), fullPage: true })

    await page.locator('.sidebar-container a').filter({ hasText: '后台账号' }).click()
    await page.locator('.app-main button').filter({ hasText: '新增' }).first().click()
    dialog = page.locator('.el-dialog:visible')
    await dialog.getByPlaceholder('请输入用户昵称').fill('浏览器合成运营')
    await dialog.getByPlaceholder('请输入用户名称').fill(input.name)
    await dialog.getByPlaceholder('请输入用户密码').fill('Synthetic-123')
    await dialog.locator('.el-form-item').filter({ has: page.locator('label', { hasText: /^角色$/ }) }).locator('.el-select').click()
    await page.locator('.el-select-dropdown:visible').getByText(input.name, { exact: true }).click()
    await dialog.getByPlaceholder('请输入用户昵称').click()
    await page.screenshot({ path: path.join(artifacts, 'account-form.png'), fullPage: true })
    await submit(dialog, 'POST', '/system/user')
    await page.locator('.app-main').getByPlaceholder('请输入用户名称').fill(input.name)
    await page.locator('.app-main button').filter({ hasText: '搜索' }).first().click()
    await page.locator('.el-table__body-wrapper').getByText(input.name, { exact: true }).waitFor()
    await page.screenshot({ path: path.join(artifacts, 'accounts.png'), fullPage: true })

    await page.locator('.sidebar-container a[href$="/permissionManagement/internalAccounts"]').click()
    await page.locator('.app-main').getByRole('button', { name: '新增微信绑定', exact: true }).click()
    dialog = page.locator('.el-dialog:visible')
    await dialog.getByPlaceholder('输入称呼或手机号搜索').click()
    await dialog.getByPlaceholder('输入称呼或手机号搜索').fill(input.name)
    await page.locator('.el-select-dropdown:visible').getByText(input.name, { exact: false }).click()
    await dialog.locator('.el-form-item').filter({ hasText: '绑定个人后台账号' }).locator('.el-select').click()
    await page.locator('.el-select-dropdown:visible').getByText('浏览器合成运营（' + input.name + '）', { exact: false }).click()
    await dialog.getByText('档案与报名操作', { exact: true }).click()
    await submit(dialog, 'PUT', '/life/staff', '保存授权')
    let row = page.locator('.el-table__body-wrapper tr').filter({ hasText: input.name })
    await row.getByText(input.name, { exact: false }).first().waitFor()
    await page.screenshot({ path: path.join(artifacts, 'bindings.png'), fullPage: true })
    await row.getByRole('button', { name: '编辑绑定与权限', exact: true }).click()
    dialog = page.locator('.el-dialog:visible')
    await dialog.getByText('档案与报名操作', { exact: true }).click()
    await submit(dialog, 'PUT', '/life/staff', '保存授权')
    row = page.locator('.el-table__body-wrapper tr').filter({ hasText: input.name })
    await row.getByRole('button', { name: '解除绑定', exact: true }).click()
    const response = page.waitForResponse(r => r.request().method() === 'DELETE' && r.url().includes('/test-api/life/staff/'))
    await page.locator('.el-message-box').getByRole('button', { name: '确定', exact: true }).click()
    assert.equal((await (await response).json()).code, 200)
    await row.waitFor({ state: 'hidden' })
    assert.deepEqual(errors, [])
    const result = { realBrowser: true, realLocalApi: true, mockedResponses: false, createdRole: true, createdAccount: true, assignedRole: true, boundWeChat: true, editedWorkspacePermission: true, unboundWeChat: true, pageErrors: errors }
    fs.writeFileSync(path.join(artifacts, 'browser-result.json'), JSON.stringify(result, null, 2))
    console.log(JSON.stringify(result))
  } catch (error) {
    if (page) fs.writeFileSync(path.join(artifacts, 'browser-failure.json'), JSON.stringify({ url: page.url(), buttons: await page.locator('.app-main button').allTextContents(), errors }, null, 2))
    if (page) await page.screenshot({ path: path.join(artifacts, 'browser-failure.png'), fullPage: true }).catch(() => {})
    throw error
  } finally {
    await browser.close()
    await new Promise(resolve => server.close(resolve))
  }
}
let input = ''
process.stdin.setEncoding('utf8')
process.stdin.on('data', chunk => { input += chunk })
process.stdin.on('end', () => main(JSON.parse(input)).catch(error => { console.error(error.message); process.exitCode = 1 }))
