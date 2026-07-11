<template>
  <div class="seckill-detail" v-loading="loading">
    <div class="seckill-main" v-if="activity">
      <!-- 头部：活动名称 + AI 热度标签 -->
      <div class="seckill-header">
        <div class="header-left">
          <h1>{{ activity.activityName }}</h1>
          <span class="hot-badge" v-if="competitorCount > 0">
            🔥 {{ competitorCount }} 人正在抢购中
          </span>
        </div>
        <CountDown
          :targetTime="activity.status === 0 ? activity.startTime : activity.endTime"
          :prefix="activity.status === 0 ? '距开始' : '距结束'"
          @finished="finished = true" />
      </div>

      <!-- 价格区域 -->
      <div class="price-section">
        <span class="seckill-price">
          <span class="currency">¥</span>
          <span class="amount" ref="priceEl">{{ activity.seckillPrice }}</span>
        </span>
        <span class="original-price">¥{{ activity.originalPrice }}</span>
        <span class="discount">{{ discount }}折</span>
      </div>

      <!-- 库存进度条 + 实时变化 -->
      <div class="stock-section">
        <div class="stock-header">
          <span>剩余库存</span>
          <span class="stock-count">
            <AnimatedNumber :value="displayStock" /> / {{ activity.totalStock }}
          </span>
        </div>
        <div
          class="stock-bar-wrap"
          :class="{ 'low-stock': stockPercent < 10, 'empty': displayStock <= 0 }"
        >
          <div class="stock-bar-fill" :style="{ width: stockPercent + '%' }">
            <div class="shimmer" v-if="stockPercent > 0"></div>
          </div>
        </div>
        <p class="sell-rate" v-if="sellRate > 0">
          每秒售出 <strong>{{ sellRate }}</strong> 件 · 预计 <strong>{{ estimateTimeLeft }}</strong> 抢光
        </p>
      </div>

      <!-- 秒杀按钮 -->
      <div class="seckill-action" ref="actionArea">
        <button
          class="seckill-btn"
          :class="btnState"
          :disabled="btnDisabled"
          @click="handleSeckill"
          ref="seckillBtn"
        >
          <span class="btn-content">
            <span class="btn-text">{{ btnText }}</span>
            <span class="btn-sub" v-if="btnState === 'ready'">点击抢购</span>
          </span>
          <span class="ripple" v-if="btnAnimating"></span>
        </button>
        <!-- 粒子特效 -->
        <ParticleEffect ref="particleRef" />
      </div>

      <!-- 中奖跑马灯 -->
      <div class="winner-marquee" v-if="winners.length > 0">
        <div class="marquee-track">
          <span
            v-for="(w, i) in winners"
            :key="i"
            class="marquee-item"
          >
            🎉 {{ w.username }} 刚刚抢到了！
          </span>
        </div>
      </div>

      <!-- AI 建议卡片 -->
      <div class="ai-card" v-if="aiSuggestion">
        <span class="ai-icon">🤖</span>
        <div class="ai-content">
          <span class="ai-title">AI 秒杀助手</span>
          <p>{{ aiSuggestion }}</p>
        </div>
      </div>
    </div>

    <el-empty v-else description="活动不存在" />

    <!-- 结果弹窗 -->
    <SeckillResultModal
      :visible="resultVisible"
      :status="resultStatus"
      :message="resultMessage"
      :orderSn="resultOrderSn"
      :seckillPrice="activity?.seckillPrice"
      :suggestion="resultSuggestion"
      @close="resultVisible = false"
      @retry="handleSeckill"
    />
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, nextTick, watch } from 'vue'
import { useRoute } from 'vue-router'
import { getSeckillDetail, getSeckillToken, executeSeckill, getSeckillResult } from '@/api/seckill'
import { getOnlineCount } from '@/api/seckill'
import { useUserStore } from '@/stores/user'
import { ElMessage } from 'element-plus'
import { createSSEConnection } from '@/utils/sse'
import CountDown from '@/components/CountDown.vue'
import AnimatedNumber from '@/components/AnimatedNumber.vue'
import ParticleEffect from '@/components/ParticleEffect.vue'
import SeckillResultModal from '@/components/SeckillResultModal.vue'

const route = useRoute()
const userStore = useUserStore()

// ===== 数据 =====
const activity = ref(null)
const loading = ref(false)
const seckilling = ref(false)
const finished = ref(false)
const btnAnimating = ref(false)
const displayStock = ref(0)
const competitorCount = ref(0)
const winners = ref([])
const sellRate = ref(0)
const aiSuggestion = ref('')
const lastStock = ref(0)
const lastStockTime = ref(0)

