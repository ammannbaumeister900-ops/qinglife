import request from '@/utils/request'

// 查询状态模板项目列表
export function listPublishTemplateProject(query) {
  return request({
    url: '/life/publishTemplateProject/list',
    method: 'get',
    params: query
  })
}

// 查询状态模板项目详细
export function getPublishTemplateProject(id) {
  return request({
    url: '/life/publishTemplateProject/' + id,
    method: 'get'
  })
}

// 新增状态模板项目
export function addPublishTemplateProject(data) {
  return request({
    url: '/life/publishTemplateProject',
    method: 'post',
    data: data
  })
}

// 修改状态模板项目
export function updatePublishTemplateProject(data) {
  return request({
    url: '/life/publishTemplateProject',
    method: 'put',
    data: data
  })
}

// 删除状态模板项目
export function delPublishTemplateProject(id) {
  return request({
    url: '/life/publishTemplateProject/' + id,
    method: 'delete'
  })
}

// 导出状态模板项目
export function exportPublishTemplateProject(query) {
  return request({
    url: '/life/publishTemplateProject/export',
    method: 'get',
    params: query
  })
}