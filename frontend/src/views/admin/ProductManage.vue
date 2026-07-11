<template>
  <div class="product-manage">
    <div class="toolbar">
      <h2>商品管理</h2>
      <el-button type="primary" @click="showSpuDialog(null)">新增 SPU</el-button>
    </div>

    <div class="search-bar">
      <el-input v-model="keyword" placeholder="搜索商品..." clearable style="width:240px" @keyup.enter="fetch" />
      <el-select v-model="filterCategory" placeholder="按分类筛选" clearable style="width:160px" @change="fetch">
        <el-option v-for="c in categories" :key="c.id" :label="c.categoryName" :value="c.id" />
      </el-select>
      <el-button type="primary" @click="fetch">搜索</el-button>
    </div>

    <el-table :data="products" v-loading="loading" border stripe>
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column prop="spuName" label="SPU名称" min-width="160" />
      <el-table-column label="最低售价" width="100">
        <template #default="{ row }">¥{{ row.minPrice || '--' }}</template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : row.status === 2 ? 'info' : 'warning'" size="small">
            {{ row.status === 1 ? '已上架' : row.status === 2 ? '已下架' : '草稿' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="创建时间" width="170" />
      <el-table-column label="操作" width="340" fixed="right">
        <template #default="{ row }">
          <el-button size="small" @click="showSpuDialog(row)">编辑</el-button>
          <el-button size="small" @click="manageSkus(row)">SKU</el-button>
          <el-button v-if="row.status !== 1" size="small" type="success" @click="publish(row.id)">上架</el-button>
          <el-button v-if="row.status === 1" size="small" type="warning" @click="unpublish(row.id)">下架</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pagination" v-if="total > size">
      <el-pagination v-model:current-page="page" :page-size="size" :total="total" layout="prev, pager, next" @current-change="fetch" />
    </div>

    <!-- SPU Dialog -->
    <el-dialog :title="editingSpu?.id ? '编辑SPU' : '新增SPU'" v-model="spuDialog" width="520px" @closed="spuFormRef?.resetFields()">
      <el-form :model="spuForm" :rules="spuRules" ref="spuFormRef" label-position="top">
        <el-form-item label="SPU 名称" prop="spuName">
          <el-input v-model="spuForm.spuName" placeholder="例如：iPhone 15 Pro Max" />
        </el-form-item>
        <el-form-item label="所属分类" prop="categoryId">
          <el-select v-model="spuForm.categoryId" placeholder="选择分类" style="width:100%">
            <el-option v-for="c in allCategories" :key="c.id" :label="c.categoryName" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="品牌">
          <el-select v-model="spuForm.brandId" placeholder="选择品牌" clearable style="width:100%">
            <el-option v-for="b in brands" :key="b.id" :label="b.brandName" :value="b.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="spuForm.status">
            <el-radio :label="0">草稿</el-radio>
            <el-radio :label="1">上架</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="主图 URL">
          <el-input v-model="spuForm.mainImage" placeholder="https://..." />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="spuForm.description" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="spuDialog = false">取消</el-button>
        <el-button type="primary" @click="saveSpu" :loading="saving">保存</el-button>
      </template>
    </el-dialog>

    <!-- SKU Dialog -->
    <el-dialog :title="`SKU 管理 — ${skuSpuName}`" v-model="skuDialog" width="750px">
      <div style="margin-bottom:12px">
        <el-button type="primary" size="small" @click="showSkuForm(null)">+ 添加SKU</el-button>
      </div>
      <el-table :data="skus" border size="small" empty-text="暂无SKU，请点击上方按钮添加">
        <el-table-column prop="id" label="ID" width="50" />
        <el-table-column prop="skuName" label="SKU名称" min-width="160" />
        <el-table-column label="售价" width="100">
          <template #default="{ row }">¥{{ row.price }}</template>
        </el-table-column>
        <el-table-column label="市场价" width="100">
          <template #default="{ row }">¥{{ row.marketPrice || '--' }}</template>
        </el-table-column>
        <el-table-column label="库存" width="80">
          <template #default="{ row }">{{ row.availableStock ?? '--' }}</template>
        </el-table-column>
        <el-table-column prop="specInfo" label="规格" width="160" />
        <el-table-column label="操作" width="120">
          <template #default="{ row }">
            <el-button size="small" @click="showSkuForm(row)">编辑</el-button>
            <el-button size="small" type="danger" @click="deleteSku(row.id)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="skuDialog = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- SKU Form Dialog -->
    <el-dialog :title="editingSku?.id ? '编辑SKU' : '添加SKU'" v-model="skuFormDialog" width="460px">
      <el-form :model="skuForm" :rules="skuRules" ref="skuFormRef" label-position="top">
        <el-form-item label="SKU 名称" prop="skuName">
          <el-input v-model="skuForm.skuName" placeholder="例如：iPhone 15 Pro Max 256GB 原色钛金属" />
        </el-form-item>
        <el-form-item label="SKU 编码">
          <el-input v-model="skuForm.skuCode" placeholder="例如：IP15PM-256-NT" />
        </el-form-item>
        <el-form-item label="售价" prop="price">
          <el-input-number v-model="skuForm.price" :min="0.01" :precision="2" style="width:100%" />
        </el-form-item>
        <el-form-item label="市场价">
          <el-input-number v-model="skuForm.marketPrice" :min="0" :precision="2" style="width:100%" />
        </el-form-item>
        <el-form-item label="库存数量">
          <el-input-number v-model="skuForm.stock" :min="0" style="width:100%" />
        </el-form-item>
        <el-form-item label="规格信息 (JSON)">
          <el-input v-model="skuForm.specInfo" placeholder='{"颜色":"红色","内存":"128G"}' />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="skuFormDialog = false">取消</el-button>
        <el-button type="primary" @click="saveSku" :loading="savingSku">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { getProductList, getCategories, getBrands, getSkuList, saveSpu as saveSpuApi, updateSpu as updateSpuApi, publishProduct, unpublishProduct } from '@/api/product'
import { ElMessage } from 'element-plus'
import request from '@/api/request'

const products = ref([])
const categories = ref([])
const allCategories = ref([])
const brands = ref([])
const loading = ref(false)
const saving = ref(false)
const keyword = ref('')
const filterCategory = ref(null)
const page = ref(1)
const size = ref(10)
const total = ref(0)

// SPU dialog
const spuDialog = ref(false)
const editingSpu = ref(null)
const spuFormRef = ref(null)
const spuForm = reactive({ spuName: '', categoryId: null, brandId: null, mainImage: '', description: '', status: 0 })
const spuRules = {
  spuName: [{ required: true, message: '请输入SPU名称', trigger: 'blur' }],
  categoryId: [{ required: true, message: '请选择分类', trigger: 'change' }]
}

// SKU dialog
const skuDialog = ref(false)
const skuFormDialog = ref(false)
const savingSku = ref(false)
const skuSpuName = ref('')
const skuSpuId = ref(null)
const skus = ref([])
const editingSku = ref(null)
const skuFormRef = ref(null)
const skuForm = reactive({ skuName: '', skuCode: '', price: 0, marketPrice: 0, specInfo: '', stock: 0 })
const skuRules = {
  skuName: [{ required: true, message: '请输入SKU名称', trigger: 'blur' }],
  price: [{ required: true, message: '请输入售价', trigger: 'blur' }]
}

async function fetch() {
  loading.value = true
  try {
    const res = await getProductList({
      page: page.value, size: size.value,
      keyword: keyword.value || undefined,
      categoryId: filterCategory.value
    })
    products.value = res.records
    total.value = res.total
  } catch { products.value = [] }
  finally { loading.value = false }
}

async function loadFilters() {
  try { allCategories.value = await getCategories() } catch { allCategories.value = [] }
  try { categories.value = await getCategories(0) } catch { categories.value = [] }
  try { brands.value = await getBrands() } catch { brands.value = [] }
}

function showSpuDialog(spu) {
  editingSpu.value = spu
  if (spu) {
    Object.assign(spuForm, {
      spuName: spu.spuName,
      categoryId: spu.categoryId,
      brandId: spu.brandId,
      mainImage: spu.mainImage || '',
      description: spu.description || '',
      status: spu.status
    })
  } else {
    spuFormRef.value?.resetFields()
    Object.assign(spuForm, {
      spuName: '', categoryId: null, brandId: null,
      mainImage: '', description: '', status: 0
    })
  }
  spuDialog.value = true
}

async function saveSpu() {
  const valid = await spuFormRef.value?.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    const payload = { ...spuForm }
    if (!payload.brandId) payload.brandId = null
    let savedSpu = null
    if (editingSpu.value?.id) {
      await updateSpuApi(editingSpu.value.id, payload)
      ElMessage.success('更新成功')
    } else {
      savedSpu = await saveSpuApi(payload)
      ElMessage.success('SPU 创建成功，请添加 SKU')
    }
    spuDialog.value = false
    await fetch()
    // 新建成功后直接使用返回的 SPU 对象（含 id）打开 SKU 管理
    if (!editingSpu.value?.id && savedSpu?.id) {
      manageSkus(savedSpu)
    }
  } catch { /* interceptor handled */ }
  finally { saving.value = false }
}

async function publish(id) { await publishProduct(id); ElMessage.success('已上架'); fetch() }
async function unpublish(id) { await unpublishProduct(id); ElMessage.success('已下架'); fetch() }

async function manageSkus(spu) {
  skuSpuId.value = spu.id
  skuSpuName.value = spu.spuName
  try {
    const raw = await getSkuList(spu.id)
    // 为每个 SKU 获取可用库存
    const enriched = await Promise.all(raw.map(async (sku) => {
      try {
        const res = await request.get(`/product/stock/${sku.id}`)
        return { ...sku, availableStock: res }
      } catch { return { ...sku, availableStock: 0 } }
    }))
    skus.value = enriched
  } catch { skus.value = [] }
  skuDialog.value = true
}

function showSkuForm(sku) {
  editingSku.value = sku
  skuFormRef.value?.resetFields()
  if (sku) {
    Object.assign(skuForm, {
      skuName: sku.skuName, skuCode: sku.skuCode || '',
      price: sku.price, marketPrice: sku.marketPrice || 0,
      specInfo: sku.specInfo || '', stock: sku.availableStock ?? 0
    })
  } else {
    Object.assign(skuForm, {
      skuName: '', skuCode: '', price: 0, marketPrice: 0, specInfo: '', stock: 100
    })
  }
  skuFormDialog.value = true
}

async function saveSku() {
  const valid = await skuFormRef.value?.validate().catch(() => false)
  if (!valid) return
  savingSku.value = true
  try {
    const payload = { ...skuForm, spuId: skuSpuId.value }
    const stockQty = payload.stock
    delete payload.stock
    let savedSkuId = editingSku.value?.id
    if (editingSku.value?.id) {
      await request.put(`/product/admin/sku/${editingSku.value.id}`, payload)
      ElMessage.success('SKU 已更新')
    } else {
      const res = await request.post('/product/admin/sku', payload)
      savedSkuId = res?.id
      ElMessage.success('SKU 已创建')
    }
    // 设置库存（新建或编辑都会调用，后端 upsert）
    if (savedSkuId && stockQty >= 0) {
      try { await request.post('/product/admin/stock', null, { params: { skuId: savedSkuId, stock: stockQty, stockType: 0 } }) } catch {}
    }
    skuFormDialog.value = false
    // 刷新 SKU 列表（含库存）
    try {
      const raw = await getSkuList(skuSpuId.value)
      const enriched = await Promise.all(raw.map(async (sku) => {
        try { const r = await request.get(`/product/stock/${sku.id}`); return { ...sku, availableStock: r } }
        catch { return { ...sku, availableStock: 0 } }
      }))
      skus.value = enriched
    } catch {}
  } catch { /* interceptor handled */ }
  finally { savingSku.value = false }
}

async function deleteSku(skuId) {
  try {
    await request.delete(`/product/admin/sku/${skuId}`)
    ElMessage.success('SKU 已删除')
    try {
      const raw = await getSkuList(skuSpuId.value)
      const enriched = await Promise.all(raw.map(async (sku) => {
        try { const r = await request.get(`/product/stock/${sku.id}`); return { ...sku, availableStock: r } }
        catch { return { ...sku, availableStock: 0 } }
      }))
      skus.value = enriched
    } catch {}
  } catch { /* interceptor handled */ }
}

onMounted(() => { loadFilters(); fetch() })
</script>

<style scoped>
.toolbar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.toolbar h2 { font-size: 20px; }
.search-bar { display: flex; gap: 12px; margin-bottom: 16px; }
.pagination { display: flex; justify-content: center; padding: 20px; }
</style>
