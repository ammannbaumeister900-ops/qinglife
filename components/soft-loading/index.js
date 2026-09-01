Component({
  properties: {
    text: {
      type: String,
      value: '正在轻轻准备…'
    }
  },

  data: {
    ready: false
  },

  lifetimes: {
    attached() {
      this.showTimer = setTimeout(() => this.setData({ ready: true }), 350)
    },

    detached() {
      clearTimeout(this.showTimer)
    }
  }
})

