import request from './request'

export function getSeckillList() {
  return request.get('/seckill/list')
}

export function getSeckillDetail(activityId) {
  return request.get(`/seckill/detail/${activityId}`)
}

export function getSeckillToken(activityId) {
  return request.get('/seckill/token', { params: { activityId } })
}

export function executeSeckill(data) {
  return request.post('/seckill/execute', data)
}

export function getSeckillResult(activityId) {
  return request.get(`/seckill/result/${activityId}`)
}

export function getOnlineCount(activityId) {
  return request.get(`/seckill/online-count/${activityId}`)
}

// Admin
export function createActivity(data) {
  return request.post('/seckill/admin/activity', data)
}

export function preWarmStock(activityId) {
  return request.post(`/seckill/admin/prewarm/${activityId}`)
}
