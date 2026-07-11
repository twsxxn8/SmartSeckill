<template>
  <div class="dashboard">
    <h2>仪表盘</h2>
    <el-row :gutter="20" class="stat-cards">
      <el-col :span="6" v-for="card in cards" :key="card.label">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-value" :style="{ color: card.color }">{{ card.value }}</div>
          <div class="stat-label">{{ card.label }}</div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="20" style="margin-top:24px">
      <el-col :span="12">
        <el-card shadow="hover">
          <template #header><b>用户概览</b></template>
          <el-descriptions :column="1" border>
            <el-descriptions-item label="总用户数">{{ stats.totalUsers }}</el-descriptions-item>
            <el-descriptions-item label="活跃用户">{{ stats.activeUsers }}</el-descriptions-item>
            <el-descriptions-item label="今日新增">{{ stats.todayNewUsers }}</el-descriptions-item>
          </el-descriptions>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="hover">
          <template #header><b>订单概览</b></template>
          <el-descriptions :column="1" border>
            <el-descriptions-item label="总订单">{{ stats.totalOrders }}</el-descriptions-item>
            <el-descriptions-item label="待支付">{{ stats.pendingOrders }}</el-descriptions-item>
            <el-descriptions-item label="已支付">{{ stats.paidOrders }}</el-descriptions-item>
            <el-descriptions-item label="已发货">{{ stats.deliveredOrders }}</el-descriptions-item>
            <el-descriptions-item label="今日订单">{{ stats.todayOrders }}</el-descriptions-item>
          </el-descriptions>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="20" style="margin-top:24px">
      <el-col :span="12">
        <el-card shadow="hover">
          <template #header><b>商品 & 秒杀</b></template>
          <el-descriptions :column="1" border>
            <el-descriptions-item label="商品总数">{{ stats.totalProducts }}</el-descriptions-item>
            <el-descriptions-item label="已上架">{{ stats.publishedProducts }}</el-descriptions-item>
            <el-descriptions-item label="进行中秒杀">{{ stats.activeSeckills }}</el-descriptions-item>
          </el-descriptions>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="hover">
          <template #header><b>快捷操作</b></template>
          <el-space direction="vertical" style="width:100%">
            <el-button type="primary" @click="$router.push('/admin/products')" style="width:100%">管理商品</el-button>
            <el-button type="warning" @click="$router.push('/admin/activities')" style="width:100%">管理秒杀活动</el-button>
            <el-button type="success" @click="$router.push('/admin/orders')" style="width:100%">处理订单</el-button>
            <el-button type="info" @click="$router.push('/admin/users')" style="width:100%">管理用户</el-button>
          </el-space>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { getDashboard } from '@/api/admin'

const stats = ref({})

onMounted(async () => {
  try { stats.value = await getDashboard() } catch { stats.value = {} }
})

const cards = computed(() => [
  { label: '总用户', value: stats.value.totalUsers ?? '--', color: '#409EFF' },
  { label: '总商品', value: stats.value.totalProducts ?? '--', color: '#67C23A' },
  { label: '总订单', value: stats.value.totalOrders ?? '--', color: '#E6A23C' },
  { label: '进行中秒杀', value: stats.value.activeSeckills ?? '--', color: '#F56C6C' }
])
</script>

<style scoped>
.dashboard h2 { margin-bottom: 24px; }
.stat-card { text-align: center; }
.stat-value { font-size: 36px; font-weight: 700; }
.stat-label { font-size: 14px; color: #999; margin-top: 8px; }
</style>
