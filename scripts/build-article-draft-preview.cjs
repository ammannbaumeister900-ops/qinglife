// Build an authenticated admin preview using the current miniapp HTML adapter.
// This produces a static shell only; no article content or credentials are embedded.
const fs = require('node:fs')
const path = require('node:path')
const root = path.resolve(__dirname, '..')
const target = process.argv[2]
if (!target) throw new Error('Pass the output HTML path; this command never deploys automatically.')
const adapter = fs.readFileSync(path.join(root, 'apps/miniapp/utils/article-rich-text.js'), 'utf8')
const template = fs.readFileSync(path.join(root, 'apps/admin/build/article-draft-preview.template.html'), 'utf8')
const wrapper = 'const richText=(()=>{const module={exports:{}};\n' + adapter + '\nreturn module.exports.richText;})();'
const output = path.resolve(target)
fs.mkdirSync(path.dirname(output), { recursive: true })
fs.writeFileSync(output, template.replace('/*__RICH_TEXT_ADAPTER__*/', wrapper), 'utf8')
console.log('Built article draft preview: ' + output)
