const legacyApi = require('./services/legacy-api')
const store = require('./utils/store')

App({
  globalData: {
    legacyReady: false,
    launchSplashShown: false
  },

  onLaunch() {
    store.ensureState()
    legacyApi.ensureToken()
      .then(() => { this.globalData.legacyReady = true })
      .catch(() => { this.globalData.legacyReady = false })
  }
})
