const { test } = require('node:test')
const assert = require('node:assert/strict')
const media = require('../config/visual-media')

test('visual image URLs are either unset or permanent HTTPS objects under img.guijianju.cn/qinglife/visual', () => {
  for (const [name, url] of Object.entries(media)) {
    if (!url) continue
    assert.match(url, /^https:\/\/img\.guijianju\.cn\/qinglife\/visual\/(home|about|camp|stories)\/[a-z0-9-]+-v\d+\.jpg$/, name)
    assert(!/[?#]|q-sign|sign=|x-cos-security-token/i.test(url), name + ' must not be a temporary signed link')
  }
})

test('pages read image URLs only through config/visual-media', () => {
  const fs = require('node:fs'), path = require('node:path'), root = path.resolve(__dirname, '..')
  const files = ['config/home-brand.js', 'config/about-content.js', 'config/camp-brand.js']
  for (const file of files) {
    const source = fs.readFileSync(path.join(root, file), 'utf8')
    assert(source.includes("require('./visual-media')"), file)
    assert(!/img\.guijianju\.cn|myqcloud\.com/.test(source), file + ' should not hard-code image hosts')
  }
  const brand = require('../config/home-brand'), about = require('../config/about-content'), camp = require('../config/camp-brand')
  assert.equal(brand.heroCdnUrl, media.homeHero)
  assert.equal(brand.aboutPhoto.url, media.homeAbout)
  assert.equal(camp.header.url, media.campHeader)
  const url = id => about.assets.find(asset => asset.id === id).url
  assert.deepEqual([url('scene-50'), url('scene-46'), url('scene-67'), url('family-huahua')], [media.aboutOpening, media.aboutReturn, media.aboutJuice, media.storyHuahua])
})
