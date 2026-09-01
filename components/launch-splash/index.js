Component({
  data: {
    visible: false,
    leaving: false
  },

  lifetimes: {
    attached() {
      const app = getApp()
      if (app.globalData.launchSplashShown) return
      app.globalData.launchSplashShown = true
      this.setData({ visible: true })
      this.leaveTimer = setTimeout(() => this.setData({ leaving: true }), 720)
      this.hideTimer = setTimeout(() => this.setData({ visible: false }), 980)
    },

    detached() {
      clearTimeout(this.leaveTimer)
      clearTimeout(this.hideTimer)
    }
  }
})

