<template>
  <div class="orders-page">
    <h2>我的订单</h2>
    <el-tabs v-model="statusFilter" @tab-change="fetchOrders">
      <el-tab-pane label="全部" name="" />
      <el-tab-pane label="待支付" name="0" />
      <el-tab-pane label="已支付" name="1" />
      <el-tab-pane label="已发货" name="2" />
      <el-tab-pane label="已完成" name="3" />
      <el-tab-pane label="已取消" name="4" />
    </el-tabs>
    <div class="order-list" v-loading="loading">
      <el-card v-for="order in orders" :key="order.orderSn" class="order-card">
        <div class="order-header">
          <span class="order-sn">订单号：{{ order.orderSn }}</span>
          <el-tag :type="statusMap[order.status]?.type">{{ statusMap[order.status]?.label }}</el-tag>
        </div>
        <div class="order-body">
          <span>金额：<b class="price">¥{{ order.payAmount }}</b></span>
          <span>{{ order.createTime }}</span>
        </div>
        <div class="order-actions">
          <el-button v-if="order.status === 0" type="warning" size="small" @click="pay(order.orderSn)">立即支付</el-button>
          <el-button v-if="order.status === 0" size="small" @click="cancel(order.orderSn)">取消</el-button>
          <el-button v-if="order.status === 2" type="success" size="small" @click="receive(order.orderSn)">确认收货</el-button>
        </div>
      </el-card>
      <el-empty v-if="!loading && orders.length === 0" description="暂无订单" />
    </div>
    <div class="pagination" v-if="total > size">
      <el-pagination v-model:current-page="page" :page-size="size" :total="total" layout="prev, pager, next" @current-change="fetchOrders" />
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { getMyOrders, payOrder, cancelOrder, receiveOrder } from '@/api/order'
import { ElMessage, ElMessageBox } from 'element-plus'

const orders = ref([])
const loading = ref(false)
const statusFilter = ref('')
const page = ref(1)
const size = ref(10)
const total = ref(0)

const statusMap = {
  0: { label: '待支付', type: 'warning' },
  1: { label: '已支付', type: 'primary' },
  2: { label: '已发货', type: 'info' },
  3: { label: '已完成', type: 'success' },
  4: { label: '已取消', type: 'danger' }
}

async function fetchOrders() {
  loading.value = true
  try {
    const res = await getMyOrders({ status: statusFilter.value || undefined, page: page.value, size: size.value })
    orders.value = res.records
    total.value = res.total
  } catch { orders.value = [] }
  finally { loading.value = false }
}

async function pay(orderSn) { await payOrder(orderSn); ElMessage.success('支付成功'); fetchOrders() }
async function cancel(orderSn) {
  try {
    await ElMessageBox.prompt('请输入取消原因', '取消订单', { inputType: 'text', inputPlaceholder: '取消原因' })
    await cancelOrder(orderSn, '用户主动取消')
    ElMessage.success('已取消')
    fetchOrders()
  } catch { /* user cancelled dialog */ }
}
async function receive(orderSn) { await receiveOrder(orderSn); ElMessage.success('已确认收货'); fetchOrders() }

onMounted(fetchOrders)
</script>

<style scoped>
.orders-page { max-width: 900px; margin: 30px auto; padding: 0 20px; }
.order-list { display: flex; flex-direction: column; gap: 12px; }
.order-card { }
.order-header, .order-body, .order-actions { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; }
.order-sn { font-size: 13px; color: #666; }
.price { color: #e74c3c; }
.order-actions { gap: 8px; justify-content: flex-end; }
.pagination { display: flex; justify-content: center; padding: 20px; }
</style>
