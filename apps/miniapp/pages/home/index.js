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
  data: { view: {}, state: {}, featuredReadings: [], stories: [], storyIndex: 0, visible: false, readingError: '', brand, failedImages: {}, taskContext: '', cancellationNotice: null, camps: [], selectedCampId: '', voiceGroups: [], voiceCards: [], voiceGroup: 0, voicePaused: false, reducedMotion: false, expandedVoice: null, fontScale: 1, voiceLoading: false, voiceError: '', voiceHasMore: false, voicePage: 0, discoverTitle: content.discoverTitle },
  async onShow() {
    const version = this.requestVersion = (this.requestVersion || 0) + 1
    this.stopVoiceTimer()
    this.setData({ visible: true })
    this.resetVoices()
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
        this.setData({ loading: false, refreshing: false })
        await this.loadVoices(this.data.selectedCampId)
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
    this.resetVoices()
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
    if (!this.data.visible || this.data.voicePaused || this.data.reducedMotion || this.data.expandedVoice || (this.data.voiceGroups.length < 2 && !this.data.voiceHasMore && this.data.voicePage < 2) || this._voiceReading || this.data.voiceLoading || this.data.voiceError || typeof setInterval !== 'function') return
    this.voiceTimer = setInterval(() => this.nextVoices(), content.voiceRotationMs)
  },
  resetVoices() {
    this.voiceVersion = (this.voiceVersion || 0) + 1
    this.voiceOpenVersion = (this.voiceOpenVersion || 0) + 1
    this._voiceBusy = null; this._voiceRows = []; this._voiceCursor = ''; this._voiceReading = false
    this.stopVoiceTimer()
    this.setData({ voiceGroups: [], voiceCards: [], voiceGroup: 0, expandedVoice: null, voiceLoading: false, voiceError: '', voiceHasMore: false, voicePage: 0 })
  },
  async loadVoices(sessionId = this.data.selectedCampId) {
    this.resetVoices()
    if (!sessionId || !this.data.visible) return
    return this.fetchVoicePage(sessionId, '', this.voiceVersion, true)
  },
  async fetchVoicePage(sessionId, cursor, version, first) {
    if (this._voiceBusy || !this.data.visible) return
    const attempt = {}; this._voiceBusy = attempt
    this.stopVoiceTimer()
    this.setData({ voiceLoading: true, voiceError: '' })
    try {
      const response = await business.campVoicePage(sessionId, cursor)
      if (version !== this.voiceVersion || !this.data.visible || sessionId !== this.data.selectedCampId) return
      if (response.hasMore && response.nextCursor === cursor) throw new Error('心声分页未能继续，请刷新重试')
      const records = presentation.publicVoiceRecords(response, sessionId)
      if (!records.length && !first && !response.hasMore) return this.loadVoices(sessionId)
      const groups = presentation.voiceGroups(records)
      this._voiceRows = records; this._voiceCursor = response.nextCursor || ''
      // Retain one bounded page only. Later groups fetch the next page instead of preloading all.
      this.setData({ voiceGroups: groups, voiceCards: groups[0] || [], voiceGroup: 0, voiceHasMore: response.hasMore, voicePage: first ? 1 : this.data.voicePage + 1 })
    } catch (error) {
      if (version === this.voiceVersion && this.data.visible) this.setData({ voiceError: '本期心声暂时未能读取，请重试。' })
    } finally {
      if (this._voiceBusy === attempt) { this._voiceBusy = null; this.setData({ voiceLoading: false }); this.syncVoiceTimer() }
    }
  },
  async changeVoiceGroup(step) {
    const groups = this.data.voiceGroups
    if (!groups.length || this.data.expandedVoice || this.data.voiceLoading) return
    if (step > 0 && this.data.voiceGroup === groups.length - 1 && (this.data.voiceHasMore || this.data.voicePage > 1)) {
      const more = this.data.voiceHasMore
      return this.fetchVoicePage(this.data.selectedCampId, more ? this._voiceCursor : '', this.voiceVersion, !more)
    }
    const voiceGroup = (this.data.voiceGroup + step + groups.length) % groups.length
    this.setData({ voiceGroup, voiceCards: groups[voiceGroup] })
  },
  async nextVoices() { await this.changeVoiceGroup(1); this.syncVoiceTimer() },
  retryVoices() { return this.loadVoices() },
  toggleVoicePause() { this.setData({ voicePaused: !this.data.voicePaused }); this.syncVoiceTimer() },
  pauseVoiceReading() { this._voiceReading = true; this.stopVoiceTimer() },
  resumeVoiceReading() { this._voiceReading = false; this.syncVoiceTimer() },
  async openVoice(event) {
    const voice = this.data.voiceCards.find(v => v.id === event.currentTarget.dataset.id)
    if (!voice || voice.sessionId !== this.data.selectedCampId) return
    const version = this.voiceVersion, openVersion = this.voiceOpenVersion = (this.voiceOpenVersion || 0) + 1
    this.pauseVoiceReading()
    try {
      const result = await business.campVoice(voice.sessionId, voice.id)
      if (version !== this.voiceVersion || openVersion !== this.voiceOpenVersion || !this.data.visible) return
      if (!result || result.contractVersion !== 1 || result.sessionId !== voice.sessionId || !result.item || result.item.id !== voice.id) throw new Error('心声归属无效')
      const current = presentation.publicVoiceRecords({ ...result, list: [{ ...result.item, personKey: voice.personKey }], hasMore: false }, voice.sessionId)[0]
      this.setData({ expandedVoice: current })
    } catch (error) {
      if (version !== this.voiceVersion || openVersion !== this.voiceOpenVersion || !this.data.visible) return
      if (error.code === 404) {
        this._voiceRows = this._voiceRows.filter(v => v.id !== voice.id)
        const groups = presentation.voiceGroups(this._voiceRows), group = Math.min(this.data.voiceGroup, Math.max(0, groups.length - 1))
        this.setData({ voiceGroups: groups, voiceGroup: group, voiceCards: groups[group] || [] })
      }
      this.setData({ voiceError: error.code === 404 ? '这条心声已不再公开。' : '心声暂时未能读取，请重试。' })
      this.resumeVoiceReading()
    }
  },
  closeVoice() { this.voiceOpenVersion = (this.voiceOpenVersion || 0) + 1; this.setData({ expandedVoice: null }); this.resumeVoiceReading() },
  retainVoiceDialog() {},
  chooseCamp(event) {
    const featured = this.data.camps.find(a => a.id === event.currentTarget.dataset.id)
    if (!featured || featured.id === this.data.selectedCampId) return
    this.setData({ selectedCampId: featured.id, featured })
    return this.loadVoices(featured.id)
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
  openCamp() { navigation.navigateTo({ url: '/pages/camp/index' }) },
  openCampList() { navigation.navigateTo({ url: '/pages/camp/index' }) },
  openPrimary() {
    const view = this.data.view
    if (view.activityId) store.updateState(state => { state.selectedActivityId = view.activityId; return state })
    if (view.route === 'reading') return navigation.openReadingList(false)
    if (view.route === 'habit') return navigation.navigateTo({ url: '/pages/mine-flow/index?view=habit' })
    if (view.route === 'daily') return navigation.navigateTo({ url: '/pages/friend-flow/index?view=daily' })
    navigation.navigateTo({ url: '/pages/camp-flow/index?view=' + view.route + (view.activityId ? '&id=' + encodeURIComponent(view.activityId) : '') })
  },
  about() { navigation.switchTab({ url: '/pages/about/index' }) },
  onShareAppMessage() { return { title: '轻生活', path: '/pages/home/index' } }
})
