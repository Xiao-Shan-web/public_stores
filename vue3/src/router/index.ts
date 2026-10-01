import { createRouter, createWebHistory } from 'vue-router'
import { ref } from 'vue'
import userRoutes from './user'
import { useAuthStore } from '@/stores/auth'
import adminRoutes from './admin'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', name: 'Login', component: () => import('@/views/Login.vue') },
    ...userRoutes,
    ...adminRoutes,
    { path: '/', redirect: '/user/home' },
    { path: '/:pathMatch(.*)*', redirect: '/user/home' }
  ]
})

// 用户端页面切换动画方向：slide-left 前进 / slide-right 后退
export const transitionName = ref('')

const isUserRoute = (path?: string) => !!path && path.startsWith('/user')

// 路由守卫
// 关键：守卫中绝不 await 网络请求，避免「白屏等 user-info 接口」造成的首屏卡顿。
// 本地存在未过期 token 即同步放行，页面框架先渲染；服务端校验由 App 挂载后静默进行，
// 校验失败再登出并跳回登录页（401 由请求拦截器统一处理）。
router.beforeEach((to, from) => {
  const authStore = useAuthStore()

  // 计算滑动方向（仅用户端 /user 之间切换才动画）
  if (!isUserRoute(to.path) || !isUserRoute(from.path) || from.matched.length === 0) {
    transitionName.value = '' // 初次进入或离开用户端，无动画
  } else {
    const toDepth = (to.meta.depth as number) ?? 0
    const fromDepth = (from.meta.depth as number) ?? 0
    transitionName.value = toDepth >= fromDepth ? 'slide-left' : 'slide-right'
  }

  // 登录页直接放行
  if (to.name === 'Login') {
    return true
  }

  // 管理员路由 + 用户端 requiresAuth 页面：仅以本地 token 为准，瞬时决策
  const requiresAuth = to.path.startsWith('/admin') || to.meta.requiresAuth === true
  if (requiresAuth && (!authStore.token || authStore.isTokenExpired)) {
    return { name: 'Login', query: { redirect: to.fullPath } }
  }

  return true
})

export default router
