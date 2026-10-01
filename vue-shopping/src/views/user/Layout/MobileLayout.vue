<template>
  <div v-if="isInitialLoading" class="mobile-layout-skeleton">
    <div class="skeleton-main">
      <div class="skeleton-card"></div>
      <div class="skeleton-card short"></div>
      <div class="skeleton-card"></div>
      <div class="skeleton-card"></div>
    </div>
    <div class="skeleton-tab-bar">
      <div v-for="i in 5" :key="i" class="skeleton-tab-item">
        <div class="skeleton-tab-icon"></div>
        <div class="skeleton-tab-label"></div>
      </div>
    </div>
  </div>

  <div v-else class="mobile-layout">
    <!-- 主内容区 -->
    <main class="main-content">
      <router-view v-slot="{ Component, route }">
        <transition :name="transitionName" mode="out-in" @before-leave="lockScroll" @after-enter="unlockScroll">
          <keep-alive :include="cachedViews">
            <component :is="Component" :key="route.fullPath" />
          </keep-alive>
        </transition>
      </router-view>
    </main>

    <!-- 导航栏 -->
    <div v-show="showTabbar" class="magic-tab-bar">
      <!-- 首页 -->
      <RouterLink :to="{name: 'UserDashboard'}" class="magic-tab-item" active-class="magic-active">
        <div class="magic-icon-wrap">
          <i class="fas fa-compass"></i>
        </div>
        <span class="magic-label">探索</span>
      </RouterLink>

      <!-- 扩展菜单 -->
      <div class="magic-tab-item magic-center-left" @click="handleExtendClick">
        <div class="magic-icon-wrap magic-elevated">
          <i class="fas fa-th-large"></i>
        </div>
        <span class="magic-label">扩展</span>
      </div>

      <!-- 扩展菜单弹窗 -->
      <div v-if="showExtendMenu" class="extend-menu-overlay" @click="showExtendMenu = false">
        <div class="extend-menu" @click.stop>
          <div class="extend-menu-header">
            <span class="extend-menu-title">更多功能</span>
            <button class="extend-menu-close" @click="showExtendMenu = false">
              <i class="fas fa-times"></i>
            </button>
          </div>
          <div class="extend-menu-grid">
            <div class="extend-menu-item" @click="navigateTo('UserCoupons')">
              <div class="extend-menu-icon">
                <i class="fas fa-ticket-alt"></i>
              </div>
              <span class="extend-menu-label">优惠券</span>
            </div>
            <div class="extend-menu-item" @click="navigateTo('UserFavorites')">
              <div class="extend-menu-icon">
                <i class="fas fa-heart"></i>
              </div>
              <span class="extend-menu-label">我的收藏</span>
            </div>
            <div class="extend-menu-item" @click="navigateTo('UserFootprint')">
              <div class="extend-menu-icon">
                <i class="fas fa-history"></i>
              </div>
              <span class="extend-menu-label">浏览足迹</span>
            </div>
            <div class="extend-menu-item" @click="navigateTo('UserDashboard')">
              <div class="extend-menu-icon">
                <i class="fas fa-gift"></i>
              </div>
              <span class="extend-menu-label">活动中心</span>
            </div>
            <div class="extend-menu-item" @click="navigateTo('UserAddress')">
              <div class="extend-menu-icon">
                <i class="fas fa-map-marker-alt"></i>
              </div>
              <span class="extend-menu-label">收货地址</span>
            </div>
            <div class="extend-menu-item" @click="navigateTo('UserSettings')">
              <div class="extend-menu-icon">
                <i class="fas fa-cog"></i>
              </div>
              <span class="extend-menu-label">设置</span>
            </div>
          </div>
        </div>
      </div>

      <!-- 购物车（凸起C位） -->
      <RouterLink :to="{name: 'Cart'}" class="magic-tab-item magic-center" active-class="magic-active">
        <div class="magic-icon-wrap magic-hero">
          <i class="fas fa-shopping-bag"></i>
          <span v-if="cartCount > 0" class="magic-badge">{{ cartCount > 99 ? '99+' : cartCount }}</span>
        </div>
        <span class="magic-label">购物袋</span>
      </RouterLink>

      <!-- 消息 -->
      <RouterLink :to="{name: 'UserMessages'}" class="magic-tab-item" active-class="magic-active">
        <div class="magic-icon-wrap">
          <i class="fas fa-bolt"></i>
        </div>
        <span class="magic-label">消息</span>
        <span v-if="msgCount > 0" class="magic-dot"></span>
      </RouterLink>

      <!-- 我的 -->
      <RouterLink :to="{name: 'UserCenter'}" class="magic-tab-item" active-class="magic-active">
        <div class="magic-icon-wrap">
          <i class="fas fa-user-astronaut"></i>
        </div>
        <span class="magic-label">我的</span>
      </RouterLink>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { RouterLink, RouterView, useRouter, useRoute } from 'vue-router'
