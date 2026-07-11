<template>
  <div class="coupons-page">
    <div class="banner">
      <h1>🎫 领券中心</h1>
      <p>限量优惠券，先到先得</p>
    </div>

    <div class="coupon-grid" v-loading="loading">
      <div v-for="c in coupons" :key="c.id" class="coupon-card" :class="{ claimed: claimedIds.has(c.id) }">
        <div class="coupon-left">
          <div class="coupon-amount">
            <template v-if="c.couponType === 1">
              <span class="symbol">¥</span><span class="num">{{ c.amount }}</span>
            </template>
            <template v-else>
              <span class="num">{{ (c.discount * 10).toFixed(1) }}</span><span class="symbol">折</span>
            </template>
          </div>
          <div class="coupon-condition">满{{ c.minAmount || 0 }}元可用</div>
        </div>
        <div class="coupon-right">
          <div class="coupon-name">{{ c.couponName }}</div>
          <div class="coupon-meta">
            <span>{{ c.startTime?.slice(0, 10) }} ~ {{ c.endTime?.slice(0, 10) }}</span>
          </div>
          <div class="coupon-stock">
            剩余 {{ Math.max(0, (c.totalCount || 0) - (c.usedCount || 0)) }} / {{ c.totalCount }}
          </div>
          <el-button
            size="small"
            :type="claimedIds.has(c.id) ? 'info' : 'danger'"
            :disabled="claimedIds.has(c.id)"
            @click="doClaim(c)"
          >
            {{ claimedIds.has(c.id) ? '已领取' : '立即领取' }}
          </el-button>
        </div>
      </div>

      <el-empty v-if="!loading && coupons.length === 0" description="暂无可用优惠券" />
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { getAvailableCoupons, getMyCoupons, claimCoupon } from '@/api/coupon'
import { useUserStore } from '@/stores/user'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'

const userStore = useUserStore()
const router = useRouter()
const coupons = ref([])
const claimedIds = ref(new Set())
const loading = ref(false)

async function fetchCoupons() {
  loading.value = true
  try {
    const [available, mine] = await Promise.all([
      getAvailableCoupons(),
      userStore.isLoggedIn ? getMyCoupons() : Promise.resolve([])
    ])
    coupons.value = available || []
    claimedIds.value = new Set((mine || []).map(c => c.id))
  } catch {
    coupons.value = []
  }
  finally { loading.value = false }
}

async function doClaim(coupon) {
  if (!userStore.isLoggedIn) {
    ElMessage.warning('请先登录再领取优惠券')
    router.push({ name: 'Login', query: { redirect: '/coupons' } })
    return
  }
  try {
    await claimCoupon(coupon.id)
    claimedIds.value.add(coupon.id)
    ElMessage.success('领取成功！')
  } catch { /* interceptor handled */ }
}

onMounted(fetchCoupons)
</script>

<style scoped>
.coupons-page { max-width: 900px; margin: 0 auto; padding: 0 20px 40px; }
.banner {
  text-align: center;
  padding: 40px 0 30px;
}
.banner h1 { font-size: 28px; margin-bottom: 6px; }
.banner p { color: #999; font-size: 14px; }

.coupon-grid {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.coupon-card {
  display: flex;
  border-radius: 10px;
  overflow: hidden;
  background: #fff;
  box-shadow: 0 2px 12px rgba(0,0,0,.06);
  transition: transform .2s;
}
.coupon-card:hover { transform: translateY(-2px); }
.coupon-card.claimed { opacity: .65; }

.coupon-left {
  width: 140px;
  background: linear-gradient(135deg, #e74c3c, #c0392b);
  color: #fff;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  padding: 20px 0;
}
.coupon-amount { text-align: center; line-height: 1.1; }
.coupon-amount .symbol { font-size: 18px; }
.coupon-amount .num { font-size: 36px; font-weight: 800; }
.coupon-condition { font-size: 12px; margin-top: 6px; opacity: .85; }

.coupon-right {
  flex: 1;
  padding: 20px 24px;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
}
.coupon-name { font-size: 16px; font-weight: 600; margin-bottom: 4px; }
.coupon-meta { font-size: 12px; color: #999; margin-bottom: 4px; }
.coupon-stock { font-size: 13px; color: #e74c3c; margin-bottom: 10px; }
</style>
