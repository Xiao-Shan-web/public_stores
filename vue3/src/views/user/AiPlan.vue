<template>
  <div class="ai-page">
    <!-- 顶部：山达 AI 私教 -->
    <div class="ai-header">
      <div class="hd-left">
        <div class="ai-avatar">
          <i class="fas fa-robot"></i>
        </div>
        <div class="ai-meta">
          <p class="ai-name">山达 AI 私教</p>
          <p class="ai-status"><span class="dot"></span>在线</p>
        </div>
      </div>
      <div class="hd-actions">
        <button class="hd-btn" title="历史计划" @click="openHistory">
          <i class="fas fa-clock-rotate-left"></i>
        </button>
        <button class="hd-btn" title="重新开始" @click="restart">
          <i class="fas fa-rotate-right"></i>
        </button>
      </div>
    </div>

    <!-- AI 训练 / 客服入口 -->
    <router-link to="/user/ai-assistant" class="ai-entry">
      <span class="ai-entry-icon"><i class="fas fa-dumbbell"></i></span>
      <span class="ai-entry-text">
        <span class="ai-entry-title">AI 训练计划 · 智能客服</span>
        <span class="ai-entry-sub">生成一周训练动作，常见问题即时解答</span>
      </span>
      <i class="fas fa-chevron-right ai-entry-arrow"></i>
    </router-link>

    <!-- 中间：对话流 -->
    <div class="chat-area" ref="chatAreaRef">
      <div
        v-for="m in msgList"
        :key="m.id"
        class="chat-row"
        :class="{ mine: m.role === 'user' }"
      >
        <div v-if="m.role === 'ai'" class="chat-avatar">
          <i class="fas fa-robot"></i>
        </div>
        <div class="chat-bubble-wrap">
          <div v-if="m.text" class="chat-bubble">{{ m.text }}</div>

          <!-- 计划卡片 -->
          <div v-if="m.plan" class="plan-card">
            <p class="plan-badge"><i class="fas fa-magic"></i> 您的专属饮食计划</p>
            <div class="plan-calories">
              <div class="pc-main">
                <p class="pc-num">{{ m.plan.dailyCalories }}</p>
                <p class="pc-unit">kcal / 日</p>
              </div>
              <div class="pc-macros">
                <div class="pc-macro"><p class="v">{{ m.plan.protein }}g</p><p class="l">蛋白质</p></div>
                <div class="pc-macro"><p class="v">{{ m.plan.carbs }}g</p><p class="l">碳水</p></div>
                <div class="pc-macro"><p class="v">{{ m.plan.fat }}g</p><p class="l">脂肪</p></div>
              </div>
            </div>
            <div class="plan-meals">
              <div class="meal" v-for="(meal, i) in m.plan.meals" :key="i">
                <div class="meal-head">
                  <span class="meal-type">{{ meal.type }}</span>
                  <span class="meal-cal">{{ meal.calories }} kcal</span>
                </div>
                <div class="meal-foods">
                  <span class="food" v-for="(f, j) in meal.foods" :key="j">{{ f }}</span>
                </div>
              </div>
            </div>
            <button class="plan-save" :disabled="m.saved || savingId === m.id" @click="handleSave(m)">
              <i :class="['fas', m.saved ? 'fa-check' : 'fa-bookmark']"></i>
              {{ m.saved ? '已保存' : (savingId === m.id ? '保存中...' : '保存计划') }}
            </button>
          </div>

          <span class="chat-time">{{ m.time }}</span>
        </div>
        <div v-if="m.role === 'user'" class="chat-avatar mine-avatar">
          <i class="fas fa-user"></i>
        </div>
      </div>

      <!-- 正在输入 -->
      <div v-if="typing" class="chat-row">
        <div class="chat-avatar">
          <i class="fas fa-robot"></i>
        </div>
        <div class="chat-bubble typing-bubble" :class="{ 'typing-text': typingText }">
          <span v-if="typingText" class="typing-label">{{ typingText }}</span>
          <template v-else><span></span><span></span><span></span></template>
        </div>
      </div>
    </div>

    <!-- 快捷回复 -->
    <div v-if="chips.length && !sending" class="chips-bar">
      <button v-for="c in chips" :key="c" class="chip" @click="sendQuick(c)">{{ c }}</button>
    </div>

    <!-- 底部：输入框 -->
    <div class="input-bar">
      <input
        v-model="draft"
        class="chat-input"
        type="text"
        maxlength="200"
        placeholder="和 AI 私教聊聊，比如体重、目标…"
        @keyup.enter="handleSend"
      />
      <button class="send-btn" :disabled="!draft.trim() || sending" @click="handleSend">
        <i class="fas fa-paper-plane"></i>
      </button>
    </div>

    <!-- 历史计划弹窗 -->
    <div v-if="showHistory" class="modal-mask" @click.self="showHistory = false">
      <div class="modal-card">
        <div class="modal-header">
          <h3>{{ historyDetail ? '计划详情' : '历史计划' }}</h3>
          <i class="fas fa-times modal-close" @click="showHistory = false"></i>
        </div>
        <div class="modal-body">
          <!-- 详情 -->
          <template v-if="historyDetail">
            <button class="back-btn" @click="historyDetail = null">
              <i class="fas fa-arrow-left"></i> 返回列表
            </button>
            <div class="plan-card in-modal">
              <p class="plan-badge"><i class="fas fa-magic"></i> 专属饮食计划</p>
              <div class="plan-calories">
                <div class="pc-main">
                  <p class="pc-num">{{ historyDetail.dailyCalories }}</p>
                  <p class="pc-unit">kcal / 日</p>
                </div>
                <div class="pc-macros">
                  <div class="pc-macro"><p class="v">{{ historyDetail.protein }}g</p><p class="l">蛋白质</p></div>
                  <div class="pc-macro"><p class="v">{{ historyDetail.carbs }}g</p><p class="l">碳水</p></div>
                  <div class="pc-macro"><p class="v">{{ historyDetail.fat }}g</p><p class="l">脂肪</p></div>
                </div>
              </div>
              <div class="plan-meals">
                <div class="meal" v-for="(meal, i) in historyDetail.meals" :key="i">
                  <div class="meal-head">
                    <span class="meal-type">{{ meal.type }}</span>
                    <span class="meal-cal">{{ meal.calories }} kcal</span>
                  </div>
                  <div class="meal-foods">
                    <span class="food" v-for="(f, j) in meal.foods" :key="j">{{ f }}</span>
                  </div>
                </div>
              </div>
            </div>
          </template>

          <!-- 列表 -->
          <template v-else>
            <div v-if="!historyList.length" class="history-empty">
              <i class="fas fa-folder-open"></i>
              <p>还没有保存过计划哦</p>
            </div>
            <div
              class="history-item"
              v-for="h in historyList"
              :key="h.id"
              @click="openDetail(h.id)"
            >
              <div class="hist-info">
                <p class="hist-title">{{ goalLabel(h.goal) }} · {{ h.dailyCalories }} kcal</p>
                <p class="hist-time">{{ formatTime(h.createdAt) }}</p>
              </div>
              <i class="fas fa-chevron-right hist-arrow"></i>
            </div>
          </template>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, nextTick, computed, onMounted } from 'vue'
