import request from '@/utils/request'

export function listPass(query) { return request({ url: '/life/pass/list', method: 'get', params: query }) }
export function getPassLedger(id) { return request({ url: '/life/pass/' + id + '/ledger', method: 'get' }) }
export function openPass(data) { return request({ url: '/life/pass', method: 'post', data }) }
export function adjustPass(id, data) { return request({ url: '/life/pass/' + id + '/adjustments', method: 'post', data }) }
