function run(method, options) {
  const originalFail = options.fail
  wx.showLoading({ title: '轻轻打开', mask: false })
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
