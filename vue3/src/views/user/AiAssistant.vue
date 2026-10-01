<template>
  <div class="ai-assistant page-container">
    <PageHeader title="AI 助手" />

    <div class="page-body">
      <!-- 顶部能力切换 -->
      <div class="seg-tabs">
        <button class="seg-tab" :class="{ active: tab === 'train' }" @click="tab = 'train'">
          <i class="fas fa-dumbbell"></i> 训练计划
        </button>
        <button class="seg-tab" :class="{ active: tab === 'service' }" @click="tab = 'service'">
          <i class="fas fa-headset"></i> 智能客服
        </button>
      </div>

      <!-- ==================== 训练计划 ==================== -->
      <template v-if="tab === 'train'">
        <div class="card form-card">
          <p class="form-title">告诉我你的情况，生成一周训练安排</p>

          <div class="field">
            <p class="field-label">训练目标</p>
            <div class="opt-row">
              <button
                v-for="g in GOAL_OPTS" :key="g.value"
                class="opt" :class="{ active: form.goal === g.value }"
                @click="form.goal = g.value"
              >{{ g.label }}</button>
            </div>
          </div>

          <div class="field">
            <p class="field-label">训练水平</p>
            <div class="opt-row">
              <button
                v-for="l in LEVEL_OPTS" :key="l.value"
                class="opt" :class="{ active: form.level === l.value }"
                @click="form.level = l.value"
              >{{ l.label }}</button>
            </div>
          </div>

          <div class="field">
            <p class="field-label">器械条件</p>
            <div class="opt-row">
              <button
                v-for="e in EQUIP_OPTS" :key="e.value"
                class="opt" :class="{ active: form.equipment === e.value }"
                @click="form.equipment = e.value"
              >{{ e.label }}</button>
            </div>
          </div>

          <div class="field">
            <p class="field-label">每周训练天数 <span class="field-value">{{ form.daysPerWeek }} 天</span></p>
            <div class="opt-row">
              <button
                v-for="d in [3, 4, 5, 6]" :key="d"
                class="opt opt-num" :class="{ active: form.daysPerWeek === d }"
                @click="form.daysPerWeek = d"
              >{{ d }}</button>
            </div>
          </div>

          <button class="generate-btn" :disabled="training" @click="handleGenerate">
            <i :class="['fas', training ? 'fa-spinner fa-spin' : 'fa-wand-magic-sparkles']"></i>
            {{ training ? '正在为你编排...' : (plan ? '重新生成计划' : '生成训练计划') }}
          </button>
        </div>

        <div v-if="plan" class="plan-result">
          <div class="card summary-card">
            <div class="summary-head">
              <p class="summary-title">
                <i class="fas fa-clipboard-list"></i> 你的一周训练计划
              </p>
              <span class="provider-badge" :class="plan.provider">
                {{ plan.provider === 'qianfan' ? 'AI 个性化' : '智能编排' }}
              </span>
            </div>
            <p class="summary-text">{{ plan.summary }}</p>
          </div>

          <div v-for="(d, i) in plan.days" :key="i" class="card day-card">
            <div class="day-head">
              <span class="day-name">{{ d.day }}</span>
              <span class="day-focus">{{ d.focus }}</span>
            </div>

            <div class="phase">
              <p class="phase-label"><i class="fas fa-fire"></i> 热身</p>
              <p class="phase-items">{{ d.warmup.join('；') }}</p>
            </div>

            <div class="ex-list">
              <div v-for="(ex, j) in d.exercises" :key="j" class="ex-item">
                <div class="ex-main">
                  <span class="ex-name">{{ ex.name }}</span>
                  <span v-if="ex.note" class="ex-note">{{ ex.note }}</span>
                </div>
                <div class="ex-meta">
                  <span class="ex-tag">{{ ex.sets }} 组</span>
                  <span class="ex-tag">{{ ex.reps }}</span>
                  <span class="ex-tag rest">休 {{ ex.rest }}</span>
                </div>
              </div>
            </div>

            <div class="phase">
              <p class="phase-label"><i class="fas fa-person-walking"></i> 拉伸放松</p>
              <p class="phase-items">{{ d.cooldown.join('；') }}</p>
            </div>
          </div>

          <div class="card tips-card">
            <p class="tips-title"><i class="fas fa-circle-info"></i> 执行建议</p>
            <ul>
              <li v-for="(t, i) in plan.tips" :key="i">{{ t }}</li>
            </ul>
          </div>
        </div>
      </template>

      <!-- ==================== 智能客服 ==================== -->
      <template v-else>
        <div class="service-wrap">
          <div class="chat-stream" ref="streamRef">
            <div
              v-for="m in msgs" :key="m.id"
              class="msg-row" :class="{ mine: m.role === 'user' }"
            >
              <div v-if="m.role === 'assistant'" class="msg-avatar">
                <i class="fas fa-headset"></i>
              </div>
              <div class="msg-body">
                <div class="msg-bubble">{{ m.content }}</div>
                <span v-if="m.source" class="msg-src" :class="m.source">{{ sourceText(m.source) }}</span>
              </div>
            </div>
            <div v-if="asking" class="msg-row">
              <div class="msg-avatar"><i class="fas fa-headset"></i></div>
              <div class="msg-bubble typing"><span></span><span></span><span></span></div>
            </div>
          </div>

          <div v-if="suggested.length" class="suggest-bar">
            <button
              v-for="(s, i) in suggested" :key="i"
              class="suggest-chip" :disabled="asking"
              @click="send(s)"
            >{{ s }}</button>
          </div>

          <div class="input-bar">
            <input
              v-model="draft"
              type="text"
              maxlength="300"
              placeholder="请输入你的问题，如：会员卡怎么激活？"
              @keyup.enter="send(draft)"
            />
            <button class="send-btn" :disabled="asking || !draft.trim()" @click="send(draft)">
              <i class="fas fa-paper-plane"></i>
            </button>
          </div>
        </div>
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref, nextTick } from 'vue'
import PageHeader from '@/components/PageHeader.vue'
import { aiAssistantAPI } from '@/api/aiAssistantAPI'
import type {
  TrainGoal, TrainLevel, TrainEquipment, TrainPlan, ChatMsg, CustomerSource
} from '@/api/aiAssistantAPI'

