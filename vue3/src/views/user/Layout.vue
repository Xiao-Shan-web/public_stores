<template>
  <div class="user-layout">
    <main ref="stageRef" class="page-content" :class="{ 'with-tabbar': showTabbar }">
      <router-view v-slot="{ Component, route }">
        <transition :name="transitionName">
          <keep-alive :include="cachedViews">
            <component :is="Component" :key="route.fullPath" />
          </keep-alive>
        </transition>
      </router-view>
    </main>

    <nav v-show="showTabbar" class="tabbar">
      <router-link
        v-for="tab in tabs"
        :key="tab.path"
        :to="tab.path"
        class="tab-item"
        active-class="active"
      >
        <span class="tab-icon-wrap">
          <i :class="['fas', tab.icon]"></i>
          <!-- 消息 Tab 显示未读数 Badge（订阅全局未读 store：系统通知 + 客服消息） -->
          <span v-if="tab.badge && unreadStore.total > 0" class="badge">{{ badgeText }}</span>
        </span>
        <span class="tab-label">{{ tab.label }}</span>
      </router-link>
    </nav>

    <!-- 全局确认弹窗（替代 window.confirm） -->
    <ConfirmDialog />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted, onBeforeUnmount } from 'vue'
import { useRoute } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useUnreadStore } from '@/stores/unread'
import { transitionName } from '@/router'
import { subscribeNotification, subscribeService } from '@/composables/useGlobalSocket'
import { playMessageSound } from '@/utils/sound'
import ConfirmDialog from '@/components/ConfirmDialog.vue'

const authStore = useAuthStore()
const unreadStore = useUnreadStore()
const route = useRoute()

const stageRef = ref<HTMLElement | null>(null)

const tabs = [
  { path: '/user/home', label: '首页', icon: 'fa-home', badge: false },
  { path: '/user/ai-plan', label: 'AI饮食计划', icon: 'fa-robot', badge: false },
  { path: '/user/messages', label: '消息', icon: 'fa-bell', badge: true },
  { path: '/user/profile', label: '我的', icon: 'fa-user', badge: false }
]

// 底部导航四页缓存
const cachedViews = ['UserHome', 'UserAiPlan', 'UserMessages', 'UserProfile']

// 仅底部导航页（depth=0）显示 tabbar，独立子页隐藏
const showTabbar = computed(() => (route.meta.depth as number) === 0)

// 未读数超过 99 显示 99+（总数来自全局 store：系统通知未读 + 客服消息未读）
const badgeText = computed(() => unreadStore.total > 99 ? '99+' : unreadStore.total)

/** WebSocket 提示音订阅取消函数（未读数由全局 store 统一管理，这里只负责声音） */
let unsubNotification: (() => void) | null = null
let unsubService: (() => void) | null = null

// 切换页面时重置滚动位置，避免滑动动画错位
watch(
  () => route.fullPath,
  () => {
    if (stageRef.value) stageRef.value.scrollTop = 0
  }
)

onMounted(() => {
  // 启动全局未读数：初始拉取 + WebSocket 事件驱动更新（无定时轮询）
  unreadStore.start()

  // WebSocket 连接由 auth store 管理（登录时建立、退出时断开）
  // 未读数由 store 统一更新，这里只负责提示音
  unsubNotification = subscribeNotification(() => {
    playMessageSound()
  })
  // 仅收到客服回复才提示音；自己发出的消息（senderId 为本人）不响铃
  unsubService = subscribeService((data) => {
    if (data.senderType === 'ADMIN' && data.senderId !== authStore.userInfo?.id) {
      playMessageSound()
    }
  })
})

onBeforeUnmount(() => {
  if (unsubNotification) unsubNotification()
  if (unsubService) unsubService()
  unreadStore.stop()
  // 全局 WebSocket 连接由 auth store 管理（退出登录时断开），这里不断开
})
</script>

