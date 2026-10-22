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

export function confirmOfflineReceipt(id, data) {
  return request({ url: '/life/registration/' + encodeURIComponent(id) + '/offline-receipt', method: 'post', data })
}

export function listOfflineReceipts(id) {
  return request({ url: '/life/registration/' + encodeURIComponent(id) + '/offline-receipts', method: 'get' })
}

export function confirmRegistrationPayment(id, data) {
  return request({ url: '/life/registration/' + id + '/payment-confirmation', method: 'put', data })
}

export function confirmSettlement(id, data) {
  return request({ url: '/life/registration/' + id + '/settlement', method: 'put', data })
}

export function revokeSettlement(id, settlementId, data) {
  return request({ url: '/life/registration/' + encodeURIComponent(id) + '/settlement/' + encodeURIComponent(settlementId) + '/revoke', method: 'put', data })
}

export function changeBatchPayment(batchId, data) {
  return request({ url: '/life/registration/batch/' + batchId + '/payment', method: 'put', data })
}

export function cancelBatchRegistration(batchId, reason) {
  return request({ url: '/life/registration/batch/' + encodeURIComponent(batchId) + '/cancel', method: 'put', data: { reason }})
}

export function getOfflineReceiptImage(registrationId, imageId) {
  return request({ url: '/life/registration/' + encodeURIComponent(registrationId) + '/offline-receipt-images/' + encodeURIComponent(imageId), method: 'get', responseType: 'blob' })
}

export function getParticipantFinance(batchId) {
  return request({ url: '/life/registration/batch/' + encodeURIComponent(batchId) + '/finance', method: 'get', financeErrors: true })
}

export function getParticipantPasses(id, scope) {
  return request({ url: '/life/selection/registrations/' + encodeURIComponent(id) + '/passes', method: 'get', params: { scope }, financeErrors: true })
}

// Fixed routes only. Each write carries the immutable operation ID prepared by the dialog.
export function writeParticipantFinance(kind, id, target, data) {
  const person = '/life/registration/' + encodeURIComponent(id)
  const routes = {
    capture: [person + '/offline-receipt', 'post'],
    allocate: ['/life/registration/batch/' + encodeURIComponent(id) + '/receipt-allocations', 'post'],
    cash: [person + '/settlement', 'put'],
    pass: [person + '/settlement', 'put'],
    refund: [person + '/refund-requests', 'post'],
    processing: [person + '/refund-requests/' + encodeURIComponent(target) + '/processing', 'put'],
    payment: [person + '/refund-requests/' + encodeURIComponent(target) + '/payments', 'post'],
    refundCancel: [person + '/refund-requests/' + encodeURIComponent(target) + '/cancel', 'put'],
    refundFail: [person + '/refund-requests/' + encodeURIComponent(target) + '/fail', 'put'],
    verify: [person + '/pass-verifications', 'post'],
    revokeVerification: [person + '/pass-verifications/' + encodeURIComponent(target) + '/revoke', 'put'],
    returnPass: [person + '/settlement/' + encodeURIComponent(target) + '/revoke', 'put'],
    cancel: [person + '/cancel', 'put']
  }
  if (!routes[kind]) return Promise.reject(new Error('未知资金操作'))
  return request({ url: routes[kind][0], method: routes[kind][1], data, financeErrors: true })
}
