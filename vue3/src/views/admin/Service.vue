<template>
  <div class="admin-service">
    <!-- 左侧会话列表 -->
    <aside class="conv-panel card">
      <div class="conv-head">
        <h3 class="panel-title">
          会话列表
          <span v-if="adminUnreadStore.count > 0" class="head-unread">{{ adminUnreadStore.count > 99 ? '99+' : adminUnreadStore.count }}</span>
        </h3>
        <span class="self-status" :class="{ online: wsConnected }">
          <span class="dot"></span>{{ wsConnected ? '客服在线' : '连接中…' }}
        </span>
      </div>
      <div class="conv-list">
        <div
          v-for="c in conversations"
          :key="c.userId"
          class="conv-item"
          :class="{ active: selectedUserId === c.userId }"
          @click="selectConversation(c)"
        >
          <!-- 头像点击放大预览，@click.stop 避免冒泡选中会话 -->
          <div class="conv-avatar" @click.stop>
            <UserAvatar
              :src="resolveAvatar(c.avatarThumb || c.avatar)"
              :preview-src="resolveAvatar(c.avatar)"
              previewable
            />
            <span class="conv-online" :class="{ on: c.online }"></span>
          </div>
          <div class="conv-main">
            <div class="conv-top-row">
              <span class="conv-name">{{ displayPhone(c.phone) }}</span>
              <span class="conv-time">{{ shortTime(c.lastTime) }}</span>
            </div>
            <div class="conv-preview-row">
              <span class="conv-preview">{{ c.lastContent }}</span>
              <span v-if="c.unread > 0" class="conv-unread">{{ c.unread > 99 ? '99+' : c.unread }}</span>
            </div>
          </div>
        </div>
        <div v-if="!conversations.length" class="conv-empty">
          <i class="fas fa-inbox"></i>
          <p>暂无会话</p>
        </div>
      </div>
    </aside>

    <!-- 右侧聊天窗口 -->
    <section class="chat-panel card">
      <template v-if="selectedUserId">
        <div class="chat-head">
          <span class="chat-user">{{ displayPhone(selectedPhone) }}</span>
          <span class="chat-user-status" :class="{ online: selectedOnline }">
            <span class="dot"></span>{{ selectedOnline ? '在线' : '离线' }}
          </span>
        </div>

        <div class="chat-body" ref="chatBodyRef">
          <button v-if="hasMore" class="load-earlier" @click="loadEarlier" :disabled="loading">
            {{ loading ? '加载中…' : '查看更早消息' }}
          </button>
          <div
            v-for="m in msgList"
            :key="m.id"
            class="chat-row"
            :class="{ mine: m.senderType === 'ADMIN' }"
          >
            <div v-if="m.senderType === 'USER'" class="chat-avatar">
              <UserAvatar
                :src="resolveAvatar(selectedConv?.avatarThumb || selectedConv?.avatar)"
                :preview-src="resolveAvatar(selectedConv?.avatar)"
                previewable
              />
            </div>
            <div class="chat-bubble-wrap">
              <div class="chat-bubble">{{ m.content }}</div>
              <span class="chat-time">{{ m.createdAt?.substring(11, 16) }}</span>
            </div>
            <div v-if="m.senderType === 'ADMIN'" class="chat-avatar admin-avatar">
              <i class="fas fa-headset"></i>
            </div>
          </div>
          <div v-if="!msgList.length && !loading" class="chat-empty">
            <i class="fas fa-comments"></i>
            <p>暂无消息，发送第一条回复吧</p>
          </div>
        </div>

        <div class="input-bar">
          <input
            v-model="draft"
            class="chat-input"
            type="text"
            maxlength="500"
            placeholder="回复用户…"
            @keyup.enter="handleSend"
          />
          <button class="send-btn" :disabled="!draft.trim() || !wsConnected" @click="handleSend">
            <i class="fas fa-paper-plane"></i>
          </button>
        </div>
      </template>

      <div v-else class="chat-placeholder">
        <i class="fas fa-comments"></i>
        <p>选择左侧会话开始沟通</p>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, nextTick, onMounted, onUnmounted } from 'vue'
import { serviceAPI, type Conversation, type ServiceMessage } from '@/api/serviceAPI'
import { message } from '@/utils/message'
import {
  subscribeService,
  subscribeState,
  subscribeUserStatus,
  sendServiceMessage,
  type ServiceMessageData
} from '@/composables/useGlobalSocket'
import { playMessageSound } from '@/utils/sound'
import { useAuthStore } from '@/stores/auth'
import UserAvatar from '@/components/UserAvatar.vue'
import { resolveAvatar } from '@/utils/avatar'
import { useAdminUnreadStore } from '@/stores/adminUnread'

