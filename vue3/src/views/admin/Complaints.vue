<template>
  <div class="governance">
    <div class="seg-tabs card">
      <button :class="{ active: tab === 'complaint' }" @click="tab = 'complaint'">投诉处理</button>
      <button :class="{ active: tab === 'arbitration' }" @click="switchArb">平台仲裁</button>
    </div>

    <!-- ============ 投诉 ============ -->
    <template v-if="tab === 'complaint'">
      <div class="filter-bar card">
        <select v-model="cFilter.status" @change="loadComplaints(1)">
          <option value="">全部状态</option>
          <option value="PENDING">待受理</option>
          <option value="PROCESSING">处理中</option>
          <option value="ARBITRATING">仲裁中</option>
          <option value="RESOLVED">已解决</option>
          <option value="CLOSED">已关闭</option>
        </select>
        <select v-model="cFilter.type" @change="loadComplaints(1)">
          <option value="">全部类型</option>
          <option value="SERVICE">服务态度</option>
          <option value="ORDER">订单支付</option>
          <option value="ENTRY">到店核销</option>
          <option value="CARD">会员卡</option>
          <option value="OTHER">其他</option>
        </select>
      </div>

      <div class="table-wrap card">
        <table class="data-table">
          <thead>
            <tr>
              <th>类型</th><th>标题/内容</th><th>用户</th><th>提交时间</th><th>状态</th><th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="cLoading"><td colspan="6" class="empty-row">加载中…</td></tr>
            <tr v-for="c in complaints" :key="c.id">
              <td>{{ typeText(c.type) }}</td>
              <td class="cell-main">
                <div class="cell-title">{{ c.title }}</div>
                <div class="cell-sub">{{ c.content }}</div>
              </td>
              <td class="user-cell"><div class="c-avatar"><UserAvatar :src="resolveAvatar(c.avatarThumb || c.avatar)" :preview-src="resolveAvatar(c.avatar)" previewable /></div><span>{{ c.userId }}</span></td>
              <td class="time-cell">{{ fmt(c.createdAt) }}</td>
              <td><span class="status-tag" :class="c.status">{{ statusText(c.status) }}</span></td>
              <td><button class="link-btn" @click="openComplaint(c.id)">处理</button></td>
            </tr>
            <tr v-if="!cLoading && !complaints.length"><td colspan="6" class="empty-row">暂无投诉</td></tr>
          </tbody>
        </table>
        <div class="pager">
          <button :disabled="cPage <= 1" @click="loadComplaints(cPage - 1)">上一页</button>
          <span>第 {{ cPage }} 页 / 共 {{ Math.max(1, Math.ceil(cTotal / 10)) }} 页（{{ cTotal }} 条）</span>
          <button :disabled="cPage >= Math.ceil(cTotal / 10)" @click="loadComplaints(cPage + 1)">下一页</button>
        </div>
      </div>
    </template>

    <!-- ============ 仲裁 ============ -->
    <template v-else>
      <div class="filter-bar card">
        <select v-model="aFilter.status" @change="loadArbitrations(1)">
          <option value="">全部状态</option>
          <option value="INVESTIGATING">调查中</option>
          <option value="DONE">已完成</option>
        </select>
      </div>
      <div class="table-wrap card">
        <table class="data-table">
          <thead>
            <tr><th>仲裁ID</th><th>来源投诉</th><th>用户</th><th>升级原因</th><th>状态</th><th>结果</th><th>操作</th></tr>
          </thead>
          <tbody>
            <tr v-if="aLoading"><td colspan="7" class="empty-row">加载中…</td></tr>
            <tr v-for="a in arbitrations" :key="a.id">
              <td>{{ a.id }}</td>
              <td>{{ a.complaintId }}</td>
              <td>{{ a.userId }}</td>
              <td class="cell-main"><div class="cell-sub">{{ a.reason }}</div></td>
              <td>{{ arbStatusText(a.status) }}</td>
              <td>{{ arbResultText(a.result) }}</td>
              <td>
                <button v-if="a.status !== 'DONE'" class="link-btn" @click="openRule(a)">裁决</button>
                <button class="link-btn" @click="viewFromArbitration(a.complaintId)">查看投诉</button>
              </td>
            </tr>
            <tr v-if="!aLoading && !arbitrations.length"><td colspan="7" class="empty-row">暂无仲裁单</td></tr>
          </tbody>
        </table>
        <div class="pager">
          <button :disabled="aPage <= 1" @click="loadArbitrations(aPage - 1)">上一页</button>
          <span>第 {{ aPage }} 页 / 共 {{ Math.max(1, Math.ceil(aTotal / 10)) }} 页（{{ aTotal }} 条）</span>
          <button :disabled="aPage >= Math.ceil(aTotal / 10)" @click="loadArbitrations(aPage + 1)">下一页</button>
        </div>
      </div>
    </template>

    <!-- ============ 投诉处理弹层 ============ -->
    <div v-if="detail" class="modal-mask" @click.self="detail = null">
      <div class="modal">
        <div class="modal-head">
          <span>投诉详情</span>
          <i class="fas fa-times" @click="detail = null"></i>
        </div>
        <div class="modal-body">
          <div class="d-row"><label>类型</label><span>{{ typeText(detail.complaint.type) }}</span>
            <label>状态</label><span class="status-tag" :class="detail.complaint.status">{{ statusText(detail.complaint.status) }}</span></div>
          <h4>{{ detail.complaint.title }}</h4>
          <p class="d-content">{{ detail.complaint.content }}</p>
          <!-- 投诉人头像（点击放大看主图）+ 提交信息 -->
          <div class="d-user">
            <div class="d-user-avatar">
              <UserAvatar
                :src="resolveAvatar(detail.complaint.avatarThumb || detail.complaint.avatar)"
                :preview-src="resolveAvatar(detail.complaint.avatar)"
                previewable
              />
            </div>
            <p class="d-line">提交人：{{ detail.complaint.userId }} ｜ 时间：{{ fmt(detail.complaint.createdAt) }}
              <span v-if="detail.complaint.contactPhone"> ｜ 电话：{{ detail.complaint.contactPhone }}</span></p>
          </div>

          <!-- 凭证图片（用户端上传，/uploads/... 域内地址） -->
          <div v-if="complaintImages.length" class="d-images">
            <label class="d-images-label">凭证图片（{{ complaintImages.length }}张，点击放大）</label>
            <div class="d-img-grid">
              <img v-for="(img, i) in complaintImages" :key="i" :src="img"
                alt="投诉凭证" loading="lazy" @click="openImagePreview(img)" />
            </div>
          </div>
          <div v-if="detail.complaint.adminReply" class="d-reply">
            <label>平台回复：</label>{{ detail.complaint.adminReply }}
          </div>
          <div v-if="detail.arbitration" class="d-arb">
            <label>仲裁：</label>{{ arbStatusText(detail.arbitration.status) }}
            <span v-if="detail.arbitration.result"> ｜ {{ arbResultText(detail.arbitration.result) }}</span>
          </div>

          <div class="op-area">
            <template v-if="detail.complaint.status === 'PENDING'">
              <button class="op-btn primary" :disabled="busy" @click="doAccept">受理</button>
            </template>
            <template v-if="['PROCESSING','ARBITRATING','PENDING'].includes(detail.complaint.status)">
              <textarea v-model="reply" rows="3" placeholder="填写处理回复（将通知用户）"></textarea>
              <div class="op-btns">
                <button class="op-btn primary" :disabled="busy || !reply.trim()" @click="doResolve">回复并解决</button>
                <button v-if="detail.complaint.status === 'PROCESSING' && !detail.arbitration"
                        class="op-btn warn" :disabled="busy || !arbReason.trim()" @click="doEscalate">升级仲裁</button>
              </div>
              <input v-if="detail.complaint.status === 'PROCESSING' && !detail.arbitration"
                     v-model="arbReason" class="arb-reason" placeholder="升级仲裁的原因（必填）" />
            </template>
            <template v-if="detail.complaint.status === 'RESOLVED'">
              <button class="op-btn" :disabled="busy" @click="doClose">关闭归档</button>
            </template>
          </div>
        </div>
      </div>
    </div>

    <!-- ============ 仲裁裁决弹层 ============ -->
    <div v-if="ruling" class="modal-mask" @click.self="ruling = null">
      <div class="modal">
        <div class="modal-head"><span>平台仲裁裁决 #{{ ruling.id }}</span><i class="fas fa-times" @click="ruling = null"></i></div>
        <div class="modal-body">
          <p class="d-line">来源投诉：{{ ruling.complaintId }} ｜ 升级原因：{{ ruling.reason }}</p>
          <label class="op-label">裁决结果</label>
          <select v-model="ruleForm.result">
            <option value="SUPPORT_USER">支持用户</option>
            <option value="PARTIAL">各担其责</option>
            <option value="SUPPORT_PLATFORM">支持平台</option>
          </select>
          <label class="op-label">裁决说明</label>
          <textarea v-model="ruleForm.decision" rows="4" placeholder="调查结论与裁决依据"></textarea>
          <label class="op-label">补偿金额（元，无补偿留空）</label>
          <input v-model="ruleForm.compensationAmount" type="number" min="0" step="0.01" placeholder="0.00" />
          <button class="op-btn primary full" :disabled="busy || !ruleForm.decision.trim()" @click="doRule">确认裁决</button>
        </div>
      </div>
    </div>
    <!-- 凭证图片放大预览统一走全局 ImagePreview（useImagePreview.open） -->
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref, computed } from 'vue'
import { message } from '@/utils/message'
import { governanceAPI } from '@/api/governanceAPI'
import type { Complaint, Arbitration, ComplaintDetail } from '@/api/governanceAPI'
import UserAvatar from '@/components/UserAvatar.vue'
import { resolveAvatar } from '@/utils/avatar'
import { useImagePreview } from '@/composables/useImagePreview'

