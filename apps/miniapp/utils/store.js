const demo = require('../data/demo')

const STORAGE_KEY = 'qinglife_miniapp_v12_demo_state'
let memoryState = null

function clone(value) {
  return JSON.parse(JSON.stringify(value))
}

function defaultState() {
  return {
    version: 1,
    stage: 'refeed',
    loggedIn: true,
    phoneLinked: true,
    phone: '13800002190',
    profile: { name: '林乔', nickname: '乔乔', minor: false },
    selectedActivityId: 'session-016',
    registration: {
      activityId: 'session-016',
      status: 'completed',
      step: 1,
      motivation: '想让身体慢下来',
      serviceConsent: true,
      participants: [
        { id: 'person-self', name: '林乔', relation: '本人', minor: false, selected: true },
        { id: 'person-child', name: '林小满', relation: '子女', minor: true, selected: false }
      ]
    },
    checkedDays: ['day-1', 'day-2', 'day-3'],
    dailyRecords: {},
    habit: { planLength: 21, currentDay: 5, completedDays: [1, 2, 3, 4], paused: false },
    newPosts: clone(demo.seedPosts),
    localFavorites: ['demo-article-1'],
    reportedPostIds: [],
    subscriptionEnabled: false,
    operationConsent: true,
    aiConsent: false
  }
}

function storageAvailable() {
  return typeof wx !== 'undefined' && wx.getStorageSync && wx.setStorageSync
}

function ensureState() {
  const current = loadState()
  if (!current || current.version !== 1) saveState(defaultState())
  return loadState()
}

function loadState() {
  if (storageAvailable()) {
    return wx.getStorageSync(STORAGE_KEY) || null
  }
  return memoryState ? clone(memoryState) : null
}

function saveState(state) {
  const next = clone(state)
  if (storageAvailable()) wx.setStorageSync(STORAGE_KEY, next)
  memoryState = next
  return clone(next)
}

function getState() {
  return clone(ensureState() || defaultState())
}

function updateState(updater) {
  const state = getState()
  const next = updater(state) || state
  return saveState(next)
}

function resetState() {
  return saveState(defaultState())
}

function applyStage(stage) {
  return updateState((state) => {
    state.stage = stage
    if (stage === 'visitor') {
      state.loggedIn = false
      state.phoneLinked = false
      state.registration.status = 'none'
      state.checkedDays = []
    }
    if (stage === 'journey') {
      state.loggedIn = true
      state.phoneLinked = true
      state.registration.status = 'confirmed'
      state.checkedDays = ['day-1']
    }
    if (stage === 'refeed') {
      state.loggedIn = true
      state.phoneLinked = true
      state.registration.status = 'completed'
      state.checkedDays = ['day-1', 'day-2', 'day-3']
      state.habit.paused = false
    }
    if (stage === 'habit') {
      state.loggedIn = true
      state.phoneLinked = true
      state.registration.status = 'completed'
      state.checkedDays = ['day-1', 'day-2', 'day-3']
      state.habit.paused = false
    }
    return state
  })
}

module.exports = {
  STORAGE_KEY,
  clone,
  defaultState,
  ensureState,
  getState,
  saveState,
  updateState,
  resetState,
  applyStage
}
