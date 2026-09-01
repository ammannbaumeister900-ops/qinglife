const assert = require('assert')
const fs = require('fs')
const path = require('path')

const root = path.resolve(__dirname, '..')
const app = JSON.parse(fs.readFileSync(path.join(root, 'app.json'), 'utf8'))
const allowedTags = new Set(['block', 'button', 'checkbox', 'cover-view', 'input', 'rich-text', 'switch', 'text', 'textarea', 'view'])

assert.deepStrictEqual(app.tabBar.list.map((item) => item.text), ['首页', '轻体营', '内容', '轻友', '我的'])
assert.strictEqual(new Set(app.pages).size, app.pages.length, '页面不能重复注册')

for (const page of app.pages) {
  const base = path.join(root, page)
  for (const extension of ['.js', '.json', '.wxml', '.wxss']) {
    assert.ok(fs.existsSync(`${base}${extension}`), `${page}${extension} 缺失`)
  }

  const js = fs.readFileSync(`${base}.js`, 'utf8')
  const wxml = fs.readFileSync(`${base}.wxml`, 'utf8')
  assert.ok(!/\.(includes|slice|filter|find)\(/.test(wxml), `${page} 的 WXML 使用了不支持的方法调用`)

  for (const match of wxml.matchAll(/<\/?([a-z-]+)/g)) {
    assert.ok(allowedTags.has(match[1]), `${page} 使用未知标签 ${match[1]}`)
  }

  for (const match of wxml.matchAll(/bind(?:tap|input)="([A-Za-z0-9_]+)"/g)) {
    assert.ok(new RegExp(`\\n\\s*(?:async\\s+)?${match[1]}\\s*\\(`).test(js), `${page} 缺少事件方法 ${match[1]}`)
  }
}

for (const file of fs.readdirSync(path.join(root, 'pages'), { withFileTypes: true })) {
  if (!file.isDirectory()) continue
  const jsPath = path.join(root, 'pages', file.name, 'index.js')
  if (!fs.existsSync(jsPath)) continue
  const js = fs.readFileSync(jsPath, 'utf8')
  for (const match of js.matchAll(/['"](\/pages\/[a-z-]+\/index)(?:\?[^'"]*)?['"]/g)) {
    assert.ok(app.pages.includes(match[1].slice(1)), `未注册的跳转目标 ${match[1]}`)
  }
}

console.log('static-contract.test.js OK')