const tab = ref<'complaint' | 'arbitration'>('complaint')

// ---- 投诉列表 ----
const complaints = ref<Complaint[]>([])
const cPage = ref(1); const cTotal = ref(0)
const cLoading = ref(false)
const cFilter = reactive({ status: '', type: '' })
const loadComplaints = async (p = 1) => {
  cPage.value = p
  cLoading.value = true
  try {
    const res = await governanceAPI.adminComplaints({ ...cFilter, page: p, size: 10 })
    if (res.success && res.data) { complaints.value = res.data.list; cTotal.value = res.data.total }
  } finally {
    cLoading.value = false
  }
}

// ---- 仲裁列表 ----
const arbitrations = ref<Arbitration[]>([])
const aPage = ref(1); const aTotal = ref(0)
const aLoading = ref(false)
const aFilter = reactive({ status: '' })
const loadArbitrations = async (p = 1) => {
  aPage.value = p
  aLoading.value = true
  try {
    const res = await governanceAPI.adminArbitrations({ ...aFilter, page: p, size: 10 })
    if (res.success && res.data) { arbitrations.value = res.data.list; aTotal.value = res.data.total }
  } finally {
    aLoading.value = false
  }
}
const switchArb = () => { tab.value = 'arbitration'; loadArbitrations(1) }

