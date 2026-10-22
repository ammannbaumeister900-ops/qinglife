#!/usr/bin/env node
/*
 * 视觉改版截图通道：通过 miniprogram-automator 截取单帧和长图。
 *
 *   node tools/capture/capture.cjs --page /pages/home/index --name home-first
 *   node tools/capture/capture.cjs --page /pages/home/index --name home-long --long --fixed-bottom 84
 *
 * 默认连接已开启自动化端口的开发者工具（ws://127.0.0.1:9420）；--launch 则用 CLI 打开
 * 本仓库 apps/miniapp 并开启端口。截图宽度取决于开发者工具模拟器缩放，宽度 < --min-width
 * 时报错退出，提示把模拟器缩放设为 100%。长图由 tools/capture/stitch.py 拼接；
 * --fixed-top / --fixed-bottom 为固定导航栏、Tab 栏的逻辑高度，拼接时只保留一次。
 */
const fs = require('fs')
const path = require('path')
const { execFileSync } = require('child_process')

const REPO = path.resolve(__dirname, '../..')
const DEFAULT_CLI = 'C:/Program Files (x86)/Tencent/微信web开发者工具/cli.bat'

function parseArgs(argv) {
  const options = {
    page: '/pages/home/index',
    name: '',
    out: path.join(REPO, 'test-artifacts/visual-2026q4'),
    ws: 'ws://127.0.0.1:9420',
    launch: false,
    port: 9420,
    cli: process.env.WECHAT_DEVTOOLS_CLI || DEFAULT_CLI,
    project: path.join(REPO, 'apps/miniapp'),
    wait: 3000,
    long: false,
    fixedTop: 0,
    fixedBottom: 0,
    minWidth: 390,
    python: process.env.PYTHON || 'python'
  }
  for (let i = 0; i < argv.length; i += 1) {
    const key = argv[i].replace(/^--/, '')
    if (key === 'long' || key === 'launch') options[key] = true
    else if (key === 'min-width') options.minWidth = Number(argv[++i])
    else if (key === 'fixed-top') options.fixedTop = Number(argv[++i])
    else if (key === 'fixed-bottom') options.fixedBottom = Number(argv[++i])
    else if (key in options) options[key] = typeof options[key] === 'number' ? Number(argv[++i]) : argv[++i]
    else throw new Error(`未知参数 --${key}`)
  }
  options.page = normalizeRoute(options.page)
  if (!options.name) options.name = options.page.replace(/^\/?pages\//, '').replace(/\/index.*$/, '').replace(/[/?=&]/g, '-')
  return options
}

// Git Bash 会把 "/pages/x/index" 改写成 "C:/Program Files/Git/pages/x/index"，reLaunch 随即报 "Uncaught"。
// 这里截取到 pages/ 或 staff/ 开始的部分；也可以直接传不带前导斜杠的 pages/x/index。
function normalizeRoute(value) {
  const match = String(value).replace(/\\/g, '/').match(/(?:^|\/)((?:pages|staff)\/.*)$/)
  if (!match) throw new Error(`无法识别的页面路径：${value}`)
  return '/' + match[1]
}

function loadAutomator() {
  const candidates = [process.env.AUTOMATOR_PATH, 'miniprogram-automator', path.join(REPO, '.tools/automator/node_modules/miniprogram-automator')]
  for (const candidate of candidates.filter(Boolean)) {
    try { return require(candidate) } catch (error) { /* 尝试下一个位置 */ }
  }
  throw new Error('找不到 miniprogram-automator：npm i --no-save miniprogram-automator，或设置 AUTOMATOR_PATH')
}

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms))

function pngSize(buffer) {
  return { width: buffer.readUInt32BE(16), height: buffer.readUInt32BE(20) }
}

// 开发者工具热重载后数秒内 reLaunch 会报 "Uncaught"，等待后重试。
async function relaunch(miniProgram, url, attempts = 4) {
  for (let attempt = 1; ; attempt += 1) {
    try { return await miniProgram.reLaunch(url) } catch (error) {
      if (attempt >= attempts) throw error
      await sleep(3000)
    }
  }
}

async function shoot(miniProgram, file, minWidth) {
  const buffer = Buffer.from(await miniProgram.screenshot(), 'base64')
  const size = pngSize(buffer)
  if (size.width < minWidth) {
    throw new Error(`截图仅 ${size.width}×${size.height}px（要求 ≥${minWidth}px 宽）。请运行 tools/capture/place-window.ps1 把开发者工具移到竖屏并最大化，模拟器缩放设为 100% 后重试。`)
  }
  fs.writeFileSync(file, buffer)
  return size
}

// 从当前滚动位置 0 的首帧开始逐屏滚动截图，再由 stitch.py 拼成长图。其他脚本在设置好展示状态后也可直接调用。
async function captureLong(miniProgram, { out, name, first, viewport, fixedTop = 0, fixedBottom = 0, minWidth = 390, python = process.env.PYTHON || 'python' }) {
  const options = { out, name, fixedTop, fixedBottom, minWidth, python }
  const pageHeight = (await miniProgram.evaluate(() => new Promise((resolve) => {
    wx.createSelectorQuery().selectViewport().scrollOffset().exec((result) => resolve(result[0]))
  }))).scrollHeight
  const frames = [{ file: first, scrollTop: 0 }]
  const step = viewport - options.fixedTop - options.fixedBottom
  const maxScroll = Math.max(0, pageHeight - viewport)
  for (let top = step; frames[frames.length - 1].scrollTop < maxScroll; top += step) {
    const scrollTop = Math.min(top, maxScroll)
    await miniProgram.pageScrollTo(scrollTop)
    await sleep(600)
    const file = path.join(options.out, `${options.name}.frame-${frames.length}.png`)
    await shoot(miniProgram, file, options.minWidth)
    frames.push({ file, scrollTop })
  }
  const spec = path.join(options.out, `${options.name}.frames.json`)
  fs.writeFileSync(spec, JSON.stringify({ viewport, pageHeight, frames }, null, 2))
  const long = path.join(options.out, `${options.name}.long.png`)
  execFileSync(options.python, [path.join(__dirname, 'stitch.py'), spec, long,
    '--top', String(options.fixedTop), '--bottom', String(options.fixedBottom)], { stdio: 'inherit' })
  console.log(`${path.relative(REPO, long)}  ${frames.length} 帧，页面高 ${pageHeight}px`)
}

async function main() {
  const options = parseArgs(process.argv.slice(2))
  const automator = loadAutomator()
  fs.mkdirSync(options.out, { recursive: true })

  const miniProgram = options.launch
    ? await automator.launch({ cliPath: options.cli, projectPath: options.project, port: options.port })
    : await automator.connect({ wsEndpoint: options.ws })
  try {
    const system = await miniProgram.systemInfo()
    await relaunch(miniProgram, options.page)
    await sleep(options.wait)
    const first = path.join(options.out, `${options.name}.png`)
    const size = await shoot(miniProgram, first, options.minWidth)
    console.log(`${path.relative(REPO, first)}  ${size.width}×${size.height}  (${system.model} ${system.windowWidth}×${system.windowHeight})`)
    if (!options.long) return
    await captureLong(miniProgram, { ...options, first, viewport: system.windowHeight })
  } finally {
    await miniProgram.pageScrollTo(0).catch(() => {})
    miniProgram.disconnect()
  }
}

module.exports = { REPO, loadAutomator, relaunch, shoot, sleep, captureLong }

if (require.main === module) main().catch((error) => {
  console.error(`capture 失败：${error && error.message ? error.message : JSON.stringify(error)}`)
  process.exit(1)
})
