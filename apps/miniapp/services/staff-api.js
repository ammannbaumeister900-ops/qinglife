const business = require('./business-api')
const runtime = require('../config/runtime')
const request = (path, method = 'GET', data) => business.request('/staff' + path, method, data)
async function image(id) {
  const token = await business.login()
  return new Promise((resolve, reject) => wx.downloadFile({ url: runtime.businessBaseUrl + '/app/qinglife/staff/images/' + encodeURIComponent(id), header: { token }, success: r => r.statusCode === 200 ? resolve(r.tempFilePath) : reject(new Error('图片读取失败或权限已失效')), fail: reject }))
}
async function encodeImage(path) {
  return new Promise((resolve, reject) => wx.getFileSystemManager().readFile({ filePath: path, encoding: 'base64', success: r => resolve(r.data), fail: reject }))
}
module.exports = { request, image, encodeImage }
