const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const vm = require('node:vm')
const { DatabaseSync } = require('node:sqlite')
const source = fs.readFileSync(path.join(__dirname, '../apps/admin/src/views/life/essay/index.vue'), 'utf8')
const script = source.match(/<script>([\s\S]*?)<\/script>/)[1].replace(/^import .*$/gm, '').replace('export default', 'globalThis.component =')
const storyId = '9007199254741001'
const labels = [{ id: storyId, name: '轻友故事', status: 1 }, { id: '8', name: '轻友故事', status: 1 }]
function fixture(api = {}) {
  const calls = []
  const sandbox = { ArticlePlacements: {}, isInteger() {}, getLabelList: async () => labels, getConfigKey: async key => {
    assert.equal(key, 'qinglife.reading.story_label_id'); return { code: 200, msg: storyId, data: null }
  }, ...api }
  vm.runInNewContext(script, sandbox)
  const component = sandbox.component, context = { ...component.data(), ...component.methods,
    resetForm() {}, msgError: message => calls.push(message), $refs: { form: { validate: () => calls.push('validate') } } }
  for (const [key, getter] of Object.entries(component.computed)) Object.defineProperty(context, key, { get: () => getter.call(context) })
  context.reset()
  return { context, calls }
}
function verifyPublicDestinations() {
  const xml = fs.readFileSync(path.join(__dirname, '../server/api/box-system/src/main/resources/mapper/life/QlMiniAppMapper.xml'), 'utf8')
  const fragments = Object.fromEntries([...xml.matchAll(/<sql id="([^"]+)">([\s\S]*?)<\/sql>/g)].map(match => [match[1], match[2]]))
  // Run the mapper's actual filtering/order/limit in memory; only its display projection is omitted.
  fragments.readingColumns = 'e.id'
  const expand = sql => sql.replace(/<include refid="([^"]+)"\s*\/>/g, (_, id) => expand(fragments[id]))
  const query = id => expand(xml.match(new RegExp('<select id="' + id + '"[^>]*>([\\s\\S]*?)<\\/select>'))[1])
  const db = new DatabaseSync(':memory:')
  try {
    db.function('regexp', (pattern, value) => new RegExp(pattern).test(value) ? 1 : 0)
    db.exec(`CREATE TABLE sys_config(config_id INTEGER, config_key TEXT, config_value TEXT);
      CREATE TABLE essay(id INTEGER, status INTEGER, home_featured INTEGER, order_num INTEGER, author INTEGER);
      CREATE TABLE label(id INTEGER, name TEXT, status INTEGER);
      CREATE TABLE essay_label(essay INTEGER, label INTEGER);
      CREATE TABLE sys_user(user_id INTEGER);
      INSERT INTO sys_config VALUES(1,'qinglife.reading.story_label_id','7');
      INSERT INTO label VALUES(7,'轻友故事',1),(8,'轻友故事',1);
      INSERT INTO essay VALUES(1,1,1,2,NULL),(2,1,1,1,NULL),(3,0,1,1,NULL),(4,1,0,1,NULL),
        (5,1,0,1,NULL),(6,1,1,1,NULL),(7,0,1,1,NULL),(8,1,1,2,NULL);
      INSERT INTO essay_label VALUES(1,7),(2,8),(3,7),(4,7),(5,8),(6,7),(6,8),(7,8);`)
    const ids = id => db.prepare(query(id)).all().map(row => row.id)
    assert.deepEqual(ids('selectFeaturedStories'), [6, 1], 'only enabled recommended canonical-tag stories')
    assert.deepEqual(ids('selectFeaturedGeneralReadings'), [2, 6, 1, 8], 'homepage includes all recommended topics including canonical stories')
    db.exec("UPDATE sys_config SET config_value='8'")
    assert.deepEqual(ids('selectFeaturedStories'), [2, 6], 'destination follows configured ID rather than the label display name')
    assert.deepEqual(ids('selectFeaturedGeneralReadings'), [2, 6, 1, 8], 'changing the story tag does not exclude topics from home')
  } finally { db.close() }
}
async function main() {
  verifyPublicDestinations()
  const { context: c } = fixture()
  assert.equal(c.recommendationReady, false)
  await c.loadRecommendationLabels()
  assert.equal(c.recommendationHint, '参与首页精选，不限制题材；按排序优先展示。')
  c.form.labels = [storyId, '8']
  assert.equal(c.recommendationHint, '参与首页精选，同时进入了解轻生活的轻友故事列表；人物全文入口另行审核关联。')
  c.form.homeFeatured = 1
  c.form.labels = ['8']
  assert.equal(c.recommendationHint, '参与首页精选，不限制题材；按排序优先展示。', 'same-name labels must not change canonical membership')
  assert.equal(c.form.homeFeatured, 1, 'changing labels keeps the switch and changes its destination')
  c.form.labels = [7]; c.storyLabelId = '7'
  assert.equal(c.recommendationHint, '参与首页精选，同时进入了解轻生活的轻友故事列表；人物全文入口另行审核关联。', 'numeric label IDs are compatible')

  const legacy = fixture({ getConfigKey: async () => ({ data: storyId }) }).context
  await legacy.loadRecommendationLabels()
  assert.equal(legacy.recommendationReady, true, 'also supports a data payload without mistaking a generic success message for an ID')

  for (const api of [
    { getConfigKey: async () => { throw Error('offline') } },
    { getConfigKey: async () => ({ data: '' }) },
    { getConfigKey: async () => ({ code: 200, msg: '操作成功' }) },
    { getConfigKey: async () => ({ data: 'not-an-id' }) },
    { getConfigKey: async () => ({ data: '999' }) },
    { getLabelList: async () => labels.map(item => ({ ...item, status: 0 })) }
  ]) {
    const { context, calls } = fixture(api)
    await context.loadRecommendationLabels()
    assert.equal(context.recommendationState, 'error')
    assert.equal(context.recommendationReady, false)
    context.form.homeFeatured = 1; context.submitForm()
    assert.equal(calls.length, 1); assert.notEqual(calls[0], 'validate', 'unknown routing blocks a recommended save')
    context.form.homeFeatured = 0; context.submitForm()
    assert.equal(calls.at(-1), 'validate', 'ordinary article editing is not blocked')
  }
  let finish
  let count = 0
  const stale = fixture({ getConfigKey: () => ++count === 1 ? new Promise(resolve => { finish = resolve }) : Promise.resolve({ data: storyId }) }).context
  const first = stale.loadRecommendationLabels()
  stale.reset(); await stale.loadRecommendationLabels()
  finish({ data: 'invalid' }); await first
  assert.equal(stale.recommendationState, 'ready', 'old dialog responses cannot replace the new routing configuration')
  assert(source.includes('label="推荐"'))
  assert(!source.includes('label="首页精选"'))
  assert(source.includes(':disabled="!recommendationReady"'))
  assert(!source.includes('首页轮播'))
  console.log('Article recommendation: tag-dependent destinations, exact IDs, configuration failure, save guard, and stale-response tests passed')
}
main().catch(error => { console.error(error); process.exitCode = 1 })
