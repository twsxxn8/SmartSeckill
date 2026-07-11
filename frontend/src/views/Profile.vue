<template>
  <div class="profile-page">
    <el-card shadow="always" class="profile-card">
      <h2>个人中心</h2>
      <el-tabs v-model="activeTab">
        <!-- ========== Tab 1: 个人信息 ========== -->
        <el-tab-pane label="个人信息" name="info">
          <el-form :model="profileForm" label-position="top" ref="profileFormRef" :rules="profileRules">
            <el-form-item label="用户名">
              <el-input :model-value="profile.username" disabled />
              <span class="field-hint">用户名不可修改</span>
            </el-form-item>
            <el-row :gutter="16">
              <el-col :span="12">
                <el-form-item label="昵称" prop="nickname">
                  <el-input v-model="profileForm.nickname" placeholder="请输入昵称" />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="手机号" prop="phone">
                  <el-input v-model="profileForm.phone" placeholder="请输入手机号" />
                </el-form-item>
              </el-col>
            </el-row>
            <el-form-item label="邮箱" prop="email">
              <el-input v-model="profileForm.email" placeholder="请输入邮箱" />
            </el-form-item>
            <el-form-item label="头像URL" prop="avatar">
              <el-input v-model="profileForm.avatar" placeholder="请输入头像链接地址" />
            </el-form-item>
            <el-form-item>
              <el-button type="danger" :loading="savingProfile" @click="saveProfile">保存修改</el-button>
              <el-button @click="resetProfile">重置</el-button>
            </el-form-item>
          </el-form>
        </el-tab-pane>

        <!-- ========== Tab 2: 地址管理 ========== -->
        <el-tab-pane label="地址管理" name="address">
          <div class="addr-header">
            <el-button type="primary" size="small" @click="showAddDialog">+ 新增地址</el-button>
          </div>
          <div v-loading="loadingAddresses">
            <div v-if="addresses.length === 0" style="text-align:center;padding:40px;color:#999">
              暂无收货地址
            </div>
            <div v-for="addr in addresses" :key="addr.id" class="addr-card">
              <div class="addr-info">
                <div class="addr-contact">
                  <b>{{ addr.receiverName }}</b>
                  <span style="margin-left:12px;color:#666">{{ addr.receiverPhone }}</span>
                  <el-tag v-if="addr.isDefault === 1" size="small" type="danger" style="margin-left:8px">默认</el-tag>
                </div>
                <div class="addr-detail">
                  {{ addr.province }} {{ addr.city }} {{ addr.district }} {{ addr.detailAddress }}
                </div>
              </div>
              <div class="addr-actions">
                <el-button size="small" text type="primary" @click="showEditDialog(addr)">编辑</el-button>
                <el-button size="small" text type="danger" @click="deleteAddr(addr.id)">删除</el-button>
              </div>
            </div>
          </div>
        </el-tab-pane>

        <!-- ========== Tab 3: 我的优惠券 ========== -->
        <el-tab-pane label="我的优惠券" name="coupon">
          <div v-loading="loadingCoupons">
            <div v-if="myCoupons.length === 0" style="text-align:center;padding:40px;color:#999">
              暂无可用优惠券
              <br><el-button size="small" type="danger" style="margin-top:12px" @click="$router.push('/coupons')">去领券中心</el-button>
            </div>
            <div v-for="c in myCoupons" :key="c.id" class="coupon-card">
              <div class="cpn-left">
                <template v-if="c.couponType === 1">
                  <span class="cpn-sym">¥</span><span class="cpn-num">{{ c.amount }}</span>
                </template>
                <template v-else>
                  <span class="cpn-num">{{ (c.discount * 10).toFixed(1) }}</span><span class="cpn-sym">折</span>
                </template>
                <div class="cpn-cond">满{{ c.minAmount || 0 }}元可用</div>
              </div>
              <div class="cpn-right">
                <div>
                  <div class="cpn-name">{{ c.couponName }}</div>
                  <div class="cpn-meta">{{ c.startTime?.slice(0, 10) }} ~ {{ c.endTime?.slice(0, 10) }}</div>
                </div>
                <el-tag size="small" type="success">可使用</el-tag>
              </div>
            </div>
          </div>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <!-- Address Add/Edit Dialog -->
    <el-dialog :title="editingAddr ? '编辑地址' : '新增地址'" v-model="addrDialogVisible" width="500px" @close="resetAddrForm">
      <el-form :model="addrForm" label-position="top" ref="addrFormRef">
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="收货人" required>
              <el-input v-model="addrForm.receiverName" placeholder="请输入收货人姓名" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="手机号" required>
              <el-input v-model="addrForm.receiverPhone" placeholder="请输入手机号" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="8">
            <el-form-item label="省">
              <el-input v-model="addrForm.province" placeholder="省份" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="市">
              <el-input v-model="addrForm.city" placeholder="城市" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="区">
              <el-input v-model="addrForm.district" placeholder="区县" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="详细地址" required>
          <el-input v-model="addrForm.detailAddress" placeholder="请输入详细地址" />
        </el-form-item>
        <el-form-item>
          <el-checkbox v-model="addrForm.isDefault">设为默认地址</el-checkbox>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="addrDialogVisible = false">取消</el-button>
        <el-button type="danger" :loading="savingAddr" @click="saveAddr">
          {{ editingAddr ? '保存修改' : '添加地址' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { getProfile, updateProfile } from '@/api/user'
import { getMyAddresses, addAddress, updateAddress, deleteAddress } from '@/api/address'
import { getMyCoupons } from '@/api/coupon'
import { ElMessage, ElMessageBox } from 'element-plus'

const activeTab = ref('info')

// ==================== Profile Tab ====================
const profile = reactive({ username: '', nickname: '', phone: '', email: '', avatar: '' })
const profileForm = reactive({ nickname: '', phone: '', email: '', avatar: '' })
const profileFormRef = ref(null)
const savingProfile = ref(false)
const profileRules = {
  phone: [{ pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确', trigger: 'blur' }],
  email: [{ type: 'email', message: '邮箱格式不正确', trigger: 'blur' }]
}

async function fetchProfile() {
  try {
    const data = await getProfile()
    Object.assign(profile, {
      username: data.username || '',
      nickname: data.nickname || '',
      phone: data.phone || '',
      email: data.email || '',
      avatar: data.avatar || ''
    })
    resetProfile()
  } catch { /* interceptor handled */ }
}

function resetProfile() {
  profileForm.nickname = profile.nickname
  profileForm.phone = profile.phone
  profileForm.email = profile.email
  profileForm.avatar = profile.avatar
}

async function saveProfile() {
  const valid = await profileFormRef.value?.validate().catch(() => false)
  if (!valid) return
  savingProfile.value = true
  try {
    const data = await updateProfile({
      nickname: profileForm.nickname || undefined,
      phone: profileForm.phone || undefined,
      email: profileForm.email || undefined,
      avatar: profileForm.avatar || undefined
    })
    // Update local state with server response
    Object.assign(profile, {
      username: data.username || profile.username,
      nickname: data.nickname || '',
      phone: data.phone || '',
      email: data.email || '',
      avatar: data.avatar || ''
    })
    resetProfile()
    ElMessage.success('个人信息已更新')
  } catch { /* interceptor handled */ }
  finally { savingProfile.value = false }
}

// ==================== Address Tab ====================
const addresses = ref([])
const loadingAddresses = ref(false)
const addrDialogVisible = ref(false)
const editingAddr = ref(null)
const savingAddr = ref(false)
const addrFormRef = ref(null)
const addrForm = reactive({
  receiverName: '', receiverPhone: '', province: '', city: '', district: '', detailAddress: '',
  isDefault: false
})

async function fetchAddresses() {
  loadingAddresses.value = true
  try { addresses.value = await getMyAddresses() }
  catch { addresses.value = [] }
  finally { loadingAddresses.value = false }
}

function showAddDialog() {
  editingAddr.value = null
  resetAddrForm()
  addrDialogVisible.value = true
}

function showEditDialog(addr) {
  editingAddr.value = addr
  addrForm.receiverName = addr.receiverName || ''
  addrForm.receiverPhone = addr.receiverPhone || ''
  addrForm.province = addr.province || ''
  addrForm.city = addr.city || ''
  addrForm.district = addr.district || ''
  addrForm.detailAddress = addr.detailAddress || ''
  addrForm.isDefault = addr.isDefault === 1
  addrDialogVisible.value = true
}

function resetAddrForm() {
  addrForm.receiverName = ''
  addrForm.receiverPhone = ''
  addrForm.province = ''
  addrForm.city = ''
  addrForm.district = ''
  addrForm.detailAddress = ''
  addrForm.isDefault = false
}

async function saveAddr() {
  if (!addrForm.receiverName || !addrForm.receiverPhone || !addrForm.detailAddress) {
    ElMessage.warning('请填写收货人、手机号和详细地址')
    return
  }
  savingAddr.value = true
  try {
    const payload = {
      receiverName: addrForm.receiverName,
      receiverPhone: addrForm.receiverPhone,
      province: addrForm.province,
      city: addrForm.city,
      district: addrForm.district,
      detailAddress: addrForm.detailAddress,
      isDefault: addrForm.isDefault ? 1 : 0
    }
    if (editingAddr.value) {
      await updateAddress(editingAddr.value.id, payload)
      ElMessage.success('地址已更新')
    } else {
      await addAddress(payload)
      ElMessage.success('地址已添加')
    }
    addrDialogVisible.value = false
    await fetchAddresses()
  } catch { /* interceptor handled */ }
  finally { savingAddr.value = false }
}

async function deleteAddr(id) {
  try {
    await ElMessageBox.confirm('确定要删除该地址吗？', '提示', { type: 'warning' })
    await deleteAddress(id)
    ElMessage.success('地址已删除')
    await fetchAddresses()
  } catch { /* cancelled or error */ }
}

// ==================== Coupon ====================
const myCoupons = ref([])
const loadingCoupons = ref(false)

async function fetchMyCoupons() {
  loadingCoupons.value = true
  try { myCoupons.value = await getMyCoupons() }
  catch { myCoupons.value = [] }
  finally { loadingCoupons.value = false }
}

// ==================== Lifecycle ====================
onMounted(() => {
  fetchProfile()
  fetchAddresses()
  fetchMyCoupons()
})
</script>

<style scoped>
.profile-page {
  max-width: 800px;
  margin: 30px auto;
  padding: 0 20px;
}
.profile-card h2 {
  margin-bottom: 20px;
}
.field-hint {
  font-size: 12px;
  color: #999;
  margin-top: 4px;
  display: block;
}

/* Address cards */
.addr-header {
  margin-bottom: 12px;
}
.addr-card {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 14px 16px;
  background: #f9fafb;
  border-radius: 8px;
  margin-bottom: 10px;
}
.addr-info {
  flex: 1;
  min-width: 0;
}
.addr-contact {
  margin-bottom: 4px;
}
.addr-detail {
  font-size: 13px;
  color: #888;
}
.addr-actions {
  flex-shrink: 0;
  margin-left: 12px;
}

/* Coupon cards in profile */
.coupon-card {
  display: flex;
  border-radius: 10px;
  overflow: hidden;
  background: #fff;
  box-shadow: 0 2px 8px rgba(0,0,0,.05);
  margin-bottom: 10px;
}
.cpn-left {
  width: 120px;
  background: linear-gradient(135deg, #e74c3c, #c0392b);
  color: #fff;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 16px 0;
  flex-shrink: 0;
}
.cpn-sym { font-size: 14px; }
.cpn-num { font-size: 28px; font-weight: 800; }
.cpn-cond { font-size: 11px; margin-top: 4px; opacity: .85; }
.cpn-right {
  flex: 1;
  padding: 16px 20px;
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.cpn-name { font-size: 14px; font-weight: 600; }
.cpn-meta { font-size: 12px; color: #999; margin-top: 4px; }
</style>
