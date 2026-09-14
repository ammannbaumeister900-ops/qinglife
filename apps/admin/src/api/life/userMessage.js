import request from '@/utils/request'

// 查询用户消息列表
export function listUserMessage(query) {
  return request({
    url: '/life/userMessage/list',
    method: 'get',
    params: query
  })
}

// 查询用户消息详细
export function getUserMessage(id) {
  return request({
    url: '/life/userMessage/' + id,
    method: 'get'
  })
}

// 新增用户消息
export function addUserMessage(data) {
  return request({
    url: '/life/userMessage',
    method: 'post',
    data: data
  })
}

// 修改用户消息
export function updateUserMessage(data) {
  return request({
    url: '/life/userMessage',
    method: 'put',
    data: data
  })
}

// 删除用户消息
export function delUserMessage(id) {
  return request({
    url: '/life/userMessage/' + id,
    method: 'delete'
  })
}

// 导出用户消息
export function exportUserMessage(query) {
  return request({
    url: '/life/userMessage/export',
    method: 'get',
    params: query
  })
}