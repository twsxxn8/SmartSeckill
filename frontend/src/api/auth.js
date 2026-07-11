import request from './request'

export function login(username, password) {
  return request.post('/auth/login', { username, password })
}

export function register(data) {
  return request.post('/auth/register', data)
}

export function sendCode(target) {
  return request.post('/auth/verification-code', { target })
}

export function refreshToken(refreshToken) {
  return request.post('/auth/refresh-token', { refreshToken })
}

export function logout(refreshToken) {
  return request.post('/auth/logout', { refreshToken })
}
