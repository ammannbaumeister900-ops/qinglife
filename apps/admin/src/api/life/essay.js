import request from '@/utils/request'

export const listArticlePlacements = () => request({ url: '/life/essay/placements', method: 'get', financeErrors: true })
export const saveArticlePlacement = (key, data) => request({ url: '/life/essay/placements/' + encodeURIComponent(key), method: 'put', data, financeErrors: true })
export const articlePlacementAudit = key => request({ url: '/life/essay/placements/' + encodeURIComponent(key) + '/audit', method: 'get' })
export const articleProvenance = id => request({ url: '/life/essay/' + encodeURIComponent(id) + '/provenance', method: 'get' })
export const articleContentReview = id => request({ url: '/life/essay/' + encodeURIComponent(id) + '/content-review', method: 'get' })

// 查询文章列表
export function listEssay(query) {
  return request({
    url: '/life/essay/list',
    method: 'get',
    params: query
  })
}

// 查询文章详细
export function getEssay(id) {
  return request({
    url: '/life/essay/' + id,
    method: 'get'
  })
}

// 新增文章
export function addEssay(data) {
  return request({
    url: '/life/essay',
    method: 'post',
    data: data
  })
}

// 修改文章
export function updateEssay(data) {
  return request({
    url: '/life/essay',
    method: 'put',
    data: data
  })
}

// 删除文章
export function delEssay(id) {
  return request({
    url: '/life/essay/' + id,
    method: 'delete'
  })
}

// 文章状态修改
export function changeStatus(id, status) {
  const data = {
    id,
    status
  }
  return request({
    url: '/life/essay/changeStatus',
    method: 'put',
    data: data
  })
}
