<template>
  <div class="admin-layout">
    <!-- 左侧边栏 -->
    <aside class="sidebar">
      <div class="logo">
        <i class="fas fa-fire logo-icon"></i>
        <span class="logo-text">山达后台</span>
      </div>

      <nav class="nav">
        <router-link
          v-for="item in menus"
          :key="item.path"
          :to="item.path"
          class="nav-item"
          active-class="active"
        >
          <i :class="['fas', item.icon]"></i>
          <span>{{ item.label }}</span>
          <span v-if="item.path === '/admin/service' && adminUnreadStore.count > 0" class="nav-badge">{{ adminUnreadStore.count > 99 ? '99+' : adminUnreadStore.count }}</span>
        </router-link>
      </nav>
    </aside>

    <!-- 右侧主区域 -->
    <div class="main-wrap">
      <!-- 顶部栏 -->
      <header class="topbar">
        <!-- 面包屑 -->
        <div class="breadcrumb">
          <span class="bc-home">首页</span>
          <i class="fas fa-chevron-right bc-sep"></i>
          <span class="bc-current">{{ currentTitle }}</span>
        </div>

        <!-- 右侧操作区：仅通知铃铛 + 固定"管理员"文字（无退出按钮） -->
        <div class="topbar-right">
          <button class="icon-btn bell" @click="goNotifications">
            <i class="fas fa-bell"></i>
            <span class="badge" v-if="unreadCount > 0">{{ unreadCount }}</span>
          </button>
          <span class="admin-name">管理员</span>
        </div>
      </header>

      <!-- 内容区域 -->
      <main class="content">
        <router-view />
      </main>
    </div>

    <!-- 全局确认弹窗（替代 window.confirm） -->
    <ConfirmDialog />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { subscribeService } from '@/composables/useGlobalSocket'
import { playMessageSound } from '@/utils/sound'
import { useAuthStore } from '@/stores/auth'
import { useAdminUnreadStore } from '@/stores/adminUnread'
import ConfirmDialog from '@/components/ConfirmDialog.vue'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const adminUnreadStore = useAdminUnreadStore()

// 侧边栏菜单（13 项）
const menus = [
  { path: '/admin/dashboard', label: '控制台', icon: 'fa-chart-line' },
  { path: '/admin/stats', label: '数据统计', icon: 'fa-chart-bar' },
  { path: '/admin/members', label: '会员管理', icon: 'fa-users' },
  { path: '/admin/memberships', label: '会员卡管理', icon: 'fa-id-card' },
  { path: '/admin/entry', label: '到店核销', icon: 'fa-fingerprint' },
  { path: '/admin/records', label: '核销记录', icon: 'fa-ticket-alt' },
  { path: '/admin/cards', label: '卡类型管理', icon: 'fa-clone' },
  { path: '/admin/coupons', label: '优惠券管理', icon: 'fa-tags' },
  { path: '/admin/activities', label: '限时活动', icon: 'fa-bullhorn' },
  { path: '/admin/stores', label: '门店管理', icon: 'fa-store' },
  { path: '/admin/service', label: '客服消息', icon: 'fa-headset' },
  { path: '/admin/notifications', label: '系统通知', icon: 'fa-bell' },
  { path: '/admin/shares', label: '分享管理', icon: 'fa-images' },
  { path: '/admin/complaints', label: '投诉仲裁', icon: 'fa-gavel' },
  { path: '/admin/risk', label: '风控中心', icon: 'fa-shield-halved' },
  { path: '/admin/audit-logs', label: '操作审计', icon: 'fa-clipboard-list' },
  { path: '/admin/settings', label: '系统设置', icon: 'fa-cog' }
]

// 面包屑当前页标题
const currentTitle = computed(() => (route.meta.title as string) || '控制台')

// 未读通知数（占位，待后端接入）
const unreadCount = ref(0)

const goNotifications = () => {
  router.push('/admin/notifications')
}

let unsubService: (() => void) | null = null

onMounted(() => {
  // 启动全局客服未读数：初始拉取 + WebSocket 事件驱动更新（无定时轮询）
  adminUnreadStore.start()
  // 全局 WebSocket 连接由 auth store 管理（登录时建立、退出时断开）
  // 未读数由 store 统一更新，这里只负责提示音
  unsubService = subscribeService((data) => {
    if (data.senderType === 'USER' && data.senderId !== authStore.userInfo?.id) {
      playMessageSound()
    }
  })
})

onBeforeUnmount(() => {
  if (unsubService) unsubService()
  adminUnreadStore.stop()
})
</script>

<style scoped>
.admin-layout {
  display: flex;
  height: 100vh;
  overflow: hidden;
}

/* ========== 左侧边栏（固定宽度，不随内容/缩放收缩） ========== */
.sidebar {
  width: 220px;
  flex-shrink: 0;
  flex-grow: 0;
  background-color: var(--color-dark);
  color: #fff;
  display: flex;
  flex-direction: column;
}

.logo {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  height: 52px;
  padding: 0 20px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
  flex-shrink: 0;
  white-space: nowrap;
}

.logo-icon {
  font-size: 22px;
  color: var(--color-primary);
  flex-shrink: 0;
}

.logo-text {
  font-size: 17px;
  font-weight: 700;
  letter-spacing: 1px;
  white-space: nowrap;
}

