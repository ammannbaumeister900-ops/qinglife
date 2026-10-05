import request from '@/utils/request'
export function selectCustomers() { return request({ url: '/life/selection/customers' }) }
export function selectSessions() { return request({ url: '/life/selection/sessions' }) }
export function selectRegistrationPasses(id) { return request({ url: '/life/selection/registrations/' + id + '/passes' }) }
