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
  navigateTo(options) {
    return run('navigateTo', options)
  },

  switchTab(options) {
    return run('switchTab', options)
  }
}
