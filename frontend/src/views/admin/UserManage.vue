<template>
  <div class="user-manage">
    <div class="toolbar"><h2>用户管理</h2></div>

    <div class="search-bar">
      <el-input v-model="keyword" placeholder="搜索用户名..." clearable style="width:240px" @keyup.enter="fetch" />
      <el-select v-model="filterStatus" placeholder="状态筛选" clearable style="width:130px" @change="fetch">
        <el-option label="正常" :value="1" />
        <el-option label="禁用" :value="0" />
      </el-select>
      <el-button type="primary" @click="fetch">搜索</el-button>
    </div>

    <el-table :data="users" v-loading="loading" border stripe>
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column prop="username" label="用户名" width="130" />
      <el-table-column prop="nickname" label="昵称" width="120" />
      <el-table-column prop="phone" label="手机号" width="130" />
      <el-table-column prop="email" label="邮箱" min-width="150" />
      <el-table-column label="状态" width="80">
        <template #default="{ row }">
          <el-switch :model-value="row.status === 1" @change="(val) => toggle(row.id, val ? 1 : 0)"
            active-color="#67C23A" inactive-color="#F56C6C" />
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="注册时间" width="170" />
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <el-button size="small" @click="showRoleDialog(row)">角色</el-button>
          <el-button size="small" type="danger" @click="showResetPwd(row)">重置密码</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pagination" v-if="total > size">
      <el-pagination v-model:current-page="page" :page-size="size" :total="total" layout="prev, pager, next" @current-change="fetch" />
    </div>

    <!-- Role Dialog -->
    <el-dialog title="分配角色" v-model="roleDialog" width="400px">
      <p style="margin-bottom:12px">用户: <b>{{ roleUser?.username }}</b></p>
      <el-radio-group v-model="selectedRole">
        <el-radio :label="1">普通用户 (ROLE_USER)</el-radio>
        <el-radio :label="2">管理员 (ROLE_ADMIN)</el-radio>
      </el-radio-group>
      <template #footer>
        <el-button @click="roleDialog = false">取消</el-button>
        <el-button type="primary" @click="saveRole">保存</el-button>
      </template>
    </el-dialog>

    <!-- Reset Password Dialog -->
    <el-dialog title="重置密码" v-model="pwdDialog" width="400px">
      <p style="margin-bottom:12px">用户: <b>{{ pwdUser?.username }}</b></p>
      <el-input v-model="newPassword" type="password" placeholder="输入新密码（最少6位）" show-password />
      <template #footer>
        <el-button @click="pwdDialog = false">取消</el-button>
        <el-button type="primary" @click="doResetPwd" :disabled="newPassword.length < 6">确认重置</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { getUserList, toggleUserStatus, resetUserPassword, assignUserRole } from '@/api/admin'
import { ElMessage } from 'element-plus'

const users = ref([])
const loading = ref(false)
const keyword = ref('')
const filterStatus = ref(null)
const page = ref(1)
const size = ref(10)
const total = ref(0)

const roleDialog = ref(false)
const roleUser = ref(null)
const selectedRole = ref(1)

const pwdDialog = ref(false)
const pwdUser = ref(null)
const newPassword = ref('')

async function fetch() {
  loading.value = true
  try {
    const res = await getUserList({
      page: page.value, size: size.value,
      keyword: keyword.value || undefined,
      status: filterStatus.value
    })
    users.value = res.records
    total.value = res.total
  } catch { users.value = [] }
  finally { loading.value = false }
}

async function toggle(userId, status) {
  try {
    await toggleUserStatus(userId, status)
    ElMessage.success(status === 1 ? '已启用' : '已禁用')
    fetch()
  } catch { /* handled */ }
}

function showRoleDialog(user) {
  roleUser.value = user
  selectedRole.value = 1 // default
  roleDialog.value = true
}

async function saveRole() {
  try {
    await assignUserRole(roleUser.value.id, selectedRole.value)
    ElMessage.success('角色已更新')
    roleDialog.value = false
  } catch { /* handled */ }
}

function showResetPwd(user) {
  pwdUser.value = user
  newPassword.value = ''
  pwdDialog.value = true
}

async function doResetPwd() {
  try {
    await resetUserPassword(pwdUser.value.id, newPassword.value)
    ElMessage.success('密码已重置')
    pwdDialog.value = false
  } catch { /* handled */ }
}

onMounted(fetch)
</script>

<style scoped>
.toolbar { margin-bottom: 16px; }
.toolbar h2 { font-size: 20px; }
.search-bar { display: flex; gap: 12px; margin-bottom: 16px; }
.pagination { display: flex; justify-content: center; padding: 20px; }
</style>
