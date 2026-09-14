import request from '@/utils/request'

export function listSession(query) {
  return request({ url: '/life/session/list', method: 'get', params: query })
}

export function getSession(id) {
  return request({ url: '/life/session/' + id, method: 'get' })
}

export function addSession(data) {
  return request({ url: '/life/session', method: 'post', data })
}

export function updateSession(data) {
  return request({ url: '/life/session', method: 'put', data })
}

export function createSessionInvitation(id) {
  return request({ url: '/life/session/' + id + '/invitation', method: 'post' })
}
