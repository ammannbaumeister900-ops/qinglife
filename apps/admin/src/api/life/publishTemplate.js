import request from '@/utils/request'

// 查询动态模板列表
export function listPublishTemplate(query) {
  return request({
    url: '/life/publishTemplate/list',
    method: 'get',
    params: query
  })
}

// 查询动态模板详细
export function getPublishTemplate(id) {
  return request({
    url: '/life/publishTemplate/' + id,
    method: 'get'
  })
}

// 新增动态模板
export function addPublishTemplate(data) {
  return request({
    url: '/life/publishTemplate',
    method: 'post',
    data: data
  })
}

// 修改动态模板
export function updatePublishTemplate(data) {
  return request({
    url: '/life/publishTemplate',
    method: 'put',
    data: data
  })
}

// 删除动态模板
export function delPublishTemplate(id) {
  return request({
    url: '/life/publishTemplate/' + id,
    method: 'delete'
  })
}

// 导出动态模板
export function exportPublishTemplate(query) {
  return request({
    url: '/life/publishTemplate/export',
    method: 'get',
    params: query
  })
}

export function getTemplateList() {
  return request({
    url: '/life/publishTemplate/getTemplateList',
    method: 'get'
  })
}
