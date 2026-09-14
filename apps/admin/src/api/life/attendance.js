import request from '@/utils/request'

export function listAttendance(query) {
  return request({ url: '/life/attendance/list', method: 'get', params: query })
}

export function updateAttendanceStatus(id, data) {
  return request({ url: '/life/attendance/' + id + '/status', method: 'put', data })
}

export function attendanceHistory(id) {
  return request({ url: '/life/attendance/' + id + '/history', method: 'get' })
}
