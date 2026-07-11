<template>
  <div class="cart-page">
    <h2>购物车 <span class="count">({{ cartStore.items.length }} 件)</span></h2>
    <div class="cart-main" v-loading="cartStore.loading">
      <div class="cart-list" v-if="cartStore.items.length > 0">
        <div class="cart-item" v-for="item in cartStore.items" :key="item.skuId">
          <el-checkbox v-model="item.checked" />
          <el-image :src="item.skuImage || defaultImg" fit="cover" class="item-img" />
          <div class="item-info">
            <h4>{{ item.skuName }}</h4>
            <p class="spec" v-if="item.specInfo">{{ item.specInfo }}</p>
          </div>
          <div class="item-price">¥{{ item.price }}</div>
          <el-input-number v-model="item.quantity" :min="1" :max="99" size="small" @change="cartStore.updateQty(item.skuId, item.quantity)" />
          <div class="item-total">¥{{ (item.price * item.quantity).toFixed(2) }}</div>
          <el-button type="danger" link @click="cartStore.removeItem(item.skuId)">删除</el-button>
        </div>
      </div>
      <el-empty v-else description="购物车是空的" />

      <div class="cart-sidebar" v-if="cartStore.items.length > 0">
        <el-card>
          <div class="summary">
            <p>已选 {{ checkedItems.length }} 件，共 {{ checkedTotal }} 件</p>
            <p class="total">合计：<span>¥{{ checkedPrice.toFixed(2) }}</span></p>
          </div>
          <el-button type="danger" size="large" style="width:100%" @click="showCheckout" :disabled="checkedItems.length === 0">
            去结算
          </el-button>
        </el-card>
      </div>
    </div>

    <!-- Checkout Dialog -->
    <el-dialog title="确认订单" v-model="checkoutVisible" width="600px" @close="checkoutStep = 1">
      <div v-if="checkoutStep === 1">
        <!-- Address Selection -->
        <h4 style="margin-bottom:12px">1. 选择收货地址</h4>
        <el-radio-group v-model="selectedAddressId" style="width:100%">
          <div v-for="addr in addresses" :key="addr.id" class="addr-item" :class="{ active: selectedAddressId === addr.id }">
            <el-radio :label="addr.id" border style="width:100%">
              <b>{{ addr.receiverName }}</b> {{ addr.receiverPhone }}<br>
              <span style="font-size:12px;color:#999">{{ addr.province }} {{ addr.city }} {{ addr.district }} {{ addr.detailAddress }}</span>
              <el-tag v-if="addr.isDefault" size="small" type="danger" style="margin-left:8px">默认</el-tag>
            </el-radio>
          </div>
        </el-radio-group>
        <el-button size="small" type="primary" text @click="showAddAddress" style="margin-top:8px">+ 添加新地址</el-button>

        <!-- Add Address mini form -->
        <div v-if="addingAddress" style="margin-top:12px;padding:12px;background:#f5f7fa;border-radius:6px">
          <el-form :model="addrForm" label-position="top" size="small">
            <el-row :gutter="12">
              <el-col :span="12"><el-form-item label="收货人"><el-input v-model="addrForm.receiverName" /></el-form-item></el-col>
              <el-col :span="12"><el-form-item label="手机号"><el-input v-model="addrForm.receiverPhone" /></el-form-item></el-col>
            </el-row>
            <el-row :gutter="12">
              <el-col :span="8"><el-form-item label="省"><el-input v-model="addrForm.province" /></el-form-item></el-col>
              <el-col :span="8"><el-form-item label="市"><el-input v-model="addrForm.city" /></el-form-item></el-col>
              <el-col :span="8"><el-form-item label="区"><el-input v-model="addrForm.district" /></el-form-item></el-col>
            </el-row>
            <el-form-item label="详细地址"><el-input v-model="addrForm.detailAddress" /></el-form-item>
          </el-form>
          <el-button size="small" type="primary" @click="saveAddress">保存并选择</el-button>
        </div>
      </div>

      <div v-if="checkoutStep === 2">
        <!-- Coupon Selection -->
        <h4 style="margin-bottom:12px">2. 选择优惠券 <span style="font-size:12px;color:#999">(可选)</span></h4>
        <el-radio-group v-model="selectedCouponId" style="width:100%">
          <el-radio :label="null" border style="width:100%;margin-bottom:8px">不使用优惠券</el-radio>
          <div v-for="c in myCoupons" :key="c.id">
            <el-radio :label="c.id" border style="width:100%;margin-bottom:8px"
              :disabled="checkedPrice < (c.minAmount || 0)">
              <b>{{ c.couponName }}</b>
              <el-tag size="small" :type="c.couponType === 1 ? 'warning' : 'primary'" style="margin-left:6px">
                {{ c.couponType === 1 ? '满' + c.minAmount + '减' + c.amount : (c.discount * 10).toFixed(1) + '折' }}
              </el-tag>
              <span style="float:right;font-size:12px;color:#999">{{ c.startTime?.slice(0,10) }} ~ {{ c.endTime?.slice(0,10) }}</span>
            </el-radio>
          </div>
        </el-radio-group>
        <p v-if="myCoupons.length === 0" style="color:#999;padding:12px">暂无可用优惠券</p>
      </div>

      <div v-if="checkoutStep === 3">
        <!-- Confirm -->
        <h4 style="margin-bottom:12px">3. 确认订单</h4>
        <el-table :data="checkedItems" border size="small">
          <el-table-column prop="skuName" label="商品" />
          <el-table-column label="单价" width="80"><template #default="{ row }">¥{{ row.price }}</template></el-table-column>
          <el-table-column label="数量" width="60"><template #default="{ row }">{{ row.quantity }}</template></el-table-column>
          <el-table-column label="小计" width="80"><template #default="{ row }">¥{{ (row.price * row.quantity).toFixed(2) }}</template></el-table-column>
        </el-table>
        <div style="text-align:right;margin-top:16px;line-height:2.2">
          <p>商品合计：<b>¥{{ checkedPrice.toFixed(2) }}</b></p>
          <p v-if="couponDiscount > 0">优惠券抵扣：<b style="color:#e74c3c">-¥{{ couponDiscount.toFixed(2) }}</b></p>
          <p style="font-size:18px">应付金额：<b style="color:#e74c3c">¥{{ finalPrice.toFixed(2) }}</b></p>
        </div>
      </div>

      <template #footer>
        <el-button @click="checkoutVisible = false">取消</el-button>
        <el-button v-if="checkoutStep > 1" @click="checkoutStep--">上一步</el-button>
        <el-button v-if="checkoutStep === 1" type="primary" @click="checkoutStep = 2" :disabled="!selectedAddressId">下一步</el-button>
        <el-button v-if="checkoutStep === 2" type="primary" @click="checkoutStep = 3">下一步</el-button>
        <el-button v-if="checkoutStep === 3" type="danger" @click="submitOrder" :loading="submitting">提交订单</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, reactive, onMounted } from 'vue'
