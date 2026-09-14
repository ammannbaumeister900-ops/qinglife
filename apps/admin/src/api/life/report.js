import request from '@/utils/request'

export function listReport(query) {
  return request({ url: '/life/report/list', method: 'get', params: query })
}

export function handleReport(id, data) {
  return request({ url: '/life/report/' + id, method: 'put', data })
}
