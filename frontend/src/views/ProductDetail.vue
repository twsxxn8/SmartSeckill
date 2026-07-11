<template>
  <div class="product-detail" v-loading="loading">
    <div class="detail-main" v-if="spu">
      <el-image :src="spu.mainImage" fit="cover" class="main-img" />
      <div class="detail-info">
        <h1>{{ spu.spuName }}</h1>
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
    <el-empty v-else description="商品不存在" />
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { getProductDetail, getSkuList } from '@/api/product'
import { useCartStore } from '@/stores/cart'
import { useUserStore } from '@/stores/user'
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

const selectedSku = computed(() => skus.value.find(s => s.id === selectedSkuId.value))

async function fetchDetail() {
  loading.value = true
  try {
    spu.value = await getProductDetail(route.params.id)
    skus.value = await getSkuList(route.params.id)
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
.detail-info h1 { font-size: 22px; }
.desc { color: #666; line-height: 1.6; }
.sku-list { margin: 8px 0; }
.price-box { background: #fef0f0; padding: 16px; border-radius: 8px; }
.price-label { color: #999; margin-right: 8px; }
.price { color: #e74c3c; font-size: 28px; font-weight: 700; }
.actions { display: flex; gap: 12px; align-items: center; margin-top: 8px; }
</style>
