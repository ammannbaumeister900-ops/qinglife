// Quill HTML stays the storage contract. Safe rich-text and native video blocks leave this adapter.
const ALLOWED = new Set('p div span br strong b em i u s del strike h1 h2 h3 h4 h5 h6 ul ol li blockquote pre code img a hr sub sup figure figcaption table thead tbody tfoot tr td th caption'.split(' '))
const BLOCKED = new Set('script style iframe object embed form input button select textarea svg math template link meta base video audio source'.split(' '))
const VOID = new Set('br img hr input embed link meta source'.split(' '))
const BODY = 'font-size:17px;line-height:1.95;color:#252b21;overflow-wrap:break-word;word-break:break-word;'
const SERIF = 'font-family:"Songti SC","STSong","SimSun",serif;'
const STYLES = {
  p: 'display:block;margin:0 0 25px;', div: 'display:block;',
  h1: SERIF + 'display:block;font-size:28px;font-weight:400;line-height:1.6;margin:44px 0 18px;',
  h2: SERIF + 'display:block;font-size:25px;font-weight:400;line-height:1.6;margin:44px 0 18px;',
  h3: 'display:block;font-size:21px;font-weight:600;line-height:1.65;margin:32px 0 16px;',
  h4: 'display:block;font-size:19px;font-weight:600;line-height:1.7;margin:28px 0 14px;',
  h5: 'display:block;font-size:17px;font-weight:600;line-height:1.75;margin:24px 0 12px;',
  h6: 'display:block;font-size:16px;font-weight:600;line-height:1.75;margin:24px 0 12px;',
  strong: 'font-weight:600;', b: 'font-weight:600;', em: 'font-style:italic;', i: 'font-style:italic;',
  u: 'text-decoration:underline;', s: 'text-decoration:line-through;', del: 'text-decoration:line-through;', strike: 'text-decoration:line-through;',
  ul: 'display:block;list-style-type:disc;padding-left:22px;margin:18px 0 25px;',
  ol: 'display:block;list-style-type:decimal;padding-left:22px;margin:18px 0 25px;',
  li: 'display:list-item;margin:0 0 9px;',
  blockquote: SERIF + 'display:block;font-size:22px;line-height:1.8;color:#435e3c;margin:34px 0;padding-left:19px;border-left:1px solid #b8c1ad;',
  pre: 'display:block;white-space:pre-wrap;word-break:break-all;font-family:monospace;font-size:14px;line-height:1.8;margin:25px 0;padding:14px;background-color:#ecefe5;',
  code: 'font-family:monospace;font-size:0.9em;',
  img: 'display:block;max-width:100%;width:auto;height:auto;margin:30px auto;',
  a: 'color:#435e3c;text-decoration:underline;word-break:break-all;',
  hr: 'display:block;border:0;border-top:1px solid #c2c8b9;margin:32px 0;',
  sub: 'font-size:0.8em;vertical-align:sub;', sup: 'font-size:0.8em;vertical-align:super;',
  figure: 'display:block;margin:32px 0;', figcaption: 'display:block;font-size:12px;color:#68715f;line-height:1.8;margin-top:10px;',
  table: 'max-width:100%;width:100%;table-layout:fixed;border-collapse:collapse;font-size:14px;line-height:1.8;margin:25px 0;',
  td: 'border:1px solid #c2c8b9;padding:6px;word-break:break-all;', th: 'border:1px solid #c2c8b9;padding:6px;font-weight:600;word-break:break-all;'
}

function decode(value) {
  const entities = { amp: '&', quot: '"', apos: "'", lt: '<', gt: '>', nbsp: '\u00a0', colon: ':', Tab: '\t', NewLine: '\n' }
  return String(value || '').replace(/&(#x[0-9a-f]+|#[0-9]+|amp|quot|apos|lt|gt|nbsp|colon|Tab|NewLine);?/gi, (all, name) => {
    if (name[0] !== '#') return entities[name] === undefined ? all : entities[name]
    const n = name[1].toLowerCase() === 'x' ? parseInt(name.slice(2), 16) : parseInt(name.slice(1), 10)
    return n > 0 && n <= 0x10ffff && !(n >= 0xd800 && n <= 0xdfff) ? String.fromCodePoint(n) : '\ufffd'
  })
}

