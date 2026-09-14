'use strict'

const http = require('http')
const fs = require('fs')
const path = require('path')

const distRoot = path.resolve(__dirname, 'dist')
const mime = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'application/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.jpeg': 'image/jpeg',
  '.gif': 'image/gif',
  '.svg': 'image/svg+xml',
  '.woff': 'font/woff',
  '.woff2': 'font/woff2'
}

function proxyApi(req, res) {
  const proxy = http.request({
    hostname: '127.0.0.1',
    port: Number(process.env.QL_MOCK_PORT || 8081),
    method: req.method,
    path: req.url.replace(/^\/admin/, ''),
    headers: req.headers
  }, apiRes => {
    res.writeHead(apiRes.statusCode, apiRes.headers)
    apiRes.pipe(res)
  })
  proxy.on('error', () => {
    res.writeHead(502, { 'Content-Type': 'application/json; charset=utf-8' })
    res.end(JSON.stringify({ code: 500, msg: '验收 Mock API 未启动' }))
  })
  req.pipe(proxy)
}

function serveFile(filePath, res) {
  fs.readFile(filePath, (error, data) => {
    if (error) {
      res.writeHead(404)
      return res.end('Not found')
    }
    res.writeHead(200, { 'Content-Type': mime[path.extname(filePath).toLowerCase()] || 'application/octet-stream' })
    res.end(data)
  })
}

http.createServer((req, res) => {
  if (req.url.startsWith('/admin/')) return proxyApi(req, res)
  const pathname = decodeURIComponent(new URL(req.url, 'http://127.0.0.1').pathname)
  const candidate = path.resolve(distRoot, '.' + pathname)
  if (candidate.startsWith(distRoot) && fs.existsSync(candidate) && fs.statSync(candidate).isFile()) {
    return serveFile(candidate, res)
  }
  return serveFile(path.join(distRoot, 'index.html'), res)
}).listen(Number(process.env.QL_UI_PORT || 8097), '127.0.0.1', () => {
  process.stdout.write('轻生活原生产后台验收地址: http://127.0.0.1:' + (process.env.QL_UI_PORT || 8097) + '/\n')
})
