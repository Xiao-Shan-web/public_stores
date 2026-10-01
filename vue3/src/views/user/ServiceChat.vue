<template>
  <div class="service-page page-container">
    <PageHeader title="在线客服" />

    <!-- 客服信息栏 -->
    <div class="svc-bar">
      <div class="svc-avatar">
        <i class="fas fa-headset"></i>
      </div>
      <div class="svc-meta">
        <p class="svc-name">山达健身客服</p>
        <p class="svc-status" :class="{ online: adminOnline }">
          <span class="dot"></span>{{ adminOnline ? '在线' : '离线' }}
          <span v-if="!wsConnected" class="ws-hint">（连接中…）</span>
        </p>
      </div>
    </div>

    <!-- 聊天区域 -->
    <div class="chat-area" ref="chatAreaRef">
      <button v-if="hasMore" class="load-earlier" @click="loadEarlier" :disabled="loading">
        {{ loading ? '加载中…' : '查看更早消息' }}
      </button>

      <div
        v-for="m in msgList"
        :key="m.id"
        class="chat-row"
        :class="{ mine: m.senderType === 'USER' }"
      >
        <div v-if="m.senderType === 'ADMIN'" class="chat-avatar">
          <i class="fas fa-headset"></i>
        </div>
        <div class="chat-bubble-wrap">
          <div class="chat-bubble">{{ m.content }}</div>
          <span class="chat-time">{{ m.createdAt?.substring(11, 16) }}</span>
        </div>
        <div v-if="m.senderType === 'USER'" class="chat-avatar mine-avatar">
          <UserAvatar :src="resolveAvatar(authStore.userInfo?.avatar)" previewable />
        </div>
      </div>

      <div v-if="!msgList.length && !loading" class="chat-empty">
        <i class="fas fa-comments"></i>
        <p> hi，有什么可以帮你？</p>
        <p class="sub">会员卡办理、激活、到店核销等问题都可以咨询</p>
      </div>
    </div>

    <!-- 输入区域 -->
    <div class="input-bar">
      <input
        v-model="draft"
        class="chat-input"
        type="text"
        maxlength="500"
        placeholder="输入消息…"
        @keyup.enter="handleSend"
      />
      <button class="send-btn" :disabled="!draft.trim() || !wsConnected" @click="handleSend">
        <i class="fas fa-paper-plane"></i>
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, nextTick, onMounted, onUnmounted, onActivated, onDeactivated } from 'vue'
import PageHeader from '@/components/PageHeader.vue'
import UserAvatar from '@/components/UserAvatar.vue'
import { resolveAvatar } from '@/utils/avatar'
import { serviceAPI, type ServiceMessage } from '@/api/serviceAPI'
import { message } from '@/utils/message'
import {
  subscribeService,
  subscribeState,
  subscribeServiceStatus,
  sendServiceMessage,
  type ServiceMessageData
} from '@/composables/useGlobalSocket'
import { playMessageSound } from '@/utils/sound'
import { useAuthStore } from '@/stores/auth'
import { useUnreadStore } from '@/stores/unread'

const authStore = useAuthStore()
const unreadStore = useUnreadStore()
const chatAreaRef = ref<HTMLElement | null>(null)

const adminOnline = ref(false)
const wsConnected = ref(false)
const msgList = ref<ServiceMessage[]>([])
const draft = ref('')
const loading = ref(false)
const page = ref(1)
const size = 20
const total = ref(0)
const hasMore = ref(false)

let unsubService: (() => void) | null = null
let unsubState: (() => void) | null = null
let unsubServiceStatus: (() => void) | null = null

/**
 * 页面是否处于激活态（keep-alive 场景下区分"停留中"与"已离开"）：
 * - true：收到客服新消息自动已读，不计未读
 * - false：不自动已读，未读数正常增加
 */
const isViewActive = ref(true)

/** 自动把全部客服消息标记已读（进入页面 / 页面激活 / 收到新消息时调用），并同步全局未读气泡 */
const markAllRead = () => {
  serviceAPI
    .markRead()
    .then(() => unreadStore.refresh())
    .catch(() => {})
}

/** 滚动到底部 */
const scrollToBottom = () => {
  nextTick(() => {
    if (chatAreaRef.value) {
      chatAreaRef.value.scrollTop = chatAreaRef.value.scrollHeight
    }
  })
}

/** 历史消息（第一页=最近20条） */
const loadMessages = async (appendEarlier = false) => {
  loading.value = true
  try {
    const res = await serviceAPI.messages({ page: page.value, size })
    if (res.success && res.data) {
      const list = res.data.list || []
      total.value = res.data.total || 0
      if (appendEarlier) {
        msgList.value = [...list, ...msgList.value]
      } else {
        msgList.value = list
        scrollToBottom()
        // 进入/重新激活页面加载最新消息后，自动把客服消息全部标记已读
        if (isViewActive.value) markAllRead()
      }
      hasMore.value = msgList.value.length < total.value
    }
  } catch {
    /* 静默 */
  } finally {
    loading.value = false
  }
}

