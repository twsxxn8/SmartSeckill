import request from './request'

export function createOrder(data) {
  return request.post('/order', data)
}

export function payOrder(orderSn) {
  return request.post(`/order/${orderSn}/pay`)
}

export function cancelOrder(orderSn, reason) {
  return request.post(`/order/${orderSn}/cancel`, { reason })
}

export function receiveOrder(orderSn) {
  return request.post(`/order/${orderSn}/receive`)
}

export function getMyOrders(params) {
  return request.get('/order/my', { params })
}

export function getOrderDetail(orderSn) {
  return request.get(`/order/${orderSn}`)
}

export function getOrderItems(orderSn) {
  return request.get(`/order/${orderSn}/items`)
}
