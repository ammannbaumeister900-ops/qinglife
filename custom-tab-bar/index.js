Component({
  data: {
    selected: 0,
    list: [
      { pagePath: '/pages/home/index', text: '首页', icon: '⌂' },
      { pagePath: '/pages/camp/index', text: '轻体营', icon: '⌁' },
      { pagePath: '/pages/content/index', text: '内容', icon: '▤' },
      { pagePath: '/pages/friends/index', text: '轻友', icon: '♡' },
      { pagePath: '/pages/mine/index', text: '我的', icon: '○' }
    ]
  },

  methods: {
    switchTab(event) {
      const data = event.currentTarget.dataset
      wx.switchTab({ url: data.path })
    }
  }
})

