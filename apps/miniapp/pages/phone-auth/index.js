const business = require('../../services/business-api')
const navigation = require('../../utils/navigation')

// Login is complete only after WeChat verifies the friend's own number; there is no hand-typed fallback.
Page({
  data: { busy: false, prompting: false, notice: '' },

  // The platform can take seconds to show its phone dialog; acknowledge the tap at once.
  // Its only callback comes when the dialog closes, so a timer also restores the label.
  promptPhone() {
    if (this.data.busy) return
    clearTimeout(this._promptTimer)
    this.setData({ prompting: true, notice: '' })
    this._promptTimer = setTimeout(() => this.endPrompt(), 10000)
  },
  endPrompt() {
    clearTimeout(this._promptTimer)
    if (this.data.prompting) this.setData({ prompting: false })
  },
  onHide() { this.endPrompt() },
  onUnload() { this.endPrompt() },

  async onPhoneNumber(event) {
    this.endPrompt()
    if (this.data.busy) return
    const code = event && event.detail && event.detail.code
    if (!code) return this.setData({ notice: '未授权手机号，可先继续浏览。' })
    this.setData({ busy: true, notice: '' })
    try {
      await business.bindPhone(code)
      wx.showToast({ title: '登录成功', icon: 'success' })
      setTimeout(() => this.leave(), 600)
    } catch (error) {
      if (error && error.code === 'IDENTITY_CHANGED') return
      this.setData({ notice: /request:fail|timeout|网络/i.test(String(error && (error.errMsg || error.message))) ? '网络不太稳定，登录没有完成，请稍后重试。' : '手机号授权没有完成，请重试。' })
    } finally { this.setData({ busy: false }) }
  },

  openPrivacy() {
    if (wx.openPrivacyContract) wx.openPrivacyContract({ fail: () => wx.showToast({ title: '隐私保护指引暂时无法打开', icon: 'none' }) })
  },

  leave() {
    if (getCurrentPages().length > 1) return wx.navigateBack({ fail: () => navigation.switchTab({ url: '/pages/mine/index' }) })
    navigation.switchTab({ url: '/pages/mine/index' })
  }
})