// ===== 弹窗 =====
const resultVisible = ref(false)
const resultStatus = ref('')
const resultMessage = ref('')
const resultOrderSn = ref('')
const resultSuggestion = ref('')

// ===== 模板 ref =====
const seckillBtn = ref(null)
const particleRef = ref(null)
const priceEl = ref(null)
const actionArea = ref(null)

// ===== SSE =====
let sseConn = null
let pollTimer = null

// ===== 计算属性 =====
const stockPercent = computed(() => {
  if (!activity.value || activity.value.totalStock === 0) return 0
  return Math.round((displayStock.value / activity.value.totalStock) * 100)
})

const discount = computed(() => {
  if (!activity.value) return '--'
  return (activity.value.seckillPrice / activity.value.originalPrice * 10).toFixed(1)
})

const btnState = computed(() => {
  const status = activity.value?.status
  if (finished.value || status === 2) return 'ended'
  if (status === 3) return 'ended'       // 已取消也当作已结束
  if (status === 0) return 'upcoming'
  if (displayStock.value <= 0) return 'soldout'
  if (seckilling.value) return 'queuing'
  return 'ready'
})

const btnDisabled = computed(() => {
  return finished.value || activity.value?.status !== 1 || displayStock.value <= 0 || seckilling.value
})

const btnText = computed(() => {
  const map = {
    ended: '已结束',
    upcoming: '即将开始',
    soldout: '已抢光',
    queuing: '排队中...',
    ready: '立即秒杀'
  }
  return map[btnState.value] || '立即秒杀'
})

const estimateTimeLeft = computed(() => {
  if (sellRate.value <= 0 || displayStock.value <= 0) return '--'
  const secs = Math.ceil(displayStock.value / sellRate.value)
  if (secs < 60) return secs + '秒'
  return Math.ceil(secs / 60) + '分钟'
})

// ===== 方法 =====

async function fetchDetail() {
  loading.value = true
  try {
    activity.value = await getSeckillDetail(route.params.id)
    displayStock.value = activity.value.availableStock
    lastStock.value = activity.value.availableStock
    lastStockTime.value = Date.now()
  } catch {
    activity.value = null
  } finally {
    loading.value = false
  }
}

function connectSSE() {
  if (!userStore.token) return
  const activityId = route.params.id

  sseConn = createSSEConnection(
    `/api/seckill/sse/${activityId}`,
    userStore.token,
    {
      stock_changed: (data) => {
        if (data.remainingStock !== undefined) {
          const prev = displayStock.value
          displayStock.value = data.remainingStock
          // 计算售出速率
          const diff = prev - data.remainingStock
          const now = Date.now()
          const elapsed = now - lastStockTime.value
          if (elapsed > 500 && diff > 0) {
            sellRate.value = Math.round(diff / (elapsed / 1000))
            lastStock.value = prev
            lastStockTime.value = now
          }
        }
      },
      competitor_joined: (data) => {
        if (data.competitorCount !== undefined) {
          competitorCount.value = data.competitorCount
        }
      },
      winner_announcement: (data) => {
        if (data.username) {
          winners.value.unshift({
            username: data.username,
            time: Date.now()
          })
          // 只保留最近 10 条
          if (winners.value.length > 10) {
            winners.value.pop()
          }
        }
      },
      seckill_result: (data) => {
        if (data.status === 'success') {
          showResult('success', data.message || '恭喜！秒杀成功', data.orderSn)
          seckilling.value = false
        } else if (data.status === 'failed') {
          showResult('failed', data.message || '很遗憾，没抢到')
          seckilling.value = false
        }
      }
    }
  )
}

async function fetchOnlineCount() {
  try {
    const data = await getOnlineCount(route.params.id)
    competitorCount.value = data.onlineCount || 0
  } catch { /* ignore */ }
}

function startPolling() {
  // 作为 SSE 的兜底，每 3 秒轮询一次在线人数
  fetchOnlineCount()
  pollTimer = setInterval(fetchOnlineCount, 3000)
}

