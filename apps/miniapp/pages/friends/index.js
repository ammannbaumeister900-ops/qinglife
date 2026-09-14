// 保留旧页面注册，让已有页面链接进入历史内容。
Page({
  onLoad() { wx.redirectTo({ url: '/pages/friend-flow/index?view=archive' }) },
  openArchive() { wx.redirectTo({ url: '/pages/friend-flow/index?view=archive' }) }
})
