// router/index.ts
import { createRouter, createWebHistory } from 'vue-router'
import { ref } from 'vue'
import { useAuthStore } from '@/stores/auth'

// 导入模块路由
import userRoutes from './modules/user.ts'
import sellerRoutes from './modules/seller.ts'
import adminRoutes from './modules/admin.ts'

// 创建路由实例
const router = createRouter({
  // 工作模式
  history: createWebHistory(import.meta.env.BASE_URL),

  // 合并所有路由模块
  routes: [
    // 登录路由
    {
      path:"/login",
      name: "Login",
      component: () => import("@/views/user/Layout/Login.vue"),
    },

    // 公共聊天路由（用户端和商家端共用）
    {
      path: '/chat/:targetId',
      name: 'Chat',
      component: () => import('@/components/Chat.vue'),
      meta: { requiresAuth: true }
    },

    ...userRoutes,    // 用户路由（动态添加）
    ...sellerRoutes,  // 商家路由（动态添加）
    ...adminRoutes,   // 管理员路由（动态添加）
    {
      path: '/',
      name: 'Home',
      component: () => import('@/views/user/Layout/Login.vue'),
      meta: { requiresAuth: false }
    },

  ],

  scrollBehavior(to, from, savedPosition) {
   return { top: 0 }
  }

})

// 用户端页面切换动画方向：slide-left 前进 / slide-right 后退 / fade 同级
export const transitionName = ref('')

// 仅 /user 之间的切换才应用滑动动画
const isUserRoute = (path?: string) => !!path && path.startsWith('/user')

router.beforeEach(async (to, from) => {
  const authStore = useAuthStore()

  // 计算滑动方向（仅用户端 /user 之间切换才动画）
  if (!isUserRoute(to.path) || !isUserRoute(from.path) || from.matched.length === 0) {
    transitionName.value = '' // 初次进入或离开用户端，无动画
  } else {
    const toDepth = (to.meta.depth as number) ?? 1
    const fromDepth = (from.meta.depth as number) ?? 1
    if (toDepth > fromDepth) {
      transitionName.value = 'slide-left'   // 进入更深层级 → 从右往左滑入
    } else if (toDepth < fromDepth) {
      transitionName.value = 'slide-right'  // 返回上层级 → 从左往右滑出
    } else {
      transitionName.value = 'fade'         // 同级切换 → 淡入淡出
    }
  }

  if (to.meta.requiresAuth) {
    if (!authStore.isLoggedIn || !authStore.userId) {
      await authStore.checkAndUpdate()
    }
    if (!authStore.isLoggedIn || !authStore.userId) {
      return { name: 'Login' }
    }
  }

  if ((to.name === 'Home' || to.path === '/') && !authStore.isLoggedIn) {
    await authStore.checkAndUpdate()
    if (authStore.isLoggedIn && authStore.userId) {
      switch (authStore.activeRole) {
        case 'ROLE_USER':
          return { name: 'UserDashboard' }
        case 'ROLE_SELLER':
          return { name: 'SellerDashboard' }
        case 'ROLE_ADMIN':
          return { name: 'AdminDashboard' }
      }
    }
  }
})

router.afterEach(() => {
  document.title = '云杉购'
})

export default router
