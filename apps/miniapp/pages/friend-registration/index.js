const business = require('../../services/business-api')
const DRAFT_KEY = 'qinglife_friend_assessment_draft_2026_v1'
const emptyForm = () => ({
  clientRequestId: 'fa_' + Date.now() + '_' + Math.random().toString(36).slice(2, 10),
  name: '', nickname: '', birthDate: '', phone: '', heightCm: '', weightKg: '', province: '', city: '',
  cleanBodyGoals: [], dietPreference: '', waterIntakeMl: '', wakeTime: '', sleepTime: '',
  bowelStatus: '', energyStatus: '', exerciseStatus: '', emotionalStatus: [], healthConditions: [],
  otherHealthCondition: '', medications: '', pregnancyStatus: '', referralSource: '', retrainingReason: '',
  sensitiveConsent: false
})

Page({
  data: {
    step: 1, loading: true, loadError: '', submitting: false, submitted: false, assessmentCount: 0,
    sessionContext: null, invitationCode: '', mode: 'form', history: [], historyLoading: false, historyError: '', record: {}, editingId: '', editDeadline: '', wasEditing: false,
    form: emptyForm(), today: '', cleanBodyGoalSelected: {}, emotionSelected: {}, healthSelected: {},
    goals: ['排毒','减重','减压','调理'],
    diets: ['全素','以素为主','以荤为主','荤素各半'],
    bowels: ['2-3次/天','1次/天','便秘'],
    energies: ['很好','一般','差'],
    exercises: ['不运动','偶尔运动','经常运动'],
    pregnancyOptions: ['否','是'],
    emotions: ['平静','开心','轻松','焦虑','压力感','悲伤','没什么感觉'],
    healthGroups: [
      { title: '心血管 / 代谢', values: ['高血压','低血压','心脏病','高血脂','胆固醇高','血糖偏高','低血糖','糖尿病'] },
      { title: '消化 / 脏器', values: ['脂肪肝','胆结石','胃炎','肾炎'] },
      { title: '甲状腺', values: ['甲状腺','甲亢','甲减'] },
      { title: '日常状态', values: ['睡眠','易疲劳','易感冒','头晕头痛','皮肤过敏'] },
      { title: '其他', values: ['咽喉炎','前列腺','妇科问题','乳腺问题','腰椎盘突出','其他','无'] }
    ]
  },

  async onLoad(options = {}) {
    if (typeof options.invite === 'string' || typeof options.scene === 'string' || options.history === '1') this.routeOptions = options
    options = this.routeOptions || {}
    let invitationCode = options.invite || ''
    if (!invitationCode && options.scene) { try { invitationCode = decodeURIComponent(options.scene) } catch (_) { invitationCode = 'invalid' } }
    this.initialized = false
    this.setData({ loading: true, loadError: '', sessionContext: null, invitationCode, submitted: false, editingId: '', form: emptyForm(), mode: 'form' })
    const today = new Date()
    const date = [today.getFullYear(), String(today.getMonth()+1).padStart(2,'0'), String(today.getDate()).padStart(2,'0')].join('-')
    try {
      const profile = await business.friendProfile()
      const sessionContext = invitationCode ? await business.assessmentInvitation(invitationCode) : null
      this.customerId = profile.id
      this.draftKey = profile.id ? DRAFT_KEY + ':' + profile.id + (invitationCode ? ':' + invitationCode : '') : ''
      // Unscoped legacy drafts have no provable owner, so never display them after a login change.
      const draft = this.draftKey && wx.getStorageSync(this.draftKey)
      const form = draft && draft.clientRequestId ? { ...emptyForm(), ...draft } : { ...emptyForm(), ...basicProfile(profile), weightKg: profile.weightKg || '' }
      this.setData({ form, sessionContext, assessmentCount: Number(profile.assessmentCount || 0), today: date, loading: false, ...selectionMaps(form) })
      this.initialized = true
      if (options.history === '1') await this.openHistory()
    } catch (error) {
      this.setData({ loading: false, loadError: error.message || '档案读取失败，请重试' })
    }
  },
  async onShow() {
    if (!this.initialized || this.data.loading) return
    try {
      const profile = await business.friendProfile()
      if (profile.id !== this.customerId) return this.onLoad(this.routeOptions || {})
      if (this.data.mode !== 'form') return this.refreshHistory()
    } catch (e) { this.initialized = false; this.setData({form:emptyForm(),history:[],record:{},loadError:e.message||'请重新登录',sessionContext:null}) }
  },
  async refreshHistory() {
    try {
      const history = (await business.friendAssessments()).map(normalizeRecord)
      const record = history.find(item => item.id === this.data.record.id)
      this.setData({ history, historyError: '', assessmentCount: history.length, record: record || { canEdit: false, sections: [] } })
      return history
    } catch (error) {
      this.setData({ history: [], historyError: error.message || '过往登记暂时无法读取', record: { canEdit: false, sections: [] } })
      return null
    }
  },
  async openHistory() {
    this.setData({ mode: 'history', historyLoading: true, historyError: '' })
    await this.refreshHistory()
    this.setData({ historyLoading: false })
  },
  viewAssessment(event) {
    const record = this.data.history.find(item => String(item.id) === String(event.currentTarget.dataset.id))
    if (record) this.setData({ mode: 'detail', record })
  },
  async editAssessment() {
    const history = await this.refreshHistory()
    const record = history && history.find(item => item.id === this.data.record.id)
    if (!record || !record.canEdit || deadlinePassed(record.editableUntil)) return wx.showToast({ title: '这条登记已不能修改，请重新查看记录', icon: 'none' })
    this.newFormBeforeEdit = JSON.parse(JSON.stringify(this.data.form))
    const form = emptyForm()
    Object.keys(form).forEach(key => { if (record[key] != null && key !== 'clientRequestId' && key !== 'sensitiveConsent') form[key] = record[key] })
    form.birthDate = formatDate(record.birthDate)
    form.revision = record.revision
    this.setData({ mode: 'form', submitted: false, editingId: record.id, editDeadline: record.editableUntil, step: 1, form, ...selectionMaps(form) })
  },
  cancelEdit() {
    const form = this.newFormBeforeEdit || emptyForm()
    this.setData({ mode: 'detail', editingId: '', form, ...selectionMaps(form) })
  },
  returnToForm() { this.setData({ mode: 'form' }) },
  pickRegion(event) {
    const value = event.detail.value
    if (!Array.isArray(value) || !value[0] || !value[1]) return
    this.setData({ 'form.province': value[0], 'form.city': value[1] })
    this.saveDraft()
  },

  field(event) {
    const key = event.currentTarget.dataset.field
    this.setData({ ['form.' + key]: event.detail.value })
    this.saveDraft()
  },
  choose(event) {
    const key = event.currentTarget.dataset.field, value = event.currentTarget.dataset.value
    this.setData({ ['form.' + key]: value })
    this.saveDraft()
  },
  multi(event) {
    const key = event.currentTarget.dataset.field, value = event.currentTarget.dataset.value
    let list = (this.data.form[key] || []).slice()
    if (key === 'healthConditions') {
      if (value === '无') list = list.includes('无') ? [] : ['无']
      else { list = list.filter(item => item !== '无'); list = list.includes(value) ? list.filter(item => item !== value) : list.concat(value) }
    } else list = list.includes(value) ? list.filter(item => item !== value) : list.concat(value)
    const selectedKey = key === 'cleanBodyGoals' ? 'cleanBodyGoalSelected' : key === 'emotionalStatus' ? 'emotionSelected' : 'healthSelected'
    const selected = {}; list.forEach(item => { selected[item] = true })
    this.setData({ ['form.' + key]: list, [selectedKey]: selected })
    this.saveDraft()
  },
  pickDate(event) { this.setData({ 'form.birthDate': event.detail.value }); this.saveDraft() },
  pickTime(event) { const key=event.currentTarget.dataset.field;this.setData({ ['form.'+key]:event.detail.value });this.saveDraft() },
  toggleConsent() { this.setData({ 'form.sensitiveConsent': !this.data.form.sensitiveConsent }); this.saveDraft() },
  saveDraft() { if (this.draftKey && !this.data.editingId) wx.setStorageSync(this.draftKey, this.data.form) },
  validate(step) {
    const f=this.data.form
    if (step===1 && (!f.name.trim() || !f.birthDate || !f.phone.trim() || !f.heightCm || !f.weightKg || !f.province.trim() || !f.city.trim())) return '请完整填写基本信息'
    if (step===1 && (Number(f.heightCm)<80 || Number(f.heightCm)>250 || Number(f.weightKg)<20 || Number(f.weightKg)>300)) return '请检查身高和体重范围'
    if (step===2 && (!f.cleanBodyGoals.length || !f.dietPreference || f.waterIntakeMl==='' || !f.wakeTime || !f.sleepTime || !f.bowelStatus || !f.energyStatus || !f.exerciseStatus)) return '请完成生活习惯问题'
    if (step===3 && (!f.emotionalStatus.length || !f.healthConditions.length)) return '请完成近期状态问题'
    if (step===3 && f.healthConditions.includes('其他') && !f.otherHealthCondition.trim()) return '请补充说明其他身体情况'
    if (step===4 && !f.referralSource.trim()) return '请填写了解轻生活的途径'
    return ''
  },
  next() { const error=this.validate(this.data.step);if(error)return wx.showToast({title:error,icon:'none'});this.setData({step:this.data.step+1});this.saveDraft() },
  previous() { if(this.data.step>1)this.setData({step:this.data.step-1}) },
  async submit() {
    if(this.data.submitting)return
    const error=this.validate(4);if(error)return wx.showToast({title:error,icon:'none'})
    if(!this.data.form.sensitiveConsent)return wx.showToast({title:'请先阅读并同意敏感个人信息授权',icon:'none'})
    for (let step = 1; step < 4; step++) { const issue = this.validate(step); if (issue) { this.setData({ step }); return wx.showToast({ title: issue, icon: 'none' }) } }
    this.setData({submitting:true})
    try {
      const profile = await business.friendProfile()
      if (profile.id !== this.customerId) {
        await this.onLoad(this.routeOptions || {})
        wx.showToast({title:'账号已切换，请重新确认登记内容',icon:'none'})
        return
      }
      const f=this.data.form
      const payload = { ...f, heightCm:Number(f.heightCm), weightKg:Number(f.weightKg), waterIntakeMl:Number(f.waterIntakeMl) }
      const editing = !!this.data.editingId
      if (!editing && this.data.invitationCode) payload.invitationCode = this.data.invitationCode
      const result = editing ? await business.amendFriendAssessment(this.data.editingId, payload) : await business.submitFriendAssessment(payload)
      if (!editing && this.draftKey) wx.removeStorageSync(this.draftKey)
      this.setData({ submitted:true, editingId:'', wasEditing:editing, savedDeadline:result.editableUntil || '', assessmentCount:Number(result.assessmentCount || this.data.assessmentCount + (editing ? 0 : 1)) })
    } catch(error) { wx.showToast({title:error.message||'提交失败，请重试',icon:'none'}) }
    finally { this.setData({submitting:false}) }
  },
  backMine() { wx.switchTab({url:'/pages/mine/index'}) },
  registerAgain() { const form = { ...emptyForm(), ...basicProfile(this.data.form) }; this.setData({ mode:'form', submitted:false, editingId:'', wasEditing:false, step:1, form, ...selectionMaps(form) }); this.saveDraft() }
})