const loadEarlier = () => {
  page.value++
  loadMessages(true)
}

/** 当前时间格式化为后端一致的 YYYY-MM-DD HH:mm:ss（用于本地乐观上屏） */
const formatNow = () => {
  const d = new Date()
  const p = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ` +
    `${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`
}

/**
 * 接收/上屏一条消息：
 * 1. id 已存在 → 忽略（重复投递）
 * 2. 自己发出的消息回传 → 忽略（本地乐观消息为准）
 * 3. 命中本地乐观临时消息（负 id + 同发送方 + 同内容）→ 用服务端消息"替换"它，不再追加
 * 4. 否则正常追加
 */
const appendMessage = (m: ServiceMessage | ServiceMessageData) => {
  if (msgList.value.some(x => x.id === m.id)) return
  if (m.senderType === 'USER' && m.senderId === authStore.userInfo?.id) return

  // 倒序查找最近一条匹配的本地乐观临时消息（负 id + 同发送方 + 同内容）
  let tempIdx = -1
  for (let i = msgList.value.length - 1; i >= 0; i--) {
    const x = msgList.value[i]
    if (x.id < 0 && x.senderType === m.senderType && x.content === m.content) {
      tempIdx = i
      break
    }
  }
  if (tempIdx >= 0) {
    // 用服务端记录替换临时消息（获得真实 id / 时间），位置不变，不产生第二条
    msgList.value.splice(tempIdx, 1, m as ServiceMessage)
  } else {
    msgList.value.push(m as ServiceMessage)
  }

  // 只对"客服发来的"消息做自动已读与提示音；自己发的消息不响铃
  if (m.senderType === 'ADMIN' && m.senderId !== authStore.userInfo?.id) {
    // 仅停留在客服页时自动已读；离开页面后不标记，未读数正常增加
    if (isViewActive.value) {
      serviceAPI.markRead().catch(() => {})
    }
    playMessageSound()
  }
  scrollToBottom()
}

/** REST 兜底发送进行中（防止弱网下重复点击产生两条消息） */
let sending = false

const handleSend = () => {
  const content = draft.value.trim()
  if (!content || sending) return
  if (sendServiceMessage({ content })) {
    // 全局 WebSocket 已发送：后端不回推给发送者，本地乐观上屏
    const myId = authStore.userInfo?.id ?? 0
    msgList.value.push({
      id: -Date.now(),
      userId: myId,
      senderId: myId,
      phone: null,
      senderType: 'USER',
      content,
      isRead: 0,
      createdAt: formatNow()
    })
    scrollToBottom()
  } else {
    // 全局 WebSocket 未连接时走 REST 兜底（响应体为落库后的消息，appendMessage 负责上屏）
    sending = true
    serviceAPI
      .send({ content })
      .then(res => {
        if (res.success && res.data) appendMessage(res.data)
      })
      .catch(() => message.error('发送失败，请稍后重试'))
      .finally(() => {
        sending = false
      })
  }
  draft.value = ''
}

/** 进入页面时拉取一次客服在线状态（以 Redis 为准）；之后实时变化由 WebSocket 推送 */
const refreshAdminOnline = () => {
  serviceAPI
    .status()
    .then(res => {
      if (res.success && res.data) adminOnline.value = !!res.data.adminOnline
    })
    .catch(() => {})
}

onMounted(() => {
  isViewActive.value = true
  loadMessages()
  refreshAdminOnline()
  // 全局 WebSocket 已由 auth store 建立，这里只订阅事件
  unsubService = subscribeService(data => appendMessage(data))
  unsubState = subscribeState(connected => {
    wsConnected.value = connected
  })
  // 客服上/下线实时推送 → 只更新在线标识，不刷新页面
  unsubServiceStatus = subscribeServiceStatus(online => {
    adminOnline.value = online
  })
})

onUnmounted(() => {
  isViewActive.value = false
  if (unsubService) unsubService()
  if (unsubState) unsubState()
  if (unsubServiceStatus) unsubServiceStatus()
})

// keep-alive 缓存时：首次激活紧随 onMounted（不重复加载）；再次进入时重新拉取最新消息并自动已读
let firstActivation = true
onActivated(() => {
  isViewActive.value = true
  if (firstActivation) {
    firstActivation = false
    return
  }
  page.value = 1
  loadMessages()
  refreshAdminOnline()
})

// 离开页面（keep-alive 停用时）：停止自动已读，此后客服新消息正常累计未读
onDeactivated(() => {
  isViewActive.value = false
})
</script>

<style scoped>
.service-page {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: var(--color-bg, #f7f8fa);
}

/* ==================== 客服信息栏 ==================== */
.svc-bar {
  display: flex;
  align-items: center;
  padding: 10px var(--spacing-md);
  background: #fff;
  border-bottom: 1px solid var(--color-border, #eee);
}

.svc-avatar {
  width: 42px;
  height: 42px;
  border-radius: 50%;
  background: var(--gradient-primary, linear-gradient(135deg, #ff6b35, #ff9558));
  display: flex;
  align-items: center;
  justify-content: center;
  margin-right: 10px;
  flex-shrink: 0;
}

.svc-avatar i {
  color: #fff;
  font-size: 18px;
}

.svc-meta {
  flex: 1;
}

.svc-name {
  font-size: 15px;
  font-weight: 700;
  color: var(--color-text);
  margin-bottom: 2px;
}

.svc-status {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 12px;
  color: var(--color-text-placeholder);
}

.svc-status.online {
  color: #52c41a;
}

.svc-status .dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: currentColor;
}

.ws-hint {
  color: var(--color-text-placeholder);
  font-size: 11px;
}

/* ==================== 聊天区域 ==================== */
.chat-area {
  flex: 1;
  overflow-y: auto;
  padding: var(--spacing-md);
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.load-earlier {
  align-self: center;
  font-size: 12px;
  color: var(--color-text-secondary);
  background: #fff;
  border: 1px solid var(--color-border);
  border-radius: 14px;
  padding: 5px 14px;
}

.chat-row {
  display: flex;
  align-items: flex-start;
  gap: 8px;
}

/* 我的消息靠右：整行右对齐，DOM 顺序已为 [气泡][头像] → 头像在右、气泡在左 */
.chat-row.mine {
  justify-content: flex-end;
}

.chat-avatar {
  width: 34px;
  height: 34px;
  border-radius: 50%;
  background: linear-gradient(135deg, #1890ff, #4aa9ff);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.mine-avatar {
  background: var(--gradient-primary, linear-gradient(135deg, #ff6b35, #ff9558));
}

.chat-bubble-wrap {
  max-width: 72%;
  display: flex;
  flex-direction: column;
}

.chat-row.mine .chat-bubble-wrap {
  align-items: flex-end;
}

.chat-bubble {
  padding: 9px 13px;
  border-radius: 14px;
  font-size: 14px;
  line-height: 1.55;
  word-break: break-word;
  white-space: pre-wrap;
  background: #fff;
  color: var(--color-text);
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
  border-top-left-radius: 4px;
}

.chat-row.mine .chat-bubble {
  background: var(--gradient-primary, linear-gradient(135deg, #ff6b35, #ff9558));
  color: #fff;
  border-top-left-radius: 14px;
  border-top-right-radius: 4px;
  box-shadow: 0 2px 8px rgba(255, 107, 53, 0.25);
}

.chat-time {
  font-size: 10px;
  color: var(--color-text-placeholder);
  margin-top: 3px;
  padding: 0 4px;
}

.chat-empty {
  margin: auto;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  text-align: center;
  color: var(--color-text-placeholder);
}

.chat-empty i {
  width: 40px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 40px;
  line-height: 1;
  margin: 0;
  padding: 0;
}

.chat-empty p {
  font-size: 14px;
  line-height: 1.5;
  margin: 0;
}

.chat-empty .sub {
  font-size: 12px;
  line-height: 1.5;
}

/* ==================== 输入区域 ==================== */
.input-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px var(--spacing-md);
  padding-bottom: calc(10px + env(safe-area-inset-bottom, 0px));
  background: #fff;
  border-top: 1px solid var(--color-border, #eee);
}

.chat-input {
  flex: 1;
  height: 38px;
  padding: 0 14px;
  border: 1px solid var(--color-border, #e5e5e5);
  border-radius: 19px;
  font-size: 14px;
  outline: none;
  background: var(--color-bg-gray, #f7f8fa);
}

.chat-input:focus {
  border-color: var(--color-primary);
  background: #fff;
}

.send-btn {
  width: 38px;
  height: 38px;
  border-radius: 50%;
  background: var(--gradient-primary, linear-gradient(135deg, #ff6b35, #ff9558));
  color: #fff;
  font-size: 15px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  box-shadow: 0 3px 8px rgba(255, 107, 53, 0.3);
  transition: transform 0.15s, opacity 0.15s;
}

.send-btn:active {
  transform: scale(0.92);
}

.send-btn:disabled {
  opacity: 0.5;
}
</style>
