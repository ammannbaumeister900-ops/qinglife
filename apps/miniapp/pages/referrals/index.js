const business = require('../../services/business-api')
const discovery = require('../../utils/discovery')
const presets = require('../../utils/invitation-presets')
const statusText = { pending:'待确认', confirmed:'已确认', waitlisted:'候补中', cancelled:'已取消', completed:'已完成' }
Page({
  data: { loading:true, error:'', eligible:false, sessions:[], records:[], sharePath:'', preparing:false, presets, selectedPreset:presets[0], selectedSessionId:'' },
  onShow() { return this.refresh() },
  onUnload() { this.requestVersion = (this.requestVersion || 0) + 1 },
  async refresh() {
    const version = this.requestVersion = (this.requestVersion || 0) + 1
    if (wx.hideShareMenu) wx.hideShareMenu()
    this.setData({loading:true,error:'',sharePath:'',records:[],sessions:[],eligible:false,preparing:false,selectedSessionId:''})
    try {
      const [{state,activities},records] = await Promise.all([business.context(),business.myReferrals()])
      if (version !== this.requestVersion) return
      const sessions = discovery.activities(activities).filter(s=>s.canRegister)
      this.setData({ eligible:!!state.loggedIn && !!state.invitationEligible, sessions, selectedSessionId:sessions.length?sessions[0].id:'', records:records.map(r=>({...r,statusText:statusText[r.registrationStatus]||r.registrationStatus})) })
    } catch (e) {
      if (version === this.requestVersion) this.setData({error:e.message||'暂时无法读取邀请记录'})
    } finally {
      if (version === this.requestVersion) this.setData({loading:false})
    }
  },
  choosePreset(e) {
    const preset = presets.find(p=>p.id===e.currentTarget.dataset.id)
    if (preset) this.setData({selectedPreset:preset})
  },
  chooseSession(e) {
    if (this.data.preparing) return
    const id=e.currentTarget.dataset.id
    if (!this.data.sessions.some(s=>s.id===id)) return
    if (wx.hideShareMenu) wx.hideShareMenu()
    this.setData({selectedSessionId:id,sharePath:''})
  },
  async prepare(e) {
    if(this.data.preparing || !this.data.eligible || this.data.loading) return
    const id=e && e.currentTarget && e.currentTarget.dataset.id || this.data.selectedSessionId
    const session=this.data.sessions.find(s=>s.id===id)
    if (!session) return wx.showToast({title:'请先选择一期轻体营',icon:'none'})
    const version=this.requestVersion
    this.setData({preparing:true,sharePath:''})
    try {
      const invitation=await business.createInvitation(id)
      if (version !== this.requestVersion) return
      this.setData({sharePath:invitation.path,selectedSessionNumber:session.sessionNumber,selectedSessionId:id})
      if (wx.showShareMenu) wx.showShareMenu({menus:['shareAppMessage']})
    } catch(e) {
      if (version === this.requestVersion) wx.showToast({title:e.message||'准备邀请失败',icon:'none'})
    } finally {
      if (version === this.requestVersion) this.setData({preparing:false})
    }
  },
  onShareAppMessage() {
    return {title:this.data.sharePath?this.data.selectedPreset.title+' · 第 '+this.data.selectedSessionNumber+' 期轻体营':'轻生活',path:this.data.sharePath||'/pages/home/index',imageUrl:this.data.selectedPreset.image}
  }
})
