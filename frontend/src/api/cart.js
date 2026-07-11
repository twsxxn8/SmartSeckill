import request from './request'

export function getCart() {
  return request.get('/cart')
}

export function addToCart(data) {
  return request.post('/cart', data)
}

export function updateCartQty(skuId, quantity) {
  return request.put(`/cart/${skuId}?quantity=${quantity}`)
}

export function removeFromCart(skuId) {
  return request.delete(`/cart/${skuId}`)
}

export function clearCart() {
  return request.delete('/cart/clear')
}

export function getCartCount() {
  return request.get('/cart/count')
}