const tab = ref<'train' | 'service'>('train')

// ==================== 训练计划 ====================
const GOAL_OPTS: { value: TrainGoal; label: string }[] = [
  { value: 'MUSCLE_GAIN', label: '增肌' },
  { value: 'FAT_LOSS', label: '减脂' },
  { value: 'SHAPE', label: '塑形保持' }
]
const LEVEL_OPTS: { value: TrainLevel; label: string }[] = [
  { value: 'BEGINNER', label: '初学者' },
  { value: 'INTERMEDIATE', label: '有基础' }
]
const EQUIP_OPTS: { value: TrainEquipment; label: string }[] = [
  { value: 'GYM', label: '健身房' },
  { value: 'DUMBBELL', label: '仅哑铃' },
  { value: 'HOME', label: '居家徒手' }
]

const form = reactive({
  goal: 'FAT_LOSS' as TrainGoal,
  level: 'BEGINNER' as TrainLevel,
  equipment: 'GYM' as TrainEquipment,
  daysPerWeek: 4
})
const training = ref(false)
const plan = ref<TrainPlan | null>(null)

const handleGenerate = async () => {
  if (training.value) return
  training.value = true
  try {
    const res = await aiAssistantAPI.generateTraining({ ...form })
    if (res.success && res.data) {
      plan.value = res.data
      await nextTick()
      // 移动端滚动到结果区
      const el = document.querySelector('.plan-result')
      el?.scrollIntoView({ behavior: 'smooth', block: 'start' })
    }
  } catch {
    /* 拦截器已统一提示 */
  } finally {
    training.value = false
  }
}

// ==================== 智能客服 ====================
const msgs = ref<ChatMsg[]>([])
const draft = ref('')
const asking = ref(false)
const suggested = ref<string[]>([])
const streamRef = ref<HTMLElement | null>(null)
let seq = 0

const sourceText = (s?: CustomerSource) => {
  if (s === 'FAQ') return '常见问题'
  if (s === 'AI') return '智能助手'
  return '建议转人工'
}

const pushAssistant = (content: string, source?: CustomerSource) => {
  msgs.value.push({ id: ++seq, role: 'assistant', content, source })
}