function formatDate(value) { return value ? String(value).slice(0,10) : '' }
function selectionMaps(form) {
  const make=list=>{const value={};(list||[]).forEach(item=>{value[item]=true});return value}
  return { cleanBodyGoalSelected:make(form.cleanBodyGoals),emotionSelected:make(form.emotionalStatus),healthSelected:make(form.healthConditions) }
}

function basicProfile(profile) {
  return { name: profile.name || '', nickname: profile.nickname || '', birthDate: formatDate(profile.birthDate), phone: profile.phone || '', heightCm: profile.heightCm || '', province: profile.province || '', city: profile.city || '' }
}
function deadlinePassed(value) { const time = Date.parse(String(value || '').replace(' ', 'T') + '+08:00'); return !Number.isFinite(time) || Date.now() >= time }
function normalizeRecord(raw) {
  const record = { ...raw, birthDate: formatDate(raw.birthDate), canEdit: raw.canEdit === true || raw.canEdit === 1 }
  for (const key of ['cleanBodyGoals', 'emotionalStatus', 'healthConditions']) {
    if (typeof record[key] === 'string') { try { record[key] = JSON.parse(record[key]) } catch (_) { record[key] = [] } }
    if (!Array.isArray(record[key])) record[key] = []
  }
  const value = (label, content) => ({ label, value: content == null || content === '' ? '未填写' : String(content) })
  record.sections = [
    { title: '基本信息', rows: [value('姓名', record.name), value('轻生活小名', record.nickname), value('出生日期', record.birthDate), value('联系电话', record.phone), value('省 / 市', [record.province,record.city].filter(Boolean).join(' / ')), value('身高 / 体重', (record.heightCm || '—') + ' cm / ' + record.weightKg + ' kg')] },
    { title: '生活习惯', rows: [value('清体目的', record.cleanBodyGoals.join('、')), value('饮食偏好', record.dietPreference), value('日饮水量', record.waterIntakeMl + ' ml'), value('起床 / 睡觉', record.wakeTime + ' / ' + record.sleepTime), value('排便', record.bowelStatus), value('精力', record.energyStatus), value('运动', record.exerciseStatus)] },
    { title: '近期状态', rows: [value('近期情绪', record.emotionalStatus.join('、')), value('身体情况', record.healthConditions.join('、')), value('其他身体情况', record.otherHealthCondition), value('药物或保健品', record.medications), value('是否孕期', record.pregnancyStatus)] },
    { title: '参加信息', rows: [value('登记期次', record.sessionNumber ? '第 '+record.sessionNumber+' 期 · '+record.sessionName : '日常登记'), value('了解途径', record.referralSource), value('再次参加的原因', record.retrainingReason)] }
  ]
  return record
}
