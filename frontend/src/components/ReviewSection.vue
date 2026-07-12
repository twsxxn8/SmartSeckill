<template>
  <div class="review-section" v-loading="loading">
    <!-- 评分摘要 -->
    <div class="review-summary" v-if="data.total > 0">
      <div class="avg-rating">
        <span class="score">{{ data.avgRating }}</span>
        <span class="out-of">/ 5</span>
      </div>
      <el-rate :model-value="data.avgRating" disabled show-score text-color="#e74c3c" />
      <span class="total-count">{{ data.total }} 条评论</span>
    </div>

    <!-- 评分分布 -->
    <div class="rating-bars" v-if="data.total > 0">
      <div class="bar-row" v-for="r in 5" :key="r">
        <span class="star-label">{{ 6 - r }}星</span>
        <el-progress
          :percentage="getPercent(6 - r)"
          :stroke-width="8"
          :color="'#f39c12'"
          :show-text="false"
        />
        <span class="bar-count">{{ data.ratingDistribution?.[String(6 - r)] || 0 }}</span>
      </div>
    </div>

    <!-- 评论列表 -->
    <div class="review-list" v-if="data.records?.length">
      <div class="review-item" v-for="item in data.records" :key="item.id">
        <div class="review-header">
          <el-avatar :size="32" :src="item.avatar" />
          <span class="review-nickname">{{ item.nickname }}</span>
          <el-rate :model-value="item.rating" disabled size="small" />
          <span class="review-time">{{ item.createTime }}</span>
        </div>
        <p class="review-content">{{ item.content }}</p>
      </div>
    </div>
    <el-empty v-else-if="!loading" description="暂无评论" />

    <!-- 分页 -->
    <el-pagination
      v-if="data.total > data.size"
      class="review-pagination"
      layout="prev, pager, next"
      :total="data.total"
      :page-size="data.size"
      :current-page="currentPage"
      @current-change="fetchReviews"
    />
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { getSpuReviews } from '@/api/review'

const props = defineProps({
  spuId: { type: Number, required: true }
})

const loading = ref(false)
const currentPage = ref(1)

const data = reactive({
  total: 0,
  page: 1,
  size: 10,
  avgRating: 0,
  ratingDistribution: {},
  records: []
})

function getPercent(star) {
  const count = data.ratingDistribution[String(star)] || 0
  if (data.total === 0) return 0
  return Math.round((count / data.total) * 100)
}

async function fetchReviews(page = 1) {
  loading.value = true
  currentPage.value = page
  try {
    const res = await getSpuReviews(props.spuId, page, 10)
    Object.assign(data, res)
  } catch {
    // keep empty state
  } finally {
    loading.value = false
  }
}

onMounted(() => fetchReviews(1))
</script>

<style scoped>
.review-section { padding: 0; }

/* 评分摘要 */
.review-summary {
  display: flex; align-items: center; gap: 12px;
  padding: 20px; background: #fafafa; border-radius: 8px; margin-bottom: 16px;
}
.avg-rating { display: flex; align-items: baseline; }
.score { font-size: 32px; font-weight: 700; color: #e74c3c; }
.out-of { color: #999; font-size: 14px; margin-left: 2px; }
.total-count { color: #999; font-size: 13px; margin-left: auto; }

/* 评分分布 */
.rating-bars { margin-bottom: 20px; }
.bar-row { display: flex; align-items: center; gap: 10px; margin-bottom: 6px; }
.star-label { width: 32px; color: #666; font-size: 13px; text-align: right; flex-shrink: 0; }
.bar-row :deep(.el-progress) { flex: 1; }
.bar-count { width: 40px; color: #999; font-size: 12px; }

/* 评论列表 */
.review-list { display: flex; flex-direction: column; gap: 0; }
.review-item {
  padding: 16px 0; border-bottom: 1px solid #f0f0f0;
}
.review-item:last-child { border-bottom: none; }
.review-header { display: flex; align-items: center; gap: 10px; margin-bottom: 10px; }
.review-nickname { font-size: 14px; color: #333; font-weight: 500; }
.review-time { color: #999; font-size: 12px; margin-left: auto; }
.review-content { color: #666; font-size: 14px; line-height: 1.6; margin: 0; padding-left: 42px; }

.review-pagination { display: flex; justify-content: center; margin-top: 20px; }
</style>