const authStore = useAuthStore()
const adminUnreadStore = useAdminUnreadStore()
const conversations = ref<Conversation[]>([])
const selectedUserId = ref<number | null>(null)
const selectedPhone = ref<string | null>(null)
const msgList = ref<ServiceMessage[]>([])
const draft = ref('')
const wsConnected = ref(false)
const loading = ref(false)
const page = ref(1)
const size = 20
const total = ref(0)
const chatBodyRef = ref<HTMLElement | null>(null)

let unsubService: (() => void) | null = null
let unsubState: (() => void) | null = null
let unsubUserStatus: (() => void) | null = null

const selectedOnline = computed(() => {
  const c = conversations.value.find(x => x.userId === selectedUserId.value)
  return !!c?.online
})
/** 当前选中会话（聊天窗口头像与预览均取该会话的头像字段） */
const selectedConv = computed(() =>
  conversations.value.find(x => x.userId === selectedUserId.value) || null
)
const hasMore = computed(() => msgList.value.length < total.value)

/** 管理端客服显示完整手机号（不脱敏），空值兜底为「未知用户」 */
const displayPhone = (phone: string | null) => phone || '未知用户'

const shortTime = (t: string | null) => {
  if (!t) return ''
  const d = new Date(t.replace(' ', 'T'))
  const now = new Date()
  const sameDay = d.toDateString() === now.toDateString()
  return sameDay ? t.substring(11, 16) : t.substring(5, 16)
}

const scrollToBottom = () => {
  nextTick(() => {
    if (chatBodyRef.value) chatBodyRef.value.scrollTop = chatBodyRef.value.scrollHeight
  })
}

/** 会话列表（未读总数由全局 adminUnreadStore 统一管理，此处只拉列表） */
const loadConversations = async () => {
  try {
    const res = await serviceAPI.conversations()
    if (res.success && res.data) conversations.value = res.data || []
  } catch {
    /* 静默 */
  }
}