async function handleSeckill() {
  if (!userStore.isLoggedIn) {
    ElMessage.warning('请先登录')
    return
  }
  if (seckilling.value) return

  // 粒子爆炸效果
  if (particleRef.value) {
    const btn = seckillBtn.value
    if (btn && btn.$el) {
      particleRef.value.burstAt(btn.$el)
    } else if (btn) {
      particleRef.value.burstAt(btn)
    }
  }
  btnAnimating.value = true
  setTimeout(() => { btnAnimating.value = false }, 600)

  seckilling.value = true
  try {
    const { token } = await getSeckillToken(activity.value.id)
    await executeSeckill({ activityId: activity.value.id, token })
    // 请求进入队列 → 等待 SSE 推送结果
    ElMessage.success('秒杀请求已提交，正在排队处理...')

    // 3 秒后兜底查结果（万一 SSE 没推送）
    setTimeout(async () => {
      if (seckilling.value) {
        try {
          const result = await getSeckillResult(activity.value.id)
          if (result.status === 'success') {
            showResult('success', result.message, result.orderSn)
            seckilling.value = false
          } else if (result.status === 'queuing') {
            // 还在排队，再等 3 秒
            setTimeout(checkResult, 3000)
          } else if (result.status === 'none' || result.status === 'failed') {
            showResult('failed', result.message || '未抢到')
            seckilling.value = false
          }
        } catch { /* ignore */ }
      }
    }, 3000)
  } catch (err) {
    seckilling.value = false
    // 刷新库存数据：异常可能是库存变化或活动状态变化导致的
    fetchDetail()
    // 异常消息已由全局 interceptor (ElMessage.error) 展示，不需要重复提示
  }
}

async function checkResult() {
  if (!seckilling.value) return
  try {
    const result = await getSeckillResult(activity.value.id)
    if (result.status === 'success') {
      showResult('success', result.message, result.orderSn)
      seckilling.value = false
    } else if (result.status === 'queuing') {
      // 继续等待
      setTimeout(checkResult, 3000)
    } else {
      showResult('failed', result.message || '未抢到')
      seckilling.value = false
    }
  } catch {
    seckilling.value = false
  }
}

function showResult(status, message, orderSn) {
  resultStatus.value = status
  resultMessage.value = message
  resultOrderSn.value = orderSn || ''
  resultVisible.value = true

  if (status === 'failed') {
    // 失败时生成 AI 建议
    const tips = [
      '下次可以提前 0.5 秒打开页面，在倒计时归零瞬间点击按钮',
      '建议使用有线网络，WiFi 延迟可能比 5G 高 50-100ms',
      '你的点击时机偏晚，试试在倒计时还剩 0.1 秒时就开始狂点',
      '可以考虑参加库存更多的场次，提高成功率'
    ]
    resultSuggestion.value = tips[Math.floor(Math.random() * tips.length)]
  } else {
    resultSuggestion.value = ''
  }
}

// ===== 生命周期 =====
onMounted(async () => {
  await fetchDetail()
  connectSSE()
  startPolling()

  // AI 建议
  if (activity.value) {
    const ratio = activity.value.availableStock / activity.value.totalStock
    if (ratio < 0.3 && activity.value.status === 1) {
      aiSuggestion.value = '库存紧张！建议提前获取 Token，倒计时结束瞬间点击秒杀按钮'
    } else if (activity.value.status === 0) {
      const startTime = new Date(activity.value.startTime).getTime()
      const remain = startTime - Date.now()
      if (remain < 5 * 60 * 1000) {
        aiSuggestion.value = '活动即将开始，请留在页面不要离开，提前获取 Token 占位'
      } else {
        aiSuggestion.value = '建议在活动开始前 1 分钟打开此页面并获取 Token'
      }
    }
  }
})

onUnmounted(() => {
  if (sseConn) sseConn.close()
  if (pollTimer) clearInterval(pollTimer)
})
</script>

