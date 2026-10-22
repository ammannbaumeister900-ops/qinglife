const { test } = require('node:test')
const assert = require('node:assert/strict')
const { richText, safeUrl } = require('../utils/article-rich-text')
const { presentArticle } = require('../utils/article-presentation')
const walk = nodes => nodes.flatMap(n => [n, ...walk(n.children || [])])
const text = nodes => walk(nodes).filter(n => n.type === 'text').map(n => n.text).join('')
const named = (result, name) => walk(result.nodes).filter(n => n.name === name)

test('Quill structure, emphasis and text order survive without editor-specific visual styling', () => {
  const r = richText('<h1>一</h1><h2>二</h2><h3>三</h3><p><strong>加粗</strong><em>斜体</em><u>下划线</u><s>删除</s><span class="ql-size-huge" style="color:red;background:yellow;font-size:100px;position:fixed">强调</span></p><blockquote>引用</blockquote><pre class="ql-syntax">a &lt; b\n换行</pre>')
  assert.equal(text(r.nodes), '一二三加粗斜体下划线删除强调引用a < b\n换行')
  for (const n of ['h1', 'h2', 'h3', 'strong', 'em', 'u', 's', 'blockquote', 'pre']) assert.equal(named(r, n).length, 1)
  const s = named(r, 'span')[0]
  assert.match(s.attrs.style, /color:#435e3c/)
  assert.match(s.attrs.style, /background-color:#e5eadb/)
  assert.doesNotMatch(JSON.stringify(r), /100px|position:fixed|ql-size-huge/)
})
test('paragraph breaks survive, empty editor paragraphs collapse, intentional in-paragraph breaks remain', () => {
  const r = richText('<p><br></p><p>甲<br>乙</p><p>&nbsp;</p><p>　</p><p>丙</p><p><br></p>')
  assert.equal(named(r, 'p').length, 2)
  assert.equal(named(r, 'br').length, 1)
  assert.equal(text(r.nodes), '甲乙丙')
  assert.equal(named(richText('<p><br><br></p>'), 'p').length, 0)
})
test('flat Quill nested lists become structural lists in the original order', () => {
  const r = richText('<ol><li>甲</li><li class="ql-indent-1">乙<strong>强调</strong></li><li class="ql-indent-2">丙</li><li>丁</li></ol><ul><li>戊</li></ul>')
  assert.equal(text(r.nodes), '甲乙强调丙丁戊')
  const lists = named(r, 'ol')
  assert.equal(lists.length, 3)
  assert.equal(lists[0].children.length, 2)
  assert.equal(lists[0].children[0].children[1].name, 'ol')
  assert.equal(named(r, 'ul').length, 1)
  assert.doesNotMatch(JSON.stringify(r), /"indent"/)
})
test('alignment and non-list indentation map only known Quill expressions', () => {
  const r = richText('<p class="ql-align-center ql-indent-2">居中</p><p style="text-align:right; margin-top:999px">右</p>')
  const p = named(r, 'p')
  assert.match(p[0].attrs.style, /text-align:center/)
  assert.match(p[0].attrs.style, /margin-left:24px/)
  assert.match(p[1].attrs.style, /text-align:right/)
  assert.doesNotMatch(JSON.stringify(r), /999px/)
})
test('images retain source order and natural sizing; figure content is not dropped by native allowlist', () => {
  const r = richText('<p>之前</p><figure><img src="https://test.invalid/a.jpg" width="900" height="20" onerror="bad()"><figcaption>来源</figcaption></figure><p>之后<img src="https://test.invalid/b.jpg"></p>')
  assert.deepEqual(r.images, ['https://test.invalid/a.jpg','https://test.invalid/b.jpg'])
  assert.equal(text(r.nodes), '之前来源之后')
  assert.equal(named(r, 'figure').length, 0)
  for (const n of named(r, 'img')) { assert.match(n.attrs.style, /height:auto/); assert.equal(n.attrs.width, undefined); assert.equal(n.attrs.onerror, undefined) }
})
test('active HTML and untrusted attributes never reach the native renderer', () => {
  const r = richText('<script>alert(1)</script><style>p{display:none}</style><iframe src="https://bad.invalid">隐藏</iframe><svg><a href="javascript:bad">X</a></svg><p onclick="x()" style="background:url(javascript:bad);position:fixed">原文</p><a href="java&#x73;cript&colon;bad()">链接</a>')
  assert.equal(text(r.nodes), '原文链接')
  assert.doesNotMatch(JSON.stringify(r.nodes), /javascript|onclick|script|iframe|background:url|position:fixed/)
  assert.equal(named(r, 'a')[0].attrs.href, undefined)
  assert.equal(r.warnings.length, 1)
})
test('only explicit safe network URLs and same API profile paths are accepted', () => {
  for (const bad of ['javascript:bad','java\nscript:bad','&#106;avascript:bad','data:image/svg+xml,bad','//bad.invalid/x','https://user@bad.invalid/x','https://test.invalid\\x','file:///a','wxfile://a','https://bad.invalid/\u0000x']) assert.equal(safeUrl(bad), '')
  assert.equal(safeUrl('https://safe.invalid/a?x=1&amp;y=2'), 'https://safe.invalid/a?x=1&y=2')
  assert.equal(safeUrl('/profile/public/a.jpg','https://api.invalid/stage'), 'https://api.invalid/stage/profile/public/a.jpg')
})
test('video becomes a native block; unrelated tags preserve their text', () => {
  const r = richText('<custom>保留<mark>文字</mark></custom><video src="https://test.invalid/v.mp4"></video>')
  assert.equal(text(r.nodes),'保留文字')
  assert.equal(named(r, 'video').length, 0)
  assert.equal(r.blocks[1].type, 'video')
  assert.equal(r.blocks[1].src, 'https://test.invalid/v.mp4')
  assert.equal(r.warnings.length, 0)
})
test('short story first paragraph can lead, without splitting, repeating, or forcing sections', () => {
  const raw = '<p>原文短句。</p><p>第二段。</p>'
  const story = richText(raw, { story:true }), method = richText(raw)
  assert.equal(text(story.nodes), text(method.nodes))
  assert.match(named(story, 'p')[0].attrs.style,/font-size:23px/)
  assert.doesNotMatch(named(method, 'p')[0].attrs.style,/23px/)
  assert.equal(named(story,'h2').length,0)
  assert.doesNotMatch(named(richText('<p>'+'长文'.repeat(40)+'</p>', {story:true}), 'p')[0].attrs.style,/23px/)
})
test('cover duplication suppresses only the extra hero, preserving all body images and paragraphs', () => {
  const a = { isStory:true, cover:'https://test.invalid/cover.jpg', content:'<p>原文</p><img src="https://test.invalid/cover.jpg"><img src="https://test.invalid/next.jpg">' }
  const p = presentArticle(a)
  assert.equal(p.cover,'')
  assert.equal(named(p,'img').length,2)
  assert.equal(a.content.includes('<img'),true)
  assert.equal(p.showHealthNote,false)
})
test('stable story flag wins over renamed or misleading labels; older detail responses retain compatibility', () => {
  assert.equal(presentArticle({isStory:1,category:'已经改名'}).isStory,true)
  assert.equal(presentArticle({isStory:false,category:'轻友故事'}).isStory,false)
  assert.equal(presentArticle({category:'日常 · 轻友故事'}).isStory,true)
  assert.equal(presentArticle({title:'轻友故事',category:'方法'}).isStory,false)
  assert.equal(presentArticle({category:'方法'}).showHealthNote,true)
})
test('missing cover, summary fallback, original byline and dates require no fictional fields', () => {
  const a={id:'1',title:'真实标题',author:'真实作者',publishedAt:'2026-10-02T07:20:46',summary:'仅有简介',isStory:true}
  const p=presentArticle(a)
  assert.equal(p.cover,'');assert.equal(p.dateLabel,'2026.10.02')
  assert.equal(text(p.nodes),'仅有简介');assert.equal(a.author,'真实作者')
  assert.equal(presentArticle({date:'日期待补充'}).dateLabel,'日期待补充')
})
test('large content is not truncated and mixed normal inline nodes retain exact text', () => {
  const body = Array.from({length:200},(_,i)=>'<p>段落'+i+'：'+ '正文'.repeat(40)+'<br>普通换行。</p>').join('')
  const r=richText(body)
  assert.equal(named(r,'p').length,200)
  assert.equal(named(r,'br').length,200)
  assert.ok(text(r.nodes).endsWith('普通换行。'))
})


test('mixed media preserves exact order, nested quotes, lists and safe MP4 source selection', () => {
  const r = richText('<h2>前言</h2><blockquote><p>引用之前</p><video poster="/profile/public/p.jpg"><source src="javascript:bad" type="video/mp4"><source src="https://test.invalid/a.webm" type="video/webm"><source src="https://test.invalid/b.mp4" type="video/mp4"></video><p>引用之后</p></blockquote><ol start="3"><li>甲</li><li>乙<video src="https://test.invalid/c.mp4"></video>续乙</li><li>丙</li></ol><p>结束<img src="https://test.invalid/end.jpg"></p>', { baseUrl: 'https://api.invalid' })
  assert.deepEqual(r.blocks.map(b => b.type), ['rich', 'video', 'rich', 'video', 'rich'])
  assert.deepEqual(r.blocks.filter(b => b.type === 'rich').map(b => text(b.nodes)), ['前言引用之前', '引用之后甲乙', '续乙丙结束'])
  assert.equal(r.blocks[1].src, 'https://test.invalid/b.mp4')
  assert.equal(r.blocks[1].poster, 'https://api.invalid/profile/public/p.jpg')
  assert.equal(named({ nodes: r.blocks[2].nodes }, 'blockquote').length, 1)
  const last = { nodes: r.blocks[4].nodes }
  assert.equal(named(last, 'ol')[0].attrs.start, '4')
  assert.match(named(last, 'li')[0].attrs.style, /list-style-type:none/)
  assert.deepEqual(r.images, ['https://test.invalid/end.jpg'])
})

test('invalid videos fail visibly without executable attributes or autoplay; blocked containers cannot leak media', () => {
  const r = richText('<script><video src="https://bad.invalid/a.mp4"></video></script><iframe><video src="https://bad.invalid/b.mp4"></video></iframe><video src="javascript:bad" poster="data:bad" autoplay onplay="bad()"></video><p>仍可阅读</p><audio src="https://test.invalid/a.mp3"></audio>')
  const video = r.blocks.filter(b => b.type === 'video')
  assert.equal(video.length, 1); assert.equal(video[0].src, ''); assert.equal(video[0].failed, true)
  assert.equal(video[0].poster, ''); assert.equal(text(r.nodes), '仍可阅读')
  assert.doesNotMatch(JSON.stringify(r), /javascript|autoplay|onplay|bad.invalid/)
  assert.match(r.warnings[0], /暂不支持/)
})

test('text-only articles use precisely the same normalized tree in the sole rich block', () => {
  const r = richText('<p>原文<strong>完整</strong></p><ul><li>清单</li></ul><blockquote>引用</blockquote>')
  assert.equal(r.blocks.length, 1); assert.deepEqual(r.blocks[0].nodes, r.nodes)
})
