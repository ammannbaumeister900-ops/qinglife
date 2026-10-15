/*
 * 轻生活 · 可读性自动检查（HTML 原型用）
 * 在浏览器里对 375px 原型执行：qlReadabilityAudit('#root')，返回 JSON 摘要。
 * 只检查可量化部分：字号、对比度、可点区域、字号种类。品牌、审美与叙事仍需人工判断。
 * 阈值与 SKILL.md §2 一致：叙事正文 16 / 功能正文 15 / 关键事实 14 / 注记 12；正文 7:1，辅助 4.5:1；可点 44px。
 */
function qlReadabilityAudit(rootSelector, options) {
  const opts = Object.assign({ longText: 40, minAny: 12, minFact: 14, minBody: 16, tap: 44 }, options || {})
  const root = rootSelector ? document.querySelector(rootSelector) : document.body
  if (!root) return { error: 'root not found: ' + rootSelector }

  const parse = c => (c.match(/[\d.]+/g) || []).map(Number)
  const lum = ([r, g, b]) => {
    const f = v => { v /= 255; return v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4) }
    return 0.2126 * f(r) + 0.7152 * f(g) + 0.0722 * f(b)
  }
  const ratio = (a, b) => { const x = lum(a), y = lum(b); return (Math.max(x, y) + 0.05) / (Math.min(x, y) + 0.05) }
  const background = el => {
    for (let n = el; n; n = n.parentElement) {
      const bg = parse(getComputedStyle(n).backgroundColor)
      if (bg.length >= 3 && (bg.length < 4 || bg[3] > 0.5)) return bg.slice(0, 3)
      if (getComputedStyle(n).backgroundImage !== 'none' && n !== el) return null // 文字压在图片上：需人工判断
    }
    return [255, 255, 255]
  }
  const visible = el => { const r = el.getBoundingClientRect(); const s = getComputedStyle(el); return r.width > 0 && r.height > 0 && s.visibility !== 'hidden' && s.display !== 'none' && Number(s.opacity) > 0.1 }
  const ownText = el => [...el.childNodes].filter(n => n.nodeType === 3).map(n => n.textContent).join('').replace(/\s+/g, '')

  const sizes = {}
  const small = [], bodySmall = [], lowContrast = [], overImage = []
  root.querySelectorAll('*').forEach(el => {
    const text = ownText(el)
    if (!text || !visible(el) || el.closest('[data-audit-ignore]')) return
    const s = getComputedStyle(el)
    const size = parseFloat(s.fontSize)
    sizes[size] = (sizes[size] || 0) + 1
    const sample = text.slice(0, 24)
    const role = el.closest('[data-role]') ? el.closest('[data-role]').dataset.role : ''
    if (size < opts.minAny || (size < opts.minFact && role !== 'note')) small.push({ size, role, text: sample })
    if (text.length >= opts.longText && size < opts.minBody) bodySmall.push({ size, chars: text.length, text: sample })
    const bg = background(el)
    if (!bg) { overImage.push(sample); return }
    const fg = parse(s.color)
    const cr = ratio(fg.slice(0, 3), bg)
    const need = text.length >= opts.longText ? 7 : 4.5
    if (cr < need) lowContrast.push({ ratio: Math.round(cr * 100) / 100, need, size, text: sample })
  })

  const taps = []
  root.querySelectorAll('a, button, [role=button], [data-tap]').forEach(el => {
    if (!visible(el) || el.closest('[data-audit-ignore]')) return
    const r = el.getBoundingClientRect()
    if (r.height < opts.tap || r.width < opts.tap) taps.push({ h: Math.round(r.height), w: Math.round(r.width), text: (el.innerText || el.getAttribute('aria-label') || '').trim().slice(0, 20) })
  })

  const distinct = Object.keys(sizes).map(Number).sort((a, b) => a - b)
  return {
    fontSizes: distinct,
    fontSizeCount: distinct.length,
    minFontSize: distinct[0],
    belowFactFloor: small,
    longTextBelowBodyFloor: bodySmall,
    lowContrast,
    textOverImage: overImage,
    smallTapTargets: taps,
    verdict: (small.length || bodySmall.length || lowContrast.length || taps.length || distinct.length > 6) ? 'review' : 'pass'
  }
}
if (typeof module !== 'undefined') module.exports = qlReadabilityAudit
