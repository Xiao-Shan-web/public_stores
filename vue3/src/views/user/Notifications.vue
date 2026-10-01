<template>
  <div class="notifications-page page-container">
    <PageHeader title="系统通知" />

    <div class="page-body">
      <!-- 工具栏：全部已读 -->
      <div class="notice-toolbar">
        <button v-if="messages.length" class="btn-read-all" @click="handleReadAll">
          <i class="fas fa-check-double"></i>
          <span>全部已读</span>
        </button>
      </div>

      <!-- 通知列表 -->
      <div v-if="messages.length" class="msg-list">
        <div
          class="msg-item"
          v-for="m in messages"
          :key="m.id"
          :class="{ unread: Number(m.isRead) !== 1 }"
          @click="handleRead(m)"
        >
          <!-- 左侧官方图标 -->
          <div class="msg-icon-wrap" :class="typeClass(m.type)">
            <i :class="['fas', typeIcon(m.type)]"></i>
          </div>
          <div class="msg-main">
            <div class="msg-title-row">
              <p class="msg-title">{{ m.title }}</p>
              <span class="msg-time">{{ formatTime(m.createdAt) }}</span>
            </div>
            <!-- 内容单行省略 -->
            <p class="msg-content">{{ m.content }}</p>
          </div>
          <!-- 未读红点 -->
          <span v-if="Number(m.isRead) !== 1" class="unread-dot"></span>
        </div>

        <button v-if="hasMore" class="btn-load-more" @click="loadMore" :disabled="loading">
          {{ loading ? '加载中...' : '加载更多' }}
        </button>
        <p v-else class="load-end">- 没有更多了 -</p>
      </div>

      <div v-else-if="!loading" class="empty-state">
        <i class="fas fa-bell"></i>
        <p>暂无系统通知</p>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import PageHeader from '@/components/PageHeader.vue'
import { messageAPI, type MessageItem } from '@/api/messageAPI'
import message from '@/utils/message'
import { subscribeNotification, type NotificationData } from '@/composables/useGlobalSocket'
import { playMessageSound } from '@/utils/sound'
import { useUnreadStore } from '@/stores/unread'

// 独立子页面：不加入 keep-alive 缓存，每次进入都刷新
defineOptions({ name: 'UserNotifications' })

const unreadStore = useUnreadStore()
const messages = ref<MessageItem[]>([])
const loading = ref(false)
const page = ref(1)
const size = 20
const total = ref(0)
const hasMore = computed(() => messages.value.length < total.value)

let unsubNotification: (() => void) | null = null

/** 将 WS 推送的通知转为前端展示格式 */
const toMessageItem = (n: NotificationData): MessageItem => ({
  id: n.id,
  type: n.type as MessageItem['type'],
  title: n.title,
  content: n.content,
  refId: n.refId,
  isRead: n.isRead,
  createdAt: n.createdAt || ''
})

const formatTime = (t: string) => {
  if (!t) return ''
  return t.replace('T', ' ').substring(5, 16)
}

const typeIcon = (type: string) => {
  const map: Record<string, string> = {
    VERIFY: 'fa-check-circle',
    EXPIRE: 'fa-clock',
    SYSTEM: 'fa-bullhorn',
    ACTIVITY: 'fa-gift',
    INTERACT: 'fa-heart',
    AI_PLAN: 'fa-robot',
    COMPLAINT: 'fa-comment-dots'
  }
  return map[type] || 'fa-bell'
}

const typeClass = (type: string) => {
  const map: Record<string, string> = {
    VERIFY: 'tp-verify',
    EXPIRE: 'tp-expire',
    SYSTEM: 'tp-system',
    ACTIVITY: 'tp-activity',
    INTERACT: 'tp-interact',
    AI_PLAN: 'tp-ai',
    COMPLAINT: 'tp-complaint'
  }
  return map[type] || 'tp-system'
}

const loadList = async () => {
  loading.value = true
  try {
    const res = await messageAPI.list({ page: page.value, size })
    if (res.success) {
      if (page.value === 1) messages.value = res.data.list || []
      else messages.value.push(...(res.data.list || []))
      total.value = res.data.total || 0
    }
  } catch {
    /* 静默 */
  } finally {
    loading.value = false
  }
}

const loadMore = () => {
  page.value++
  loadList()
}

const handleRead = async (m: MessageItem) => {
  if (Number(m.isRead) === 1) return
  try {
    const res = await messageAPI.markRead(m.id)
    if (res.success) {
      m.isRead = 1
      // 已读 → 全局未读数实时减少，底部导航气泡同步
      unreadStore.refresh()
    }
  } catch {
    /* 静默 */
  }
}