// ---- 详情/处理 ----
const detail = ref<ComplaintDetail | null>(null)
const reply = ref(''); const arbReason = ref(''); const busy = ref(false)

/** 解析投诉凭证图片 JSON（后端 imagesJson 为字符串数组） */
const complaintImages = computed<string[]>(() => {
  const raw = detail.value?.complaint?.imagesJson
  if (!raw) return []
  try {
    const arr = JSON.parse(raw)
    return Array.isArray(arr) ? arr.filter((u): u is string => typeof u === 'string' && !!u) : []
  } catch {
    return []
  }
})

/** 图片放大预览统一走全局 ImagePreview（遮罩 / 手势 / 失败回退由组件统一处理） */
const { open: openImagePreview } = useImagePreview()

const openComplaint = async (id: number) => {
  const res = await governanceAPI.adminComplaintDetail(id)
  if (res.success && res.data) {
    detail.value = res.data
    reply.value = res.data.complaint.adminReply || ''
    arbReason.value = ''
  }
}
const viewFromArbitration = async (complaintId: number) => {
  tab.value = 'complaint'
  await loadComplaints(1)
  await openComplaint(complaintId)
}

const refreshDetail = async () => {
  if (detail.value) await openComplaint(detail.value.complaint.id)
}
const doAccept = async () => {
  if (!detail.value) return
  busy.value = true
  try {
    const res = await governanceAPI.acceptComplaint(detail.value.complaint.id)
    if (res.success) { message.success('已受理'); await refreshDetail(); loadComplaints(cPage.value) }
  } finally { busy.value = false }
}
const doResolve = async () => {
  if (!detail.value) return
  busy.value = true
  try {
    const res = await governanceAPI.resolveComplaint(detail.value.complaint.id, reply.value.trim())
    if (res.success) { message.success('已回复并解决'); await refreshDetail(); loadComplaints(cPage.value) }
  } finally { busy.value = false }
}
const doClose = async () => {
  if (!detail.value) return
  busy.value = true
  try {
    const res = await governanceAPI.closeComplaint(detail.value.complaint.id)
    if (res.success) { message.success('已关闭'); await refreshDetail(); loadComplaints(cPage.value) }
  } finally { busy.value = false }
}
const doEscalate = async () => {
  if (!detail.value) return
  busy.value = true
  try {
    const res = await governanceAPI.arbitrateComplaint(detail.value.complaint.id, arbReason.value.trim())
    if (res.success) { message.success('已升级仲裁'); await refreshDetail(); loadComplaints(cPage.value) }
  } finally { busy.value = false }
}

