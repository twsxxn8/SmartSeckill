import request from './request'

// 用户优惠券
export function getMyCoupons(status) {
  const params = status !== undefined ? { status } : {}
  return request.get('/coupon/my', { params })
}

export function claimCoupon(couponId) {
  return request.post(`/coupon/${couponId}/claim`)
}

export function getAvailableCoupons() {
  return request.get('/coupon/available')
}
