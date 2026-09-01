Component({
  data: {
    visible: true,
    leaving: false,
    mode: 'page'
  },

  lifetimes: {
    attached() {
      const app = getApp()
      this.firstLaunch = !app.globalData.launchSplashShown
      app.globalData.launchSplashShown = true
      this.setData({ mode: this.firstLaunch ? 'brand' : 'page' })
    },

    detached() {
      clearTimeout(this.leaveTimer)
      clearTimeout(this.hideTimer)
      clearTimeout(this.nativeHideTimer)
    }
  },

  pageLifetimes: {
    show() {
      const firstLaunch = this.firstLaunch
      this.firstLaunch = false
      clearTimeout(this.leaveTimer)
      clearTimeout(this.hideTimer)
      clearTimeout(this.nativeHideTimer)
      this.setData({
        visible: true,
        leaving: false,
        mode: firstLaunch ? 'brand' : 'page'
      }, () => {
        const platform = wx.getDeviceInfo ? wx.getDeviceInfo().platform : wx.getSystemInfoSync().platform
        this.nativeHideTimer = setTimeout(() => wx.hideLoading(), platform === 'devtools' ? 3000 : 100)
      })
      this.leaveTimer = setTimeout(() => this.setData({ leaving: true }), firstLaunch ? 720 : 620)
      this.hideTimer = setTimeout(() => this.setData({ visible: false }), firstLaunch ? 980 : 880)
    }
  }
})
