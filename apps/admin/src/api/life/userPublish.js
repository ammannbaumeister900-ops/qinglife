import request from '@/utils/request'

// 查询用户动态列表
export function listUserPublish(query) {
  return request({
    url: '/life/userPublish/list',
    method: 'get',
    params: query
  })
}

// 查询用户动态详细
export function getUserPublish(id) {
  return request({
    url: '/life/userPublish/' + id,
    method: 'get'
  })
}

// 新增用户动态
export function addUserPublish(data) {
  return request({
    url: '/life/userPublish',
    method: 'post',
    data: data
  })
}

// 修改用户动态
export function updateUserPublish(data) {
  return request({
    url: '/life/userPublish',
    method: 'put',
    data: data
  })
}

// 删除用户动态
export function delUserPublish(id) {
  return request({
    url: '/life/userPublish/' + id,
    method: 'delete'
  })
}

// 导出用户动态
export function exportUserPublish(query) {
  return request({
    url: '/life/userPublish/export',
    method: 'get',
    params: query
  })
}