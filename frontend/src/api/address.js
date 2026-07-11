import request from './request'

export function getMyAddresses() {
  return request.get('/user/address')
}

export function addAddress(data) {
  return request.post('/user/address', data)
}

export function updateAddress(id, data) {
  return request.put(`/user/address/${id}`, data)
}

export function deleteAddress(id) {
  return request.delete(`/user/address/${id}`)
}
