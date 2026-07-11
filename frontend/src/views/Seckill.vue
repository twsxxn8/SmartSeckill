<template>
  <div class="seckill-page">
    <div class="seckill-banner">
      <h1>🔥 限时秒杀</h1>
      <p>超值好物，手慢无！</p>
    </div>
    <div class="seckill-list" v-loading="loading">
      <div class="seckill-item" v-for="act in activities" :key="act.id" @click="$router.push(`/seckill/${act.id}`)">
        <div class="act-header">
          <h3>{{ act.activityName }}</h3>
          <CountDown :targetTime="act.status === 0 ? act.startTime : act.endTime" :prefix="act.status === 0 ? '距开始' : '距结束'" @finished="fetchActivities" />
        </div>
        <div class="act-body">
          <div class="price-box">
            <span class="seckill-price">¥{{ act.seckillPrice }}</span>
            <span class="original-price">¥{{ act.originalPrice }}</span>
          </div>
          <div class="stock-info">
            <el-progress :percentage="stockPercent(act)" :color="progressColor(act)" :stroke-width="14" />
            <span>已抢 {{ act.totalStock - act.availableStock }} / {{ act.totalStock }} 件</span>
          </div>
          <div class="heat-info" v-if="act.status === 1">
            <span class="heat-badge">🔥 正在进行中</span>
          </div>
          <el-tag v-if="act.status === 1" type="danger" size="large" effect="dark">进行中</el-tag>
          <el-tag v-else-if="act.status === 0" type="warning" size="large">即将开始</el-tag>
          <el-tag v-else type="info" size="large">已结束</el-tag>
        </div>
      </div>
      <el-empty v-if="!loading && activities.length === 0" description="暂无秒杀活动" />
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { getSeckillList } from '@/api/seckill'
import CountDown from '@/components/CountDown.vue'

const activities = ref([])
const loading = ref(false)
let pollTimer = null

function stockPercent(act) {
  if (act.totalStock === 0) return 0
  return Math.round(((act.totalStock - act.availableStock) / act.totalStock) * 100)
}

function progressColor(act) {
  const pct = stockPercent(act)
  if (pct >= 90) return '#e74c3c'
  if (pct >= 60) return '#f39c12'
  return '#e74c3c'
}

async function fetchActivities() {
  loading.value = true
  try {
    const data = await getSeckillList()
    activities.value = Array.isArray(data) ? data : (data?.records || [])
  }
  catch { activities.value = [] }
  finally { loading.value = false }
}

onMounted(fetchActivities)
</script>

<style scoped>
.seckill-banner {
  background: linear-gradient(135deg, #e74c3c, #f39c12);
  color: #fff;
  text-align: center;
  padding: 40px 20px;
}
.seckill-banner h1 { font-size: 32px; }
.seckill-list { max-width: 900px; margin: 20px auto; padding: 0 20px; display: flex; flex-direction: column; gap: 16px; }
.seckill-item { background: #fff; border-radius: 12px; padding: 20px; cursor: pointer; transition: box-shadow .2s; }
.seckill-item:hover { box-shadow: 0 4px 16px rgba(0,0,0,.1); }
.act-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
.act-body { display: flex; align-items: center; gap: 20px; }
.price-box { min-width: 160px; }
.seckill-price { color: #e74c3c; font-size: 24px; font-weight: 700; }
.original-price { color: #999; text-decoration: line-through; margin-left: 8px; font-size: 14px; }
.stock-info { flex: 1; }
.stock-info span { font-size: 12px; color: #999; }
.heat-info { margin-top: 4px; }
.heat-badge {
  display: inline-block;
  background: linear-gradient(135deg, #fff5f5, #ffe8e8);
  color: #e74c3c;
  padding: 2px 10px;
  border-radius: 12px;
  font-size: 12px;
  font-weight: 600;
}
</style>