import { useCartStore } from '@/stores/cart'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getMyAddresses, addAddress } from '@/api/address'
import { getMyCoupons } from '@/api/coupon'
import { createOrder } from '@/api/order'

const cartStore = useCartStore()
const router = useRouter()

const defaultImg = 'data:image/svg+xml,' + encodeURIComponent('<svg xmlns="http://www.w3.org/2000/svg" width="200" height="200" fill="#ddd"><rect width="200" height="200"/><text x="50%" y="50%" dominant-baseline="middle" text-anchor="middle" fill="#999" font-size="16">暂无图片</text></svg>')

// Checkout flow
const checkoutVisible = ref(false)
const checkoutStep = ref(1)
const submitting = ref(false)
const addresses = ref([])
const selectedAddressId = ref(null)
const myCoupons = ref([])
const selectedCouponId = ref(null)

// Add address
const addingAddress = ref(false)
const addrForm = reactive({ receiverName: '', receiverPhone: '', province: '', city: '', district: '', detailAddress: '' })

// Computed from cart items with checkboxes
const checkedItems = computed(() => cartStore.items.filter(i => i.checked))
const checkedTotal = computed(() => checkedItems.value.reduce((s, i) => s + i.quantity, 0))
const checkedPrice = computed(() => checkedItems.value.reduce((s, i) => s + i.price * i.quantity, 0))

