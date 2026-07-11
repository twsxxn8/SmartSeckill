<template>
  <div class="order-manage">
    <div class="toolbar"><h2>订单管理</h2></div>

    <div class="search-bar">
      <el-input v-model="searchSn" placeholder="输入订单编号查询" clearable style="width:240px" @keyup.enter="fetch" />
      <el-select v-model="filterStatus" placeholder="订单状态" clearable style="width:150px" @change="fetch">
        <el-option label="待支付" :value="0" />
        <el-option label="已支付" :value="1" />
        <el-option label="已发货" :value="2" />
        <el-option label="已完成" :value="3" />
        <el-option label="已取消" :value="4" />
      </el-select>
      <el-button type="primary" @click="fetch">查询</el-button>
    </div>

    <el-table :data="orders" v-loading="loading" border stripe>
      <el-table-column prop="orderSn" label="订单编号" width="180" />
      <el-table-column label="用户" width="80">
        <template #default="{ row }">#{{ row.userId }}</template>
      </el-table-column>
      <el-table-column prop="payAmount" label="金额" width="100">
        <template #default="{ row }">¥{{ row.payAmount || row.totalAmount }}</template>
      </el-table-column>
      <el-table-column label="类型" width="80">
        <template #default="{ row }">
          <el-tag size="small" :type="row.orderType === 1 ? 'danger' : ''">{{ row.orderType === 1 ? '秒杀' : '普通' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag size="small" :type="statusType(row.status)">{{ statusText(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="receiverName" label="收货人" width="80" />
      <el-table-column prop="createTime" label="下单时间" width="170" />
      <el-table-column label="操作" width="140" fixed="right">
        <template #default="{ row }">
          <el-button v-if="row.status === 1" size="small" type="success" @click="deliver(row.orderSn)">发货</el-button>
          <el-tag v-else-if="row.status === 2" size="small" type="info">已发货</el-tag>
          <el-tag v-else-if="row.status === 3" size="small" type="success">已完成</el-tag>
          <el-tag v-else-if="row.status === 4" size="small" type="info">已取消</el-tag>
          <span v-else>--</span>
        </template>
      </el-table-column>
    </el-table>

    <div class="pagination" v-if="total > size">
      <el-pagination v-model:current-page="page" :page-size="size" :total="total" layout="prev, pager, next" @current-change="fetch" />
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { getAllOrders, deliverOrder } from '@/api/admin'
import { ElMessage } from 'element-plus'

const orders = ref([])
const loading = ref(false)
const filterStatus = ref(null)
const searchSn = ref('')
const page = ref(1)
const size = ref(10)
const total = ref(0)

const statusMap = { 0: '待支付', 1: '已支付', 2: '已发货', 3: '已完成', 4: '已取消' }
const statusType = (s) => s === 0 ? 'warning' : s === 1 ? 'primary' : s === 2 ? 'info' : s === 3 ? 'success' : 'info'
const statusText = (s) => statusMap[s] ?? '未知'

async function fetch() {
  loading.value = true
  try {
    const res = await getAllOrders({
      page: page.value, size: size.value,
      orderSn: searchSn.value || undefined,
      status: filterStatus.value !== null ? filterStatus.value : undefined
    })
    orders.value = res.records
    total.value = res.total
  } catch { orders.value = [] }
  finally { loading.value = false }
}

async function deliver(orderSn) {
  try {
    await deliverOrder(orderSn)
    ElMessage.success('发货成功')
    fetch()
  } catch { /* handled */ }
}

onMounted(fetch)
</script>

<style scoped>
.toolbar { margin-bottom: 16px; }
.toolbar h2 { font-size: 20px; }
.search-bar { display: flex; gap: 12px; margin-bottom: 16px; }
.pagination { display: flex; justify-content: center; padding: 20px; }
</style>
