<template>
  <div class="messages-page page-container">
    <!-- 顶部导航栏 -->
    <div class="page-navbar">
      <button class="page-nav-back" @click="router.back()">
        <i class="fas fa-chevron-left"></i>
      </button>
      <div class="page-nav-title">
        <span>消息中心</span>
      </div>
      <div class="page-nav-right"></div>
    </div>

    <!-- 加载骨架屏 -->
    <div v-if="loading" class="skeleton-list">
      <div v-for="i in 3" :key="i" class="skeleton-card">
        <div class="skeleton" style="width:48px;height:48px;border-radius:50%;"></div>
        <div style="flex:1;">
          <div class="skeleton-line medium"></div>
          <div class="skeleton-line short" style="margin-top:8px;"></div>
        </div>
      </div>
    </div>

    <!-- 消息内容 -->
    <div v-else class="messages-content" ref="scrollContainer">
      <!-- 下拉刷新区域 -->
      <div class="refresh-container" @touchstart.passive="onTouchStart" @touchmove="onTouchMove" @touchend="onTouchEnd">
        <div class="refresh-indicator" :style="{ transform: `translateY(${refreshOffset}px)` }">
          <i v-if="!refreshing" class="fas fa-chevron-down" :class="{ 'rotate': refreshOffset > 50 }"></i>
          <div v-else class="loading-spinner"></div>
          <span>{{ refreshing ? '刷新中...' : (refreshOffset > 50 ? '松开刷新' : '下拉刷新') }}</span>
        </div>
      </div>

      <!-- 三个置顶卡片 -->
      <div class="notify-cards">
        <div class="notify-card" v-for="card in topCards" :key="card.type"
          @click="goToCard(card.type)">
          <div class="card-icon" :style="{ background: card.bg }">
            <i :class="['fas', card.icon]"></i>
          </div>
          <div class="card-info">
            <div class="card-title">{{ card.label }}</div>
            <div class="card-preview">{{ card.latest || '暂无消息' }}</div>
          </div>
          <div class="card-right">
            <span v-if="card.unread > 0" class="card-badge">
              {{ card.unread > 99 ? '99+' : card.unread }}
            </span>
            <i class="fas fa-chevron-right card-arrow"></i>
          </div>
        </div>
      </div>

      <!-- 客服消息区（有对话记录才显示） -->
      <div class="chat-section" v-if="chatMessages.length > 0">
        <div class="section-title">
          <span>客服消息</span>
          <span class="section-hint">您与商家的对话</span>
        </div>
        <div class="chat-cards">
          <!-- 置顶会话 -->
          <div v-if="topSessions.length > 0" class="top-label">
            <i class="fas fa-star"></i>
            <span>置顶</span>
          </div>
          <div
            v-for="chat in topSessions"
            :key="chat.id"
            class="chat-card-wrapper"
            @touchstart.passive="onSwipeStart($event, chat.id)"
            @touchmove="onSwipeMove"
            @touchend="onSwipeEnd($event, chat)"
          >
            <div class="chat-card" @click="goToChat(chat.id)">
              <div v-if="chat.isTop" class="top-tag"><i class="fas fa-star"></i></div>
              <div v-if="chat.isMuted" class="mute-tag"><i class="fas fa-bell-slash"></i></div>
              <img :src="chat.avatar || defaultAvatar" class="shop-avatar" />
              <div class="chat-info">
                <div class="shop-name">{{ chat.sellerName }}</div>
                <div class="last-message">{{ chat.lastMessage }}</div>
              </div>
              <div class="chat-right">
                <span v-if="chat.unreadCount > 0 && !chat.isMuted" class="card-badge">
                  {{ chat.unreadCount > 99 ? '99+' : chat.unreadCount }}
                </span>
                <span class="chat-time">{{ chat.lastMessageTime }}</span>
              </div>
            </div>
            <!-- 滑动操作区域 -->
            <div class="chat-actions">
              <button class="action-btn action-top" @click="toggleTop(chat)">
                <i class="fas fa-star"></i>
                <span>{{ chat.isTop ? '取消' : '置顶' }}</span>
              </button>
              <button class="action-btn action-mute" @click="toggleMute(chat)">
                <i class="fas fa-bell-slash"></i>
                <span>{{ chat.isMuted ? '取消' : '免打扰' }}</span>
              </button>
              <button class="action-btn action-delete" @click="deleteSession(chat)">
                <i class="fas fa-trash"></i>
                <span>删除</span>
              </button>
            </div>
          </div>

          <!-- 普通会话 -->
          <div v-if="normalSessions.length > 0" class="normal-label">
            <span>最近消息</span>
          </div>
          <div
            v-for="chat in normalSessions"
            :key="chat.id"
            class="chat-card-wrapper"
            @touchstart.passive="onSwipeStart($event, chat.id)"
            @touchmove="onSwipeMove"
            @touchend="onSwipeEnd($event, chat)"
          >
            <div class="chat-card" @click="goToChat(chat.id)">
              <div v-if="chat.isTop" class="top-tag"><i class="fas fa-star"></i></div>
              <div v-if="chat.isMuted" class="mute-tag"><i class="fas fa-bell-slash"></i></div>
              <img :src="chat.avatar || defaultAvatar" class="shop-avatar" />
              <div class="chat-info">
                <div class="shop-name">{{ chat.sellerName }}</div>
                <div class="last-message">{{ chat.lastMessage }}</div>
              </div>
              <div class="chat-right">
                <span v-if="chat.unreadCount > 0 && !chat.isMuted" class="card-badge">
                  {{ chat.unreadCount > 99 ? '99+' : chat.unreadCount }}
                </span>
                <span class="chat-time">{{ chat.lastMessageTime }}</span>
              </div>
            </div>
            <!-- 滑动操作区域 -->
            <div class="chat-actions">
              <button class="action-btn action-top" @click="toggleTop(chat)">
                <i class="fas fa-star"></i>
                <span>{{ chat.isTop ? '取消' : '置顶' }}</span>
              </button>
              <button class="action-btn action-mute" @click="toggleMute(chat)">
                <i class="fas fa-bell-slash"></i>
                <span>{{ chat.isMuted ? '取消' : '免打扰' }}</span>
              </button>
              <button class="action-btn action-delete" @click="deleteSession(chat)">
                <i class="fas fa-trash"></i>
                <span>删除</span>
              </button>
            </div>
          </div>
        </div>
      </div>

      <!-- 空状态 -->
      <div v-if="allEmpty" class="empty-state">
        <i class="fas fa-inbox"></i>
        <p>暂无消息</p>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
