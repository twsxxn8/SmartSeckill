<template>
  <div class="register-page">
    <el-card class="register-card" shadow="always">
      <h2>注册</h2>
      <el-form :model="form" :rules="rules" ref="formRef" label-position="top" @submit.prevent="handleRegister">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" placeholder="请输入用户名" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input v-model="form.password" type="password" placeholder="请输入密码" show-password />
        </el-form-item>
        <el-form-item label="手机号" prop="phone">
          <el-input v-model="form.phone" placeholder="请输入手机号" />
        </el-form-item>
        <el-form-item label="验证码" prop="verificationCode">
          <div style="display:flex;gap:8px">
            <el-input v-model="form.verificationCode" placeholder="请输入验证码" />
            <el-button :disabled="countdown > 0" @click="sendCode">{{ countdown > 0 ? countdown + 's' : '发送验证码' }}</el-button>
          </div>
        </el-form-item>
        <el-form-item>
          <el-button type="danger" native-type="submit" :loading="loading" style="width: 100%">注册</el-button>
        </el-form-item>
      </el-form>
      <p class="switch">已有账号？<router-link to="/login" class="link">立即登录</router-link></p>
    </el-card>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()
const formRef = ref(null)
const loading = ref(false)
const countdown = ref(0)
const form = reactive({ username: '', password: '', phone: '', email: '', nickname: '', verificationCode: '' })
const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, min: 6, message: '密码至少6位', trigger: 'blur' }],
  phone: [{ required: true, message: '请输入手机号', trigger: 'blur' }],
  verificationCode: [{ required: true, message: '请输入验证码', trigger: 'blur' }]
}

async function sendCode() {
  if (!form.phone) return
  await userStore.sendVerificationCode(form.phone)
  countdown.value = 60
  const t = setInterval(() => { countdown.value--; if (countdown.value <= 0) clearInterval(t) }, 1000)
}

async function handleRegister() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  loading.value = true
  try {
    // 清理空的可选字段，避免后端 @Pattern 校验报错
    const data = { ...form }
    if (!data.email) delete data.email
    if (!data.nickname) delete data.nickname
    await userStore.register(data)
  }
  catch { /* error handled by interceptor */ }
  finally { loading.value = false }
}
</script>

<style scoped>
.register-page { display: flex; justify-content: center; align-items: center; min-height: calc(100vh - 60px); background: #f5f7fa; }
.register-card { width: 420px; }
.register-card h2 { text-align: center; margin-bottom: 20px; }
.switch { text-align: center; font-size: 14px; color: #999; }
.link { color: #e74c3c; }
</style>
