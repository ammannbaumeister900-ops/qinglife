Component({
  data: {
    selected: 0,
    list: [
      { pagePath: '/pages/home/index', text: '首页', iconPath: '/assets/tab/home.svg', selectedIconPath: '/assets/tab/home-active.svg' },
      { pagePath: '/pages/camp/index', text: '轻体营', iconPath: '/assets/tab/sprout.svg', selectedIconPath: '/assets/tab/sprout-active.svg' },
      { pagePath: '/pages/content/index', text: '内容', iconPath: '/assets/tab/book.svg', selectedIconPath: '/assets/tab/book-active.svg' },
      { pagePath: '/pages/friends/index', text: '轻友', iconPath: '/assets/tab/friends.svg', selectedIconPath: '/assets/tab/friends-active.svg' },
      { pagePath: '/pages/mine/index', text: '我的', iconPath: '/assets/tab/profile.svg', selectedIconPath: '/assets/tab/profile-active.svg' }
    ]
  },

  methods: {
    switchTab(event) {
      const data = event.currentTarget.dataset
      wx.switchTab({ url: data.path })
    }
  }
})
