import request from '@/utils/request'
export function listReflections(params) { return request({ url: '/life/camp-reflection/list', method: 'get', params }) }
export function reviewReflection(id, action, data) { return request({ url: '/life/camp-reflection/' + id + '/review/' + action, method: 'put', data }) }
export function displayReflection(id, action, data) { return request({ url: '/life/camp-reflection/' + id + '/display/' + action, method: 'put', data }) }

export function approveAndPublishReflection(id, data) { return request({ url: '/life/camp-reflection/' + id + '/approve-and-publish', method: 'put', data }) }
