import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  { path: '/', name: 'Home', component: () => import('@/views/Home.vue') },
  { path: '/login', name: 'Login', component: () => import('@/views/Login.vue') },
  { path: '/register', name: 'Register', component: () => import('@/views/Register.vue') },
  { path: '/product/:id', name: 'ProductDetail', component: () => import('@/views/ProductDetail.vue') },
  { path: '/cart', name: 'Cart', component: () => import('@/views/Cart.vue'), meta: { requiresAuth: true } },
  { path: '/orders', name: 'Orders', component: () => import('@/views/Orders.vue'), meta: { requiresAuth: true } },
  { path: '/profile', name: 'Profile', component: () => import('@/views/Profile.vue'), meta: { requiresAuth: true } },
  { path: '/coupons', name: 'Coupons', component: () => import('@/views/Coupons.vue') },
  { path: '/my-coupons', name: 'MyCoupons', component: () => import('@/views/MyCoupons.vue'), meta: { requiresAuth: true } },
  { path: '/seckill', name: 'Seckill', component: () => import('@/views/Seckill.vue') },
  { path: '/seckill/:id', name: 'SeckillDetail', component: () => import('@/views/SeckillDetail.vue'), meta: { requiresAuth: true } },
  // Admin nested routes
  {
    path: '/admin',
    component: () => import('@/views/admin/AdminLayout.vue'),
    meta: { requiresAuth: true, requiresAdmin: true },
    children: [
      { path: '', name: 'AdminDashboard', component: () => import('@/views/admin/Dashboard.vue') },
      { path: 'products', name: 'AdminProducts', component: () => import('@/views/admin/ProductManage.vue') },
      { path: 'orders', name: 'AdminOrders', component: () => import('@/views/admin/OrderManage.vue') },
      { path: 'activities', name: 'AdminActivities', component: () => import('@/views/admin/ActivityManage.vue') },
      { path: 'users', name: 'AdminUsers', component: () => import('@/views/admin/UserManage.vue') },
      { path: 'coupons', name: 'AdminCoupons', component: () => import('@/views/admin/CouponManage.vue') }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// Navigation guard
router.beforeEach((to, from, next) => {
  const token = sessionStorage.getItem('accessToken')
  const userInfo = JSON.parse(sessionStorage.getItem('userInfo') || 'null')

  if (to.meta.requiresAuth && !token) {
    next({ name: 'Login', query: { redirect: to.fullPath } })
    return
  }

  if (to.meta.requiresAdmin && userInfo?.role !== 'ROLE_ADMIN') {
    next({ name: 'Home' })
    return
  }

  next()
})

export default router