<style scoped>
/* ===== 布局 ===== */
.seckill-detail { max-width: 640px; margin: 40px auto; padding: 0 20px; }
.seckill-main { background: #fff; border-radius: 20px; padding: 40px; box-shadow: 0 8px 40px rgba(0,0,0,.08); }

/* ===== 头部 ===== */
.seckill-header { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 24px; }
.header-left h1 { font-size: 26px; margin: 0 0 6px; }
.hot-badge {
  display: inline-block;
  background: linear-gradient(135deg, #e74c3c, #f39c12);
  color: #fff;
  padding: 4px 14px;
  border-radius: 20px;
  font-size: 13px;
  font-weight: 600;
  animation: hot-pulse 1.5s ease-in-out infinite;
}
@keyframes hot-pulse { 0%,100% { transform: scale(1); } 50% { transform: scale(1.05); } }

/* ===== 价格 ===== */
.price-section { margin-bottom: 24px; display: flex; align-items: baseline; gap: 12px; }
.seckill-price { color: #e74c3c; font-weight: 700; }
.currency { font-size: 24px; }
.amount { font-size: 48px; line-height: 1; transition: all .15s; }
.original-price { color: #bbb; text-decoration: line-through; font-size: 16px; }
.discount { background: #e74c3c; color: #fff; padding: 2px 10px; border-radius: 4px; font-size: 12px; font-weight: 600; }

/* ===== 库存进度条 ===== */
.stock-section { margin-bottom: 24px; }
.stock-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; font-size: 14px; }
.stock-count { font-weight: 700; color: #e74c3c; font-size: 18px; }
.stock-bar-wrap {
  height: 24px; background: #f0f0f0; border-radius: 12px; overflow: hidden;
  position: relative; transition: all .3s;
}
.stock-bar-wrap.low-stock { background: #fde8e8; }
.stock-bar-wrap.empty { background: #f5f5f5; }
.stock-bar-fill {
  height: 100%; border-radius: 12px;
  background: linear-gradient(90deg, #e74c3c, #ff6b6b);
  transition: width .3s ease-out;
  position: relative; min-width: 0;
}
.low-stock .stock-bar-fill { background: linear-gradient(90deg, #c0392b, #e74c3c); animation: flash-bar .5s infinite; }
@keyframes flash-bar { 0%,100% { opacity: 1; } 50% { opacity: .7; } }
.shimmer {
  position: absolute; inset: 0;
  background: linear-gradient(90deg, transparent, rgba(255,255,255,.3), transparent);
  animation: shimmer 1.5s infinite;
}
@keyframes shimmer { 0% { transform: translateX(-100%); } 100% { transform: translateX(200%); } }
.sell-rate { font-size: 12px; color: #999; margin-top: 6px; }
.sell-rate strong { color: #e74c3c; }

/* ===== 按钮 ===== */
.seckill-action { position: relative; margin-bottom: 20px; }
.seckill-btn {
  width: 100%; height: 56px; border: none; border-radius: 12px;
  font-size: 20px; font-weight: 700; color: #fff; cursor: pointer;
  background: linear-gradient(135deg, #e74c3c, #c0392b);
  transition: all .2s; position: relative; overflow: hidden;
  display: flex; flex-direction: column; align-items: center; justify-content: center;
  gap: 2px;
}
.seckill-btn:hover:not(:disabled) { transform: translateY(-2px); box-shadow: 0 8px 24px rgba(231, 76, 60, .4); }
.seckill-btn:active:not(:disabled) { transform: scale(.97); }
.seckill-btn:disabled { cursor: not-allowed; }
.seckill-btn.ended, .seckill-btn.soldout { background: #bbb; }
.seckill-btn.upcoming { background: linear-gradient(135deg, #f39c12, #e67e22); }
.seckill-btn.queuing { background: linear-gradient(135deg, #3498db, #2980b9); animation: queue-pulse 1s infinite; }
@keyframes queue-pulse { 0%,100% { box-shadow: 0 0 0 0 rgba(52,152,219,.4); } 50% { box-shadow: 0 0 0 12px rgba(52,152,219,0); } }
.btn-text { font-size: 20px; }
.btn-sub { font-size: 12px; opacity: .8; }
.ripple {
  position: absolute; inset: 0; border-radius: 12px;
  background: radial-gradient(circle, rgba(255,255,255,.4) 0%, transparent 70%);
  animation: ripple-out .6s ease-out forwards;
}
@keyframes ripple-out { 0% { transform: scale(0); opacity: 1; } 100% { transform: scale(2); opacity: 0; } }

/* ===== 跑马灯 ===== */
.winner-marquee {
  background: linear-gradient(90deg, #fff8e1, #fff3cd, #fff8e1);
  border-radius: 8px; padding: 10px 0; margin-bottom: 16px;
  overflow: hidden; position: relative;
}
.marquee-track {
  display: flex; gap: 40px;
  animation: marquee-scroll 12s linear infinite;
}
.marquee-item { white-space: nowrap; font-size: 13px; color: #e67e22; font-weight: 600; }
@keyframes marquee-scroll { 0% { transform: translateX(0); } 100% { transform: translateX(-50%); } }

/* ===== AI 建议卡片 ===== */
.ai-card {
  display: flex; gap: 12px; align-items: flex-start;
  background: linear-gradient(135deg, #f0f0ff, #e8f0fe);
  border: 1px solid #d0d8ff; border-radius: 12px;
  padding: 14px 18px; margin-top: 16px;
}
.ai-icon { font-size: 24px; flex-shrink: 0; }
.ai-title { font-size: 13px; color: #667eea; font-weight: 600; display: block; margin-bottom: 4px; }
.ai-content p { margin: 0; font-size: 13px; color: #555; line-height: 1.5; }
</style>
