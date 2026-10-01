<template>
  <div class="messages-page page-container">
    <!-- 双卡片入口：一眼看到两个独立入口，点击进入对应页面 -->
    <div class="entry-list">
      <!-- 系统通知卡片 -->
      <div class="entry-card" @click="goNotifications">
        <div class="entry-icon notice-icon">
          <i class="fas fa-bell"></i>
        </div>
        <div class="entry-info">
          <div class="entry-title-row">
            <p class="entry-title">系统通知</p>
            <span v-if="noticeUnread > 0" class="entry-unread">{{ noticeUnread > 99 ? '99+' : noticeUnread }} 条未读</span>
          </div>
          <p class="entry-desc">会员卡到期、核销等通知</p>
        </div>
        <i class="fas fa-chevron-right entry-arrow"></i>
      </div>

      <!-- 客服消息卡片 -->
      <div class="entry-card" @click="goService">
        <div class="entry-icon service-icon">
          <i class="fas fa-headset"></i>
        </div>
        <div class="entry-info">
          <div class="entry-title-row">
            <p class="entry-title">山达健身客服</p>
            <span class="svc-status" :class="{ online: adminOnline }">
              <span class="svc-status-dot"></span>
              {{ adminOnline ? '在线' : '离线' }}
            </span>
          </div>
          <p class="entry-desc">{{ serviceUnread > 0 ? `有 ${serviceUnread} 条未读回复，点击进入会话` : '在线 · 点击进入会话' }}</p>
        </div>
        <span v-if="serviceUnread > 0" class="entry-unread-badge">{{ serviceUnread > 99 ? '99+' : serviceUnread }}</span>
        <i class="fas fa-chevron-right entry-arrow"></i>
      </div>
    </div>

    <!-- 温馨提示 -->
    <div class="service-tips">
      <p class="tips-title"><i class="fas fa-circle-info"></i> 温馨提示</p>
      <p class="tips-item">· 咨询高峰期客服回复可能有延迟，请耐心等待</p>
      <p class="tips-item">· 会员卡激活、人脸录入等问题可优先查看「我的」页面指引</p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onActivated, onUnmounted } from 'vue'
import { storeToRefs } from 'pinia'
import { useRouter } from 'vue-router'
import { serviceAPI } from '@/api/serviceAPI'
import { subscribeServiceStatus } from '@/composables/useGlobalSocket'
import { useUnreadStore } from '@/stores/unread'

// keep-alive 缓存按组件 name 匹配
defineOptions({ name: 'UserMessages' })

const router = useRouter()

// ==================== 未读数（全局 store）与在线状态 ====================
const unreadStore = useUnreadStore()
// 卡片上的未读数直接订阅全局状态（通知 + 客服），WS 推送 / 已读均实时同步
const { noticeCount: noticeUnread, serviceCount: serviceUnread } = storeToRefs(unreadStore)
const adminOnline = ref(false)
let unsubServiceStatus: (() => void) | null = null

/** 客服在线状态（未读数由全局 store 统一刷新） */
const refreshStatus = async () => {
  try {
    const statusRes = await serviceAPI.status()
    if (statusRes.success && statusRes.data) {
      adminOnline.value = !!statusRes.data.adminOnline
    }
  } catch {
    /* 静默 */
  }
}

const goNotifications = () => router.push('/user/notifications')
const goService = () => router.push('/user/service')

onMounted(() => {
  // 进入时拉取一次（以服务端为准）：未读数 + 客服在线状态
  unreadStore.refresh()
  refreshStatus()

  // 客服上/下线 → 仅更新客服卡片在线标识，不刷新未读数、不刷新整页
  // （未读数的 WS 订阅已集中在全局 unread store 中处理）
  unsubServiceStatus = subscribeServiceStatus(online => {
    adminOnline.value = online
  })
})

// keep-alive 激活时刷新（从通知列表/客服页返回可能产生新未读）
onActivated(() => {
  unreadStore.refresh()
  refreshStatus()
})

onUnmounted(() => {
  if (unsubServiceStatus) unsubServiceStatus()
})
</script>

<style scoped>
.messages-page {
  padding: var(--spacing-md);
}

/* ==================== 双卡片入口 ==================== */
.entry-list {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-md);
}

.entry-card {
  position: relative;
  display: flex;
  align-items: center;
  background: #fff;
  border-radius: var(--radius-md);
  padding: var(--spacing-md);
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.04);
  cursor: pointer;
  transition: transform 0.15s;
}

.entry-card:active {
  transform: scale(0.98);
}

.entry-icon {
  width: 48px;
  height: 48px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-right: var(--spacing-sm);
  flex-shrink: 0;
}

.entry-icon i {
  color: #fff;
  font-size: 20px;
}

/* 系统通知：蓝色铃铛 */
.notice-icon {
  background: linear-gradient(135deg, #1890ff, #4aa9ff);
  box-shadow: 0 4px 10px rgba(24, 144, 255, 0.3);
}

/* 客服：橙色耳机（品牌主色） */
.service-icon {
  background: var(--gradient-primary, linear-gradient(135deg, #ff6b35, #ff9558));
  box-shadow: 0 4px 10px rgba(255, 107, 53, 0.3);
}

.entry-info {
  flex: 1;
  min-width: 0;
}

.entry-title-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}

.entry-title {
  font-size: 15px;
  font-weight: 700;
  color: var(--color-text);
}

.entry-desc {
  font-size: 13px;
  color: var(--color-text-secondary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* 系统通知未读：文本徽标 */
.entry-unread {
  flex-shrink: 0;
  font-size: 11px;
  color: #ff4d4f;
  background: rgba(255, 77, 79, 0.1);
  padding: 2px 8px;
  border-radius: 10px;
}

/* 客服未读：数字红点 */
.entry-unread-badge {
  flex-shrink: 0;
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  border-radius: 9px;
  background: #ff4d4f;
  color: #fff;
  font-size: 11px;
  line-height: 18px;
  text-align: center;
  margin-left: 8px;
}

/* 客服在线状态 */
.svc-status {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 11px;
  color: var(--color-text-placeholder);
  background: var(--color-bg-gray, #f5f5f5);
  padding: 2px 8px;
  border-radius: 10px;
}

.svc-status.online {
  color: #52c41a;
  background: rgba(82, 196, 26, 0.1);
}

.svc-status-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: currentColor;
}

.entry-arrow {
  color: var(--color-text-placeholder);
  font-size: 13px;
  margin-left: 8px;
}

/* ==================== 温馨提示 ==================== */
.service-tips {
  margin-top: var(--spacing-lg);
  padding: var(--spacing-md);
  background: var(--color-bg-gray, #f7f8fa);
  border-radius: var(--radius-md);
}

.tips-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text-secondary);
  margin-bottom: 8px;
}

.tips-item {
  font-size: 12px;
  color: var(--color-text-placeholder);
  line-height: 2;
}
</style>
