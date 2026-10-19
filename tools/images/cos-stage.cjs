#!/usr/bin/env node
/*
 * 把 export.py 导出的展示图整理成 COS 上传目录与上传清单（不联网、不上传）。
 *   node tools/images/cos-stage.cjs [--out DIR]
 * 只收录已确认可公开、且有合格源图的条目；其余列在 held 里并写明原因。
 * 对象键带版本号（-v1），以后换图用新版本号，不覆盖云端已有文件。
 */
const fs = require('fs')
const path = require('path')
const crypto = require('crypto')

const WORKSPACE = path.resolve(__dirname, '../../..')
const EXPORTS = path.join(WORKSPACE, 'outputs', 'qing-images')
const args = process.argv.slice(2)
const OUT = path.resolve(args[args.indexOf('--out') + 1] && args.includes('--out') ? args[args.indexOf('--out') + 1] : path.join(WORKSPACE, 'outputs', 'cos-upload'))
const BUCKET = 'qinglife-assets-1301503747', REGION = 'ap-shanghai', DOMAIN = 'https://img.guijianju.cn/'

// use → { key, media（config/visual-media.js 中的键）, page }；held 写原因的不上传
const PLAN = {
  'home-hero': { key: 'qinglife/visual/home/guides-hero-v1.jpg', media: 'homeHero', page: '首页首屏 · 导游合影 #42' },
  'home-about': { key: 'qinglife/visual/home/about-guide-dahai-v1.jpg', media: 'homeAbout', page: '首页「了解轻生活」· 现场 #43' },
  'about-opening': { key: 'qinglife/visual/about/opening-guide-gongzhu-v1.jpg', media: 'aboutOpening', page: '认知页开场 · 现场 #50' },
  'about-return': { key: 'qinglife/visual/about/return-guide-junjun-v1.jpg', media: 'aboutReturn', page: '认知页高点 · 现场 #46' },
  'about-juice': { key: 'qinglife/visual/about/juice-bottle-v1.jpg', media: 'aboutJuice', page: '认知页饮食说明 · 现场 #67' },
  'camp-header': { key: 'qinglife/visual/camp/header-garden-path-v1.jpg', media: 'campHeader', page: '清体营页头 · 现场 #48' },
  'about-story': { key: 'qinglife/visual/stories/huahua-family-v1.jpg', media: 'storyHuahua', page: '认知页人物故事 · 花花一家（2976×4464 原图导出）' },
  'home-hero-local': { bundled: true }, 'share-about': { bundled: true }
}

const manifest = JSON.parse(fs.readFileSync(path.join(EXPORTS, 'manifest.json'), 'utf8')).images
const upload = [], held = [], bundled = []
for (const image of manifest) {
  const plan = PLAN[image.use]
  if (!plan) { held.push({ use: image.use, reason: '没有上传规划' }); continue }
  if (plan.bundled) { bundled.push({ use: image.use, local: image.local }); continue }
  const reason = plan.held || (!image.releaseReady && '未确认可公开') || (image.overBudget && image.bytes > image.maxBytes * 1.1 && '超出体积上限过多')
  if (reason) { held.push({ use: image.use, key: plan.key, reason }); continue }
  const source = path.join(EXPORTS, image.cdnFilename)
  const data = fs.readFileSync(source)
  if (data[0] !== 0xff || data[1] !== 0xd8) throw new Error(`${image.cdnFilename} 不是 JPEG`)
  const target = path.join(OUT, plan.key)
  fs.mkdirSync(path.dirname(target), { recursive: true })
  fs.copyFileSync(source, target)
  upload.push({ use: image.use, page: plan.page, media: plan.media, key: plan.key, url: DOMAIN + plan.key, source: path.relative(WORKSPACE, source).replace(/\\/g, '/'),
    size: image.size, bytes: data.length, sha256: crypto.createHash('sha256').update(data).digest('hex'), contentType: 'image/jpeg', ...(image.overBudget ? { note: `超出 ${image.maxBytes / 1024}KB 建议上限` } : {}) })
}
fs.mkdirSync(OUT, { recursive: true })
fs.writeFileSync(path.join(OUT, 'upload-plan.json'), JSON.stringify({ bucket: BUCKET, region: REGION, domain: DOMAIN, upload, held, bundled }, null, 2) + '\n')
console.log(`待上传 ${upload.length} 张 → ${path.relative(WORKSPACE, OUT)}`)
for (const u of upload) console.log(`  ${u.key}  ${u.size.join('×')}  ${(u.bytes / 1024).toFixed(1)}KB${u.note ? '  ' + u.note : ''}`)
for (const h of held) console.log(`  暂不上传 ${h.key || h.use}：${h.reason}`)
