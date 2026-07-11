import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '@/router'

const request = axios.create({
  baseURL: '/api',
  timeout: 10000
})

// Request interceptor — attach JWT token
request.interceptors.request.use(config => {
  const token = sessionStorage.getItem('accessToken')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// Response interceptor — handle errors globally
request.interceptors.response.use(
  response => {
    const { code, message, data } = response.data
    if (code === 200) return data
    if (code === 401) {
      sessionStorage.removeItem('accessToken')
      sessionStorage.removeItem('refreshToken')
      sessionStorage.removeItem('userInfo')
      router.push('/login')
      return Promise.reject(new Error('未登录'))
    }
    const err = new Error(message || '请求失败')
    err.code = code
    ElMessage.error(message || '请求失败')
    return Promise.reject(err)
  },
  error => {
    // 尝试从 HTTP 错误响应中提取后端返回的错误消息
    const data = error.response?.data
    if (data?.message) {
      ElMessage.error(data.message)
    } else if (error.code === 'ECONNABORTED') {
      ElMessage.error('请求超时，请稍后重试')
    } else if (!error.response) {
      ElMessage.error('网络异常，请稍后重试')
    } else {
      const statusMsg = { 400: '请求参数错误', 401: '认证失败', 403: '无权限访问', 404: '资源不存在', 500: '服务器内部错误' }
      ElMessage.error(statusMsg[error.response.status] || `请求失败 (${error.response.status})`)
    }
    return Promise.reject(error)
  }
)

export default request
