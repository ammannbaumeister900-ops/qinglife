import request from '@/utils/request'

// 查询动态标签列表
export function listTag(query) {
  return request({
    url: '/life/tag/list',
    method: 'get',
    params: query
  })
}

// 查询动态标签详细
export function getTag(id) {
  return request({
    url: '/life/tag/' + id,
    method: 'get'
  })
}

// 新增动态标签
export function addTag(data) {
  return request({
    url: '/life/tag',
    method: 'post',
    data: data
  })
}

// 修改动态标签
export function updateTag(data) {
  return request({
    url: '/life/tag',
    method: 'put',
    data: data
  })
}

// 删除动态标签
export function delTag(id) {
  return request({
    url: '/life/tag/' + id,
    method: 'delete'
  })
}

// 导出动态标签
export function exportTag(query) {
  return request({
    url: '/life/tag/export',
    method: 'get',
    params: query
  })
}