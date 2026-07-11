import request from './request'

// Dashboard
export function getDashboard() {
  return request.get('/admin/dashboard')
}

// Users
export function getUserList(params) {
  return request.get('/admin/users', { params })
}
export function toggleUserStatus(userId, status) {
  return request.put(`/admin/users/${userId}/status`, { status })
}
export function resetUserPassword(userId, password) {
  return request.put(`/admin/users/${userId}/reset-password`, { password })
}
export function assignUserRole(userId, roleId) {
  return request.put(`/admin/users/${userId}/role`, { roleId })
}

// Orders (admin)
export function getAllOrders(params) {
  return request.get('/order/admin/all', { params })
}
export function deliverOrder(orderSn) {
  return request.post(`/order/admin/${orderSn}/deliver`)
}

// Coupons
export function getCouponList(params) {
  return request.get('/coupon/admin/list', { params })
}
export function createCoupon(data) {
  return request.post('/coupon/admin', data)
}
export function updateCoupon(id, data) {
  return request.put(`/coupon/admin/${id}`, data)
}
export function deleteCoupon(id) {
  return request.delete(`/coupon/admin/${id}`)
}

// Seckill (admin extra)
export function updateActivity(id, data) {
  return request.put(`/seckill/admin/activity/${id}`, data)
}
export function deleteActivity(id) {
  return request.delete(`/seckill/admin/activity/${id}`)
}
export function getAllActivities() {
  return request.get('/seckill/admin/activities')
}
