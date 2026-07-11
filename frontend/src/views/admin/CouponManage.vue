<template>
  <div class="coupon-manage">
    <div class="toolbar">
      <h2>优惠券管理</h2>
      <el-button type="primary" @click="showDialog(null)">新增优惠券</el-button>
    </div>

    <el-table :data="coupons" v-loading="loading" border stripe>
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column prop="couponName" label="名称" min-width="150" />
      <el-table-column label="类型" width="80">
        <template #default="{ row }">{{ row.couponType === 1 ? '满减' : '折扣' }}</template>
      </el-table-column>
      <el-table-column label="优惠" width="90">
        <template #default="{ row }">
          {{ row.couponType === 1 ? '¥' + row.amount : (row.discount * 10).toFixed(1) + '折' }}
        </template>
      </el-table-column>
      <el-table-column label="门槛" width="80">
        <template #default="{ row }">满{{ row.minAmount }}元</template>
      </el-table-column>
      <el-table-column label="发放/总量" width="90">
        <template #default="{ row }">{{ row.usedCount ?? 0 }}/{{ row.totalCount }}</template>
      </el-table-column>
      <el-table-column label="有效期" width="170">
        <template #default="{ row }">{{ row.startTime?.slice(0,10) }} ~ {{ row.endTime?.slice(0,10) }}</template>
      </el-table-column>
      <el-table-column label="状态" width="80">
        <template #default="{ row }">
          <el-tag size="small" :type="row.status === 1 ? 'success' : 'info'">{{ row.status === 1 ? '启用' : '停用' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="140" fixed="right">
        <template #default="{ row }">
          <el-button size="small" @click="showDialog(row)">编辑</el-button>
          <el-button size="small" type="danger" @click="remove(row.id)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pagination" v-if="total > size">
      <el-pagination v-model:current-page="page" :page-size="size" :total="total" layout="prev, pager, next" @current-change="fetch" />
    </div>

    <!-- Edit Dialog -->
    <el-dialog :title="editing?.id ? '编辑优惠券' : '新增优惠券'" v-model="dialogVisible" width="500px">
      <el-form :model="form" label-position="top">
        <el-form-item label="优惠券名称" required>
          <el-input v-model="form.couponName" placeholder="例如：新人专享 满100减20" />
        </el-form-item>
        <el-form-item label="类型" required>
          <el-radio-group v-model="form.couponType">
            <el-radio :label="1">满减</el-radio>
            <el-radio :label="2">折扣</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="form.couponType === 1" label="满减金额（元）">
          <el-input-number v-model="form.amount" :min="0" :precision="2" style="width:100%" />
        </el-form-item>
        <el-form-item v-if="form.couponType === 2" label="折扣比例 (0.85=85折)">
          <el-input-number v-model="form.discount" :min="0.01" :max="0.99" :step="0.05" :precision="2" style="width:100%" />
        </el-form-item>
        <el-form-item label="使用门槛（元）">
          <el-input-number v-model="form.minAmount" :min="0" :precision="2" style="width:100%" />
        </el-form-item>
        <el-form-item label="发放数量">
          <el-input-number v-model="form.totalCount" :min="1" style="width:100%" />
        </el-form-item>
        <el-form-item label="开始时间">
          <el-date-picker v-model="form.startTime" type="datetime" placeholder="选择开始时间" style="width:100%" />
        </el-form-item>
        <el-form-item label="结束时间">
          <el-date-picker v-model="form.endTime" type="datetime" placeholder="选择结束时间" style="width:100%" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="form.status" :active-value="1" :inactive-value="0" active-text="启用" inactive-text="停用" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="save" :loading="saving">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { getCouponList, createCoupon, updateCoupon, deleteCoupon } from '@/api/admin'
import dayjs from '@/utils/dayjs'
import { ElMessage, ElMessageBox } from 'element-plus'

const coupons = ref([])
const loading = ref(false)
const saving = ref(false)
const page = ref(1)
const size = ref(10)
const total = ref(0)
const dialogVisible = ref(false)
const editing = ref(null)
const form = reactive({
  couponName: '', couponType: 1, amount: 10, discount: 0.9, minAmount: 0,
  totalCount: 100, startTime: '', endTime: '', status: 1
})

async function fetch() {
  loading.value = true
  try {
    const res = await getCouponList({ page: page.value, size: size.value })
    coupons.value = res.records
    total.value = res.total
  } catch { coupons.value = [] }
  finally { loading.value = false }
}

function showDialog(coupon) {
  editing.value = coupon
  if (coupon) {
    Object.assign(form, {
      couponName: coupon.couponName, couponType: coupon.couponType,
      amount: coupon.amount, discount: coupon.discount, minAmount: coupon.minAmount,
      totalCount: coupon.totalCount, startTime: coupon.startTime, endTime: coupon.endTime, status: coupon.status
    })
  } else {
    Object.assign(form, {
      couponName: '', couponType: 1, amount: 10, discount: 0.9, minAmount: 0,
      totalCount: 100,
      startTime: dayjs().format('YYYY-MM-DD HH:mm:ss'),
      endTime: dayjs().add(30, 'day').format('YYYY-MM-DD HH:mm:ss'),
      status: 1
    })
  }
  dialogVisible.value = true
}

async function save() {
  saving.value = true
  try {
    if (editing.value?.id) {
      await updateCoupon(editing.value.id, { ...form })
      ElMessage.success('更新成功')
    } else {
      await createCoupon({ ...form })
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    fetch()
  } catch { /* handled */ }
  finally { saving.value = false }
}

async function remove(id) {
  try {
    await ElMessageBox.confirm('确定删除该优惠券？', '确认', { type: 'warning' })
    await deleteCoupon(id)
    ElMessage.success('已删除')
    fetch()
  } catch { /* cancelled or error */ }
}

onMounted(fetch)
</script>

<style scoped>
.toolbar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.toolbar h2 { font-size: 20px; }
.pagination { display: flex; justify-content: center; padding: 20px; }
</style>