import {
  aiPlanAPI,
  type ChatPlanData,
  type ChatStep,
  type PlanHistoryItem,
  type PlanDetail
} from '@/api/aiPlanAPI'
import message from '@/utils/message'

// keep-alive 缓存按组件 name 匹配
defineOptions({ name: 'UserAiPlan' })

interface ChatMsg {
  id: number
  role: 'ai' | 'user'
  text?: string
  plan?: ChatPlanData
  saved?: boolean
  time: string
}

let msgSeq = 1
const msgList = ref<ChatMsg[]>([])
const chatAreaRef = ref<HTMLElement | null>(null)
const draft = ref('')
const sending = ref(false)
const typing = ref(false)
/** 正在输入气泡的文案（为空时显示三点动画，有值时显示文字如"正在帮你算热量…"） */
const typingText = ref('')
const currentStep = ref<ChatStep>('WEIGHT')
const sessionId = ref('')
const savingId = ref<number | null>(null)

// ==================== 随机欢迎语 ====================

const WELCOME_TEMPLATES = [
  '欢迎来到山达健身，我是您的 AI 私教~',
  '嗨，我是山达 AI 私教，专门管您"吃"这件事哈哈',
  '来啦？我们一起打造您的专属饮食计划吧~',
  '您好呀，我是您的专属 AI 私教，准备好了吗？'
]

const WEIGHT_ASK_TEMPLATES = [
  '今天想怎么练？先告诉我您的体重吧？',
  '我们先从体重基数开始吧~您多少公斤？',
  '第一步：体重多少公斤呀？直接说数字就行~'
]

const pick = <T,>(arr: T[]): T => arr[Math.floor(Math.random() * arr.length)]
const sleep = (ms: number) => new Promise(r => setTimeout(r, ms))
const nowTime = () => {
  const d = new Date()
  return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
}
const genSessionId = () =>
  typeof crypto !== 'undefined' && crypto.randomUUID
    ? crypto.randomUUID().replace(/-/g, '')
    : `${Date.now()}${Math.floor(Math.random() * 1e6)}`

