import request from '@/utils/request'

export function listRegistration(query) {
  return request({ url: '/life/registration/list', method: 'get', params: query })
}

export function getRegistration(id) {
  return request({ url: '/life/registration/' + id, method: 'get' })
}

export function addRegistration(data) {
  return request({ url: '/life/registration', method: 'post', data })
}

export function updateRegistration(data) {
  return request({ url: '/life/registration', method: 'put', data })
}

export function changePayment(id, data) {
  return request({ url: '/life/registration/' + id + '/payment', method: 'put', data })
}

export function confirmSettlement(id, data) {
  return request({ url: '/life/registration/' + id + '/settlement', method: 'put', data })
}

export function changeBatchPayment(batchId, data) {
  return request({ url: '/life/registration/batch/' + batchId + '/payment', method: 'put', data })
}

export function cancelBatchRegistration(batchId, reason) {
  return request({ url: '/life/registration/batch/' + encodeURIComponent(batchId) + '/cancel', method: 'put', data: { reason } })
}