const couponDiscount = computed(() => {
  if (!selectedCouponId.value) return 0
  const c = myCoupons.value.find(x => x.id === selectedCouponId.value)
  if (!c) return 0
  if (c.couponType === 1) return c.amount
  return checkedPrice.value * (1 - c.discount)
})

const finalPrice = computed(() => Math.max(0, checkedPrice.value - couponDiscount.value))

async function showCheckout() {
  if (checkedItems.value.length === 0) {
    ElMessage.warning('请选择要结算的商品')
    return
  }
  checkoutStep.value = 1
  selectedCouponId.value = null

  // 加载地址列表
  try {
    addresses.value = await getMyAddresses()
  } catch {
    addresses.value = []
  }
  if (addresses.value.length === 0) {
    ElMessage.warning('暂无收货地址，请先添加地址')
    // 仍然打开弹窗让用户添加地址
  } else {
    const def = addresses.value.find(a => a.isDefault === 1) || addresses.value[0]
    selectedAddressId.value = def.id
  }

  // 加载优惠券（非关键，失败不影响下单流程）
  try {
    myCoupons.value = await getMyCoupons()
  } catch {
    myCoupons.value = []
  }
  checkoutVisible.value = true
}

function showAddAddress() { addingAddress.value = true }

async function saveAddress() {
  try {
    await addAddress({ ...addrForm })
    ElMessage.success('地址已添加')
    addingAddress.value = false
    try { addresses.value = await getMyAddresses() } catch {}
  } catch { /* handled */ }
}

async function submitOrder() {
  if (!selectedAddressId.value) { ElMessage.warning('请选择收货地址'); return }
  submitting.value = true
  try {
    const items = checkedItems.value.map(i => ({
      skuId: i.skuId, spuId: i.spuId, quantity: i.quantity
    }))
    const payload = {
      addressId: selectedAddressId.value,
      items,
      couponId: selectedCouponId.value
    }
    const order = await createOrder(payload)
    ElMessage.success('下单成功！订单号：' + order.orderSn)
    // 从购物车移除已购商品
    for (const item of checkedItems.value) {
      await cartStore.removeItem(item.skuId)
    }
    checkoutVisible.value = false
    router.push('/orders')
  } catch (e) {
    // 地址无效时重新加载地址列表，确保前端数据与服务端一致
    if (e.code === 1502) {
      ElMessage.error('收货地址无效，请重新选择')
      try { addresses.value = await getMyAddresses() } catch { addresses.value = [] }
      if (addresses.value.length > 0) {
        selectedAddressId.value = addresses.value[0].id
      } else {
        selectedAddressId.value = null
      }
    }
    // 其他错误由 interceptor 统一处理
  }
  finally { submitting.value = false }
}

onMounted(() => {
  cartStore.fetchCart()
})
</script>

<style scoped>
.cart-page { max-width: 1100px; margin: 30px auto; padding: 0 20px; }
.cart-page h2 { margin-bottom: 20px; }
.count { color: #999; font-size: 14px; font-weight: normal; }
.cart-main { display: flex; gap: 20px; align-items: flex-start; }
.cart-list { flex: 1; display: flex; flex-direction: column; gap: 12px; }
.cart-item { display: flex; align-items: center; gap: 12px; background: #fff; padding: 16px; border-radius: 8px; }
.item-img { width: 80px; height: 80px; border-radius: 6px; flex-shrink: 0; }
.item-info { flex: 1; min-width: 0; }
.item-info h4 { font-size: 15px; }
.spec { color: #999; font-size: 12px; margin-top: 4px; }
.item-price, .item-total { font-weight: 700; color: #e74c3c; width: 90px; text-align: center; flex-shrink: 0; }
.cart-sidebar { width: 280px; position: sticky; top: 80px; }
.summary { margin-bottom: 16px; line-height: 2; }
.total span { color: #e74c3c; font-size: 22px; font-weight: 700; }
.addr-item { margin-bottom: 8px; }
.addr-item .el-radio { width: 100%; }
</style>