// ---- 裁决 ----
const ruling = ref<Arbitration | null>(null)
const ruleForm = reactive({ result: 'SUPPORT_USER', decision: '', compensationAmount: '' })
const openRule = (a: Arbitration) => {
  ruling.value = a
  ruleForm.result = 'SUPPORT_USER'; ruleForm.decision = ''; ruleForm.compensationAmount = ''
}
const doRule = async () => {
  if (!ruling.value) return
  busy.value = true
  try {
    const res = await governanceAPI.ruleArbitration(ruling.value.id, {
      result: ruleForm.result,
      decision: ruleForm.decision.trim(),
      compensationAmount: ruleForm.compensationAmount ? Number(ruleForm.compensationAmount) : null
    })
    if (res.success) {
      message.success('裁决完成'); ruling.value = null; loadArbitrations(aPage.value)
    }
  } finally { busy.value = false }
}

const typeText = (t: string) => ({
  SERVICE: '服务态度', ORDER: '订单支付', ENTRY: '到店核销', CARD: '会员卡', OTHER: '其他'
} as Record<string, string>)[t] || t
const statusText = (s: string) => ({
  PENDING: '待受理', PROCESSING: '处理中', ARBITRATING: '仲裁中', RESOLVED: '已解决', CLOSED: '已关闭'
} as Record<string, string>)[s] || s
const arbStatusText = (s: string) => ({ INVESTIGATING: '调查中', RULING: '裁决中', DONE: '已完成' } as Record<string, string>)[s] || s
const arbResultText = (r?: string | null) => ({
  SUPPORT_USER: '支持用户', SUPPORT_PLATFORM: '支持平台', PARTIAL: '各担其责'
} as Record<string, string>)[r || ''] || '-'
const fmt = (t?: string | null) => (t ? t.replace('T', ' ').substring(0, 16) : '-')

onMounted(() => loadComplaints(1))
</script>