<style scoped>
.user-layout {
  display: flex;
  flex-direction: column;
  height: 100vh;
  overflow: hidden;
  background: var(--color-bg, #f5f5f5);
}

.page-content {
  position: relative;
  flex: 1;
  box-sizing: border-box;
  overflow-x: hidden;
  overflow-y: auto;
  -webkit-overflow-scrolling: touch;
}

/* 底部导航页底部留出 tabbar 高度（含安全区） */
.page-content.with-tabbar {
  padding-bottom: calc(var(--tabbar-height) + env(safe-area-inset-bottom));
}

.tabbar {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  height: calc(var(--tabbar-height) + env(safe-area-inset-bottom));
  padding-bottom: env(safe-area-inset-bottom);
  box-sizing: border-box;
  display: flex;
  background: #fff;
  border-top: 1px solid var(--color-border);
  z-index: 100;
  box-shadow: 0 -2px 12px rgba(0, 0, 0, 0.04);
}

.tab-item {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: var(--tabbar-height);
  color: var(--color-text-placeholder);
  text-decoration: none;
  position: relative;
  /* 移动端触摸优化：去除默认高亮、防止双击缩放、禁用选中文本 */
  -webkit-tap-highlight-color: transparent;
  touch-action: manipulation;
  user-select: none;
  transition: background-color 0.2s ease, color 0.25s ease;
}

/* 选中态顶部指示器（从中心展开，平滑出现） */
.tab-item::before {
  content: '';
  position: absolute;
  top: 0;
  left: 50%;
  width: 22px;
  height: 3px;
  border-radius: 0 0 3px 3px;
  background: var(--gradient-primary);
  transform: translateX(-50%) scaleX(0);
  transform-origin: center;
  transition: transform 0.25s ease;
}

.tab-icon-wrap {
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  transition: transform 0.25s ease;
}

.tab-item i {
  font-size: 20px;
  margin-bottom: 3px;
  transition: transform 0.25s ease, color 0.25s ease;
}

.tab-label {
  font-size: 11px;
  transition: color 0.25s ease, transform 0.25s ease;
}

/* 选中态：高亮 + 图标放大 + 顶部指示器展开 */
.tab-item.active {
  color: var(--color-primary);
}

.tab-item.active .tab-icon-wrap {
  transform: scale(1.12);
}

.tab-item.active::before {
  transform: translateX(-50%) scaleX(1);
}

/* 按压反馈：半透明背景 + 图标缩小（在选中态之后定义，确保按压时覆盖选中态放大） */
.tab-item:active {
  background-color: rgba(255, 107, 53, 0.08);
}

.tab-item:active .tab-icon-wrap {
  transform: scale(0.88);
}

/* 未读数 Badge */
.badge {
  position: absolute;
  top: -6px;
  right: -10px;
  min-width: 16px;
  height: 16px;
  padding: 0 4px;
  background: var(--color-danger, #ff4d4f);
  color: #fff;
  border-radius: 8px;
  font-size: 10px;
  line-height: 16px;
  text-align: center;
  font-weight: 600;
}
</style>

<style>
/* ============================================
   用户端页面滑动动画（全局，非 scoped）
   transition 类名会作用到每个页面根元素
   ============================================ */

/* 前进：新页面从右滑入，旧页面左移并变暗 */
.slide-left-enter-active,
.slide-left-leave-active,
.slide-right-enter-active,
.slide-right-leave-active {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  overflow: hidden;
  transition: transform 0.3s ease, opacity 0.3s ease;
  will-change: transform;
}

.slide-left-enter-from {
  transform: translateX(100%);
}

.slide-left-leave-to {
  transform: translateX(-30%);
  opacity: 0.8;
}

/* 后退：旧页面从左滑入，新页面右移 */
.slide-right-enter-from {
  transform: translateX(-30%);
  opacity: 0.8;
}

.slide-right-leave-to {
  transform: translateX(100%);
}
</style>
