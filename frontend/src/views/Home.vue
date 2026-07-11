<template>
  <div class="home">
    <!-- Banner -->
    <div class="banner">
      <h1>SmartSeckill AI 秒杀商城</h1>
      <p>高性能 · 高并发 · AI 风控</p>
      <el-button type="danger" size="large" round @click="$router.push('/seckill')">🔥 查看秒杀活动</el-button>
    </div>

    <!-- Search & Filters -->
    <div class="filters">
      <el-input v-model="keyword" placeholder="搜索商品..." clearable style="width: 300px" @keyup.enter="search" />
      <el-select v-model="categoryId" placeholder="全部分类" clearable style="width: 160px" @change="onCategoryChange">
        <el-option v-for="c in categories" :key="c.id" :label="c.categoryName" :value="c.id" />
      </el-select>
      <el-select v-model="brandId" placeholder="全部品牌" clearable style="width: 160px" @change="search">
        <el-option v-for="b in brands" :key="b.id" :label="b.brandName" :value="b.id" />
      </el-select>
      <el-button type="primary" @click="search">搜索</el-button>
    </div>

    <!-- Product Grid -->
    <div class="product-grid" v-loading="loading">
      <ProductCard v-for="p in products" :key="p.id" :product="p" />
    </div>
    <el-empty v-if="!loading && products.length === 0" description="暂无商品" />

    <!-- Pagination -->
    <div class="pagination" v-if="total > 0">
      <el-pagination
        v-model:current-page="page"
        :page-size="size"
        :total="total"
        layout="prev, pager, next"
        @current-change="fetchProducts" />
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { getProductList, getCategories, getBrands } from '@/api/product'
import ProductCard from '@/components/ProductCard.vue'

const products = ref([])
const categories = ref([])
const brands = ref([])
const loading = ref(false)
const keyword = ref('')
const categoryId = ref(null)
const brandId = ref(null)
const page = ref(1)
const size = ref(12)
const total = ref(0)

async function fetchProducts() {
  loading.value = true
  try {
    const res = await getProductList({ page: page.value, size: size.value, keyword: keyword.value, categoryId: categoryId.value, brandId: brandId.value, status: 1 })
    products.value = res.records
    total.value = res.total
  } catch { products.value = [] }
  finally { loading.value = false }
}

async function fetchFilters() {
  // 默认只展示一级分类，避免二三级分类与一级重复出现
  try { categories.value = await getCategories(0) } catch { categories.value = [] }
  try { brands.value = await getBrands() } catch { brands.value = [] }
}

async function onCategoryChange(val) {
  brandId.value = null  // 切换分类时重置品牌选择
  if (val) {
    try { brands.value = await getBrands(val) } catch { brands.value = [] }
  } else {
    try { brands.value = await getBrands() } catch { brands.value = [] }
  }
  search()
}

function search() { page.value = 1; fetchProducts() }

onMounted(() => { fetchFilters(); fetchProducts() })
</script>

<style scoped>
.banner {
  background: linear-gradient(135deg, #e74c3c 0%, #c0392b 100%);
  color: #fff;
  text-align: center;
  padding: 60px 20px;
}
.banner h1 { font-size: 36px; margin-bottom: 8px; }
.banner p { font-size: 16px; opacity: .9; margin-bottom: 20px; }
.filters {
  display: flex;
  gap: 12px;
  padding: 20px 40px;
  background: #fff;
  border-bottom: 1px solid #eee;
  flex-wrap: wrap;
}
.product-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 16px;
  padding: 20px 40px;
}
.pagination { display: flex; justify-content: center; padding: 20px; }
</style>
