const store = require('../../utils/store')
const demo = require('../../data/demo')

const latestRecommendedArticle = demo.articles
  .filter((item) => item.status === 'published' && item.recommended)
  .sort((left, right) => String(right.date).localeCompare(String(left.date)))[0] || null

const stageContent = {
  visitor: {
    date: '今天',
    title: '从一次轻体营，慢慢认识轻生活。',
    copy: '先看看活动和内容，不需要一进入就授权或填写资料。',
    kicker: '认识轻生活',
    task: '看看近期开放的三日轻体营',
    taskCopy: '了解活动价值、流程、时间和参加规则。',
    action: '了解轻体营',
    route: 'camp'
  },
  journey: {
    date: '轻体营进行中',
    title: '今天不用做很多，按自己的节奏来。',
    copy: '当日行程、参与者签到和休息节点都在这里继续。',
    kicker: '第2天 · 感受生机',
    task: '查看今天的行程与签到状态',
    taskCopy: '10:00—17:00 · 上海青浦',
    action: '进入今日行程',
    route: 'journey'
  },
  refeed: {
    date: '复食第3天',
    title: '照顾好今天，就是在慢慢照顾生活。',
    copy: '轻生活不要求你完成更多，只陪你看见身体和此刻真正需要什么。',
    kicker: '今天的一件事',
    task: '今天让饮食保持简单',
    taskCopy: '选择完成、轻量或休息，只记录事实，不给自己打分。',
    action: '记录今天的复食',
    route: 'daily'
  },
  habit: {
    date: '习惯第5天',
    title: '稳定不是连续完成，而是愿意再回来。',
    copy: '可以完成、换成轻量版本，也可以今天先休息。',
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
    if (route === 'camp') return wx.switchTab({ url: '/pages/camp/index' })
    if (route === 'journey') return wx.navigateTo({ url: '/pages/camp-flow/index?view=journey' })
    wx.navigateTo({ url: '/pages/friend-flow/index?view=daily' })
  },

  openLatestArticle() {
    if (!this.data.latestArticle) return
    wx.navigateTo({ url: `/pages/article/index?id=${encodeURIComponent(this.data.latestArticle.id)}` })
  },

  onShareAppMessage() {
    return { title: '轻一点，慢慢来｜轻生活', path: '/pages/home/index' }
  }
})