/** 历史消息 */
const loadMessages = async (appendEarlier = false) => {
  if (!selectedUserId.value) return
  loading.value = true
  try {
    const res = await serviceAPI.messages({ userId: selectedUserId.value, page: page.value, size })
    if (res.success && res.data) {
      const list = res.data.list || []
      total.value = res.data.total || 0
      if (appendEarlier) {
        msgList.value = [...list, ...msgList.value]
      } else {
        msgList.value = list
        scrollToBottom()
      }
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

const selectConversation = async (c: Conversation) => {
  selectedUserId.value = c.userId
  selectedPhone.value = c.phone
  // 记录当前会话：该用户后续消息由聊天页自动已读，不计入全局未读，避免气泡闪烁
  adminUnreadStore.setActiveUserId(c.userId)
  page.value = 1
  msgList.value = []
  await loadMessages()
  // 进入会话即自动把该用户发来的消息全部标记已读，并刷新全局未读角标
  c.unread = 0
  try {
    await serviceAPI.adminReadAll(c.userId)
    adminUnreadStore.refresh()
  } catch {
    /* 静默 */
  }
}

/**
 * 接收/上屏一条消息：
 * 1. id 已存在 → 忽略（重复投递）
 * 2. 自己（客服）发出的消息回传 → 忽略（本地乐观消息为准）
 * 3. 当前会话内命中本地乐观临时消息（负 id + 同发送方 + 同内容）→ 替换，不追加
 */
const appendMessage = async (m: ServiceMessage | ServiceMessageData) => {
  if (msgList.value.some(x => x.id === m.id)) return
  if (m.senderType === 'ADMIN' && m.senderId === authStore.userInfo?.id) return

  const isCurrent = m.userId === selectedUserId.value
  if (isCurrent) {
    let tempIdx = -1
    for (let i = msgList.value.length - 1; i >= 0; i--) {
      const x = msgList.value[i]
      if (x.id < 0 && x.senderType === m.senderType && x.content === m.content) {
        tempIdx = i
        break
      }
    }
    if (tempIdx >= 0) {
      msgList.value.splice(tempIdx, 1, m as ServiceMessage)
    } else {
      msgList.value.push(m as ServiceMessage)
    }
    scrollToBottom()
  }
  // 停留在该用户会话时：先标记已读，再刷新会话列表与全局未读角标，避免列表短暂显示未读
  if (isCurrent && m.senderType === 'USER') {
    try {
      await serviceAPI.adminReadAll(m.userId)
      adminUnreadStore.refresh()
    } catch {
      /* 静默 */
    }
    const conv = conversations.value.find(c => c.userId === m.userId)
    if (conv) conv.unread = 0
  }
  // 刷新会话列表（最后一条消息 / 在线状态；非当前会话保留未读数）
  loadConversations()
  // 只对"用户发来的"消息响铃；客服自己发出的消息（senderId 为本人）不响铃
  if (m.senderType === 'USER' && m.senderId !== authStore.userInfo?.id) {
    playMessageSound()
  }
}

/** 当前时间格式化为后端一致的 YYYY-MM-DD HH:mm:ss（用于本地乐观上屏） */
const formatNow = () => {
  const d = new Date()
  const p = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ` +
    `${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`
}

/** REST 兜底发送进行中（防止弱网下重复点击产生两条消息） */
let sending = false

const handleSend = () => {
  const content = draft.value.trim()
  if (!content || !selectedUserId.value || sending) return
  const toUserId = selectedUserId.value
  if (sendServiceMessage({ content, toUserId })) {
    // 全局 WebSocket 已发送：后端不回推给发送者，本地乐观上屏
    msgList.value.push({
      id: -Date.now(),
      userId: toUserId,
      senderId: authStore.userInfo?.id ?? 0,
      phone: selectedPhone.value,
      senderType: 'ADMIN',
      content,
      isRead: 0,
      createdAt: formatNow()
    })
    scrollToBottom()
    // 刷新会话列表最后一条消息（服务端落库后接口以 DB 为准）
    loadConversations()
  } else {
    // 全局 WebSocket 未连接时 REST 兜底（响应体为落库后的消息，appendMessage 负责上屏）
    sending = true
    serviceAPI
      .send({ content, toUserId })
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

onMounted(() => {
  loadConversations()
  // 全局 WebSocket 已由 auth store 建立，这里只订阅事件
  // 自身在线状态由全局连接的后端生命周期维护，不在此处调用 online/offline
  unsubService = subscribeService(data => appendMessage(data))
  unsubState = subscribeState(connected => {
    wsConnected.value = connected
  })
  // 用户上/下线 → 实时更新会话列表对应用户的在线绿点（不刷新整页、不重复请求）
  unsubUserStatus = subscribeUserStatus(({ userId, online }) => {
    const conv = conversations.value.find(c => c.userId === userId)
    if (conv) conv.online = online
  })
})

onUnmounted(() => {
  // 离开客服页：清除当前会话记录，后续消息恢复正常未读计数
  adminUnreadStore.setActiveUserId(null)
  if (unsubService) unsubService()
  if (unsubState) unsubState()
  if (unsubUserStatus) unsubUserStatus()
})
</script>

<style scoped>
.admin-service {
  display: flex;
  gap: var(--spacing-sm);
  height: calc(100vh - 38px - 16px);
  min-height: 420px;
}

/* ==================== 左侧会话列表 ==================== */
.conv-panel {
  width: 220px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.conv-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 10px;
  border-bottom: 1px solid var(--color-border);
}

.panel-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text);
}

/* 未读总数角标 */
.head-unread {
  min-width: 16px;
  height: 16px;
  padding: 0 4px;
  border-radius: 8px;
  background: var(--color-danger);
  color: #fff;
  font-size: 10px;
  line-height: 16px;
  text-align: center;
}

.self-status {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 11px;
  color: var(--color-text-placeholder);
}

.self-status.online {
  color: #52c41a;
}

.self-status .dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: currentColor;
}

.conv-list {
  flex: 1;
  overflow-y: auto;
}

.conv-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 7px 10px;
  cursor: pointer;
  border-bottom: 1px solid var(--color-border);
  transition: background 0.15s;
}

.conv-item:hover {
  background: var(--color-bg, #fafafa);
}

.conv-item.active {
  background: rgba(255, 107, 53, 0.06);
  border-left: 2px solid var(--color-primary);
  padding-left: 8px;
}

.conv-avatar {
  position: relative;
  width: 30px;
  height: 30px;
  border-radius: 50%;
  background: var(--color-bg-gray, #f0f2f5);
  color: var(--color-text-secondary);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.conv-online {
  position: absolute;
  right: 0;
  bottom: 0;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #ccc;
  border: 2px solid #fff;
}

.conv-online.on {
  background: #52c41a;
}

.conv-main {
  flex: 1;
  min-width: 0;
}

.conv-top-row {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  margin-bottom: 2px;
}

.conv-name {
  font-size: 12px;
  font-weight: 600;
  color: var(--color-text);
}

.conv-time {
  font-size: 10px;
  color: var(--color-text-placeholder);
  flex-shrink: 0;
}

.conv-preview-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 6px;
}

.conv-preview {
  flex: 1;
  min-width: 0;
  font-size: 12px;
  color: var(--color-text-secondary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.conv-unread {
  min-width: 14px;
  height: 14px;
  padding: 0 4px;
  border-radius: 7px;
  background: #ff4d4f;
  color: #fff;
  font-size: 9px;
  line-height: 14px;
  text-align: center;
  flex-shrink: 0;
}

.conv-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 32px 0;
  color: var(--color-text-placeholder);
}

.conv-empty i {
  font-size: 24px;
  line-height: 1;
  margin-bottom: 6px;
}

/* ==================== 右侧聊天窗口 ==================== */
.chat-panel {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.chat-head {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  border-bottom: 1px solid var(--color-border);
}

.chat-user {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text);
}

.chat-user-status {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 11px;
  color: var(--color-text-placeholder);
  background: var(--color-bg-gray, #f5f5f5);
  padding: 2px 8px;
  border-radius: 10px;
}

.chat-user-status.online {
  color: #52c41a;
  background: rgba(82, 196, 26, 0.1);
}

.chat-user-status .dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: currentColor;
}

.chat-body {
  flex: 1;
  overflow-y: auto;
  padding: 10px 12px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  background: var(--color-bg, #fafafa);
}

.load-earlier {
  align-self: center;
  font-size: 11px;
  color: var(--color-text-secondary);
  background: #fff;
  border: 1px solid var(--color-border);
  border-radius: 12px;
  padding: 3px 10px;
}

.chat-row {
  display: flex;
  align-items: flex-start;
  gap: 6px;
}

/* 我的消息靠右：整行右对齐，DOM 顺序已为 [气泡][头像] → 头像在右、气泡在左 */
.chat-row.mine {
  justify-content: flex-end;
}

.chat-avatar {
  width: 26px;
  height: 26px;
  border-radius: 50%;
  background: var(--color-bg-gray, #f0f2f5);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.admin-avatar {
  background: var(--gradient-primary, linear-gradient(135deg, #ff6b35, #ff9558));
}

.admin-avatar i {
  color: #fff;
}

.chat-bubble-wrap {
  max-width: 64%;
  display: flex;
  flex-direction: column;
}

.chat-row.mine .chat-bubble-wrap {
  align-items: flex-end;
}

.chat-bubble {
  padding: 6px 10px;
  border-radius: 10px;
  font-size: 12px;
  line-height: 1.5;
  word-break: break-word;
  white-space: pre-wrap;
  background: #fff;
  color: var(--color-text);
  border: 1px solid var(--color-border);
  border-top-left-radius: 4px;
}

.chat-row.mine .chat-bubble {
  background: var(--gradient-primary, linear-gradient(135deg, #ff6b35, #ff9558));
  color: #fff;
  border: none;
  border-top-left-radius: 10px;
  border-top-right-radius: 4px;
}

.chat-time {
  font-size: 10px;
  color: var(--color-text-placeholder);
  margin-top: 3px;
  padding: 0 4px;
}

.chat-empty,
.chat-placeholder {
  margin: auto;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  text-align: center;
  color: var(--color-text-placeholder);
}

.chat-empty i,
.chat-placeholder i {
  width: 28px;
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28px;
  line-height: 1;
  margin: 0;
  padding: 0;
}

.chat-empty p,
.chat-placeholder p {
  margin: 0;
  font-size: 12px;
  line-height: 1.5;
}

/* ==================== 输入区 ==================== */
.input-bar {
  display: flex;
  align-items: center;
  gap: var(--spacing-xs);
  padding: 8px 10px;
  border-top: 1px solid var(--color-border);
}

.chat-input {
  flex: 1;
  height: 28px;
  padding: 0 10px;
  border: 1px solid var(--color-border);
  border-radius: 14px;
  font-size: 12px;
  outline: none;
  background: var(--color-bg-gray, #f7f8fa);
}

.chat-input:focus {
  border-color: var(--color-primary);
  background: #fff;
}

.send-btn {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: var(--gradient-primary, linear-gradient(135deg, #ff6b35, #ff9558));
  color: #fff;
  font-size: 11px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  transition: transform 0.15s, opacity 0.15s;
}

.send-btn:active {
  transform: scale(0.92);
}

.send-btn:disabled {
  opacity: 0.5;
}
</style>
