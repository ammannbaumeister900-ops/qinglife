import request from '@/utils/request'

export function listCustomer(query) {
  return request({ url: '/life/customer/list', method: 'get', params: query })
}

export function getCustomer(id) {
  return request({ url: '/life/customer/' + id, method: 'get' })
}

export function getCustomerTimeline(id) {
  return request({ url: '/life/customer/' + id + '/timeline', method: 'get' })
}

export function addCustomer(data) {
  return request({ url: '/life/customer', method: 'post', data })
}

export function updateCustomer(data) {
  return request({ url: '/life/customer', method: 'put', data })
}