// ==================== 消息 ====================

const scrollToBottom = () => {
  nextTick(() => {
    if (chatAreaRef.value) {
      chatAreaRef.value.scrollTop = chatAreaRef.value.scrollHeight
    }
  })
}

const pushAi = (text?: string, plan?: ChatPlanData) => {
  msgList.value.push({ id: msgSeq++, role: 'ai', text, plan, time: nowTime() })
  scrollToBottom()
}

const sendWelcome = () => {
  sessionId.value = genSessionId()
  currentStep.value = 'WEIGHT'
  pushAi(pick(WELCOME_TEMPLATES))
  setTimeout(() => {
    if (msgList.value.length <= 1) pushAi(pick(WEIGHT_ASK_TEMPLATES))
  }, 900)
}

const restart = () => {
  if (sending.value) return
  msgList.value = []
  sendWelcome()
}

// ==================== 发送 ====================

const handleSend = () => {
  const text = draft.value.trim()
  if (!text || sending.value) return
  draft.value = ''
  requestChat(text)
}

const sendQuick = (label: string) => {
  if (sending.value) return
  requestChat(label)
}

const requestChat = async (text: string) => {
  sending.value = true
  msgList.value.push({ id: msgSeq++, role: 'user', text, time: nowTime() })
  scrollToBottom()

  typing.value = true
  typingText.value = ''
  scrollToBottom()

  try {
    const res = await aiPlanAPI.chat({ sessionId: sessionId.value, message: text })

    if (!res.success || !res.data) {
      typing.value = false
      pushAi('哎呀，我这边走神了一下，再发一次好吗？')
      return
    }
    const data = res.data
    sessionId.value = data.sessionId

    // 生成计划时先显示"正在帮你算热量…"，模拟教练在计算
    if (data.status === 'PLAN_READY' && data.planData) {
      typingText.value = '正在帮你算热量…'
      await sleep(800 + Math.random() * 500)
      typing.value = false
      typingText.value = ''
      pushAi(data.reply)
      await sleep(600 + Math.random() * 400)
      pushAi(undefined, data.planData)
      currentStep.value = 'DONE'
    } else {
      // 普通对话：短暂"正在输入"后回复
      await sleep(300 + Math.random() * 400)
      typing.value = false
      pushAi(data.reply)
      currentStep.value = data.step || 'WEIGHT'
    }
  } catch {
    typing.value = false
    typingText.value = ''
    pushAi('网络好像不太稳，稍后再试试~')
  } finally {
    sending.value = false
  }
}

// ==================== 快捷回复 ====================

const CHIP_OPTIONS: Partial<Record<ChatStep, string[]>> = {
  GENDER: ['男', '女'],
  GOAL: ['增肌', '减脂', '保持'],
  ACTIVITY: ['久坐不动', '偶尔运动', '中等运动量', '高强度训练']
}
const chips = computed(() => CHIP_OPTIONS[currentStep.value] || [])

// ==================== 保存计划 ====================

const handleSave = async (m: ChatMsg) => {
  if (!m.plan || m.saved || savingId.value !== null) return
  savingId.value = m.id
  try {
    const res = await aiPlanAPI.save({
      height: m.plan.height,
      weight: m.plan.weight,
      age: m.plan.age,
      gender: m.plan.gender,
      goal: m.plan.goal,
      dailyCalories: m.plan.dailyCalories,
      protein: m.plan.protein,
      carbs: m.plan.carbs,
      fat: m.plan.fat,
      meals: m.plan.meals
    })
    if (res.success) {
      m.saved = true
      pushAi('计划已经存进您的历史记录啦，随时可以回看~')
    } else {
      message.error(res.message || '保存失败')
    }
  } catch (e: any) {
    message.error(e.message || '保存失败')
  } finally {
    savingId.value = null
  }
}

// ==================== 历史计划 ====================

const showHistory = ref(false)
const historyList = ref<PlanHistoryItem[]>([])
const historyDetail = ref<PlanDetail | null>(null)

const openHistory = async () => {
  showHistory.value = true
  historyDetail.value = null
  try {
    const res = await aiPlanAPI.list({ page: 1, size: 10 })
    if (res.success) historyList.value = res.data.list || []
  } catch {
    /* 静默 */
  }
}

const openDetail = async (id: number) => {
  try {
    const res = await aiPlanAPI.detail(id)
    if (res.success) {
      historyDetail.value = res.data
    } else {
      message.error(res.message || '加载失败')
    }
  } catch (e: any) {
    message.error(e.message || '加载失败')
  }
}

