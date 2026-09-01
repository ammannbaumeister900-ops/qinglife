const store = require('../../utils/store')
const demo = require('../../data/demo')
const navigation = require('../../utils/navigation')

const latestRecommendedArticle = demo.articles
  .filter((item) => item.status === 'published' && item.recommended)
  .sort((left, right) => String(right.date).localeCompare(String(left.date)))[0] || null

const stageContent = {
  visitor: {
    kicker: '认识轻生活',
    task: '看看近期开放的三日轻体营',
    taskCopy: '了解活动价值、流程、时间和参加规则。',
    action: '了解轻体营',
    route: 'camp'
  },
  journey: {
    kicker: '第2天 · 感受生机',
    task: '查看今天的行程与签到状态',
    taskCopy: '10:00—17:00 · 上海青浦',
    action: '进入今日行程',
    route: 'journey'
  },
  refeed: {
    kicker: '今天的一件事',
    task: '今天让饮食保持简单',
    taskCopy: '选择完成、轻量或休息，只记录事实，不给自己打分。',
    action: '记录今天的复食',
    route: 'daily'
  },
  habit: {
    kicker: '今天的一件事',
    task: '给今天留一句话',
    taskCopy: '约3分钟 · 只选一个此刻的感受也可以。',
    action: '开始今天的轻行动',
    route: 'daily'
  }
}

Page({
  data: {
    state: {},
    view: stageContent.refeed,
    activity: demo.activities[0],
    latestArticle: latestRecommendedArticle,
    todaySaved: false
  },

  onShow() {
    const tab = this.getTabBar && this.getTabBar()
    if (tab) tab.setData({ selected: 0 })
    this.refresh()
  },

  refresh() {
    const state = store.getState()
    const dayKey = state.stage === 'refeed' ? 'refeed-3' : `habit-${state.habit.currentDay}`
    this.setData({
      state,
      view: stageContent[state.stage] || stageContent.visitor,
      activity: demo.activities.find((item) => item.id === state.selectedActivityId) || demo.activities[0],
      todaySaved: !!state.dailyRecords[dayKey]
    })
  },

  openPrimary() {
    const route = this.data.view.route
    if (route === 'camp') return navigation.switchTab({ url: '/pages/camp/index' })
    if (route === 'journey') return navigation.navigateTo({ url: '/pages/camp-flow/index?view=journey' })
    navigation.navigateTo({ url: '/pages/friend-flow/index?view=daily' })
  },

  openLatestArticle() {
    if (!this.data.latestArticle) return
    navigation.navigateTo({ url: `/pages/article/index?id=${encodeURIComponent(this.data.latestArticle.id)}` })
  },

  onShareAppMessage() {
    return { title: '轻一点，慢慢来｜轻生活', path: '/pages/home/index' }
  }
})
