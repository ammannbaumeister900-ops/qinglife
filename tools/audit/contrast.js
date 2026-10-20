#!/usr/bin/env node
/*
 * 改版页面对比度检查（PLAN §5-P6）：扫描页面 WXSS 中每条设置了 color 的规则，按 WXML 嵌套找到实际背景色，计算 WCAG 对比度。
 *   node tools/audit/contrast.js [--json out.json]
 * 门槛：正文色（--q-ink / --q-on-dark 等）≥ 7:1；辅助色（--q-ink-2 / --q-on-dark-2 / --q-moss / --q-amber / --q-juice）≥ 4.5:1。
 * 汁绿只允许出现在深林底上。只解析令牌与本文件能确定的背景；无法确定的列为"未判定"，不算通过。
 */
const fs = require('fs')
const path = require('path')

const MINIAPP = path.resolve(__dirname, '../../apps/miniapp')
const PAGES = [
  { name: '首页', wxml: 'pages/home/index.wxml', wxss: 'pages/home/index.wxss' },
  { name: '了解轻生活', wxml: 'pages/about/index.wxml', wxss: 'pages/about/index.wxss' },
  { name: '清体营', wxml: 'pages/camp/index.wxml', wxss: 'pages/camp/index.wxss' },
  { name: '详情头部与报名栏', wxml: 'pages/camp-flow/index.wxml', wxss: 'pages/camp-flow/detail.wxss', scope: 'detail-page' },
  { name: 'Tab 栏', wxml: 'custom-tab-bar/index.wxml', wxss: 'custom-tab-bar/index.wxss' }
]
const AUX = new Set(['--q-ink-2', '--q-on-dark-2', '--q-moss', '--q-amber', '--q-juice'])

