<template>
  <div class="product-card" @click="$router.push(`/product/${product.id}`)">
    <el-image :src="product.mainImage || defaultImg" fit="cover" class="product-img" />
    <div class="product-info">
      <h3 class="product-name">{{ product.spuName }}</h3>
      <div class="product-bottom">
        <span class="price">¥{{ product.minPrice || '--' }}</span>
        <span class="sales" v-if="product.sales">已售 {{ product.sales }}</span>
      </div>
    </div>
  </div>
</template>

<script setup>
defineProps({ product: { type: Object, required: true } })
const defaultImg = 'data:image/svg+xml,' + encodeURIComponent('<svg xmlns="http://www.w3.org/2000/svg" width="200" height="200" fill="#ddd"><rect width="200" height="200"/><text x="50%" y="50%" dominant-baseline="middle" text-anchor="middle" fill="#999" font-size="16">暂无图片</text></svg>')
</script>

<style scoped>
.product-card {
  background: #fff;
  border-radius: 8px;
  overflow: hidden;
  cursor: pointer;
  transition: transform .2s, box-shadow .2s;
}
.product-card:hover { transform: translateY(-4px); box-shadow: 0 8px 24px rgba(0,0,0,.1); }
.product-img { width: 100%; height: 200px; }
.product-info { padding: 12px; }
.product-name { font-size: 14px; margin-bottom: 8px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.product-bottom { display: flex; justify-content: space-between; align-items: center; }
.price { color: #e74c3c; font-size: 18px; font-weight: 700; }
.sales { color: #999; font-size: 12px; }
</style>