<style scoped>
.seg-tabs { display: flex; gap: 8px; padding: 8px; margin-bottom: 12px; }
.seg-tabs button {
  flex: 1; border: none; background: #f0f2f5; border-radius: 8px; padding: 9px 0;
  font-size: 14px; color: #666;
}
.seg-tabs button.active { background: var(--color-primary, #ff6a00); color: #fff; font-weight: 600; }
.filter-bar { display: flex; gap: 10px; padding: 12px; margin-bottom: 12px; }
.filter-bar select { padding: 7px 10px; border: 1px solid #e5e5e5; border-radius: 6px; font-size: 13px; }

.cell-main { max-width: 280px; }
.cell-title { font-weight: 600; font-size: 13px; }
.cell-sub {
  font-size: 12px; color: #888; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
  max-width: 260px;
}
/* 用户列：头像 + 用户ID */
.user-cell { display: flex; align-items: center; gap: 8px; }
.c-avatar {
  width: 30px; height: 30px; border-radius: 50%; overflow: hidden;
  background: var(--color-bg-gray); color: var(--color-text-placeholder);
  display: flex; align-items: center; justify-content: center; flex-shrink: 0;
}
.status-tag { padding: 2px 9px; border-radius: 9px; font-size: 12px; background: #f0f0f0; color: #888; }
.status-tag.PENDING { background: #fff3e0; color: #fa8c16; }
.status-tag.PROCESSING, .status-tag.ARBITRATING { background: #e6f4ff; color: #1677ff; }
.status-tag.RESOLVED { background: #f0fff0; color: #52c41a; }
.link-btn { border: none; background: none; color: var(--color-primary, #ff6a00); font-size: 13px; cursor: pointer; }

.pager {
  display: flex; align-items: center; justify-content: center; gap: 14px;
  padding: 14px 0 4px; font-size: 13px; color: #888;
}
.pager button {
  border: 1px solid #e5e5e5; background: #fff; border-radius: 6px;
  padding: 5px 14px; font-size: 13px; cursor: pointer;
}
.pager button:disabled { color: #ccc; cursor: not-allowed; }

.modal-mask {
  position: fixed; inset: 0; background: rgba(0,0,0,0.45); z-index: 100;
  display: flex; align-items: center; justify-content: center; padding: 16px;
}
.modal { background: #fff; border-radius: 12px; width: 100%; max-width: 520px; max-height: 88vh; overflow-y: auto; }
.modal-head {
  display: flex; justify-content: space-between; align-items: center;
  padding: 15px 18px; border-bottom: 1px solid #f0f0f0; font-weight: 700;
}
.modal-head i { color: #999; cursor: pointer; }
.modal-body { padding: 16px 18px; }
.d-row { display: flex; gap: 8px; align-items: center; font-size: 13px; margin-bottom: 12px; }
.d-row label { color: #888; }
.modal-body h4 { margin: 0 0 8px; font-size: 16px; }
.d-content { font-size: 14px; line-height: 1.7; color: #333; margin-bottom: 10px; }
.d-line { font-size: 12px; color: #999; }
.d-reply, .d-arb {
  background: #f7f8fa; border-radius: 8px; padding: 10px 12px; margin-top: 10px;
  font-size: 13px; line-height: 1.7;
}
.d-arb { background: #f9f0ff; }
.op-area { margin-top: 16px; border-top: 1px dashed #eee; padding-top: 14px; }
.op-area textarea, .op-area input, .modal-body select, .modal-body textarea, .modal-body input {
  width: 100%; box-sizing: border-box; border: 1px solid #e5e5e5; border-radius: 8px;
  padding: 9px 11px; font-size: 13.5px; margin-top: 8px;
}
.op-label { display: block; font-size: 13px; color: #666; margin-top: 12px; }
.op-btns { display: flex; gap: 10px; margin-top: 10px; }
.op-btn {
  border: 1px solid #e5e5e5; background: #fff; border-radius: 8px; padding: 9px 18px;
  font-size: 14px; cursor: pointer;
}
.op-btn.primary { background: var(--color-primary, #ff6a00); color: #fff; border-color: var(--color-primary, #ff6a00); }
.op-btn.warn { background: #fff; color: #722ed1; border-color: #722ed1; }
.op-btn.full { width: 100%; margin-top: 14px; }
.op-btn:disabled { opacity: 0.5; }
.arb-reason { margin-top: 8px; }

/* ---------- 凭证图片 ---------- */
.d-images { margin-top: 12px; }
.d-images-label { display: block; font-size: 13px; color: #888; margin-bottom: 8px; }
.d-img-grid { display: flex; flex-wrap: wrap; gap: 8px; }
.d-img-grid img {
  width: 84px; height: 84px; object-fit: cover; border-radius: 8px;
  border: 1px solid #eee; cursor: zoom-in; display: block;
}
.d-img-grid img:hover { border-color: var(--color-primary, #ff6a00); }

/* ---------- 投诉详情：投诉人头像行 ---------- */
.d-user { display: flex; align-items: center; gap: 10px; margin: 10px 0; }
.d-user-avatar {
  width: 40px; height: 40px; border-radius: 50%; overflow: hidden;
  background: var(--color-bg-gray, #f0f2f5); color: #bbb; flex-shrink: 0;
}
.d-user .d-line { margin: 0; }
</style>
