// 改版展示图的 COS 地址，集中维护（图片域名 img.guijianju.cn，对象前缀 qinglife/visual/）。
// 只有已上传、并验证可匿名 HTTPS 加载的对象才填写；空字符串表示继续用本地兜底或不显示。
// 2026-10-19：以下 6 个对象已上传并逐张匿名校验 200 / image/jpeg / 字节数 / SHA256（outputs/cos-upload/upload-result.json）。
// 换图请上传新版本号（-v2…），不要覆盖云端已有对象。清单见 tools/images/cos-stage.cjs 生成的 upload-plan.json。
const BASE = 'https://img.guijianju.cn/qinglife/visual/'
const KEYS = {
  homeHero: 'home/guides-hero-v1.jpg', //（首页首屏 #42）
  homeAbout: 'home/about-guide-dahai-v1.jpg', //（首页「了解轻生活」#43）
  aboutOpening: 'about/opening-guide-gongzhu-v1.jpg', //（认知页开场 #50）
  aboutReturn: 'about/return-guide-junjun-v1.jpg', //（认知页高点 #46）
  aboutJuice: 'about/juice-bottle-v1.jpg', //（认知页饮食说明 #67）
  campHeader: 'camp/header-garden-path-v1.jpg', //（清体营页头 #48）
  storyHuahua: '' // stories/huahua-family-v1.jpg（花花一家，待高清原图）
}
module.exports = Object.keys(KEYS).reduce((urls, name) => ({ ...urls, [name]: KEYS[name] ? BASE + KEYS[name] : '' }), {})
