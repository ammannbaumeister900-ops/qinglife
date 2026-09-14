import request from '@/utils/request'

// 查询动态评论列表
export function listUserComment(query) {
  return request({
    url: '/life/userComment/list',
    method: 'get',
    params: query
  })
}

// 查询动态评论详细
export function getUserComment(id) {
  return request({
    url: '/life/userComment/' + id,
    method: 'get'
  })
}

// 新增动态评论
export function addUserComment(data) {
  return request({
    url: '/life/userComment',
    method: 'post',
    data: data
  })
}

// 修改动态评论
export function updateUserComment(data) {
  return request({
    url: '/life/userComment',
    method: 'put',
    data: data
  })
}

// 删除动态评论
export function delUserComment(id) {
  return request({
    url: '/life/userComment/' + id,
    method: 'delete'
  })
}

// 导出动态评论
export function exportUserComment(query) {
  return request({
    url: '/life/userComment/export',
    method: 'get',
    params: query
  })
}