const goalLabel = (g: string) => {
  const map: Record<string, string> = {
    MUSCLE_GAIN: '增肌',
    FAT_LOSS: '减脂',
    MAINTAIN: '保持'
  }
  return map[g] || g
}

const formatTime = (t: string) => {
  if (!t) return ''
  return t.replace('T', ' ').substring(0, 16)
}

onMounted(() => {
  sendWelcome()
})
</script>

<style scoped>
.ai-page {
  height: 100%;
  display: flex;
  flex-direction: column;
  background: var(--color-bg, #f7f8fa);
  overflow: hidden;
}

/* ==================== 顶部 ==================== */
.ai-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px var(--spacing-md);
  background: var(--gradient-primary);
  color: #fff;
  flex-shrink: 0;
}

.hd-left {
  display: flex;
  align-items: center;
}

.ai-avatar {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.22);
  display: flex;
  align-items: center;
  justify-content: center;
  margin-right: 10px;
  flex-shrink: 0;
}

.ai-avatar i {
  color: #fff;
  font-size: 17px;
}

.ai-name {
  font-size: 15px;
  font-weight: 700;
}

.ai-status {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 11px;
  opacity: 0.9;
  margin-top: 2px;
}

.ai-status .dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #52e06d;
}

.hd-actions {
  display: flex;
  gap: 6px;
}

.hd-btn {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  border: none;
  background: rgba(255, 255, 255, 0.2);
  color: #fff;
  font-size: 13px;
  cursor: pointer;
  transition: transform 0.15s;
}

.hd-btn:active {
  transform: scale(0.9);
}

