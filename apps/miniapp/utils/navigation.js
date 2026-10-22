let readingFilter = null
function run(method, options) {
  const originalFail = options.fail
  // 旧社区路径仍可被历史入口调用，统一转向只读归档。
  if (options.url && options.url.split('?')[0] === '/pages/friends/index') {
    return wx.navigateTo({ url: '/pages/friend-flow/index?view=archive' })
  }
  return wx[method](Object.assign({}, options, {
    fail(error) {
      wx.hideLoading()
      if (originalFail) originalFail(error)
    }
  }))
}

module.exports = {
  // Tab pages cannot receive query parameters; consume this intent once on show.
  openReadingList(stories) {
    readingFilter = !!stories
    return run('switchTab', { url: '/pages/content/index', fail() { readingFilter = null } })
  },
  consumeReadingFilter() {
    const filter = readingFilter
    readingFilter = null
    return filter
  },
  navigateTo(options) {
    return run('navigateTo', options)
  },

  redirectTo(options) {
    return run('redirectTo', options)
  },

  // Return to a non-tab page without stacking it again: go back when it is the previous page, otherwise replace the current one.
  returnTo(options) {
    const pages = typeof getCurrentPages === 'function' ? getCurrentPages() : []
    const previous = pages[pages.length - 2]
    if (previous && '/' + previous.route === options.url.split('?')[0]) return wx.navigateBack()
    return run('redirectTo', options)
  },

  switchTab(options) {
    return run('switchTab', options)
  }
}
