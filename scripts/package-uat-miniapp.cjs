'use strict'
const fs = require('fs'), path = require('path'), crypto = require('crypto'), cp = require('child_process')
const root = path.resolve(__dirname, '..')
const version = process.argv[2]
if (!version || !/^[a-zA-Z0-9._-]{1,32}$/.test(version)) throw new Error('Usage: node scripts/package-uat-miniapp.cjs <version>')
const output = path.join(root, '.wechat', 'uat-' + version)
if (fs.existsSync(output)) throw new Error('Version directory exists; use a new version to preserve the experience artifact')
const target = path.join(output, 'miniapp')
fs.mkdirSync(output, { recursive: true })
fs.cpSync(path.join(root, 'apps/miniapp'), target, { recursive: true, filter: p => !/(^|[\\/])(tests|test-artifacts|node_modules)([\\/]|$)/.test(p) && !p.endsWith('project.private.config.json') })
const config = JSON.parse(fs.readFileSync(path.join(target, 'project.config.json')))
if (config.appid !== 'wxe21e6518a440d613') throw new Error('Unexpected AppID; stop packaging')
config.description = '轻生活独立多人验收体验版'; config.projectname = 'qinglife-uat-stable'; config.setting.urlCheck = true
fs.writeFileSync(path.join(target, 'project.config.json'), JSON.stringify(config, null, 2) + '\n')
fs.writeFileSync(path.join(target, 'config/runtime.js'), 'module.exports = ' + JSON.stringify({ environment: 'staging', businessBaseUrl: 'https://qinglife.guijianju.cn/uat-20261011-api', allowedBusinessOrigins: [], legacyEnabled: false, legacyBaseUrl: '', legacyWritesEnabled: false }, null, 2) + ';\n')
const collect = dir => fs.readdirSync(dir, { withFileTypes: true }).flatMap(x => x.isDirectory() ? collect(path.join(dir, x.name)) : [path.join(dir, x.name)])
const files = Object.fromEntries(collect(target).map(p => [path.relative(target, p).replaceAll('\\', '/'), crypto.createHash('sha256').update(fs.readFileSync(p)).digest('hex')]))
const manifest = { version, appId: config.appid, api: 'https://qinglife.guijianju.cn/uat-20261011-api', sourceHead: cp.execFileSync('git', ['rev-parse', 'HEAD'], { cwd: root }).toString().trim(), sourceDirty: !!cp.execFileSync('git', ['status', '--porcelain'], { cwd: root }).toString().trim(), files }
fs.writeFileSync(path.join(output, 'manifest.json'), JSON.stringify(manifest, null, 2) + '\n')
console.log(JSON.stringify({ output, project: target, version, sourceDirty: manifest.sourceDirty, files: Object.keys(files).length }))
