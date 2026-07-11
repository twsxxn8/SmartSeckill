<template>
  <div class="admin-layout">
    <el-container>
      <el-aside width="220px" class="admin-sidebar">
        <div class="sidebar-header">
          <h3>🛠 后台管理</h3>
        </div>
        <el-menu :default-active="$route.path" router :unique-opened="true">
          <el-menu-item index="/admin">
            <el-icon><DataAnalysis /></el-icon>
            <span>仪表盘</span>
          </el-menu-item>
          <el-menu-item index="/admin/products">
            <el-icon><Goods /></el-icon>
            <span>商品管理</span>
          </el-menu-item>
          <el-menu-item index="/admin/orders">
            <el-icon><Document /></el-icon>
            <span>订单管理</span>
          </el-menu-item>
          <el-menu-item index="/admin/activities">
            <el-icon><Timer /></el-icon>
            <span>秒杀活动</span>
          </el-menu-item>
          <el-menu-item index="/admin/users">
            <el-icon><User /></el-icon>
            <span>用户管理</span>
          </el-menu-item>
          <el-menu-item index="/admin/coupons">
            <el-icon><Ticket /></el-icon>
            <span>优惠券管理</span>
          </el-menu-item>
        </el-menu>
        <div class="sidebar-footer">
          <el-button text @click="$router.push('/')">← 返回商城</el-button>
        </div>
      </el-aside>
      <el-main class="admin-main">
        <router-view />
      </el-main>
    </el-container>
  </div>
</template>

<script setup>
import { DataAnalysis, Goods, Document, Timer, User, Ticket } from '@element-plus/icons-vue'
</script>

<style scoped>
.admin-layout { min-height: calc(100vh - 60px); }
.admin-sidebar {
  background: #fff;
  border-right: 1px solid #eee;
  display: flex;
  flex-direction: column;
  min-height: calc(100vh - 60px);
}
.sidebar-header {
  padding: 20px 20px 16px;
  border-bottom: 1px solid #eee;
}
.sidebar-header h3 { font-size: 18px; color: #333; }
.sidebar-footer {
  margin-top: auto;
  padding: 16px 20px;
  border-top: 1px solid #eee;
}
.admin-main { padding: 24px; background: #f5f7fa; min-height: calc(100vh - 60px); }
.el-menu { border-right: none; }
</style>
