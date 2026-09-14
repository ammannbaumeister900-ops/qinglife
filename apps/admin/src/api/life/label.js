import request from '@/utils/request'

// 查询文章标签列表
export function listLabel(query) {
  return request({
    url: '/life/label/list',
    method: 'get',
    params: query
  })
}

// 查询文章标签详细
export function getLabel(id) {
  return request({
    url: '/life/label/' + id,
    method: 'get'
  })
}

// 新增文章标签
export function addLabel(data) {
  return request({
    url: '/life/label',
    method: 'post',
    data: data
  })
}

// 修改文章标签
export function updateLabel(data) {
  return request({
    url: '/life/label',
    method: 'put',
    data: data
  })
}

// 删除文章标签
export function delLabel(id) {
  return request({
    url: '/life/label/' + id,
    method: 'delete'
  })
}

export function getLabelList(){
  return request({
    url: '/life/label/getLabelList',
    method: 'get'
  })
}