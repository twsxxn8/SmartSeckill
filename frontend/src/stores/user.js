import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { login as loginApi, register as registerApi, sendCode, refreshToken as refreshTokenApi, logout as logoutApi } from '@/api/auth'
import { ElMessage } from 'element-plus'
import router from '@/router'

export const useUserStore = defineStore('user', () => {
  const token = ref(sessionStorage.getItem('accessToken') || '')
  const refreshTokenValue = ref(sessionStorage.getItem('refreshToken') || '')
  const userInfo = ref(JSON.parse(sessionStorage.getItem('userInfo') || 'null'))

  const isLoggedIn = computed(() => !!token.value)
  const isAdmin = computed(() => userInfo.value?.role === 'ROLE_ADMIN')
  const userId = computed(() => userInfo.value?.userId)

  function saveAuth(data) {
    token.value = data.accessToken
    refreshTokenValue.value = data.refreshToken
    userInfo.value = {
      userId: data.userId,
      username: data.username,
      role: data.role
    }
    sessionStorage.setItem('accessToken', data.accessToken)
    sessionStorage.setItem('refreshToken', data.refreshToken)
    sessionStorage.setItem('userInfo', JSON.stringify(userInfo.value))
  }

  function clearAuth() {
    token.value = ''
    refreshTokenValue.value = ''
    userInfo.value = null
    sessionStorage.removeItem('accessToken')
    sessionStorage.removeItem('refreshToken')
    sessionStorage.removeItem('userInfo')
  }

  async function login(username, password) {
    const data = await loginApi(username, password)
    saveAuth(data)
    ElMessage.success('登录成功')
    router.push('/')
  }

  async function register(form) {
    await registerApi(form)
    ElMessage.success('注册成功，请登录')
    router.push('/login')
  }

  async function sendVerificationCode(target) {
    await sendCode(target)
    ElMessage.success('验证码已发送')
  }

  async function refresh() {
    try {
      const data = await refreshTokenApi(refreshTokenValue.value)
      saveAuth(data)
    } catch {
      clearAuth()
      router.push('/login')
    }
  }

  function logout() {
    // 先通知服务端将 token 加入黑名单（fire-and-forget，即使失败也要清除本地状态）
    logoutApi(refreshTokenValue.value).catch(() => {})
    clearAuth()
    ElMessage.success('已退出登录')
    router.push('/')
  }

  return {
    token, refreshTokenValue, userInfo, isLoggedIn, isAdmin, userId,
    login, register, sendVerificationCode, refresh, logout, clearAuth
  }
})
