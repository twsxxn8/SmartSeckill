<template>
  <div class="my-coupons-page">
    <h2>我的优惠券</h2>

    <el-radio-group v-model="activeStatus" @change="fetchCoupons" style="margin-bottom: 20px">
      <el-radio-button :label="0">未使用</el-radio-button>
      <el-radio-button :label="1">已使用</el-radio-button>
      <el-radio-button :label="2">已过期</el-radio-button>
    </el-radio-group>

    <div class="coupon-list" v-loading="loading">
      <div v-for="c in coupons" :key="c.id" class="coupon-card" :class="statusClass(c.userStatus)">
        <div class="coupon-left">
          <div class="amount">
            <template v-if="c.couponType === 1">
              <span class="sym">¥</span><span class="num">{{ c.amount }}</span>
            </template>
            <template v-else>
              <span class="num">{{ (c.discount * 10).toFixed(1) }}</span><span class="sym">折</span>
            </template>
          </div>
          <div class="cond">满{{ c.minAmount || 0 }}元可用</div>
        </div>
        <div class="coupon-right">
          <div class="info">
            <span class="name">{{ c.couponName }}</span>
            <span class="meta">{{ c.startTime?.slice(0, 10) }} ~ {{ c.endTime?.slice(0, 10) }}</span>
          </div>
          <el-tag size="small" :type="statusTag(c.userStatus)">{{ statusLabel(c.userStatus) }}</el-tag>
        </div>
        <div class="watermark" v-if="c.userStatus !== 0">{{ statusWatermark(c.userStatus) }}</div>
      </div>

      <el-empty v-if="!loading && coupons.length === 0" description="暂无优惠券" />
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { getMyCoupons } from '@/api/coupon'

const coupons = ref([])
const activeStatus = ref(0)
const loading = ref(false)

async function fetchCoupons() {
  loading.value = true
  try {
    const list = await getMyCoupons(activeStatus.value)
    coupons.value = list || []
  } catch { coupons.value = [] }
  finally { loading.value = false }
}

function statusClass(s) {
  return { 0: 'active', 1: 'used', 2: 'expired' }[s] || ''
}

function statusLabel(s) {
  return { 0: '未使用', 1: '已使用', 2: '已过期' }[s] || '未知'
}

function statusTag(s) {
  return { 0: 'success', 1: 'info', 2: 'warning' }[s] || 'info'
}

function statusWatermark(s) {
  return { 1: '已使用', 2: '已过期' }[s] || ''
}

onMounted(fetchCoupons)
</script>

<style scoped>
.my-coupons-page { max-width: 800px; margin: 30px auto; padding: 0 20px; }
.my-coupons-page h2 { margin-bottom: 16px; }

.coupon-list { display: flex; flex-direction: column; gap: 14px; }

.coupon-card {
  display: flex;
  border-radius: 10px;
  overflow: hidden;
  background: #fff;
  box-shadow: 0 2px 12px rgba(0,0,0,.06);
  position: relative;
}
.coupon-card.used, .coupon-card.expired { opacity: .6; }

.coupon-left {
  width: 130px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  padding: 18px 0;
  color: #fff;
}
.coupon-card.active .coupon-left { background: linear-gradient(135deg, #e74c3c, #c0392b); }
.coupon-card.used .coupon-left { background: linear-gradient(135deg, #909399, #606266); }
.coupon-card.expired .coupon-left { background: linear-gradient(135deg, #c0c4cc, #909399); }

.amount { line-height: 1.1; text-align: center; }
.amount .sym { font-size: 16px; }
.amount .num { font-size: 32px; font-weight: 800; }
.cond { font-size: 11px; margin-top: 4px; opacity: .85; }

.coupon-right {
  flex: 1;
  padding: 18px 20px;
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.info { display: flex; flex-direction: column; }
.name { font-size: 15px; font-weight: 600; }
.meta { font-size: 12px; color: #999; margin-top: 4px; }

.watermark {
  position: absolute;
  right: 20px;
  top: 50%;
  transform: translateY(-50%) rotate(-15deg);
  font-size: 36px;
  font-weight: 900;
  color: rgba(0,0,0,.08);
  pointer-events: none;
}
</style>
