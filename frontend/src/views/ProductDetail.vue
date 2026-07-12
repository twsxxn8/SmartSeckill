<template>
  <div class="product-detail" v-loading="loading">
    <div class="detail-main" v-if="spu">
      <el-image :src="spu.mainImage" fit="cover" class="main-img" />
      <div class="detail-info">
        <h1>{{ spu.spuName }}</h1>
        <div class="meta-line" v-if="spu.brandName || spu.categoryName">
          <span v-if="spu.brandName" class="meta-tag">品牌：{{ spu.brandName }}</span>
          <span v-if="spu.categoryName" class="meta-tag">分类：{{ spu.categoryName }}</span>
        </div>
        <p class="desc">{{ spu.description }}</p>
        <div class="sku-list">
          <el-radio-group v-model="selectedSkuId" size="large">
            <el-radio-button v-for="sku in skus" :key="sku.id" :value="sku.id">
              {{ sku.skuName }} — ¥{{ sku.price }}
            </el-radio-button>
          </el-radio-group>
        </div>
        <div class="price-box">
          <span class="price-label">价格</span>
          <span class="price">¥{{ selectedSku?.price || '--' }}</span>
        </div>
        <div class="actions">
          <el-input-number v-model="quantity" :min="1" :max="99" />
          <el-button type="danger" size="large" @click="addToCart">加入购物车</el-button>
        </div>
      </div>
    </div>
    <el-empty v-else-if="!loading" description="商品不存在" />

    <!-- 标签页：商品详情 | 商品评论 -->
    <div class="detail-tabs" v-if="spu">
      <el-tabs v-model="activeTab">
        <el-tab-pane label="商品详情" name="detail">
          <div class="spec-section">
            <h3>规格参数</h3>
            <SpecTable :specs="currentSpecs" />
          </div>
        </el-tab-pane>
        <el-tab-pane label="商品评论" name="reviews">
          <ReviewSection :spuId="spu.id" />
        </el-tab-pane>
      </el-tabs>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { getProductDetailFull } from '@/api/product'
import { useCartStore } from '@/stores/cart'
import { useUserStore } from '@/stores/user'
import SpecTable from '@/components/SpecTable.vue'
import ReviewSection from '@/components/ReviewSection.vue'
import { ElMessage } from 'element-plus'
import router from '@/router'

const route = useRoute()
const cartStore = useCartStore()
const userStore = useUserStore()
const spu = ref(null)
const skus = ref([])
const selectedSkuId = ref(null)
const quantity = ref(1)
const loading = ref(false)
const activeTab = ref('detail')

const selectedSku = computed(() => skus.value.find(s => s.id === selectedSkuId.value))

const currentSpecs = computed(() => {
  const sku = skus.value.find(s => s.id === selectedSkuId.value)
  return sku?.specInfo || {}
})

async function fetchDetail() {
  loading.value = true
  try {
    const data = await getProductDetailFull(route.params.id)
    spu.value = data
    skus.value = data.skus || []
    if (skus.value.length > 0) selectedSkuId.value = skus.value[0].id
  } catch { spu.value = null }
  finally { loading.value = false }
}

async function addToCart() {
  if (!userStore.isLoggedIn) { router.push('/login'); return }
  if (!selectedSku.value) { ElMessage.warning('请选择规格'); return }
  await cartStore.addItem({
    skuId: selectedSku.value.id,
    spuId: spu.value.id,
    skuName: `${spu.value.spuName} - ${selectedSku.value.skuName}`,
    skuImage: selectedSku.value.image || spu.value.mainImage,
    specInfo: selectedSku.value.specInfo,
    price: selectedSku.value.price,
    quantity: quantity.value
  })
}

onMounted(fetchDetail)
</script>

<style scoped>
.product-detail { max-width: 1100px; margin: 30px auto; padding: 0 20px; }
.detail-main { display: flex; gap: 40px; background: #fff; padding: 30px; border-radius: 12px; }
.main-img { width: 400px; height: 400px; border-radius: 8px; }
.detail-info { flex: 1; display: flex; flex-direction: column; gap: 16px; }
.detail-info h1 { font-size: 22px; margin: 0; }
.meta-line { display: flex; gap: 20px; }
.meta-tag { color: #666; font-size: 13px; background: #f5f5f5; padding: 2px 10px; border-radius: 4px; }
.desc { color: #666; line-height: 1.6; margin: 0; }
.sku-list { margin: 8px 0; }
.price-box { background: #fef0f0; padding: 16px; border-radius: 8px; }
.price-label { color: #999; margin-right: 8px; }
.price { color: #e74c3c; font-size: 28px; font-weight: 700; }
.actions { display: flex; gap: 12px; align-items: center; margin-top: 8px; }

.detail-tabs { background: #fff; padding: 0 30px 30px; border-radius: 0 0 12px 12px; margin-top: 0; }
.detail-tabs :deep(.el-tabs__header) { margin-bottom: 0; }
.spec-section { margin-top: 20px; }
.spec-section h3 { font-size: 15px; color: #333; margin-bottom: 12px; padding-bottom: 8px; border-bottom: 1px solid #f0f0f0; }
</style>
