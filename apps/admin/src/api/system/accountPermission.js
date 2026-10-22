import request from '@/utils/request'
export function listAccounts(params) { return request({ url: '/system/account-permissions', params }) }
export function getAccountPages(id) { return request({ url: '/system/account-permissions/' + id }) }
export function saveAccountPages(id, data) { return request({ url: '/system/account-permissions/' + id, method: 'put', data }) }
