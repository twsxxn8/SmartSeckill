<template>
  <div class="activity-manage">
    <div class="toolbar">
      <h2>秒杀活动管理</h2>
      <el-button type="danger" @click="showDialog(null)">新增活动</el-button>
    </div>

    <el-table :data="activities" v-loading="loading" border stripe>
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column prop="activityName" label="活动名称" min-width="160" />
      <el-table-column prop="seckillPrice" label="秒杀价" width="100">
        <template #default="{ row }">¥{{ row.seckillPrice }}</template>
      </el-table-column>
      <el-table-column prop="originalPrice" label="原价" width="90">
        <template #default="{ row }">¥{{ row.originalPrice }}</template>
      </el-table-column>
      <el-table-column label="库存" width="110">
        <template #default="{ row }">{{ row.availableStock }}/{{ row.totalStock }}</template>
      </el-table-column>
      <el-table-column label="限购" width="60">
        <template #default="{ row }">{{ row.limitPerUser }}件</template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag size="small" :type="row.status === 1 ? 'danger' : row.status === 2 ? 'info' : 'warning'">
            {{ row.status === 0 ? '未开始' : row.status === 1 ? '进行中' : row.status === 2 ? '已结束' : '已取消' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="startTime" label="开始时间" width="170" />
      <el-table-column prop="endTime" label="结束时间" width="170" />
      <el-table-column label="操作" width="240" fixed="right">
        <template #default="{ row }">
          <el-button size="small" @click="showDialog(row)">编辑</el-button>
          <el-button size="small" type="warning" @click="preWarm(row.id)" :disabled="row.status === 2">预热</el-button>
          <el-button size="small" type="danger" @click="remove(row.id)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- Create/Edit Dialog -->
    <el-dialog :title="editing?.id ? '编辑秒杀活动' : '新增秒杀活动'" v-model="dialogVisible" width="520px">
      <el-form :model="form" label-position="top">
        <el-form-item label="活动名称" required>
          <el-input v-model="form.activityName" placeholder="例如：iPhone 15 Pro Max 限时秒杀" />
        </el-form-item>
        <el-form-item label="SKU ID" required>
          <el-input-number v-model="form.skuId" :min="1" style="width:100%" />
        </el-form-item>
        <el-form-item label="秒杀价格" required>
          <el-input-number v-model="form.seckillPrice" :min="0" :precision="2" style="width:100%" />
        </el-form-item>
        <el-form-item label="商品原价">
          <el-input-number v-model="form.originalPrice" :min="0" :precision="2" style="width:100%" />
        </el-form-item>
        <el-form-item label="总库存">
          <el-input-number v-model="form.totalStock" :min="1" style="width:100%" />
        </el-form-item>
        <el-form-item label="每人限购">
          <el-input-number v-model="form.limitPerUser" :min="1" :max="10" style="width:100%" />
        </el-form-item>
        <el-form-item label="开始时间">
          <el-date-picker v-model="form.startTime" type="datetime" placeholder="选择开始时间" style="width:100%" value-format="YYYY-MM-DD HH:mm:ss" />
        </el-form-item>
        <el-form-item label="结束时间">
          <el-date-picker v-model="form.endTime" type="datetime" placeholder="选择结束时间" style="width:100%" value-format="YYYY-MM-DD HH:mm:ss" />
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
import { createActivity, preWarmStock } from '@/api/seckill'
import { getAllActivities, updateActivity, deleteActivity } from '@/api/admin'
import dayjs from '@/utils/dayjs'
import { ElMessage, ElMessageBox } from 'element-plus'

const activities = ref([])
const loading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const editing = ref(null)
const form = reactive({
  activityName: '', skuId: null, seckillPrice: 0, originalPrice: 0, totalStock: 100, limitPerUser: 1,
  startTime: '', endTime: ''
})

async function fetch() {
  loading.value = true
  try { activities.value = await getAllActivities() }
  catch { activities.value = [] }
  finally { loading.value = false }
}

function showDialog(activity) {
  editing.value = activity
  if (activity) {
    Object.assign(form, {
      activityName: activity.activityName, skuId: activity.skuId,
      seckillPrice: activity.seckillPrice, originalPrice: activity.originalPrice,
      totalStock: activity.totalStock,
      limitPerUser: activity.limitPerUser,
      startTime: activity.startTime, endTime: activity.endTime
    })
  } else {
    Object.assign(form, {
      activityName: '', skuId: null, seckillPrice: 0, originalPrice: 0,
      totalStock: 100, limitPerUser: 1,
      startTime: dayjs().add(1, 'hour').format('YYYY-MM-DD HH:mm:ss'),
      endTime: dayjs().add(3, 'hour').format('YYYY-MM-DD HH:mm:ss')
    })
  }
  dialogVisible.value = true
}

async function save() {
  saving.value = true
  try {
    if (editing.value?.id) {
      await updateActivity(editing.value.id, { ...form })
      ElMessage.success('更新成功')
    } else {
      await createActivity({ ...form })
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    fetch()
  } catch { /* handled */ }
  finally { saving.value = false }
}

async function preWarm(id) {
  try {
    await preWarmStock(id)
    ElMessage.success('库存已预热到Redis')
  } catch { /* handled */ }
}

async function remove(id) {
  try {
    await ElMessageBox.confirm('确定删除该秒杀活动？', '确认', { type: 'warning' })
    await deleteActivity(id)
    ElMessage.success('已删除')
    fetch()
  } catch { /* cancelled */ }
}

onMounted(fetch)
</script>

<style scoped>
.toolbar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.toolbar h2 { font-size: 20px; }
</style>
