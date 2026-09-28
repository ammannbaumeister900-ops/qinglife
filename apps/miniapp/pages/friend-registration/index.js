const business = require('../../services/business-api')
const DRAFT_KEY = 'qinglife_friend_assessment_draft_2026_v1'
const emptyForm = () => ({
  clientRequestId: 'fa_' + Date.now() + '_' + Math.random().toString(36).slice(2, 10),
  name: '', nickname: '', birthDate: '', phone: '', heightCm: '', weightKg: '', city: '',
  cleanBodyGoals: [], dietPreference: '', waterIntakeMl: '', wakeTime: '', sleepTime: '',
  bowelStatus: '', energyStatus: '', exerciseStatus: '', emotionalStatus: [], healthConditions: [],
  otherHealthCondition: '', medications: '', pregnancyStatus: '', referralSource: '', retrainingReason: '',
  sensitiveConsent: false
})

Page({
  data: {
    step: 1, loading: true, submitting: false, submitted: false, assessmentCount: 0,
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

  async onLoad() {
    const today = new Date()
    const date = [today.getFullYear(), String(today.getMonth()+1).padStart(2,'0'), String(today.getDate()).padStart(2,'0')].join('-')
    const draft = wx.getStorageSync(DRAFT_KEY)
    let form = draft && draft.clientRequestId ? { ...emptyForm(), ...draft } : emptyForm()
    try {
      const profile = await business.friendProfile()
      if (!draft) form = { ...form, name: profile.name || '', nickname: profile.nickname || '', birthDate: formatDate(profile.birthDate), phone: profile.phone || '', heightCm: profile.heightCm || '', weightKg: profile.weightKg || '', city: profile.city || '' }
      this.setData({ assessmentCount: Number(profile.assessmentCount || 0) })
    } catch (error) {
      wx.showToast({ title: error.message || '档案读取失败', icon: 'none' })
    }
    this.setData({ form, today: date, loading: false, ...selectionMaps(form) })
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
  saveDraft() { wx.setStorageSync(DRAFT_KEY, this.data.form) },
  validate(step) {
    const f=this.data.form
    if (step===1 && (!f.name.trim() || !f.birthDate || !f.phone.trim() || !f.heightCm || !f.weightKg || !f.city.trim())) return '请完整填写基本信息'
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
    this.setData({submitting:true})
    try {
      const f=this.data.form
      const result=await business.submitFriendAssessment({ ...f, heightCm:Number(f.heightCm), weightKg:Number(f.weightKg), waterIntakeMl:Number(f.waterIntakeMl) })
      wx.removeStorageSync(DRAFT_KEY)
      this.setData({submitted:true,assessmentCount:Number(result.assessmentCount||this.data.assessmentCount+1)})
    } catch(error) { wx.showToast({title:error.message||'提交失败，请重试',icon:'none'}) }
    finally { this.setData({submitting:false}) }
  },
  backMine() { wx.navigateBack({delta:1}) },
  registerAgain() { const form={...emptyForm(),name:this.data.form.name,nickname:this.data.form.nickname,birthDate:this.data.form.birthDate,phone:this.data.form.phone,heightCm:this.data.form.heightCm,city:this.data.form.city};this.setData({submitted:false,step:1,form,...selectionMaps(form)}) }
})

function formatDate(value) { return value ? String(value).slice(0,10) : '' }
function selectionMaps(form) {
  const make=list=>{const value={};(list||[]).forEach(item=>{value[item]=true});return value}
  return { cleanBodyGoalSelected:make(form.cleanBodyGoals),emotionSelected:make(form.emotionalStatus),healthSelected:make(form.healthConditions) }
}
