import request from '@/utils/request'

// 查询注册用户信息列表
export function listUserInfo(query) {
  return request({
    url: '/life/userInfo/list',
    method: 'get',
    params: query
  })
}

// 查询注册用户信息详细
export function getUserInfo(id) {
  return request({
    url: '/life/userInfo/' + id,
    method: 'get'
  })
}

// 新增注册用户信息
export function addUserInfo(data) {
  return request({
    url: '/life/userInfo',
    method: 'post',
    data: data
  })
}

// 修改注册用户信息
export function updateUserInfo(data) {
  return request({
    url: '/life/userInfo',
    method: 'put',
    data: data
  })
}

// 删除注册用户信息
export function delUserInfo(id) {
  return request({
    url: '/life/userInfo/' + id,
    method: 'delete'
  })
}

// 用户状态修改
export function changeStatus(id, status) {
  const data = {
    id,
    status
  }
  return request({
    url: '/life/userInfo/changeStatus',
    method: 'put',
    data: data
  })
}