const handleReadAll = async () => {
  try {
    const res = await messageAPI.markAllRead()
    if (res.success) {
      messages.value.forEach(m => (m.isRead = 1))
      unreadStore.refresh()
      message.success('已全部标记为已读')
    }
  } catch (e: any) {
    message.error(e.message || '操作失败')
  }
}

/**
 * 进入页面：加载通知后自动全部标记已读，底部导航气泡实时清零。
 * 标记失败不阻塞页面浏览。
 */
const enterAndAutoRead = async () => {
  await loadList()
  if (!messages.value.some(m => Number(m.isRead) !== 1)) {
    unreadStore.refresh()
    return
  }
  try {
    const res = await messageAPI.markAllRead()
    if (res.success) {
      messages.value.forEach(m => (m.isRead = 1))
    }
  } catch {
    /* 静默 */
  }
  unreadStore.refresh()
}

onMounted(() => {
  enterAndAutoRead()
  // 监听 WebSocket 推送的系统通知 → 顶部插入 + 提示音
  // （未读数由全局 unread store 统一 +1 / 对账，这里不重复维护）
  unsubNotification = subscribeNotification((data) => {
    // 去重：避免 REST 加载和 WS 推送重复
    if (messages.value.some(m => m.id === data.id)) return
    messages.value.unshift(toMessageItem(data))
    total.value += 1
    playMessageSound()
  })
})

onUnmounted(() => {
  if (unsubNotification) unsubNotification()
})
</script>

<style scoped>
.notifications-page {
  min-height: 100vh;
  background: var(--color-bg);
}

.page-body {
  padding: var(--spacing-md);
}

/* ==================== 工具栏 ==================== */
.notice-toolbar {
  display: flex;
  justify-content: flex-end;
  margin-bottom: var(--spacing-sm);
}

.btn-read-all {
  background: var(--color-bg-gray, #f5f5f5);
  border: none;
  border-radius: var(--radius-md);
  padding: 6px 12px;
  font-size: 13px;
  color: var(--color-text-secondary);
  display: flex;
  align-items: center;
  gap: 4px;
}

/* ==================== 通知列表 ==================== */
.msg-list {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-sm);
}

.msg-item {
  position: relative;
  display: flex;
  align-items: center;
  padding: var(--spacing-md);
  background: #fff;
  border-radius: var(--radius-md);
  border-left: 3px solid transparent;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.04);
  cursor: pointer;
  transition: opacity 0.2s;
}

/* 未读：左侧强调色 */
.msg-item.unread {
  border-left-color: var(--color-primary);
  background: linear-gradient(90deg, rgba(255, 107, 53, 0.04), #fff 30%);
}

/* 已读：灰显 */
.msg-item:not(.unread) {
  opacity: 0.62;
}

.msg-icon-wrap {
  width: 38px;
  height: 38px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-right: var(--spacing-sm);
  flex-shrink: 0;
}

.msg-icon-wrap i {
  color: #fff;
  font-size: 15px;
}

.tp-verify {
  background: linear-gradient(135deg, #52c41a, #7ad13f);
}
.tp-expire {
  background: linear-gradient(135deg, #fa8c16, #ffb347);
}
.tp-system {
  background: linear-gradient(135deg, #1890ff, #4aa9ff);
}
.tp-activity {
  background: linear-gradient(135deg, #eb2f96, #ff85c0);
}
.tp-interact {
  background: linear-gradient(135deg, #ff4d4f, #ff7a45);
}
.tp-ai {
  background: linear-gradient(135deg, #722ed1, #b37feb);
}
.tp-complaint {
  background: linear-gradient(135deg, #13c2c2, #5cdbd3);
}

.msg-main {
  flex: 1;
  min-width: 0;
}

.msg-title-row {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 3px;
}

/* 标题加粗 */
.msg-title {
  font-size: 15px;
  font-weight: 700;
  color: var(--color-text);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.msg-time {
  font-size: 11px;
  color: var(--color-text-placeholder);
  flex-shrink: 0;
}

/* 内容单行省略 */
.msg-content {
  font-size: 13px;
  color: var(--color-text-secondary);
  line-height: 1.5;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* 未读红点 */
.unread-dot {
  position: absolute;
  top: 10px;
  right: 10px;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--color-danger, #ff4d4f);
  box-shadow: 0 0 0 3px rgba(255, 77, 79, 0.15);
}

.btn-load-more {
  width: 100%;
  background: #fff;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  padding: 10px;
  color: var(--color-text-secondary);
  font-size: 14px;
}

.load-end {
  text-align: center;
  color: var(--color-text-placeholder);
  font-size: 12px;
  padding: var(--spacing-md) 0;
}

.empty-state {
  text-align: center;
  padding: 60px 0;
  color: var(--color-text-placeholder);
}

.empty-state i {
  font-size: 42px;
  margin-bottom: var(--spacing-sm);
  display: block;
}
</style>
