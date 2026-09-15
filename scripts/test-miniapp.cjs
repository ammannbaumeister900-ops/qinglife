'use strict'
const { spawnSync } = require('node:child_process')
const { readdirSync } = require('node:fs')
const path = require('node:path')
const directory = path.resolve(__dirname, '../apps/miniapp/tests')
for (const file of readdirSync(directory).filter(file => file.endsWith('.test.js')).sort()) {
  const result = spawnSync(process.execPath, [path.join(directory, file)], { stdio: 'inherit', cwd: path.resolve(__dirname, '..') })
  if (result.error) { console.error(result.error); process.exit(1) }
  if (result.status !== 0) process.exit(result.status || 1)
}
