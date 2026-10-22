const text = value => value == null ? '' : String(value).trim()
function ready(item) {
  return !!item && item.state === 'releaseReady' && item.authorization === 'confirmed' && item.editorialReview === 'approved' && ['confirmed', 'user-confirmed-brief'].includes(item.factCheck) && !!item.source
}
function photo(assets, id, requireIdentity = false) {
  const item = (assets || []).find(asset => asset.id === id)
  if (!ready(item) || (requireIdentity && item.identityCheck !== 'confirmed')) return null
  if (!/^https:\/\/[^/]+\//.test(item.url || '') && !/^\/assets\/brand\/[\w.-]+\.(jpg|jpeg|png|webp)$/i.test(item.url || '')) return null
  return { id: item.id, url: item.url, caption: text(item.caption), alt: text(item.alt) }
}
function storyArticle(story) {
  const full = story && story.fullStory
  if (!ready(story) || story.identityCheck !== 'confirmed' || !ready(full) || full.publicVerified !== true || full.personKey !== story.personKey || !text(full.evidenceRef)) return ''
  const id = text(full.businessArticleId)
  if (!/^[1-9]\d*$/.test(id)) return ''
  return '/pages/article/index?id=' + encodeURIComponent(id) + '&source=business&title=' + encodeURIComponent(text(full.title))
}
function presentAbout(content) {
  const founders = ready(content.founders) ? { ...content.founders,
    peopleLabel: (content.founders.people || []).map(person => person.name + (person.nickname ? '（' + person.nickname + '）' : '')).join(' · '),
    photo: photo(content.assets, content.founders.earlyPhotoAssetId, true) } : null
  const item = content.storySlot && content.storySlot.item
  const story = ready(item) && item.identityCheck === 'confirmed' ? { ...item, heading: content.storySlot.heading,
    label: text(item.name) + '的故事', historyText: (item.history || []).join('\n'),
    portrait: photo(content.assets, item.portraitAssetId, true), articleUrl: storyArticle(item) } : null
  const history = content.historicalEvidence || {}, report = history.report || {}
  const valid = history.state === 'releaseReady' && history.factCheck === 'user-confirmed-brief'
  const numbers = ['campCount', 'participantOccurrences', 'returningOccurrences'].every(key => Number.isInteger(report[key]) && report[key] >= 0)
  const stats = valid && report.state === 'releaseReady' && report.factCheck === 'user-confirmed-brief' && numbers && report.participantOccurrences > 0 && report.returningOccurrences <= report.participantOccurrences ? {
    ...report, percent: (report.returningOccurrences / report.participantOccurrences * 100).toFixed(1)
  } : null
  // Display-only fields below; every photo still passes the same readiness gate. People photos also need confirmed identity.
  const camps = valid && /^(\d+)(.*)$/.exec(text(history.campsLabel))
  return { entry: { ...content.entry, photo: photo(content.assets, content.entry.photoAssetId, true) }, founders, everyday: content.everyday,
    // The #46 scene must never sit next to an approved person's story, or readers would take the guide's client for that person.
    story, returnHeading: story ? story.heading : content.storySlot && content.storySlot.heading, storyScene: story ? null : photo(content.assets, content.storySlot && content.storySlot.photoAssetId, true),
    history: valid ? { ...history, campsNumber: camps ? camps[1] : '', campsUnit: camps ? camps[2] : '' } : null, stats,
    transparency: { ...content.transparency, photo: photo(content.assets, content.transparency.photoAssetId) }, share: content.share }
}
module.exports = { presentAbout, storyArticle }
