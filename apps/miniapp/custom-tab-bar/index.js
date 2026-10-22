const navigation = require('../utils/navigation')

Component({
  data: {
    selected: 0,
    list: [
      { pagePath: '/pages/home/index', text: '首页', iconPath: '/assets/tab/home.svg', selectedIconPath: '/assets/tab/home-active.svg' },
      { pagePath: '/pages/about/index', text: '了解轻生活', iconPath: '/assets/tab/sprout.svg', selectedIconPath: '/assets/tab/sprout-active.svg' },
      { pagePath: '/pages/content/index', text: '轻读', iconPath: '/assets/tab/book.svg', selectedIconPath: '/assets/tab/book-active.svg' },
      { pagePath: '/pages/mine/index', text: '我的', iconPath: '/assets/tab/profile.svg', selectedIconPath: '/assets/tab/profile-active.svg' }
    ]
  },

  methods: {
    switchTab(event) {
      const data = event.currentTarget.dataset
      if (Number(data.index) === this.data.selected) return
      navigation.switchTab({ url: data.path })
    }
  }
})
