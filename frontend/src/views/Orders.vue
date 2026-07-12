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
        <div class="order-header" @click="toggleItems(order)">
          <span class="order-sn">订单号：{{ order.orderSn }}</span>
          <div style="display:flex;align-items:center;gap:10px">
            <span class="toggle-hint">{{ expandedOrder === order.orderSn ? '收起 ▲' : '展开 ▼' }}</span>
            <el-tag :type="statusMap[order.status]?.type">{{ statusMap[order.status]?.label }}</el-tag>
          </div>
        </div>
        <div class="order-body">
          <span>金额：<b class="price">¥{{ order.payAmount }}</b></span>
          <span>{{ order.createTime }}</span>
        </div>

        <!-- 展开的订单项列表 -->
        <div class="order-items" v-if="expandedOrder === order.orderSn" v-loading="itemsLoading">
          <div class="order-item" v-for="item in currentItems" :key="item.id">
            <el-image v-if="item.skuImage" :src="item.skuImage" fit="cover" class="item-img" />
            <div class="item-info">
              <div class="item-name">{{ item.skuName }}</div>
              <div class="item-meta" v-if="item.specInfo">{{ item.specInfo }}</div>
              <div class="item-price">¥{{ item.skuPrice }} × {{ item.quantity }}</div>
            </div>
            <el-button
              v-if="order.status === 3"
              type="warning"
              size="small"
              @click="openReview(order.orderSn, item)"
            >评价</el-button>
            <el-tag v-else-if="item.reviewed" type="info" size="small">已评价</el-tag>
          </div>
          <el-empty v-if="!itemsLoading && currentItems.length === 0" description="暂无商品信息" :image-size="40" />
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

    <!-- 评价对话框 -->
    <el-dialog v-model="reviewVisible" title="发表评价" width="420px" :close-on-click-modal="false">
      <div class="review-form">
        <div class="review-item-info">
          <span>{{ reviewItem?.skuName }}</span>
        </div>
        <div class="review-rating">
          <span class="label">评分</span>
          <el-rate v-model="reviewRating" :max="5" />
        </div>
        <div class="review-content">
          <span class="label">评价内容（选填）</span>
          <el-input v-model="reviewContent" type="textarea" :rows="3" maxlength="1000" show-word-count placeholder="分享你的使用体验…" />
        </div>
      </div>
      <template #footer>
        <el-button @click="reviewVisible = false">取消</el-button>
        <el-button type="danger" @click="submitReview" :loading="reviewSubmitting">提交评价</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { getMyOrders, payOrder, cancelOrder, receiveOrder, getOrderItems } from '@/api/order'
import { createReview } from '@/api/review'
import { ElMessage, ElMessageBox } from 'element-plus'

const orders = ref([])
const loading = ref(false)
const statusFilter = ref('')
const page = ref(1)
const size = ref(10)
const total = ref(0)

// 展开/收起订单详情
const expandedOrder = ref(null)
const currentItems = ref([])
const itemsLoading = ref(false)

// 评价弹窗
const reviewVisible = ref(false)
const reviewItem = ref(null)
const reviewOrderSn = ref('')
const reviewRating = ref(5)
const reviewContent = ref('')
const reviewSubmitting = ref(false)

const statusMap = {
  0: { label: '待支付', type: 'warning' },
  1: { label: '已支付', type: 'primary' },
  2: { label: '已发货', type: 'info' },
  3: { label: '已完成', type: 'success' },
  4: { label: '已取消', type: 'danger' }
}

async function fetchOrders() {
  loading.value = true
  expandedOrder.value = null
  currentItems.value = []
  try {
    const res = await getMyOrders({ status: statusFilter.value || undefined, page: page.value, size: size.value })
    orders.value = res.records
    total.value = res.total
  } catch { orders.value = [] }
  finally { loading.value = false }
}

async function toggleItems(order) {
  if (expandedOrder.value === order.orderSn) {
    expandedOrder.value = null
    currentItems.value = []
    return
  }
  expandedOrder.value = order.orderSn
  itemsLoading.value = true
  try {
    currentItems.value = await getOrderItems(order.orderSn)
  } catch { currentItems.value = [] }
  finally { itemsLoading.value = false }
}

function openReview(orderSn, item) {
  reviewOrderSn.value = orderSn
  reviewItem.value = item
  reviewRating.value = 5
  reviewContent.value = ''
  reviewVisible.value = true
}

async function submitReview() {
  if (reviewRating.value < 1) { ElMessage.warning('请选择评分'); return }
  reviewSubmitting.value = true
  try {
    await createReview({
      spuId: reviewItem.value.spuId,
      skuId: reviewItem.value.skuId,
      orderSn: reviewOrderSn.value,
      rating: reviewRating.value,
      content: reviewContent.value
    })
    ElMessage.success('评价成功')
    reviewVisible.value = false
  } catch { /* error handled by interceptor */ }
  finally { reviewSubmitting.value = false }
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
.order-header, .order-body, .order-actions { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; }
.order-header { cursor: pointer; user-select: none; }
.order-header:hover { opacity: 0.8; }
.order-sn { font-size: 13px; color: #666; }
.toggle-hint { font-size: 12px; color: #999; }
.price { color: #e74c3c; }
.order-actions { gap: 8px; justify-content: flex-end; }

.order-items { border-top: 1px solid #f0f0f0; padding-top: 12px; margin-top: 8px; }
.order-item { display: flex; align-items: center; gap: 12px; padding: 10px 0; border-bottom: 1px solid #fafafa; }
.order-item:last-child { border-bottom: none; }
.item-img { width: 60px; height: 60px; border-radius: 6px; flex-shrink: 0; }
.item-info { flex: 1; }
.item-name { font-size: 14px; color: #333; }
.item-meta { font-size: 12px; color: #999; margin-top: 2px; }
.item-price { font-size: 13px; color: #e74c3c; margin-top: 2px; }

.review-form { display: flex; flex-direction: column; gap: 16px; }
.review-item-info { font-size: 14px; color: #333; padding: 8px; background: #fafafa; border-radius: 6px; }
.review-rating, .review-content { display: flex; flex-direction: column; gap: 6px; }
.review-rating .label, .review-content .label { font-size: 13px; color: #666; }

.pagination { display: flex; justify-content: center; padding: 20px; }
</style>
