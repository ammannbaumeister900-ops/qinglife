// Data-only hook: keep the current page's approved story, layout and lifecycle handlers.
const business = require('../services/business-api')
const { storyArticle } = require('./about-presentation')
const PEOPLE = ['huahua-family', 'leilei', 'founders']
function withStoryContent(page, api = business) {
  const show = page.onShow, hide = page.onHide, unload = page.onUnload
  const clear = instance => {
    if (instance.data.story) instance.setData({ story: { ...instance.data.story, fullStory: null, articleUrl: '' } })
  }
  page.loadStoryContent = async function() {
    let enabled = false
    try { enabled = api.enabled() } catch (_) { return }
    if (!enabled) return
    const personKey = this.data.story && this.data.story.personKey
    const version = this.storyContentVersion = (this.storyContentVersion || 0) + 1
    clear(this)
    if (!PEOPLE.includes(personKey)) return
    try {
      const result = await api.articlePlacement('about.' + personKey)
      if (version !== this.storyContentVersion || !this.data.story || this.data.story.personKey !== personKey) return
      if (!result || result.contractVersion !== 1 || result.placementKey !== 'about.' + personKey || !result.item || result.item.personKey !== personKey) return
      const story = { ...this.data.story, fullStory: result.item }
      story.articleUrl = storyArticle(story)
      if (story.articleUrl) this.setData({ story })
    } catch (_) { /* No automatic sample, old-ID or alternate-person fallback. Retry on next show. */ }
  }
  page.onShow = function(...args) { const result = show && show.apply(this, args); this.loadStoryContent(); return result }
  page.onHide = function(...args) { this.storyContentVersion = (this.storyContentVersion || 0) + 1; clear(this); return hide && hide.apply(this, args) }
  page.onUnload = function(...args) { this.storyContentVersion = (this.storyContentVersion || 0) + 1; return unload && unload.apply(this, args) }
  return page
}
module.exports = { withStoryContent }