const read = file => fs.readFileSync(path.join(MINIAPP, file), 'utf8')
const strip = css => css.replace(/\/\*[\s\S]*?\*\//g, '')

function tokens() {
  const map = {}
  for (const [, name, value] of strip(read('styles/qing-tokens.wxss')).matchAll(/(--q-[\w-]+)\s*:\s*(#[0-9a-f]{6})/gi)) map[name] = value
  return map
}

function rules(css) {
  const out = []
  const source = strip(css).replace(/@keyframes[^{]+\{(?:[^{}]*\{[^}]*\})*\s*\}/g, '')
  for (const [, selectors, body] of source.matchAll(/([^{}@]+)\{([^{}]*)\}/g)) {
    const decls = {}
    for (const part of body.split(';')) {
      const i = part.indexOf(':')
      if (i > 0) decls[part.slice(0, i).trim()] = part.slice(i + 1).trim()
    }
    for (const selector of selectors.split(',')) out.push({ selector: selector.trim(), decls })
  }
  return out
}

// 把 var(--x) 逐级解析为十六进制；--camp-* / --detail-* 等别名在页面里声明为 var(--q-*)。
function resolver(tokenMap, aliasRules) {
  const alias = {}
  for (const rule of aliasRules) for (const [key, value] of Object.entries(rule.decls)) if (key.startsWith('--') && !key.startsWith('--q-')) alias[key] = value
  const resolve = (value, depth = 0) => {
    if (!value || depth > 5) return null
    const v = value.replace(/!important/, '').trim()
    if (/^#[0-9a-f]{6}$/i.test(v)) return { hex: v, token: null }
    const m = v.match(/^var\((--[\w-]+)(?:\s*,\s*([^)]+))?\)$/)
    if (!m) return null
    if (tokenMap[m[1]]) return { hex: tokenMap[m[1]], token: m[1] }
    if (alias[m[1]]) { const r = resolve(alias[m[1]], depth + 1); return r && { ...r, token: r.token || m[1] } }
    return m[2] ? resolve(m[2], depth + 1) : null
  }
  return resolve
}

function luminance(hex) {
  const [r, g, b] = [1, 3, 5].map(i => parseInt(hex.slice(i, i + 2), 16) / 255).map(c => c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4)
  return 0.2126 * r + 0.7152 * g + 0.0722 * b
}
const ratio = (a, b) => { const [x, y] = [luminance(a), luminance(b)].sort((p, q) => q - p); return (x + 0.05) / (y + 0.05) }

// WXML 中每个静态类名 → 它的祖先类名链（含 {{ }} 里引号内的类名）。
function nesting(wxml) {
  const classOf = attr => {
    if (!attr) return []
    const names = attr.replace(/\{\{([\s\S]*?)\}\}/g, (_, expr) => ' ' + (expr.match(/'([\w -]+)'/g) || []).join(' ').replace(/'/g, '') + ' ')
    return names.split(/\s+/).filter(n => /^[a-z][\w-]*$/i.test(n))
  }
  const chains = {}, stack = []
  for (const token of wxml.match(/<(?:[^<>"']|"[^"]*"|'[^']*')+>/g) || []) {
    if (token.startsWith('</')) { stack.pop(); continue }
    const cls = classOf((token.match(/\sclass="([^"]*)"/) || [])[1])
    const ancestors = stack.flat()
    for (const c of cls) (chains[c] = chains[c] || []).push(ancestors.slice())
    if (!token.endsWith('/>')) stack.push(cls)
  }
  return chains
}

function audit() {
  const tokenMap = tokens()
  const findings = []
  for (const page of PAGES) {
    const css = rules(read(page.wxss)), resolve = resolver(tokenMap, css), chains = nesting(read(page.wxml))
    const pageBg = (css.find(r => r.selector === 'page' && r.decls.background) || {}).decls
    const defaultBg = resolve(pageBg && pageBg.background) || { hex: tokenMap['--q-linen'], token: '--q-linen' }
    // 设了实色背景的类 → 背景色
    const containers = {}
    for (const rule of css) {
      const bg = resolve(rule.decls.background || rule.decls['background-color'])
      const cls = (rule.selector.match(/\.([\w-]+)(?![\w-])(?!.*\.)/) || [])[1]
      if (bg && cls && !/:nth-child|\[/.test(rule.selector)) containers[cls] = bg
    }
    for (const rule of css) {
      const color = rule.decls.color
      if (!color || /inherit|currentColor|transparent/.test(color)) continue
      const fg = resolve(color)
      const subject = (rule.selector.match(/\.([\w-]+)[^ ]*$/) || [])[1]
      if (page.scope && !rule.selector.includes(page.scope)) continue
      if (!fg) { findings.push({ page: page.name, selector: rule.selector, issue: '颜色不是令牌，未判定', fg: color }); continue }
      let bg = resolve(rule.decls.background || rule.decls['background-color'])
      if (!bg) {
        const selectorClasses = [...rule.selector.matchAll(/\.([\w-]+)/g)].map(m => m[1])
        const ancestors = selectorClasses.filter(c => c !== subject)
        const inSelector = ancestors.slice().reverse().find(c => containers[c])
        // 取与选择器祖先类相符的那条 DOM 链，再找其中最近的有底色祖先；找不到才退回选择器里写到的外层类。
        const chain = (chains[subject] || []).find(c => ancestors.every(a => c.includes(a))) || []
        const inDom = chain.slice().reverse().find(c => containers[c])
        bg = (inDom && containers[inDom]) || (inSelector && containers[inSelector]) || defaultBg
      }
      const value = ratio(fg.hex, bg.hex)
      const need = AUX.has(fg.token) ? 4.5 : 7
      const juiceOnLight = fg.token === '--q-juice' && bg.token !== '--q-forest'
      findings.push({ page: page.name, selector: rule.selector, fg: fg.token || fg.hex, bg: bg.token || bg.hex, ratio: Math.round(value * 100) / 100, need,
        pass: value >= need && !juiceOnLight, ...(juiceOnLight ? { issue: '汁绿用在了浅底上' } : {}) })
    }
  }
  return findings
}

const findings = audit()
const failed = findings.filter(f => f.pass === false || f.issue)
const out = process.argv.indexOf('--json')
if (out > 0) fs.writeFileSync(process.argv[out + 1], JSON.stringify(findings, null, 2))
console.log(`对比度：检查 ${findings.length} 条规则，未通过 / 未判定 ${failed.length} 条`)
for (const f of failed) console.log(`✖ ${f.page}  ${f.selector}  ${f.fg} on ${f.bg || '?'}  ${f.ratio || ''}${f.need ? ' < ' + f.need : ''}${f.issue ? '  ' + f.issue : ''}`)
const pairs = [...new Set(findings.filter(f => f.pass).map(f => `${f.fg} on ${f.bg} = ${f.ratio}`))].sort()
console.log('通过的组合：\n  ' + pairs.join('\n  '))
process.exit(failed.length ? 1 : 0)