.nav {
  flex: 1;
  padding: 12px 0;
  overflow-y: auto;
  overflow-x: hidden;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 13px 22px;
  color: rgba(255, 255, 255, 0.65);
  text-decoration: none;
  font-size: 14px;
  transition: all 0.2s;
  border-left: 2px solid transparent;
  white-space: nowrap;
}

.nav-item i {
  font-size: 16px;
  width: 20px;
  text-align: center;
  flex-shrink: 0;
}

/* 侧边栏未读角标 */
.nav-badge {
  margin-left: auto;
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  border-radius: 9px;
  background: var(--color-danger, #ff4d4f);
  color: #fff;
  font-size: 11px;
  line-height: 18px;
  text-align: center;
  flex-shrink: 0;
}

.nav-item:hover {
  background-color: rgba(255, 255, 255, 0.06);
  color: #fff;
}

.nav-item.active {
  background: linear-gradient(90deg, rgba(255, 107, 53, 0.18), transparent);
  color: #fff;
  border-left-color: var(--color-primary);
}

.nav-item.active i {
  color: var(--color-primary);
}

/* ========== 右侧主区域（自适应剩余空间，防止子内容反向撑破布局） ========== */
.main-wrap {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background-color: var(--color-bg);
}

.topbar {
  height: 52px;
  background-color: #fff;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;
  border-bottom: 1px solid var(--color-border);
  flex-shrink: 0;
}

/* 面包屑 */
.breadcrumb {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  font-size: 14px;
}

.bc-home {
  color: var(--color-text-secondary);
}

.bc-sep {
  font-size: 11px;
  color: var(--color-text-placeholder);
}

.bc-current {
  color: var(--color-text);
  font-weight: 600;
}

/* 顶部栏右侧 */
.topbar-right {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
}

.icon-btn {
  position: relative;
  width: 34px;
  height: 34px;
  border-radius: 50%;
  background: var(--color-bg-gray);
  color: var(--color-text-secondary);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 15px;
  transition: all 0.2s;
}

.icon-btn:hover {
  background-color: var(--color-primary);
  color: #fff;
}

.bell .badge {
  position: absolute;
  top: -2px;
  right: -2px;
  min-width: 16px;
  height: 16px;
  padding: 0 4px;
  border-radius: 8px;
  background-color: var(--color-danger);
  color: #fff;
  font-size: 10px;
  line-height: 16px;
  text-align: center;
}

.admin-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--color-text);
}

/* 内容区：自适应剩余高度，仅纵向滚动，禁止横向滚动/挤压变形 */
.content {
  flex: 1;
  min-height: 0;
  min-width: 0;
  padding: 16px;
  box-sizing: border-box;
  overflow-y: auto;
  overflow-x: hidden;
}

/* 内容区直接子页面防止 flex 撑破 */
.content > * {
  min-width: 0;
  max-width: 100%;
}
</style>

<style>
/* ========== admin 全局基础样式（仅作用于后台布局内，统一各页正常尺寸） ========== */
.admin-layout {
  font-size: 14px;
}

/* 卡片统一内边距，盒模型不溢出 */
.admin-layout .card {
  padding: 16px;
  box-sizing: border-box;
}

.admin-layout .empty-state {
  padding: 32px 0;
}

.admin-layout .empty-state i {
  font-size: 36px;
  margin-bottom: 10px;
}

/* 表格：14px、舒适行高与单元格内边距，文字自动换行避免横向溢出 */
.admin-layout .content table {
  width: 100%;
  border-collapse: collapse;
  font-size: 14px;
  line-height: 1.7;
}
.admin-layout .content table th,
.admin-layout .content table td {
  padding: 12px;
  font-size: 14px;
  text-align: left;
  border-bottom: 1px solid var(--color-border, #eee);
  word-break: break-word;
}
.admin-layout .content table th {
  color: var(--color-text-secondary, #666);
  font-weight: 600;
  white-space: nowrap;
  background-color: var(--color-bg, #fafafa);
}
/* 表格外层不出现横向滚动条（内容已限制不溢出） */
.admin-layout .content .table-wrap {
  overflow-x: hidden;
  max-width: 100%;
  box-sizing: border-box;
}

/* 表单控件：正常尺寸（排除聊天圆形输入框） */
.admin-layout .content input:not(.chat-input):not([type="checkbox"]):not([type="radio"]),
.admin-layout .content select:not(.chat-input),
.admin-layout .content textarea:not(.chat-input) {
  font-size: 14px;
  padding: 7px 11px;
  min-height: 36px;
  box-sizing: border-box;
  max-width: 100%;
  border: 1px solid var(--color-border, #d9d9d9);
  border-radius: var(--radius-sm, 6px);
  outline: none;
  line-height: 1.5;
}
.admin-layout .content textarea:not(.chat-input) {
  min-height: 76px;
  resize: vertical;
}

/* 矩形操作按钮：正常尺寸（排除圆形/图标/链接类按钮，避免破坏特殊控件） */
.admin-layout .content button:not(.send-btn):not(.icon-btn):not(.load-earlier):not(.modal-close):not(.link-btn):not(.scope-opt) {
  font-size: 14px;
  padding: 8px 16px;
  min-height: 36px;
  line-height: 1.5;
  border-radius: var(--radius-sm, 6px);
  box-sizing: border-box;
  cursor: pointer;
  white-space: nowrap;
}

/* 表格行内文字链接按钮 */
.admin-layout .content button.link-btn {
  font-size: 13px;
  padding: 3px 6px;
  background: none;
  border: none;
}
</style>
