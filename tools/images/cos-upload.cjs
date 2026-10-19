#!/usr/bin/env node
/*
 * 按 cos-stage.cjs 生成的 upload-plan.json 上传展示图到 COS，并匿名校验 https://img.guijianju.cn/ 访问。
 *   COS_SECRET_ID=… COS_SECRET_KEY=… [COS_SESSION_TOKEN=…] node tools/images/cos-upload.cjs [--plan DIR/upload-plan.json] [--verify-only]
 * - 密钥只从环境变量读取，不写盘、不打印；建议用限定 qinglife/visual/* 的子账号或临时密钥。
 * - 不覆盖：先 HEAD，已存在且 sha256 不同则停止；PUT 同时带 x-cos-forbid-overwrite: true。
 * - 上传走存储桶默认域名（证书有效）；校验走自定义域名，不带任何签名，等同小程序匿名加载。
 * 签名按 COS 请求签名 v5（q-sign-algorithm=sha1）实现，只用 Node 内置 crypto，不引入第三方包。
 */
const fs = require('fs')
const path = require('path')
const https = require('https')
const crypto = require('crypto')

const WORKSPACE = path.resolve(__dirname, '../../..')
const args = process.argv.slice(2)
const planFile = path.resolve(args.includes('--plan') ? args[args.indexOf('--plan') + 1] : path.join(WORKSPACE, 'outputs', 'cos-upload', 'upload-plan.json'))
const verifyOnly = args.includes('--verify-only')
const plan = JSON.parse(fs.readFileSync(planFile, 'utf8'))
const HOST = `${plan.bucket}.cos.${plan.region}.myqcloud.com`

const hmac = (key, text) => crypto.createHmac('sha1', key).update(text).digest('hex')
const sha1 = text => crypto.createHash('sha1').update(text).digest('hex')
const encode = value => encodeURIComponent(value).replace(/[!'()*]/g, c => '%' + c.charCodeAt(0).toString(16).toUpperCase())

function authorization(method, pathname, headers) {
  const id = process.env.COS_SECRET_ID, key = process.env.COS_SECRET_KEY
  if (!id || !key) throw new Error('缺少 COS_SECRET_ID / COS_SECRET_KEY 环境变量')
  const now = Math.floor(Date.now() / 1000), keyTime = `${now - 60};${now + 600}`
  const names = Object.keys(headers).map(name => name.toLowerCase()).sort()
  const headerString = names.map(name => `${encode(name)}=${encode(String(headers[Object.keys(headers).find(h => h.toLowerCase() === name)]))}`).join('&')
  const httpString = `${method.toLowerCase()}\n${pathname}\n\n${headerString}\n`
  const stringToSign = `sha1\n${keyTime}\n${sha1(httpString)}\n`
  const signature = hmac(hmac(key, keyTime), stringToSign)
  return `q-sign-algorithm=sha1&q-ak=${id}&q-sign-time=${keyTime}&q-key-time=${keyTime}&q-header-list=${names.join(';')}&q-url-param-list=&q-signature=${signature}`
}

function request(options, body) {
  return new Promise((resolve, reject) => {
    const req = https.request({ timeout: 30000, ...options }, res => {
      const chunks = []
      res.on('data', chunk => chunks.push(chunk))
      res.on('end', () => resolve({ status: res.statusCode, headers: res.headers, body: Buffer.concat(chunks) }))
    })
    req.on('timeout', () => req.destroy(new Error('请求超时')))
    req.on('error', reject)
    if (body) req.write(body)
    req.end()
  })
}

async function cos(method, key, body, extra = {}) {
  const pathname = '/' + key.split('/').map(encode).join('/')
  const headers = { host: HOST, ...extra }
  if (process.env.COS_SESSION_TOKEN) headers['x-cos-security-token'] = process.env.COS_SESSION_TOKEN
  headers.Authorization = authorization(method, pathname, Object.fromEntries(Object.entries(headers).filter(([name]) => name !== 'x-cos-security-token')))
  return request({ method, host: HOST, path: pathname, headers }, body)
}

async function anonymous(url, attempts = 5) {
  let last
  for (let i = 0; i < attempts; i += 1) {
    try { return await request({ method: 'GET', host: new URL(url).host, path: new URL(url).pathname, agent: false }) } catch (error) { last = error }
  }
  throw last
}

async function main() {
  const results = []
  for (const item of plan.upload) {
    const local = fs.readFileSync(path.join(path.dirname(planFile), item.key))
    if (crypto.createHash('sha256').update(local).digest('hex') !== item.sha256) throw new Error(`${item.key} 本地文件与清单不符`)
    const record = { key: item.key, url: item.url }
    if (!verifyOnly) {
      const head = await cos('HEAD', item.key)
      if (head.status === 200) {
        const remote = await cos('GET', item.key)
        const same = crypto.createHash('sha256').update(remote.body).digest('hex') === item.sha256
        if (!same) throw new Error(`${item.key} 云端已存在且内容不同，停止（不覆盖）。请换新版本号`)
        record.upload = '已存在且一致，跳过'
      } else if (head.status === 404) {
        const put = await cos('PUT', item.key, local, { 'Content-Type': item.contentType, 'Content-Length': local.length, 'Cache-Control': 'public, max-age=31536000, immutable', 'x-cos-forbid-overwrite': 'true' })
        if (put.status !== 200) throw new Error(`${item.key} 上传失败 ${put.status} ${put.body.toString().slice(0, 300)}`)
        record.upload = '已上传'
      } else {
        throw new Error(`${item.key} HEAD 返回 ${head.status}：${head.body.toString().slice(0, 200)}（检查子账号权限）`)
      }
    }
    try {
      const got = await anonymous(item.url)
      const ok = got.status === 200 && /^image\/jpeg/.test(got.headers['content-type'] || '') && crypto.createHash('sha256').update(got.body).digest('hex') === item.sha256
      record.verify = ok ? `200 image/jpeg ${got.body.length}B，内容一致` : `失败：${got.status} ${got.headers['content-type'] || ''} ${got.body.toString().slice(0, 120)}`
      record.ok = ok
    } catch (error) {
      record.verify = `失败：${error.code || error.message}`
      record.ok = false
    }
    results.push(record)
    console.log(`${record.ok ? '✔' : '✖'} ${item.key}  ${record.upload || ''}  ${record.verify}`)
  }
  fs.writeFileSync(path.join(path.dirname(planFile), 'upload-result.json'), JSON.stringify({ at: new Date().toISOString(), results }, null, 2) + '\n')
  process.exit(results.every(r => r.ok) ? 0 : 1)
}

main().catch(error => { console.error(`cos-upload 失败：${error.message}`); process.exit(1) })
