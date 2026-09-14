import request from '@/utils/request'

// 查询联系信息列表
export function listContact(query) {
  return request({
    url: '/life/contact/list',
    method: 'get',
    params: query
  })
}

// 查询联系信息详细
export function getContact(id) {
  return request({
    url: '/life/contact/' + id,
    method: 'get'
  })
}

// 新增联系信息
export function addContact(data) {
  return request({
    url: '/life/contact',
    method: 'post',
    data: data
  })
}

// 修改联系信息
export function updateContact(data) {
  return request({
    url: '/life/contact',
    method: 'put',
    data: data
  })
}

// 删除联系信息
export function delContact(id) {
  return request({
    url: '/life/contact/' + id,
    method: 'delete'
  })
}

// 导出联系信息
export function exportContact(query) {
  return request({
    url: '/life/contact/export',
    method: 'get',
    params: query
  })
}