/* ==================== AI 训练/客服入口 ==================== */
.ai-entry {
  display: flex;
  align-items: center;
  gap: 10px;
  margin: 0 var(--spacing-md) 8px;
  padding: 10px 12px;
  background: linear-gradient(135deg, rgba(255, 106, 0, 0.1), rgba(255, 154, 60, 0.06));
  border: 1px solid rgba(255, 106, 0, 0.25);
  border-radius: 12px;
  text-decoration: none;
}
.ai-entry-icon {
  width: 36px;
  height: 36px;
  border-radius: 10px;
  background: var(--color-primary, #ff6a00);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 16px;
  flex-shrink: 0;
}
.ai-entry-text { display: flex; flex-direction: column; gap: 2px; flex: 1; min-width: 0; }
.ai-entry-title { font-size: 13.5px; font-weight: 700; color: var(--color-text, #222); }
.ai-entry-sub { font-size: 11.5px; color: var(--color-text-secondary, #888); }
.ai-entry-arrow { color: var(--color-primary, #ff6a00); font-size: 12px; }

/* ==================== 对话流 ==================== */
.chat-area {
  flex: 1;
  overflow-y: auto;
  padding: var(--spacing-md);
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.chat-row {
  display: flex;
  align-items: flex-start;
  gap: 8px;
}

.chat-row.mine {
  flex-direction: row-reverse;
}

.chat-avatar {
  width: 34px;
  height: 34px;
  border-radius: 50%;
  background: var(--gradient-primary);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.chat-avatar i {
  color: #fff;
  font-size: 14px;
}

.mine-avatar {
  background: linear-gradient(135deg, #1890ff, #4aa9ff);
}

.chat-bubble-wrap {
  max-width: 78%;
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
  background: var(--gradient-primary);
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

/* 正在输入 */
.typing-bubble {
  display: flex;
  align-items: center;
  gap: 4px;
  min-height: 22px;
}

.typing-bubble.typing-text {
  padding: 9px 14px;
  font-size: 13px;
  color: var(--color-text-secondary);
  background: #f5f5f5;
}

.typing-label {
  white-space: nowrap;
}

.typing-bubble > span:not(.typing-label) {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 12px 14px;
}

.typing-bubble > span:not(.typing-label) {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--color-text-placeholder);
  animation: typing 1.2s infinite ease-in-out;
}

.typing-bubble > span:not(.typing-label):nth-child(2) {
  animation-delay: 0.15s;
}

.typing-bubble > span:not(.typing-label):nth-child(3) {
  animation-delay: 0.3s;
}

@keyframes typing {
  0%, 60%, 100% { transform: translateY(0); opacity: 0.4; }
  30% { transform: translateY(-4px); opacity: 1; }
}

/* ==================== 计划卡片 ==================== */
.plan-card {
  width: 280px;
  max-width: 100%;
  background: #fff;
  border-radius: 14px;
  border-top-left-radius: 4px;
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.06);
  padding: 12px;
  margin-top: 2px;
}

.plan-badge {
  font-size: 13px;
  font-weight: 700;
  color: var(--color-primary);
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 10px;
}

.plan-calories {
  background: var(--gradient-primary);
  border-radius: 10px;
  padding: 10px;
  color: #fff;
  margin-bottom: 10px;
}

.pc-main {
  text-align: center;
  margin-bottom: 8px;
}

.pc-num {
  font-size: 28px;
  font-weight: 700;
  line-height: 1.2;
}

.pc-unit {
  font-size: 11px;
  opacity: 0.9;
}

.pc-macros {
  display: flex;
  justify-content: space-around;
  border-top: 1px solid rgba(255, 255, 255, 0.3);
  padding-top: 8px;
}

.pc-macro {
  text-align: center;
}

.pc-macro .v {
  font-size: 14px;
  font-weight: 700;
}

.pc-macro .l {
  font-size: 11px;
  opacity: 0.9;
  margin-top: 1px;
}

.plan-meals {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.meal {
  border: 1px solid var(--color-border, #eee);
  border-radius: 8px;
  padding: 8px 10px;
}

.meal-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 6px;
}

.meal-type {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text);
}

.meal-cal {
  font-size: 11px;
  color: var(--color-primary);
  font-weight: 600;
}

.meal-foods {
  display: flex;
  flex-wrap: wrap;
  gap: 5px;
}

.food {
  background: var(--color-bg-gray, #f7f8fa);
  color: var(--color-text-secondary);
  font-size: 11px;
  padding: 3px 7px;
  border-radius: 9px;
}

.plan-save {
  width: 100%;
  margin-top: 10px;
  background: #fff;
  color: var(--color-primary);
  border: 1px solid var(--color-primary);
  border-radius: 8px;
  padding: 9px;
  font-size: 13px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
}

.plan-save:disabled {
  opacity: 0.55;
}

.plan-card.in-modal {
  width: 100%;
  border-top-left-radius: 14px;
  margin-top: 8px;
}

/* ==================== 快捷回复 ==================== */
.chips-bar {
  display: flex;
  gap: 8px;
  padding: 8px var(--spacing-md) 0;
  overflow-x: auto;
  flex-shrink: 0;
}

.chip {
  flex-shrink: 0;
  background: #fff;
  border: 1px solid var(--color-primary);
  color: var(--color-primary);
  font-size: 13px;
  padding: 6px 14px;
  border-radius: 16px;
  cursor: pointer;
  transition: background 0.15s;
}

.chip:active {
  background: rgba(255, 107, 26, 0.08);
}

/* ==================== 输入区域 ==================== */
.input-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px var(--spacing-md);
  background: #fff;
  border-top: 1px solid var(--color-border, #eee);
  flex-shrink: 0;
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
  background: var(--gradient-primary);
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

/* ==================== 历史弹窗 ==================== */
.modal-mask {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
  padding: var(--spacing-md);
}

.modal-card {
  width: 100%;
  max-width: 420px;
  max-height: 80vh;
  background: #fff;
  border-radius: var(--radius-lg, 12px);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.modal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: var(--spacing-md) var(--spacing-lg);
  border-bottom: 1px solid var(--color-border, #eee);
}

.modal-header h3 {
  font-size: 16px;
  font-weight: 700;
}

.modal-close {
  color: var(--color-text-placeholder);
  font-size: 18px;
  cursor: pointer;
}

.modal-body {
  padding: var(--spacing-md) var(--spacing-lg);
  overflow-y: auto;
  flex: 1;
}

.back-btn {
  background: none;
  border: none;
  color: var(--color-text-secondary);
  font-size: 13px;
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 0;
  margin-bottom: 4px;
  cursor: pointer;
}

.history-empty {
  text-align: center;
  color: var(--color-text-placeholder);
  padding: 30px 0;
}

.history-empty i {
  font-size: 34px;
  margin-bottom: 8px;
  display: block;
}

.history-empty p {
  font-size: 13px;
}

.history-item {
  display: flex;
  align-items: center;
  padding: 13px 0;
  border-bottom: 1px solid var(--color-border, #eee);
  cursor: pointer;
}

.history-item:last-child {
  border-bottom: none;
}

.hist-info {
  flex: 1;
}

.hist-title {
  font-size: 14px;
  color: var(--color-text);
  font-weight: 500;
}

.hist-time {
  font-size: 12px;
  color: var(--color-text-placeholder);
  margin-top: 2px;
}

.hist-arrow {
  color: var(--color-text-placeholder);
  font-size: 12px;
}
</style>
