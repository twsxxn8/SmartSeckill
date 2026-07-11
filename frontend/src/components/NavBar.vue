<template>
  <el-menu mode="horizontal" :ellipsis="false" class="navbar">
    <div class="nav-left">
      <router-link to="/" class="logo">SmartSeckill</router-link>
      <el-menu-item index="/" @click="$router.push('/')">首页</el-menu-item>
      <el-menu-item index="/seckill" @click="$router.push('/seckill')">
        <span style="color: #e74c3c; font-weight: bold">秒杀</span>
      </el-menu-item>
      <el-menu-item index="/coupons" @click="$router.push('/coupons')">领券中心</el-menu-item>
    </div>
    <div class="nav-right">
      <template v-if="userStore.isLoggedIn">
        <el-menu-item index="/cart" @click="$router.push('/cart')">
          <el-icon><ShoppingCart /></el-icon>
          <span>购物车</span>
          <el-badge v-if="cartCount > 0" :value="cartCount" :max="99" style="margin-left: 4px" />
        </el-menu-item>
        <el-menu-item index="/orders" @click="$router.push('/orders')">我的订单</el-menu-item>
        <el-sub-menu index="user">
          <template #title>{{ userStore.userInfo?.username }}</template>
          <el-menu-item @click="$router.push('/profile')">个人中心</el-menu-item>
          <el-menu-item v-if="userStore.isAdmin" @click="$router.push('/admin')">后台管理</el-menu-item>
          <el-menu-item @click="userStore.logout()">退出登录</el-menu-item>
        </el-sub-menu>
      </template>
      <template v-else>
        <el-menu-item index="/login" @click="$router.push('/login')">登录</el-menu-item>
        <el-menu-item index="/register" @click="$router.push('/register')">注册</el-menu-item>
      </template>
    </div>
  </el-menu>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useUserStore } from '@/stores/user'
import { getCartCount } from '@/api/cart'

const userStore = useUserStore()
const cartCount = ref(0)

async function fetchCount() {
  try { cartCount.value = await getCartCount() } catch { cartCount.value = 0 }
}

onMounted(() => { if (userStore.isLoggedIn) fetchCount() })
// Refresh cart count when navigating
import { watch } from 'vue'
import { useRoute } from 'vue-router'
const route = useRoute()
watch(() => route.path, () => { if (userStore.isLoggedIn) fetchCount() })
</script>

<style scoped>
.navbar {
  display: flex;
  justify-content: space-between;
  padding: 0 40px;
  position: sticky;
  top: 0;
  z-index: 100;
}
.nav-left, .nav-right { display: flex; align-items: center; }
.logo {
  font-size: 20px;
  font-weight: 800;
  color: #e74c3c;
  margin-right: 20px;
  letter-spacing: -0.5px;
}
</style>
