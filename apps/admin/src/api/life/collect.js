import request from '@/utils/request'

// 查询用户收藏文章列表
export function listCollect(query) {
  return request({
    url: '/life/collect/list',
    method: 'get',
    params: query
  })
}

// 查询用户收藏文章详细
export function getCollect(id) {
  return request({
    url: '/life/collect/' + id,
    method: 'get'
  })
}

// 新增用户收藏文章
export function addCollect(data) {
  return request({
    url: '/life/collect',
    method: 'post',
    data: data
  })
}

// 修改用户收藏文章
export function updateCollect(data) {
  return request({
    url: '/life/collect',
    method: 'put',
    data: data
  })
}

// 删除用户收藏文章
export function delCollect(id) {
  return request({
    url: '/life/collect/' + id,
    method: 'delete'
  })
}

// 导出用户收藏文章
export function exportCollect(query) {
  return request({
    url: '/life/collect/export',
    method: 'get',
    params: query
  })
}