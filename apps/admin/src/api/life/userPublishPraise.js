import request from '@/utils/request'

// 查询用户动态点赞记录列表
export function listUserPublishPraise(query) {
  return request({
    url: '/life/userPublishPraise/list',
    method: 'get',
    params: query
  })
}

// 查询用户动态点赞记录详细
export function getUserPublishPraise(id) {
  return request({
    url: '/life/userPublishPraise/' + id,
    method: 'get'
  })
}

// 新增用户动态点赞记录
export function addUserPublishPraise(data) {
  return request({
    url: '/life/userPublishPraise',
    method: 'post',
    data: data
  })
}

// 修改用户动态点赞记录
export function updateUserPublishPraise(data) {
  return request({
    url: '/life/userPublishPraise',
    method: 'put',
    data: data
  })
}

// 删除用户动态点赞记录
export function delUserPublishPraise(id) {
  return request({
    url: '/life/userPublishPraise/' + id,
    method: 'delete'
  })
}

// 导出用户动态点赞记录
export function exportUserPublishPraise(query) {
  return request({
    url: '/life/userPublishPraise/export',
    method: 'get',
    params: query
  })
}