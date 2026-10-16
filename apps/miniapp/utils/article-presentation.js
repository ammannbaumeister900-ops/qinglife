const { richText, safeUrl } = require('./article-rich-text')
function isStory(article) {
  if (article.isStory !== undefined && article.isStory !== null) return article.isStory === true || article.isStory === 1 || article.isStory === '1'
  // Compatibility for older detail/legacy responses without the stable server flag.
  return String(article.category || '').split(/\s*(?:·|&|,|，)\s*/).includes('轻友故事')
}
function presentArticle(article = {}, baseUrl = '') {
  const story = isStory(article)
  const content = richText(article.content || article.summary || '', { story, baseUrl })
  const cover = safeUrl(article.cover, baseUrl)
  const date = String(article.date || article.publishedAt || '')
  return {
    isStory: story, nodes: content.nodes, blocks: content.blocks, hasVideo: content.blocks.some(block => block.type === 'video'), warnings: content.warnings,
    cover: story && cover && !content.images.includes(cover) ? cover : '',
    dateLabel: /^\d{4}-\d{2}-\d{2}/.test(date) ? date.slice(0, 10).replace(/-/g, '.') : date,
    // Keep the existing note for other reading types; never infer medical relevance from prose.
    showHealthNote: !story
  }
}
module.exports = { presentArticle, isStory }