import { authAPI } from '@/api/authAPI'
import Message from '@/utils/message'
import { transitionName } from '@/router'

const router = useRouter()
const route = useRoute()
const isInitialLoading = ref(true)
const msgCount = ref<number>(0)
const cartCount = ref(0)
const showExtendMenu = ref(false)

// 底部导航四页缓存（组件 name 与路由 name 一致）
const cachedViews = ['UserDashboard', 'UserMessages', 'UserCenter', 'Cart']

// 仅底部导航页（depth=1）显示 tabbar，独立子页与深层页隐藏
const showTabbar = computed(() => (route.meta.depth as number) === 1)

// 动画期间禁用页面滚动，防止卡顿
const lockScroll = () => { document.body.style.overflow = 'hidden' }
const unlockScroll = () => { document.body.style.overflow = '' }

const handleExtendClick = () => {
  showExtendMenu.value = true
}

const navigateTo = (routeName: string) => {
  showExtendMenu.value = false
  router.push({ name: routeName })
}

const loadMessageCount = async () => {
  try {
    // const response = await authAPI.getUnreadMessageCount()
    // if (response.success) {
    //   msgCount.value = response.data || 0
    // }
  } catch (error) {
    Message.error('加载消息数量失败')
    msgCount.value = 0
  }
}

// 获取购物车数量
const loadCartCount = async () => {
  try {
    const res = await authAPI.getCartCount()
    if (res.success) cartCount.value = res.data || 0
  } catch {}
}

onMounted(() => {
  Promise.all([loadMessageCount(), loadCartCount()]).finally(() => {
    setTimeout(() => {
      isInitialLoading.value = false
    }, 300)
  })
})
</script>

<style scoped>
@import url('@/static/css/user/移动端布局');
</style>

<style>
/* ============================================
   用户端页面切换动画（全局，非 scoped）
   三级 depth 体系：slide-left 前进 / slide-right 后退 / fade 同级
   mode="out-in"：旧页离场后新页入场，避免同时渲染抖动。
   GPU 加速：仅用 transform/opacity，配 will-change。
   ============================================ */

/* —— 前进：从右往左滑入（进入更深层级） —— */
.slide-left-enter-active,
.slide-left-leave-active {
  transition: transform 0.3s cubic-bezier(0.4, 0, 0.2, 1),
              opacity 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  will-change: transform;
}

.slide-left-enter-from {
  transform: translateX(100%);
}

.slide-left-leave-to {
  transform: translateX(-30%);
  opacity: 0.6;
}

/* —— 后退：从左往右滑出（返回上层级） —— */
.slide-right-enter-active,
.slide-right-leave-active {
  transition: transform 0.3s cubic-bezier(0.4, 0, 0.2, 1),
              opacity 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  will-change: transform;
}

.slide-right-enter-from {
  transform: translateX(-30%);
  opacity: 0.6;
}

.slide-right-leave-to {
  transform: translateX(100%);
}

/* —— 同级切换：淡入淡出 —— */
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>
