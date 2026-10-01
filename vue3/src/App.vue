<template>
  <router-view />
  <!-- 全局图片放大预览（头像/图片点击后弹出，单例遮罩） -->
  <ImagePreview />
</template>

<script setup lang="ts">
import { onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { disconnectGlobalSocket } from '@/composables/useGlobalSocket'
import ImagePreview from '@/components/ImagePreview.vue'

const router = useRouter()

// 全局启动：首屏渲染完成后再静默恢复登录态（不 await、不阻塞渲染）。
// 路由守卫已依据本地 token 放行进入页面，这里只负责向服务端做一次后台校验，
// 校验失败（如 token 已被吊销）再登出并跳回登录页；成功后 auth store 会延后连接 WebSocket。
// 注意：必须等 router.isReady()（首挂时 useRoute() 初始值是 START_LOCATION，
// name 为 undefined、fullPath 为 '/'，提前判断会把登录页错误重定向成 redirect=/）。
onMounted(() => {
  const authStore = useAuthStore()
  router.isReady().then(() => {
    if (router.currentRoute.value.name === 'Login') return
    authStore.checkLoginStatus().then((ok) => {
      if (!ok && router.currentRoute.value.name !== 'Login') {
        router.replace({
          name: 'Login',
          query: { redirect: router.currentRoute.value.fullPath }
        })
      }
    })
  })
})

// 页面关闭/刷新时：断开 WebSocket
// 后端监听到连接断开后会删除 Redis 在线标记并广播下线（TCP 关闭即触发，无需前端额外上报）
const handleBeforeUnload = () => {
  const authStore = useAuthStore()
  if (authStore.isLoggedIn) {
    disconnectGlobalSocket()
  }
}
window.addEventListener('beforeunload', handleBeforeUnload)

onBeforeUnmount(() => {
  window.removeEventListener('beforeunload', handleBeforeUnload)
})
</script>