function safeUrl(value, baseUrl = '') {
  const url = decode(value).trim()
  if (!url || /[\u0000-\u0020\u007f\\]/.test(url)) return ''
  if (/^https?:\/\/[^/?#@]+(?:[/?#]|$)/i.test(url)) return url
  if (/^(?:\/?profile\/)/.test(url) && /^https?:\/\//i.test(baseUrl)) return baseUrl.replace(/\/$/, '') + '/' + url.replace(/^\//, '')
  return ''
}

function attributes(raw) {
  const attrs = {}
  raw.replace(/([^\s=/>]+)(?:\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s"'=<>]+)))?/g, (_, key, a, b, c) => {
    attrs[key.toLowerCase()] = decode(a === undefined ? b === undefined ? c || '' : b : a)
    return ''
  })
  return attrs
}

function parse(html) {
  const root = { children: [] }, stack = [root]
  const tokens = /<!--[\s\S]*?-->|<\/?([a-z][a-z0-9:-]*)\b((?:[^"'<>]|"[^"]*"|'[^']*')*)>/gi
  let cursor = 0, match
  const text = value => { if (value) stack[stack.length - 1].children.push({ type: 'text', text: decode(value) }) }
  while ((match = tokens.exec(html))) {
    text(html.slice(cursor, match.index)); cursor = tokens.lastIndex
    if (match[0].startsWith('<!--')) continue
    const name = match[1].toLowerCase()
    if (match[0][1] === '/') {
      for (let i = stack.length - 1; i > 0; i--) if (stack[i].name === name) { stack.length = i; break }
    } else {
      const node = { name, attrs: attributes(match[2]), children: [] }
      stack[stack.length - 1].children.push(node)
      if (!VOID.has(name) && !/\/>$/.test(match[0]) && stack.length < 65) stack.push(node)
    }
  }
  text(html.slice(cursor))
  return root.children
}

function visible(node) {
  return node.type === 'text' ? !!node.text.replace(/[\s\u00a0\u200b]/g, '') : node.type === 'video' || node.name === 'img' || node.children.some(visible)
}

function arrangeList(node) {
  if (!['ol', 'ul'].includes(node.name)) return
  const source = node.children, parents = [node]; node.children = []
  let previousDepth = 0
  source.forEach(child => {
    if (child.type === 'text' && !child.text.trim()) return
    if (child.name !== 'li') { node.children.push(child); return }
    const requested = Math.min(8, Number(child.indent || 0))
    const depth = Math.min(requested, previousDepth + 1)
    while (parents.length > depth + 1) parents.pop()
    while (parents.length < depth + 1) {
      const parent = parents[parents.length - 1], last = parent.children[parent.children.length - 1]
      if (!last || last.name !== 'li') break
      const list = { name: node.name, attrs: { style: 'display:block;padding-left:16px;margin:9px 0 0;' }, children: [] }
      last.children.push(list); parents.push(list)
    }
    parents[parents.length - 1].children.push(child); previousDepth = parents.length - 1
  })
}

// Lift media out of rich-text while retaining the surrounding paragraph/list/quote structure.
function mediaBlocks(nodes) {
  const blocks = []
  function append(part) {
    const last = blocks[blocks.length - 1]
    if (part.type === 'rich' && last && last.type === 'rich') last.nodes.push(...part.nodes)
    else blocks.push(part)
  }
  function split(node) {
    if (node.type === 'video') return [node]
    if (!node.children || !node.children.length) return [{ type: 'rich', nodes: [node] }]
    const parts = [], start = Number(node.attrs.start || 1)
    let pending = [], itemNumber = start, pendingStart = start, continued = false
    const flush = () => {
      if (!pending.length) return
      const attrs = { ...node.attrs }
      if (node.name === 'ol') attrs.start = String(pendingStart)
      if (node.name === 'li' && continued) attrs.style += 'list-style-type:none;'
      parts.push({ type: 'rich', nodes: [{ ...node, attrs, children: pending }] })
      pending = []; continued = true
    }
    node.children.forEach(child => {
      split(child).forEach(part => {
        if (part.type === 'video') { flush(); parts.push(part) }
        else { if (!pending.length) pendingStart = itemNumber; pending.push(...part.nodes) }
      })
      if (child.name === 'li') itemNumber++
    })
    flush()
    return parts
  }
  nodes.forEach(node => split(node).forEach(append))
  return blocks.map((block, i) => block.type === 'video'
    ? { ...block, id: 'article-video-' + i, failed: !block.src }
    : { ...block, id: 'article-text-' + i, nodes: [{ name: 'div', attrs: { style: BODY }, children: block.nodes }] })
}

function nativeVideo(node, baseUrl) {
  const direct = safeUrl(node.attrs.src, baseUrl)
  const sources = node.children.filter(n => n.name === 'source')
    .filter(n => !n.attrs.type || /^video\//i.test(n.attrs.type))
  sources.sort((a, b) => Number(/^video\/mp4(?:;|$)/i.test(b.attrs.type || '')) - Number(/^video\/mp4(?:;|$)/i.test(a.attrs.type || '')))
  const src = direct || sources.map(n => safeUrl(n.attrs.src, baseUrl)).find(Boolean) || ''
  return { type: 'video', src, poster: safeUrl(node.attrs.poster, baseUrl) }
}

function richText(html, options = {}) {
  const warnings = new Set(), images = []
  function convert(nodes) {
    return nodes.reduce((result, n) => {
      if (n.type === 'text') { result.push(n); return result }
      if (n.name === 'video') { result.push(nativeVideo(n, options.baseUrl)); return result }
      if (BLOCKED.has(n.name)) {
        if (n.name === 'video' || n.name === 'audio' || n.name === 'iframe') warnings.add('本文包含当前阅读页暂不支持的音视频内容。')
        return result
      }
      const children = convert(n.children)
      if (!ALLOWED.has(n.name)) { result.push(...children); return result }
      const attrs = {}, classes = (n.attrs.class || '').split(/\s+/), inline = n.attrs.style || ''
      let style = STYLES[n.name] || ''
      const align = classes.find(c => /^ql-align-(center|right|justify)$/.test(c))
      const alignment = align ? align.slice(9) : (inline.match(/(?:^|;)\s*text-align\s*:\s*(center|right|justify|left)\s*(?:;|$)/i) || [])[1]
      if (alignment) style += 'text-align:' + alignment + ';'
      if (n.name === 'span' && /(?:^|;)\s*color\s*:/i.test(inline)) style += 'color:#435e3c;'
      if (n.name === 'span' && /(?:^|;)\s*background(?:-color)?\s*:/i.test(inline)) style += 'background-color:#e5eadb;'
      if (/(?:^|;)\s*font-weight\s*:\s*(bold|[6-9]00)\s*(?:;|$)/i.test(inline)) style += 'font-weight:600;'
      if (/(?:^|;)\s*font-style\s*:\s*italic\s*(?:;|$)/i.test(inline)) style += 'font-style:italic;'
      const decoration = (inline.match(/(?:^|;)\s*text-decoration(?:-line)?\s*:\s*(underline|line-through)\s*(?:;|$)/i) || [])[1]
      if (decoration) style += 'text-decoration:' + decoration + ';'
      const indent = Number((classes.find(c => /^ql-indent-[1-8]$/.test(c)) || '').slice(10) || 0)
      if (indent && n.name !== 'li') style += 'margin-left:' + indent * 12 + 'px;'
      if (n.name === 'img') {
        const src = safeUrl(n.attrs.src, options.baseUrl)
        if (!src) { warnings.add('部分图片地址不可用。'); return result }
        attrs.src = src; attrs.alt = n.attrs.alt || ''; images.push(src)
      }
      if (n.name === 'a') { const href = safeUrl(n.attrs.href); if (href) attrs.href = href }
      if (['ol', 'td', 'th'].includes(n.name)) for (const key of ['start', 'colspan', 'rowspan']) {
        if (/^\d{1,3}$/.test(n.attrs[key] || '') && Number(n.attrs[key]) > 0) attrs[key] = n.attrs[key]
      }
      // The native allowlist has no figure/figcaption; keep their content in semantic blocks.
      const name = n.name === 'figure' || n.name === 'figcaption' ? 'div' : n.name
      const node = { name, attrs: { ...attrs, style }, children }
      if (n.name === 'li') node.indent = indent
      if (['ol', 'ul'].includes(name)) arrangeList(node)
      if (['p', 'div', 'blockquote'].includes(name) && !visible(node)) return result
      result.push(node); return result
    }, [])
  }
  const children = convert(parse(String(html || '')))
  // Editor Enter creates <p>; Shift+Enter (<br>) is retained inside a nonempty paragraph.
  while (children[0] && children[0].type === 'text' && !children[0].text.trim()) children.shift()
  while (children.length && children[children.length - 1].type === 'text' && !children[children.length - 1].text.trim()) children.pop()
  if (children[0] && children[0].attrs) children[0].attrs.style += 'margin-top:0;'
  const first = children[0]
  const textOf = n => n.type === 'text' ? n.text : (n.children || []).map(textOf).join('')
  if (options.story && first && first.name === 'p' && !images.length && textOf(first).trim().length <= 24) {
    // Never split or duplicate source prose to manufacture a lead or a pull quote.
    first.attrs.style += SERIF + 'font-size:23px;line-height:1.8;margin-bottom:27px;'
  }
  const stripInternal = list => list.forEach(n => { delete n.indent; if (n.children) stripInternal(n.children) })
  stripInternal(children)
  const hasVideo = list => list.some(n => n.type === 'video' || n.children && hasVideo(n.children))
  const blocks = hasVideo(children) ? mediaBlocks(children) : [{ type: 'rich', id: 'article-text-0', nodes: [{ name: 'div', attrs: { style: BODY }, children }] }]
  return { nodes: blocks.filter(b => b.type === 'rich').flatMap(b => b.nodes), blocks, images, warnings: [...warnings] }
}

module.exports = { richText, safeUrl }

