const business = require('../../services/business-api')
const store = require('../../utils/store')
const demo = require('../../data/demo')
const domain = require('../../utils/domain')
const discovery = require('../../utils/discovery')
const navigation = require('../../utils/navigation')
const readingSamples = require('../../data/reading-samples')
const brand = require('../../config/home-brand')
const registrationHistory = require('../../utils/registration-history')
const content = require('../../config/brand-content')
const presentation = require('../../utils/home-presentation')
function showHomeTask(state, view) {
  const reg = (state.registrations || {})[view.activityId] || state.registration || {}
  return !!state.loggedIn && !!view.activityId && ['pending', 'waitlist', 'waitlisted', 'rescheduled'].includes(reg.status)
}
function taskContext(view, activities) {
  const activity = activities.find(item => item.id === view.activityId)
  return activity && activity.sessionNumber ? '第 ' + activity.sessionNumber + ' 期 · 你的参与' : '你的轻生活'
}
Page({
  data: { view: {}, state: {}, featuredReadings: [], stories: [], storyIndex: 0, visible: false, readingError: '', brand, failedImages: {}, taskContext: '', cancellationNotice: null, camps: [], selectedCampId: '', voiceGroups: [], voiceCards: [], voiceGroup: 0, voicePaused: false, reducedMotion: false, expandedVoice: null, fontScale: 1, discoverTitle: content.discoverTitle },
  async onShow() {
    const version = this.requestVersion = (this.requestVersion || 0) + 1
    this.stopVoiceTimer()
    const groups = presentation.voiceGroups(content.voices)
    const group = Math.min(this.data.voiceGroup, Math.max(0, groups.length - 1))
    this.setData({ visible: true, voiceGroups: groups, voiceGroup: group, voiceCards: groups[group] || [] })
    this.measurePreferences()
    const tab = this.getTabBar && this.getTabBar()
    if (tab) tab.setData({ selected: 0 })
    if (business.enabled()) {
      this.setData({ loading: !this.data.ready, refreshing: !!this.data.ready, loadError: '' })
      try {
        const [{ state, activities }, readings] = await Promise.all([business.context(), business.homeReadings().catch(() => null)])
        if (version !== this.requestVersion) return
        const view = domain.homeTask(state, activities)
        const cancellationNotice = registrationHistory.cancellation(state, activities, business.baseUrl ? business.baseUrl() : '')
        this.setData({ showHomeTask: showHomeTask(state, view), cancellationNotice, state, stories: readings && readings.stories || [], featuredReadings: readings && readings.featured || [],
          storyIndex: 0, readingError: readings ? '' : '轻读暂时未能读取，请稍后重试。', ready: true,
          returning: discovery.returning(state), ...presentation.camps(activities, this.data.selectedCampId, content.gatheringTimes),
          view, taskContext: taskContext(view, activities), failedImages: {} })
      } catch (error) {
        if (version !== this.requestVersion) return
        this.setData({ loadError: error.message || '首页暂时无法读取，请重试', ...(error.code === 401 ? { ready: false, state: {}, cancellationNotice: null, returning: false, showHomeTask: false } : {}) })
      } finally {
        if (version === this.requestVersion) this.setData({ loading: false, refreshing: false })
      }
      return
    }
    const state = store.getState()
    const view = domain.homeTask(state, demo.activities)
    this.setData({ loading: false, refreshing: false, loadError: '', showHomeTask: showHomeTask(state, view), state, stories: [], storyIndex: 0, readingError: '', featuredReadings: readingSamples.slice(0, 4), ready: true,
      returning: discovery.returning(state), ...presentation.camps(demo.activities, this.data.selectedCampId, content.gatheringTimes),
      view, taskContext: taskContext(view, demo.activities), failedImages: {} })
  },
  onReady() { this.measurePreferences() },
  dismissCancellation() {
    const notice = this.data.cancellationNotice
    if (!notice) return
    store.updateState(state => {
      state.dismissedRegistrationCancellations = { ...state.dismissedRegistrationCancellations, [notice.key]: true }
      return state
    })
    this.setData({ cancellationNotice: null })
  },
  openCancellation() {
    const notice = this.data.cancellationNotice
    if (notice) navigation.navigateTo({ url: '/pages/camp-flow/index?view=journey&id=' + encodeURIComponent(notice.activityId) + '&registrationId=' + encodeURIComponent(notice.registrationId) })
  },
  onHide() {
    this.requestVersion = (this.requestVersion || 0) + 1
    this.stopVoiceTimer()
    this.setData({ visible: false, expandedVoice: null })
  },
  onUnload() { this.onHide() },
  measurePreferences() {
    if (typeof wx === 'undefined') return
    const info = wx.getSystemInfoSync ? wx.getSystemInfoSync() : {}
    this.setData({ fontScale: Math.max(1, Math.min(1.4, (Number(info.fontSizeSetting) || 16) / 16)) })
    // WXSS media preference is measured locally; no unsupported native setting is assumed.
    if (wx.createSelectorQuery) {
      const version = this.requestVersion
      wx.createSelectorQuery().select('.home-motion-probe').boundingClientRect(rect => {
        if (!this.data.visible || version !== this.requestVersion || !rect) return
        this.setData({ reducedMotion: rect.width > 0 })
        this.syncVoiceTimer()
      }).exec()
    }
    this.syncVoiceTimer()
  },
  stopVoiceTimer() {
    if (this.voiceTimer != null && typeof clearInterval === 'function') clearInterval(this.voiceTimer)
    this.voiceTimer = null
  },
  syncVoiceTimer() {
    this.stopVoiceTimer()
    if (!this.data.visible || this.data.voicePaused || this.data.reducedMotion || this.data.expandedVoice || this.data.voiceGroups.length < 2 || typeof setInterval !== 'function') return
    this.voiceTimer = setInterval(() => this.changeVoiceGroup(1), content.voiceRotationMs)
  },
  changeVoiceGroup(step) {
    const groups = this.data.voiceGroups
    if (groups.length < 2 || this.data.expandedVoice) return
    const voiceGroup = (this.data.voiceGroup + step + groups.length) % groups.length
    this.setData({ voiceGroup, voiceCards: groups[voiceGroup] })
  },
  nextVoices() { this.changeVoiceGroup(1); this.syncVoiceTimer() },
  toggleVoicePause() { this.setData({ voicePaused: !this.data.voicePaused }); this.syncVoiceTimer() },
  pauseVoiceReading() { this.stopVoiceTimer() },
  resumeVoiceReading() { this.syncVoiceTimer() },
  openVoice(event) {
    const voice = this.data.voiceCards.find(v => v.id === event.currentTarget.dataset.id)
    if (!voice) return
    this.stopVoiceTimer()
    this.setData({ expandedVoice: voice })
  },
  closeVoice() { this.setData({ expandedVoice: null }); this.syncVoiceTimer() },
  retainVoiceDialog() {},
  chooseCamp(event) {
    const featured = this.data.camps.find(a => a.id === event.currentTarget.dataset.id)
    if (featured) this.setData({ selectedCampId: featured.id, featured })
  },
  imageFailed(event) {
    const src = event.currentTarget.dataset.src
    if (src) this.setData({ failedImages: { ...this.data.failedImages, [src]: true } })
  },
  storyChanged(event) { this.setData({ storyIndex: event.detail.current }) },
  stepStory(event) {
    if (this.data.stories.length < 2) return
    const index = this.data.storyIndex + Number(event.currentTarget.dataset.step)
    this.setData({ storyIndex: Math.max(0, Math.min(this.data.stories.length - 1, index)) })
  },
  openActivity(event) {
    if (!this.data.featured) return
    navigation.navigateTo({ url: '/pages/camp-flow/index?view=' + (event.currentTarget.dataset.view || 'detail') + '&id=' + encodeURIComponent(this.data.featured.id) })
  },
  openReading() { navigation.openReadingList(false) },
  openStories() { navigation.openReadingList(true) },
  openArticle(event) { const { id, title } = event.currentTarget.dataset; navigation.navigateTo({ url: '/pages/article/index?id=' + encodeURIComponent(id) + '&title=' + encodeURIComponent(title || '') }) },
  openCamp() { navigation.switchTab({ url: '/pages/camp/index' }) },
  openPrimary() {
    const view = this.data.view
    if (view.activityId) store.updateState(state => { state.selectedActivityId = view.activityId; return state })
    if (view.route === 'reading') return navigation.openReadingList(false)
    if (view.route === 'habit') return navigation.navigateTo({ url: '/pages/mine-flow/index?view=habit' })
    if (view.route === 'daily') return navigation.navigateTo({ url: '/pages/friend-flow/index?view=daily' })
    navigation.navigateTo({ url: '/pages/camp-flow/index?view=' + view.route + (view.activityId ? '&id=' + encodeURIComponent(view.activityId) : '') })
  },
  about() { navigation.navigateTo({ url: '/pages/about/index' }) },
  onShareAppMessage() { return { title: '轻生活', path: '/pages/home/index' } }
})
