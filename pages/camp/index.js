const demo = require('../../data/demo')
const store = require('../../utils/store')
const navigation = require('../../utils/navigation')

Page({
  data: {
    activities: demo.activities,
    leaders: demo.leaders,
    state: {},
    current: null
  },

  onShow() {
    const tab = this.getTabBar && this.getTabBar()
    if (tab) tab.setData({ selected: 1 })
    const state = store.getState()
    this.setData({
      state,
      current: demo.activities.find((item) => item.id === state.selectedActivityId) || demo.activities[0]
    })
  },

  openActivity(event) {
    const id = event.currentTarget.dataset.id
    store.updateState((state) => {
      state.selectedActivityId = id
      return state
    })
    navigation.navigateTo({ url: '/pages/camp-flow/index?view=detail' })
  },

  openCurrent() {
    const status = this.data.state.registration.status
    const view = status === 'none' ? 'detail' : 'journey'
    navigation.navigateTo({ url: `/pages/camp-flow/index?view=${view}` })
  },

  onShareAppMessage() {
    return { title: '三日轻体营｜轻生活', path: '/pages/camp/index' }
  }
})
