const business = require('../../services/business-api')
const reflection = require('../../utils/reflection')
const navigation = require('../../utils/navigation')
Page({
  data: { sessionId: '', phase: '', context: {}, note: '', length: 0, revision: 0, status: 'private', shared: false, anonymous: false, loading: true, error: '', saving: false, voiceStarting: false, voicePressing: false, cancelOnRelease: false, showVoicePrivacy: false, privacyContractName: '', microphoneDenied: false, recording: false, transcribing: false, voiceError: '', draftDirty: false, pastShares: [], source: '', sources: [], sourceConflict: false, originalVersion: '', scoreText: '' },
  onLoad(options) { this.setData({ sessionId: options.id || '' }); this.bindIdentity(); return this.load() },
  bindIdentity() { if (!this.unwatchIdentity && business.watchIdentity) this.unwatchIdentity = business.watchIdentity(() => this.clearPrivate('身份已变化，请重新读取本人记录')) },
  clearPrivate(error = '') {
    this.loadVersion = (this.loadVersion || 0) + 1
    this.scope = null; this.cancelVoice()
    this.setData({ context: {}, note: '', length: 0, revision: 0, status: 'private', shared: false, anonymous: false, pastShares: [], source: '', sources: [], sourceConflict: false, originalVersion: '', scoreText: '', draftDirty: false, loading: false, saving: false, error })
  },
  current(scope = this.scope) {
    if (this.leftPage || this.unloaded) return false
    try { if (business.assertIdentity) { if (!scope) return false; business.assertIdentity(scope) }; return true }
    catch (_) { this.clearPrivate('身份已变化，请重新读取本人记录'); return false }
  },
  async load() {
    const invocation = this.loadInvocation = (this.loadInvocation || 0) + 1
    this.clearPrivate()
    this.setData({ loading: true, error: '' })
    let version
    try {
      if (!business.enabled()) throw new Error('心声需要在已连接服务的小程序中保存')
      if (business.identityScope && !business.identityScope().token) await business.login()
      if (this.leftPage || this.unloaded || invocation !== this.loadInvocation) return
      const scope = business.identityScope ? business.identityScope() : null
      this.scope = scope; version = this.loadVersion
      this.setData({ loading: true, error: '' })
      if (business.privateOverview) await business.privateOverview(scope)
      if (version !== this.loadVersion || !this.current(scope)) return
      const context = await business.campReflection(this.data.sessionId, scope)
      if (version !== this.loadVersion || !this.current(scope)) return
      const selected = reflection.selectedPhase(context)
      const draft = reflection.draft(context, selected)
      this.setData({ context, phase: selected, ...draft, length: reflection.count(draft.note), pastShares: reflection.pastShares(context, selected), draftDirty: false })
    } catch (error) { if (invocation === this.loadInvocation && !this.leftPage && !this.unloaded && (version == null || version === this.loadVersion)) this.setData({ error: error.message }) }
    finally { if (invocation === this.loadInvocation && (version == null || version === this.loadVersion)) this.setData({ loading: false }) }
  },
  canWrite() { return this.current() && !this.data.loading && !this.data.error && !this.data.sourceConflict && (this.data.phase === 'before' ? this.data.context.canBefore : this.data.context.canAfter) },
  async chooseSource(event) {
    const source = event.currentTarget.dataset.source
    if (this.data.saving || !this.current() || !(this.data.sources || []).some(row => row.source === source)) return
    const scope = this.scope, version = this.loadVersion
    if (this.data.draftDirty) {
      const answer = await new Promise(resolve => wx.showModal({ title: '切换原稿来源', content: '当前未保存的修改将被所选原文替换。两份历史记录仍保留。', success: resolve, fail: () => resolve({ confirm: false }) }))
      if (!answer.confirm || version !== this.loadVersion || !this.current(scope) || this.data.saving) return
    }
    this.cancelVoice()
    const draft = reflection.draft(this.data.context, this.data.phase, source)
    this.setData({ ...draft, length: reflection.count(draft.note), draftDirty: false })
  },
  onInput(event) {
    if (this.data.saving || !this.canWrite()) return
    // The native textarea owns composition and cursor; only external edits replace its value.
    this.data.note = event.detail.value
    this.setData({ length: reflection.count(event.detail.value), draftDirty: true })
  },
  async resetDraft() {
    if (this.resetPending || this.data.saving || !this.canWrite() || !this.data.note) return
    const scope = this.scope, version = this.loadVersion
    this.resetPending = true
    try {
      const answer = await new Promise(resolve => wx.showModal({ title: '重置心声', content: '清空当前文字？', confirmText: '清空', confirmColor: '#637556', success: resolve, fail: () => resolve({ confirm: false }) }))
      if (!answer.confirm || version !== this.loadVersion || !this.current(scope) || this.data.saving || !this.canWrite() || this.leftPage || this.unloaded) return
      this.cancelVoice()
      this.setData({ note: '', length: 0, draftDirty: true })
    } finally { this.resetPending = false }
  },
  save() { return this.saveDraft('submit') },
  savePrivate() { return this.saveDraft('private') },
  async saveDraft(action) {
    if (this.data.saving || !this.canWrite()) return
    const note = this.data.note.trim(), length = reflection.count(note)
    const unified = this.data.phase === 'after' && this.data.context.unifiedAfter
    if ((!length && (action === 'submit' || !unified)) || length > 150) return wx.showToast({ title: '请留下一至150字的心声', icon: 'none' })
    if (action === 'submit' && !this.data.context.authorName) return wx.showToast({ title: '请先完善轻生活小名', icon: 'none' })
    const scope = this.scope, version = this.loadVersion
    const payload = { registrationId: this.data.context.registrationId, phase: this.data.phase, revision: this.data.revision, note, action, shareConsent: action === 'submit', anonymous: action === 'submit' ? false : this.data.anonymous }
    if (unified && (!this.data.originalVersion || !this.data.source)) return wx.showToast({ title: '请重新读取并选择原稿来源', icon: 'none' })
    if (unified) Object.assign(payload, { originalVersion: this.data.originalVersion, source: this.data.source })
    // Submit the visible draft only. An unfinished recording must never change it later.
    this.cancelVoice()
    this.setData({ saving: true })
    try {
      if (business.privateOverview) await business.privateOverview(scope)
      if (version !== this.loadVersion || !this.current(scope)) return
      const result = await business.saveCampReflection(payload, scope)
      if (version !== this.loadVersion || !this.current(scope)) return
      if (unified) {
        if (!result || !result.context || !result.context.unifiedAfter || result.context.registrationId !== payload.registrationId || !result.context.experienceRecord || !result.context.originalVersion) throw new Error('保存结果未确认，请重新读取本人记录')
        const draft = reflection.draft(result.context, this.data.phase, 'experience')
        this.setData({ context: result.context, ...draft, length: reflection.count(draft.note), pastShares: reflection.pastShares(result.context, this.data.phase), draftDirty: false })
      } else {
        const records = (this.data.context.records || []).filter(row => row.phase !== this.data.phase).concat(result)
        this.setData({ context: { ...this.data.context, records }, ...reflection.draft({ records }, this.data.phase), pastShares: reflection.pastShares({ records }, this.data.phase), length, draftDirty: false })
      }
      wx.showToast({ title: action === 'submit' ? '已提交，等待审核' : '私人原稿已保存', icon: 'none' })
    } catch (error) { if (version === this.loadVersion && this.current(scope)) wx.showToast({ title: error.message, icon: 'none' }) }
    finally { if (version === this.loadVersion && this.current(scope)) this.setData({ saving: false }) }
  },
  async withdraw(event) {
    if (this.data.saving || !this.current()) return
    const scope = this.scope, version = this.loadVersion
    const answer = await new Promise(resolve => wx.showModal({ title: '收回公开分享', content: '这份心声会停止展示，你的私人草稿仍会保留', confirmText: '收回分享', success: resolve }))
    if (!answer.confirm || version !== this.loadVersion || !this.current(scope)) return
    const selected = event && event.currentTarget && event.currentTarget.dataset.phase || this.data.phase
    if (!['before', 'after'].includes(selected)) return
    const activePhase = this.data.phase
    const draft = this.data.draftDirty ? { note: this.data.note, length: this.data.length, anonymous: this.data.anonymous, revision: this.data.revision, draftDirty: true, source: this.data.source, sourceConflict: this.data.sourceConflict } : null
    this.cancelVoice()
    this.setData({ saving: true })
    try {
      if (business.privateOverview) await business.privateOverview(scope)
      if (version !== this.loadVersion || !this.current(scope)) return
      await business.withdrawCampReflection(this.data.sessionId, selected, scope)
      if (version !== this.loadVersion || !this.current(scope)) return
      await this.load()
      if (this.scope !== scope || !this.current(scope)) return
      if (draft && this.data.phase === activePhase) {
        if (selected === activePhase) draft.revision = this.data.revision
        this.setData(draft)
      }
      wx.showToast({ title: '已收回分享', icon: 'none' })
    }
    catch (error) { if (this.scope === scope && this.current(scope)) wx.showToast({ title: error.message, icon: 'none' }) }
    finally { if (this.scope === scope && this.current(scope)) this.setData({ saving: false }) }
  },
  voiceCurrent(attempt) { return attempt === (this.voiceAttempt || 0) && this.current() && !this.data.saving },
  voiceCall(invoke, attempt, timeout = 15000, message = '语音准备超时，请重试') {
    return new Promise((resolve, reject) => {
      let timer, settled = false
      const finish = (error, result) => {
        if (settled) return
        settled = true; clearTimeout(timer)
        if (this.cancelVoiceCall === cancel) this.cancelVoiceCall = null
        if (!this.voiceCurrent(attempt)) return reject({ cancelled: true })
        error ? reject(error) : resolve(result)
      }
      const cancel = () => finish({ cancelled: true })
      this.cancelVoiceCall = cancel
      timer = setTimeout(() => finish(new Error(message)), timeout)
      try { invoke({ success: result => finish(null, result), fail: error => finish(error) }) }
      catch (error) { finish(error) }
    })
  },
  voiceFailure(error, stage) {
    const detail = String(error && (error.errMsg || error.message) || '')
    this.lastVoiceFailure = { stage, code: error && error.errCode, detail }
    let message = error && error.message || '录音未成功，请重试'
    if (/privacy|隐私|not declared/i.test(detail)) message = /not declared|not configured|未配置/i.test(detail) ? '小程序录音隐私声明未配置' : '微信隐私授权未完成'
    else if (stage === 'microphone') message = '请允许使用麦克风'
    else if (stage === 'consent') message = '录音确认未完成，请重试'
    this.setData({ voiceStarting: false, voicePressing: false, voiceError: message, microphoneDenied: stage === 'microphone' })
  },
  voiceTouchStart(event) {
    if (this.data.saving || this.data.voiceStarting || this.data.transcribing || this.recordAttempt != null || !this.canWrite()) return
    if (!this.data.context.voiceAvailable) return this.setData({ voiceError: '语音暂未开通' })
    this.voiceHeld = true
    const touch = event && event.touches && event.touches[0]
    this.voiceTouchY = touch && touch.clientY
    this.setData({ voicePressing: true, cancelOnRelease: false, voiceError: '' })
  },
  async beginVoice() {
    if (!this.voiceHeld || this.data.saving || this.data.voiceStarting || this.data.transcribing || !this.canWrite()) return
    const attempt = (this.voiceAttempt || 0) + 1
    this.voiceAttempt = attempt
    if (this.voiceReady && this.voiceConsent) { this.startRecorder(attempt); return }
    this.setData({ voiceStarting: true, microphoneDenied: false })
    let stage = 'consent'
    try {
      if (!this.voiceConsent) {
        // A permission dialog ends the original gesture; consent never starts background recording.
        this.voiceHeld = false; this.setData({ voicePressing: false })
        const consent = await this.voiceCall(callbacks => wx.showModal({ title: '语音转文字', content: '录音将由腾讯云转成可编辑文字，不保存原录音。文字在你提交后进入审核', confirmText: '允许录音', ...callbacks }), attempt, 120000, '录音确认未完成，请重试')
        if (!consent.confirm) return
        this.voiceConsent = true
      }
      stage = 'privacy'
      if (wx.getPrivacySetting) {
        const privacy = await this.voiceCall(callbacks => wx.getPrivacySetting(callbacks), attempt)
        if (privacy.needAuthorization) {
          this.voiceHeld = false
          this.setData({ voicePressing: false, showVoicePrivacy: true, privacyContractName: privacy.privacyContractName || '隐私保护指引' })
          return
        }
      }
      await this.prepareMicrophone(attempt)
    } catch (error) { if (!error.cancelled && this.voiceCurrent(attempt)) this.voiceFailure(error, stage) }
    finally { if (this.voiceCurrent(attempt) && this.recordAttempt !== attempt) this.setData({ voiceStarting: false, voicePressing: !!this.voiceHeld }) }
  },
  async prepareMicrophone(attempt) {
    try {
      const setting = wx.getSetting ? await this.voiceCall(callbacks => wx.getSetting(callbacks), attempt) : { authSetting: {} }
      const authorized = (setting.authSetting || {})['scope.record']
      if (authorized === false) throw new Error('请允许使用麦克风')
      if (!authorized) {
        this.voiceHeld = false; this.setData({ voicePressing: false })
        await this.voiceCall(callbacks => wx.authorize({ scope: 'scope.record', ...callbacks }), attempt, 60000, '麦克风授权未完成')
      }
      if (!this.voiceCurrent(attempt)) return
      this.voiceReady = true
      if (this.voiceHeld) this.startRecorder(attempt)
      else wx.showToast({ title: '已开启，按住说话', icon: 'none' })
    } catch (error) { if (!error.cancelled && this.voiceCurrent(attempt)) { this.voiceReady = false; this.voiceFailure(error, 'microphone') } }
    finally { if (this.voiceCurrent(attempt) && this.recordAttempt !== attempt) this.setData({ voiceStarting: false, voicePressing: !!this.voiceHeld }) }
  },
  async agreeVoicePrivacy() {
    if (!this.data.showVoicePrivacy || this.data.saving || !this.canWrite()) return
    const attempt = this.voiceAttempt
    this.voiceHeld = false
    this.setData({ showVoicePrivacy: false, voiceStarting: true, voicePressing: false })
    await this.prepareMicrophone(attempt)
  },
  viewVoicePrivacy() {
    this.viewingVoicePrivacy = true
    wx.openPrivacyContract({ fail: () => { this.viewingVoicePrivacy = false; this.setData({ voiceError: '隐私指引暂时无法打开' }) } })
  },
  startRecorder(attempt) {
    if (!this.voiceCurrent(attempt) || !this.voiceHeld) return
    try {
      this.setupRecorder(); this.recordAttempt = attempt
      this.setData({ voiceStarting: true })
      this.recordStartTimer = setTimeout(() => {
        if (this.voiceCurrent(attempt) && this.data.voiceStarting) this.cancelVoice('录音未能启动，请重试')
      }, 15000)
      this.recorder.start({ duration: 45000, sampleRate: 16000, numberOfChannels: 1, encodeBitRate: 48000, format: 'mp3' })
    } catch (error) { clearTimeout(this.recordStartTimer); this.recordAttempt = null; this.voiceReady = false; this.voiceFailure(error, 'recorder') }
  },
  setupRecorder() {
    if (this.recorder) return
    this.recorder = wx.getRecorderManager()
    this.recorderStart = () => {
      clearTimeout(this.recordStartTimer)
      if (!this.voiceCurrent(this.recordAttempt)) { this.recorder.stop(); return }
      this.setData({ voiceStarting: false, recording: true, voiceError: '' })
      if (!this.voiceHeld) this.recorder.stop()
    }
    this.recorderError = error => {
      const attempt = this.recordAttempt
      this.recordAttempt = null; clearTimeout(this.recordStartTimer)
      if (this.unloaded) this.releaseRecorder()
      if (this.voiceCurrent(attempt)) { this.voiceReady = false; this.voiceHeld = false; this.setData({ recording: false, transcribing: false }); this.voiceFailure(error, 'recorder') }
    }
    this.recorderStop = result => {
      const attempt = this.recordAttempt
      this.recordAttempt = null; clearTimeout(this.recordStartTimer)
      if (this.unloaded) this.releaseRecorder()
      if (!this.voiceCurrent(attempt)) { this.removeAudio(result.tempFilePath); return }
      this.voiceHeld = false
      this.setData({ voiceStarting: false, recording: false, voicePressing: false, cancelOnRelease: false })
      this.recognize(result, attempt)
    }
    this.recorder.onStart(this.recorderStart); this.recorder.onError(this.recorderError); this.recorder.onStop(this.recorderStop)
  },
  voiceTouchMove(event) {
    const touch = event && event.touches && event.touches[0]
    if (!this.voiceHeld || !touch || this.voiceTouchY == null) return
    this.setData({ cancelOnRelease: this.voiceTouchY - touch.clientY > 60 })
  },
  endVoice() {
    const discard = this.data.cancelOnRelease
    this.voiceHeld = false
    this.setData({ voicePressing: false, cancelOnRelease: false })
    if (discard) { this.cancelVoice(); return }
    if (this.data.recording) this.recorder.stop()
    // If release precedes onStart, onStart immediately stops this same recording.
  },
  cancelVoice(message = '') {
    this.voiceHeld = false; this.voiceAttempt = (this.voiceAttempt || 0) + 1
    if (this.cancelVoiceCall) this.cancelVoiceCall()
    clearTimeout(this.recordStartTimer)
    this.setData({ voiceStarting: false, voicePressing: false, showVoicePrivacy: false, recording: false, transcribing: false, cancelOnRelease: false, voiceError: typeof message === 'string' ? message : '' })
    if (this.recorder && this.recordAttempt != null) {
      try { this.recorder.stop() } catch (_) { this.recordAttempt = null }
    }
  },
  async recognize(result, attempt = this.voiceAttempt || 0) {
    const audioPath = result.tempFilePath
    if (!this.voiceCurrent(attempt)) { this.removeAudio(audioPath); return }
    this.setData({ transcribing: true, voiceError: '' })
    try {
      if (!result.duration || result.duration < 500) throw new Error('说话时间太短，请再试一次')
      if (result.duration > 45000) throw new Error('每段最多45秒')
      const file = await this.voiceCall(callbacks => wx.getFileSystemManager().readFile({ filePath: audioPath, encoding: 'base64', ...callbacks }), attempt, 15000, '录音读取未成功')
      if (!this.voiceCurrent(attempt)) return
      const response = await business.transcribeReflection({ registrationId: this.data.context.registrationId, phase: this.data.phase, durationMs: result.duration, audio: file.data, voiceConsent: true }, this.scope)
      if (!this.voiceCurrent(attempt)) return
      if (!response.text || !response.text.trim()) throw new Error('没有听清，请再试一次')
      const note = reflection.voiceText(this.data.note, response.text.trim())
      this.setData({ note, length: reflection.count(note), draftDirty: true })
    } catch (error) { if (!error.cancelled && this.voiceCurrent(attempt)) this.setData({ voiceError: error.message || '转换未成功，请重试' }) }
    finally { this.removeAudio(audioPath); if (this.voiceCurrent(attempt)) this.setData({ transcribing: false }) }
  },
  removeAudio(filePath) { if (filePath) wx.getFileSystemManager().unlink({ filePath, fail() {} }) },
  onShow() { this.leftPage = false; this.viewingVoicePrivacy = false; this.voiceReady = false; this.bindIdentity(); if (this.reloadPrivate) { this.reloadPrivate = false; return this.load() } },
  onHide() { this.voiceHeld = false; if (this.viewingVoicePrivacy) return; this.leftPage = true; this.reloadPrivate = true; if (this.unwatchIdentity) this.unwatchIdentity(); this.unwatchIdentity = null; this.clearPrivate() },
  releaseRecorder() { if (this.recorder) { this.recorder.offStart(this.recorderStart); this.recorder.offStop(this.recorderStop); this.recorder.offError(this.recorderError) } },
  onUnload() { this.unloaded = true; this.viewingVoicePrivacy = false; this.onHide(); if (this.recordAttempt == null) this.releaseRecorder() },

  back() { wx.navigateBack({ fail() { navigation.redirectTo({ url: '/pages/camp/index' }) } }) }
})