defineOptions({ name: 'UserMessages' })

import { ref, computed, onMounted, nextTick, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { authAPI } from '@/api/authAPI'
import Message from '@/utils/message'
import defaultAvatar from '@/static/images/user-avatar.jpg'

const router = useRouter()
const loading = ref(true)
const refreshing = ref(false)
const refreshOffset = ref(0)
const startY = ref(0)
const currentSwipeId = ref<number | null>(null)
const swipeOffset = ref(0)
const scrollContainer = ref<HTMLElement | null>(null)

const orderUnread = ref(0)
const systemUnread = ref(0)
const orderLatest = ref('')
const systemLatest = ref('')
const chatMessages = ref<any[]>([])

const topCards = computed(() => [
  {
    type: 'SYSTEM', label: '系统相关', icon: 'fa-bell', bg: '#8b5cf6',
    unread: systemUnread.value, latest: systemLatest.value
  },
  {
    type: 'ORDER', label: '订单相关', icon: 'fa-receipt', bg: '#f59e0b',
    unread: orderUnread.value, latest: orderLatest.value
  }
])

const topSessions = computed(() => chatMessages.value.filter(c => c.isTop))
const normalSessions = computed(() => chatMessages.value.filter(c => !c.isTop))

const allEmpty = computed(() =>
  orderUnread.value === 0 && systemUnread.value === 0 &&
  chatMessages.value.length === 0
)

const loadNotifications = async () => {
  try {
    const res = await authAPI.getNotificationsSummary()
    if (res.success && res.data) {
      orderUnread.value = res.data.ORDER?.unread || 0
      orderLatest.value = res.data.ORDER?.latest || ''
      systemUnread.value = res.data.SYSTEM?.unread || 0
      systemLatest.value = res.data.SYSTEM?.latest || ''

      // 获取会话列表（支持置顶、免打扰）
      const sessionsRes = await authAPI.getChatSessions()
      if (sessionsRes.success && sessionsRes.data) {
        chatMessages.value = sessionsRes.data.map((s: any) => ({
          id: s.id,
          sellerName: s.name,
          avatar: s.avatar,
          lastMessage: s.latest,
          lastMessageTime: formatTime(s.time),
          unreadCount: s.unread,
          isTop: s.isTop,
          isMuted: s.isMuted
        }))
      } else {
        // 兼容 getSummary 接口返回的 chats 数据
        chatMessages.value = (res.data.chats || []).map((c: any) => ({
          id: c.id,
          sellerName: c.name,
          avatar: c.avatar,
          lastMessage: c.latest,
          lastMessageTime: formatTime(c.time),
          unreadCount: c.unread
        }))
      }
    }
  } catch (error: any) {
    Message.error('加载消息失败')
  } finally {
    loading.value = false
    refreshing.value = false
  }
}

const formatTime = (dateStr: string) => {
  if (!dateStr) return ''
  const date = new Date(dateStr)
  if (isNaN(date.getTime())) return ''
  const now = new Date()
  const diff = now.getTime() - date.getTime()
  const days = Math.floor(diff / (1000 * 60 * 60 * 24))

  if (days === 0) {
    return date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
  } else if (days === 1) {
    return '昨天'
  } else if (days < 7) {
    return days + '天前'
  } else {
    return date.toLocaleDateString('zh-CN', { month: '2-digit', day: '2-digit' })
  }
}

const goToCard = (type: string) => {
  router.push({ name: 'MessageList', query: { type } })
}

const goToChat = (targetId: number) => {
  router.push({ name: 'Chat', params: { targetId } })
}

// 下拉刷新相关
const onTouchStart = (e: TouchEvent) => {
  const touch = e.touches[0]
  if (!touch) return
  const target = e.target as HTMLElement
  if (target.closest('.refresh-container') && window.scrollY === 0) {
    startY.value = touch.clientY
  }
}

const onTouchMove = (e: TouchEvent) => {
  if (refreshing.value) return
  const touch = e.touches[0]
  if (!touch) return
  const currentY = touch.clientY
  const diff = currentY - startY.value
  if (diff > 0 && window.scrollY === 0) {
    refreshOffset.value = Math.min(diff * 0.5, 100)
  }
}

const onTouchEnd = () => {
  if (refreshOffset.value > 50 && !refreshing.value) {
    refreshing.value = true
    loadNotifications()
  }
  refreshOffset.value = 0
}

// 左滑操作相关
const onSwipeStart = (e: TouchEvent, id: number) => {
  currentSwipeId.value = id
}

const onSwipeMove = (e: TouchEvent) => {
  // 可以添加更复杂的滑动逻辑
}

const onSwipeEnd = (e: TouchEvent, chat: any) => {
  currentSwipeId.value = null
}

const toggleTop = async (chat: any) => {
  try {
    const res = await authAPI.setSessionTop(chat.id, !chat.isTop)
    if (res.success) {
      chat.isTop = !chat.isTop
      Message.success(res.message || '操作成功')
    }
  } catch (error) {
    Message.error('操作失败')
  }
}

const toggleMute = async (chat: any) => {
  try {
    const res = await authAPI.setSessionMute(chat.id, !chat.isMuted)
    if (res.success) {
      chat.isMuted = !chat.isMuted
      Message.success(res.message || '操作成功')
    }
  } catch (error) {
    Message.error('操作失败')
  }
}

const deleteSession = async (chat: any) => {
  try {
    const res = await authAPI.deleteChatSession(chat.targetId)
    if (res.success) {
      const index = chatMessages.value.findIndex(c => c.id === chat.id)
      if (index > -1) {
        chatMessages.value.splice(index, 1)
      }
      Message.success('删除成功')
    }
  } catch (error) {
    Message.error('删除失败')
  }
}

const handleNewMessage = () => {
  loadNotifications()
}

onMounted(() => {
  loadNotifications()
  window.addEventListener('new-chat-message', handleNewMessage)
})

onUnmounted(() => {
  window.removeEventListener('new-chat-message', handleNewMessage)
})
</script>

<style scoped>
@import url('https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css');
@import url('@/static/css/user/消息中心.css');

.refresh-container {
  position: relative;
  overflow: hidden;
}

.refresh-indicator {
  position: absolute;
  top: -60px;
  left: 0;
  right: 0;
  height: 60px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  transition: transform 0.2s;
  color: #94a3b8;
  font-size: 12px;
}

.refresh-indicator i {
  font-size: 16px;
  margin-bottom: 4px;
  transition: transform 0.2s;
}

.refresh-indicator i.rotate {
  transform: rotate(180deg);
}

.loading-spinner {
  width: 20px;
  height: 20px;
  border: 2px solid #e2e8f0;
  border-top-color: #4a6491;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.chat-card-wrapper {
  position: relative;
  overflow: hidden;
}

.chat-actions {
  position: absolute;
  right: 0;
  top: 0;
  bottom: 0;
  display: flex;
  transform: translateX(100%);
  transition: transform 0.2s;
}

.chat-card-wrapper.active .chat-actions {
  transform: translateX(0);
}

.action-btn {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  width: 60px;
  border: none;
  cursor: pointer;
  color: white;
  font-size: 12px;
  transition: all 0.2s;
}

.action-btn i {
  font-size: 16px;
  margin-bottom: 4px;
}

.action-top {
  background: #f59e0b;
}

.action-mute {
  background: #6b7280;
}

.action-delete {
  background: #ef4444;
}

.action-btn:active {
  opacity: 0.8;
}

.top-tag, .mute-tag {
  position: absolute;
  top: 8px;
  font-size: 10px;
  color: #94a3b8;
}

.top-tag {
  left: 56px;
}

.mute-tag {
  left: 72px;
}

.top-label, .normal-label {
  padding: 8px 16px;
  font-size: 12px;
  color: #94a3b8;
  background: #f5f7fa;
  display: flex;
  align-items: center;
  gap: 4px;
}

.top-label i {
  color: #f59e0b;
}

.empty-state {
  text-align: center;
  padding: 80px 20px;
  color: #94a3b8;
}

.empty-state i {
  font-size: 48px;
  margin-bottom: 12px;
  display: block;
}
</style>
