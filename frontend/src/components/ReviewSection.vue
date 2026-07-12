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

    <!-- 评论列表（树形） -->
    <div class="review-list" v-if="data.records?.length">
      <div class="review-tree-item" v-for="item in data.records" :key="item.id">
        <!-- 顶层评论 -->
        <div class="comment-root">
          <div class="review-header">
            <el-avatar :size="36" :src="item.avatar" />
            <div class="header-info">
              <span class="review-nickname">{{ item.nickname }}</span>
              <el-rate v-if="item.rating" :model-value="item.rating" disabled size="small" />
            </div>
            <span class="review-time">{{ item.createTime }}</span>
          </div>
          <p class="review-content">{{ item.content }}</p>
          <div class="review-actions">
            <el-button text size="small" @click="toggleReply(item)">
              <el-icon><ChatDotRound /></el-icon>
              {{ replyTarget?.id === item.id ? '取消回复' : `回复${item.replyCount ? ` (${item.replyCount})` : ''}` }}
            </el-button>
          </div>

          <!-- 回复输入框 -->
          <div class="reply-input" v-if="replyTarget?.id === item.id">
            <el-input
              v-model="replyContent"
              type="textarea"
              :rows="2"
              maxlength="500"
              show-word-count
              :placeholder="`回复 ${item.nickname}…`"
            />
            <div class="reply-actions">
              <el-button size="small" @click="replyTarget = null">取消</el-button>
              <el-button size="small" type="primary" @click="submitReply(item)" :loading="replySubmitting">回复</el-button>
            </div>
          </div>

          <!-- 子回复列表 -->
          <div class="replies" v-if="item.replies?.length">
            <div class="reply-item" v-for="reply in item.replies" :key="reply.id">
              <div class="reply-header">
                <el-avatar :size="24" :src="reply.avatar" />
                <span class="reply-nickname">{{ reply.nickname }}</span>
                <span class="reply-to" v-if="reply.replyToNickname">回复 {{ reply.replyToNickname }}</span>
                <span class="reply-time">{{ reply.createTime }}</span>
              </div>
              <p class="reply-content">{{ reply.content }}</p>
              <el-button text size="small" @click="toggleReply(item, reply)" class="reply-btn">回复</el-button>

              <!-- 回复子评论的输入框 -->
              <div class="reply-input sub-reply" v-if="replyTarget?.id === reply.id">
                <el-input
                  v-model="replyContent"
                  type="textarea"
                  :rows="2"
                  maxlength="500"
                  show-word-count
                  :placeholder="`回复 ${reply.nickname}…`"
                />
                <div class="reply-actions">
                  <el-button size="small" @click="replyTarget = null">取消</el-button>
                  <el-button size="small" type="primary" @click="submitReply(item, reply)" :loading="replySubmitting">回复</el-button>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
    <el-empty v-else-if="!loading" description="暂无评论，来发表第一条吧" />

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
import { getSpuReviews, createReview } from '@/api/review'
import { useUserStore } from '@/stores/user'
import { ElMessage } from 'element-plus'
import { ChatDotRound } from '@element-plus/icons-vue'

const props = defineProps({
  spuId: { type: Number, required: true }
})

const userStore = useUserStore()
const loading = ref(false)
const currentPage = ref(1)

const data = reactive({
  total: 0, page: 1, size: 10, avgRating: 0,
  ratingDistribution: {}, records: []
})

// 回复状态
const replyTarget = ref(null) // 当前回复目标: { id, nickname }
const replyContent = ref('')
const replySubmitting = ref(false)

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
  } catch { /* keep empty */ }
  finally { loading.value = false }
}

function toggleReply(rootItem, replyItem) {
  const target = replyItem || rootItem
  if (replyTarget.value?.id === target.id) {
    replyTarget.value = null
    replyContent.value = ''
  } else {
    replyTarget.value = { id: target.id, nickname: target.nickname, rootId: rootItem.id }
    replyContent.value = ''
  }
}

async function submitReply(rootItem, replyItem) {
  if (!userStore.isLoggedIn) { ElMessage.warning('请先登录'); return }
  if (!replyContent.value.trim()) { ElMessage.warning('请输入回复内容'); return }

  replySubmitting.value = true
  try {
    const parentItem = replyItem || rootItem
    await createReview({
      spuId: props.spuId,
      skuId: rootItem.skuId,
      parentId: parentItem.id,
      content: replyContent.value.trim()
    })
    ElMessage.success('回复成功')
    replyTarget.value = null
    replyContent.value = ''
    // 重新加载当前页
    await fetchReviews(currentPage.value)
  } catch { /* error handled by interceptor */ }
  finally { replySubmitting.value = false }
}

onMounted(() => fetchReviews(1))
</script>

<style scoped>
.review-section { padding: 0; }

/* 评分摘要 */
.review-summary { display: flex; align-items: center; gap: 12px; padding: 20px; background: #fafafa; border-radius: 8px; margin-bottom: 16px; }
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

/* 评论列表 - 树形 */
.review-list { display: flex; flex-direction: column; gap: 0; }
.review-tree-item { border-bottom: 1px solid #f0f0f0; padding: 16px 0; }
.review-tree-item:last-child { border-bottom: none; }

.comment-root {  }
.review-header { display: flex; align-items: flex-start; gap: 10px; margin-bottom: 10px; }
.header-info { display: flex; flex-direction: column; gap: 2px; }
.review-nickname { font-size: 14px; color: #333; font-weight: 500; }
.review-time { color: #999; font-size: 12px; margin-left: auto; }
.review-content { color: #333; font-size: 15px; line-height: 1.6; margin: 0 0 8px 0; padding-left: 46px; }
.review-actions { padding-left: 46px; margin-bottom: 4px; }

/* 回复 */
.reply-input { margin: 8px 0 12px 46px; }
.reply-input.sub-reply { margin-left: 70px; }
.reply-input :deep(.el-textarea__inner) { resize: none; }
.reply-actions { display: flex; justify-content: flex-end; gap: 8px; margin-top: 6px; }

/* 子回复列表 */
.replies { background: #f8f9fa; border-radius: 8px; padding: 8px 12px; margin-left: 46px; margin-top: 8px; }
.reply-item { padding: 10px 0; border-bottom: 1px solid #eee; }
.reply-item:last-child { border-bottom: none; }
.reply-header { display: flex; align-items: center; gap: 8px; margin-bottom: 4px; }
.reply-nickname { font-size: 13px; color: #333; font-weight: 500; }
.reply-to { font-size: 12px; color: #1890ff; }
.reply-time { font-size: 11px; color: #bbb; margin-left: auto; }
.reply-content { color: #555; font-size: 14px; line-height: 1.5; margin: 0 0 0 0; padding-left: 32px; }
.reply-btn { padding-left: 32px; font-size: 12px; margin-top: 2px; color: #999; }

.review-pagination { display: flex; justify-content: center; margin-top: 20px; }
</style>
