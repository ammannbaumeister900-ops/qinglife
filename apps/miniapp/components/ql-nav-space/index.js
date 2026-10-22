Component({
  properties: {
    title: { type: String, value: '' },
    back: { type: Boolean, value: false },
    brand: { type: Boolean, value: false },
    immersive: { type: Boolean, value: false }
  },

  data: {
    totalHeight: 88,
    spaceStyle: 'height: 88px;',
    barStyle: 'height: 88px;',
    controlStyle: 'top: 48px; height: 32px; line-height: 32px;',
    titleStyle: 'left: 112px; right: 112px;'
  },

  observers: {
    immersive(value) {
      this.setData({ spaceStyle: `height: ${value ? 0 : this.data.totalHeight}px;` })
    }
  },

  lifetimes: {
    attached() {
      const windowInfo = wx.getWindowInfo ? wx.getWindowInfo() : wx.getSystemInfoSync()
      const statusBarHeight = Number(windowInfo.statusBarHeight || 24)
      let menu = null
      try {
        menu = wx.getMenuButtonBoundingClientRect && wx.getMenuButtonBoundingClientRect()
      } catch (error) {
        menu = null
      }
      const menuTop = menu && menu.top ? menu.top : statusBarHeight + 6
      const menuHeight = menu && menu.height ? menu.height : 32
      const totalHeight = menuTop + menuHeight + 8
      const windowWidth = Number(windowInfo.windowWidth || windowInfo.screenWidth || 375)
      // Reserve the capsule width on both sides so the title stays centered.
      const titleInset = menu && menu.left > 0 && menu.left < windowWidth
        ? Math.max(56, windowWidth - menu.left + 12) : 112
      this.setData({
        totalHeight,
        spaceStyle: `height: ${this.data.immersive ? 0 : totalHeight}px;`,
        barStyle: `height: ${totalHeight}px;`,
        controlStyle: `top: ${menuTop}px; height: ${menuHeight}px; line-height: ${menuHeight}px;`,
        titleStyle: `left: ${titleInset}px; right: ${titleInset}px;`
      })
    }
  },

  methods: {
    goBack() {
      const pages = getCurrentPages()
      if (pages.length > 1) return wx.navigateBack()
      wx.switchTab({ url: '/pages/home/index' })
    }
  }
})
