import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import * as cartApi from '@/api/cart'
import { ElMessage } from 'element-plus'

export const useCartStore = defineStore('cart', () => {
  const items = ref([])
  const loading = ref(false)
  const checkedMap = ref({})  // { [skuId]: boolean }

  const totalCount = computed(() => items.value.reduce((sum, i) => sum + i.quantity, 0))
  const totalPrice = computed(() => items.value.reduce((sum, i) => sum + (i.price || 0) * i.quantity, 0))

  /** 将服务端数据转为带 checked 状态的对象，确保 Vue 响应式正确追踪 */
  function mapWithChecked(rawItems) {
    return rawItems.map(item => {
      const key = String(item.skuId)
      if (!(key in checkedMap.value)) checkedMap.value[key] = true
      return { ...item, checked: checkedMap.value[key] }
    })
  }

  async function fetchCart() {
    loading.value = true
    try {
      items.value = mapWithChecked(await cartApi.getCart())
    } catch { items.value = [] }
    finally { loading.value = false }
  }

  async function addItem(item) {
    await cartApi.addToCart(item)
    ElMessage.success('已加入购物车')
    await fetchCart()
  }

  async function updateQty(skuId, qty) {
    await cartApi.updateCartQty(skuId, qty)
    const wasChecked = checkedMap.value[String(skuId)]
    items.value = mapWithChecked(await cartApi.getCart())
    if (wasChecked !== undefined) checkedMap.value[String(skuId)] = wasChecked
  }

  async function removeItem(skuId) {
    await cartApi.removeFromCart(skuId)
    ElMessage.success('已移除')
    delete checkedMap.value[String(skuId)]
    await fetchCart()
  }

  async function clearAll() {
    await cartApi.clearCart()
    items.value = []
    checkedMap.value = {}
  }

  return { items, loading, totalCount, totalPrice, fetchCart, addItem, updateQty, removeItem, clearAll }
})