const scrollBottom = () => {
  nextTick(() => {
    const el = streamRef.value
    if (el) el.scrollTop = el.scrollHeight
  })
}

const send = async (raw?: string) => {
  const text = (raw ?? draft.value).trim()
  if (!text || asking.value) return
  msgs.value.push({ id: ++seq, role: 'user', content: text })
  draft.value = ''
  asking.value = true
  suggested.value = []
  scrollBottom()
  try {
    const res = await aiAssistantAPI.customerAsk(text)
    if (res.success && res.data) {
      pushAssistant(res.data.answer, res.data.source)
    } else {
      pushAssistant('我暂时没连上服务，请稍后再试～', 'HANDOFF')
    }
  } catch {
    pushAssistant('网络好像开小差了，稍后再问我一次，或直接联系人工客服。', 'HANDOFF')
  } finally {
    asking.value = false
    scrollBottom()
  }
}

onMounted(async () => {
  pushAssistant('你好，我是山达健身智能助手～办卡、激活、刷脸进门、优惠券等问题都可以问我。')
  try {
    const res = await aiAssistantAPI.customerSuggested()
    if (res.success && res.data?.list) suggested.value = res.data.list
  } catch {
    /* 推荐问题非关键 */
  }
})
</script>

<style scoped>
.ai-assistant { min-height: 100vh; background: var(--color-bg-gray, #f5f6f8); }
.page-body { padding: 12px 14px 24px; }

.seg-tabs {
  display: flex;
  background: #fff;
  border-radius: var(--radius-sm, 10px);
  padding: 4px;
  margin-bottom: 12px;
}
.seg-tab {
  flex: 1;
  border: none;
  background: transparent;
  padding: 10px 0;
  border-radius: 8px;
  font-size: 14px;
  color: var(--color-text-secondary, #666);
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
}
.seg-tab.active {
  background: var(--color-primary, #ff6a00);
  color: #fff;
  font-weight: 600;
}

.card {
  background: #fff;
  border-radius: var(--radius-sm, 10px);
  padding: 14px;
  margin-bottom: 12px;
}

/* 表单 */
.form-title { font-size: 15px; font-weight: 700; margin-bottom: 14px; }
.field { margin-bottom: 14px; }
.field-label {
  font-size: 13px;
  color: var(--color-text-secondary, #666);
  margin-bottom: 8px;
  display: flex;
  justify-content: space-between;
}
.field-value { color: var(--color-primary, #ff6a00); font-weight: 600; }
.opt-row { display: flex; flex-wrap: wrap; gap: 8px; }
.opt {
  border: 1px solid var(--color-border, #e5e5e5);
  background: #fff;
  border-radius: 18px;
  padding: 7px 16px;
  font-size: 13px;
  color: var(--color-text, #333);
}
.opt-num { min-width: 48px; }
.opt.active {
  border-color: var(--color-primary, #ff6a00);
  background: rgba(255, 106, 0, 0.08);
  color: var(--color-primary, #ff6a00);
  font-weight: 600;
}
.generate-btn {
  width: 100%;
  border: none;
  border-radius: 22px;
  padding: 12px 0;
  background: var(--color-primary, #ff6a00);
  color: #fff;
  font-size: 15px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
}
.generate-btn:disabled { opacity: 0.7; }

/* 训练结果 */
.summary-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 8px; }
.summary-title { font-size: 15px; font-weight: 700; display: flex; align-items: center; gap: 6px; }
.provider-badge { font-size: 11px; padding: 2px 8px; border-radius: 10px; }
.provider-badge.qianfan { background: #f0e6ff; color: #722ed1; }
.provider-badge.local { background: #eef6ff; color: #1677ff; }
.summary-text { font-size: 13px; color: var(--color-text-secondary, #555); line-height: 1.7; }

.day-head {
  display: flex;
  align-items: center;
  gap: 10px;
  padding-bottom: 10px;
  margin-bottom: 10px;
  border-bottom: 1px dashed var(--color-border, #eee);
}
.day-name {
  background: var(--color-primary, #ff6a00);
  color: #fff;
  font-size: 12px;
  font-weight: 600;
  padding: 3px 10px;
  border-radius: 10px;
}
.day-focus { font-size: 14px; font-weight: 600; }

.phase { margin: 10px 0; }
.phase-label { font-size: 12px; color: var(--color-primary, #ff6a00); margin-bottom: 4px; }
.phase-items { font-size: 12.5px; color: var(--color-text-secondary, #666); line-height: 1.6; }

.ex-list { display: flex; flex-direction: column; gap: 10px; }
.ex-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  background: var(--color-bg-gray, #f7f8fa);
  border-radius: 8px;
  padding: 10px 12px;
}
.ex-main { display: flex; flex-direction: column; gap: 3px; flex: 1; min-width: 0; }
.ex-name { font-size: 14px; font-weight: 600; }
.ex-note { font-size: 11.5px; color: var(--color-text-placeholder, #999); }
.ex-meta { display: flex; gap: 6px; flex-shrink: 0; flex-wrap: wrap; justify-content: flex-end; }
.ex-tag {
  font-size: 11px;
  background: #fff;
  border: 1px solid var(--color-border, #e5e5e5);
  border-radius: 8px;
  padding: 2px 7px;
  color: var(--color-text-secondary, #666);
}
.ex-tag.rest { color: var(--color-primary, #ff6a00); }

.tips-title { font-size: 14px; font-weight: 700; margin-bottom: 8px; display: flex; align-items: center; gap: 6px; }
.tips-card ul { padding-left: 18px; }
.tips-card li { font-size: 13px; color: var(--color-text-secondary, #555); line-height: 1.9; }

/* 智能客服 */
.service-wrap {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 150px);
  min-height: 420px;
}
.chat-stream {
  flex: 1;
  overflow-y: auto;
  padding: 6px 2px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.msg-row { display: flex; gap: 8px; align-items: flex-start; }
.msg-row.mine { justify-content: flex-end; }
.msg-avatar {
  width: 34px;
  height: 34px;
  border-radius: 50%;
  background: var(--color-primary, #ff6a00);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  flex-shrink: 0;
}
.msg-body { max-width: 78%; display: flex; flex-direction: column; align-items: flex-start; gap: 4px; }
.msg-bubble {
  background: #fff;
  border-radius: 4px 14px 14px 14px;
  padding: 10px 13px;
  font-size: 14px;
  line-height: 1.65;
  color: var(--color-text, #333);
  white-space: pre-wrap;
  word-break: break-word;
}
.mine .msg-bubble {
  background: var(--color-primary, #ff6a00);
  color: #fff;
  border-radius: 14px 4px 14px 14px;
}
.msg-src { font-size: 11px; color: var(--color-text-placeholder, #aaa); }
.msg-src.FAQ { color: #1677ff; }
.msg-src.AI { color: #722ed1; }
.msg-src.HANDOFF { color: #fa8c16; }

.typing { display: flex; gap: 4px; align-items: center; padding: 14px; }
.typing span {
  width: 6px; height: 6px; border-radius: 50%;
  background: #ccc;
  animation: blink 1.2s infinite both;
}
.typing span:nth-child(2) { animation-delay: 0.2s; }
.typing span:nth-child(3) { animation-delay: 0.4s; }
@keyframes blink { 0%, 80%, 100% { opacity: 0.25; } 40% { opacity: 1; } }

.suggest-bar { display: flex; gap: 8px; overflow-x: auto; padding: 8px 0; }
.suggest-chip {
  flex-shrink: 0;
  border: 1px solid var(--color-border, #e5e5e5);
  background: #fff;
  border-radius: 16px;
  padding: 6px 13px;
  font-size: 12.5px;
  color: var(--color-text-secondary, #555);
}
.suggest-chip:active { border-color: var(--color-primary, #ff6a00); color: var(--color-primary, #ff6a00); }

.input-bar { display: flex; gap: 8px; padding-top: 8px; }
.input-bar input {
  flex: 1;
  border: 1px solid var(--color-border, #e5e5e5);
  border-radius: 20px;
  padding: 10px 16px;
  font-size: 14px;
  background: #fff;
}
.input-bar input:focus { outline: none; border-color: var(--color-primary, #ff6a00); }
.send-btn {
  width: 42px;
  height: 42px;
  border-radius: 50%;
  border: none;
  background: var(--color-primary, #ff6a00);
  color: #fff;
  flex-shrink: 0;
}
.send-btn:disabled { opacity: 0.5; }
</style>
