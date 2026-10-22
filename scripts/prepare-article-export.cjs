// Offline field adapter only. Canonical HTML/media/idempotency validation stays in ArticleImport.
const fs = require('node:fs'), path = require('node:path')
function prepare(bundle) {
  const issues = [], skipped = [], articles = []
  if (bundle.sourceRelation === 'shared') return { state: 'SHARED_SOURCE_NO_COPY', issues: [], skipped: [], manifest: null }
  if (bundle.sourceRelation !== 'different' || !bundle.sourceEvidenceRef) issues.push({ code: 'SOURCE_RELATION_UNCONFIRMED' })
  if (!bundle.sourceSystem || !bundle.batchId) issues.push({ code: 'MISSING_SOURCE_SYSTEM_OR_BATCH' })
  const rows = bundle.articles && (bundle.articles.rows || bundle.articles)
  const labels = bundle.labels && (bundle.labels.rows || bundle.labels)
  if (!Array.isArray(rows) || !Array.isArray(labels)) return { state: 'BLOCKED', issues: [{ code: 'MISSING_EXPORT_ARRAYS' }], skipped, manifest: null }
  const labelMap = new Map(labels.map(label => [String(label.id), label]))
  if (labels.some(label => typeof label.id === 'number' && !Number.isSafeInteger(label.id))) issues.push({ code: 'UNSAFE_SOURCE_LABEL_ID' })
  const seen = new Set()
  for (const row of rows) {
    const id = row.id == null || typeof row.id === 'number' && !Number.isSafeInteger(row.id) ? '' : String(row.id)
    const reject = code => issues.push({ sourceId: id || null, code })
    if (!id) { reject('MISSING_OR_UNSAFE_SOURCE_ID'); continue }
    if (seen.has(id)) { reject('DUPLICATE_SOURCE_ID'); continue } seen.add(id)
    if (Number(row.status) === 0) { skipped.push({ sourceId: id, code: 'DISABLED_SOURCE_NOT_MIGRATED' }); continue }
    if (Number(row.status) !== 1) { reject('UNKNOWN_SOURCE_STATUS'); continue }
    const detail = (bundle.details || {})[id]
    if (!detail || String(detail.id) !== id || !Array.isArray(detail.labels) || !detail.evidenceRef) { reject('MISSING_AUTHORITATIVE_LABEL_RELATION'); continue }
    const mapped = detail.labels.map(labelId => labelMap.get(String(labelId)))
    if (mapped.some(label => !label)) { reject('UNKNOWN_SOURCE_LABEL_ID'); continue }
    const approval = (bundle.approvalRefs || {})[id], sourceUrl = (bundle.sourceUrls || {})[id]
    if (!approval || !sourceUrl) { reject('MISSING_APPROVAL_OR_SOURCE_URL'); continue }
    if (!row.title || !row.content || !(row.authorName || row.authorNickName) || !row.insertTime) { reject('MISSING_SOURCE_FIELDS'); continue }
    // Source time is preserved verbatim. The earlier JSON/UI eight-hour discrepancy is not guessed away.
    articles.push({ sourceId: id, sourceUrl, title: row.title, introduction: row.Introduction, content: row.content, authorName: row.authorName || row.authorNickName, publishedAt: row.insertTime, timeBasis: 'legacy_insert_time', cover: row.titleUrl || '', status: 1, orderNum: row.orderNum, approvalRef: approval, labels: mapped.map(label => ({ sourceId: String(label.id), name: label.name, status: Number(label.status), level: label.level })), mediaMap: (bundle.mediaMaps || {})[id] || {} })
  }
  if (articles.length > 5) issues.push({ code: 'SPLIT_INTO_BATCHES_OF_AT_MOST_FIVE' })
  return { state: issues.length ? 'BLOCKED' : 'READY_FOR_ISOLATED_DRY_RUN', issues, skipped, manifest: issues.length ? null : { sourceSystem: bundle.sourceSystem, batchId: bundle.batchId, readableInLocalTest: false, articles } }
}
if (require.main === module) {
  const [input, output] = process.argv.slice(2)
  if (!input || !output) throw new Error('Usage: node scripts/prepare-article-export.cjs bundle.json output.json (restricted local files only)')
  const result = prepare(JSON.parse(fs.readFileSync(input, 'utf8').replace(/^\uFEFF/, '')))
  const target = path.resolve(output), local = path.resolve(__dirname, '../.tools') + path.sep
  if (!target.startsWith(local)) throw new Error('Output must stay under the ignored .tools directory')
  const contents = result.manifest ? { ...result.manifest, preparation: { state: result.state, skipped: result.skipped }} : result
  fs.mkdirSync(path.dirname(target), { recursive: true }); fs.writeFileSync(target, JSON.stringify(contents, null, 2), { flag: 'wx', mode: 0o600 })
  console.log(JSON.stringify({ state: result.state, issues: result.issues, skipped: result.skipped.length, prepared: result.manifest ? result.manifest.articles.length : 0 }))
  if (result.state === 'BLOCKED') process.exitCode = 1
}
module.exports = { prepare }
