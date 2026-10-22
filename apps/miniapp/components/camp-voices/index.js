Component({
  properties: { voices: { type: Array, value: [], observer(rows) { this.setData({ lanes: [rows.filter((_,i)=>i%2===0),rows.filter((_,i)=>i%2===1)] }) } }, active: { type: Boolean, value: true } },
  data: { lanes: [], paused: false, inView: false },
  lifetimes: {
    attached() { this.observer = this.createIntersectionObserver(); this.observer.relativeToViewport().observe('.voice-section', res => this.setData({ inView: res.intersectionRatio > 0 })) },
    detached() { if (this.observer) this.observer.disconnect() }
  },
  methods: {
    pause() { this.setData({ paused: !this.data.paused }) },
    read(event) { this.setData({ paused: true }); const voice = this.data.voices.find(row => row.id === event.currentTarget.dataset.id); if (voice) wx.showModal({ title: (voice.author || '本期轻友') + ' · 轻友心声', content: voice.note, showCancel: false, confirmText: '慢慢收好' }) }
  }
})
