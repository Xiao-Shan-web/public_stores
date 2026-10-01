<template>
  <div class="complaints page-container">
    <PageHeader title="投诉建议" />

    <div class="page-body">
      <!-- ============ 列表态 ============ -->
      <template v-if="view === 'list'">
        <button class="new-btn" @click="openForm">
          <i class="fas fa-plus"></i> 提交投诉
        </button>

        <div v-if="loading" class="tip">加载中...</div>
        <div v-else-if="!list.length" class="empty">
          <i class="fas fa-comment-dots"></i>
          <p>暂无投诉记录</p>
          <p class="empty-sub">遇到服务、订单或核销问题，欢迎告诉我们</p>
        </div>

        <div v-else class="c-list">
          <div v-for="c in list" :key="c.id" class="c-card" @click="openDetail(c.id)">
            <div class="c-top">
              <span class="c-type">{{ typeText(c.type) }}</span>
              <span class="c-status" :class="c.status">{{ statusText(c.status) }}</span>
            </div>
            <p class="c-title">{{ c.title }}</p>
            <p class="c-content">{{ c.content }}</p>
            <div class="c-foot">
              <span>{{ formatTime(c.createdAt) }}</span>
              <i class="fas fa-chevron-right"></i>
            </div>
          </div>
        </div>
      </template>

      <!-- ============ 表单态 ============ -->
      <template v-else-if="view === 'form'">
        <div class="card">
          <!-- 1. 投诉类型 -->
          <p class="block-title">投诉类型</p>
          <div class="type-row">
            <button
              v-for="t in TYPE_OPTS" :key="t.value"
              class="type-opt" :class="{ active: form.type === t.value }"
              @click="form.type = t.value"
            >{{ t.label }}</button>
          </div>

          <!-- 2. 标题 -->
          <p class="block-title">标题</p>
          <input v-model="form.title" class="inp" maxlength="100" placeholder="一句话概括问题" />

          <!-- 3. 投诉内容 -->
          <p class="block-title">投诉内容</p>
          <textarea v-model="form.content" class="ta" rows="6" maxlength="1000"
            placeholder="请描述事情经过、时间、涉及门店或订单，便于我们核实处理"></textarea>
          <p class="counter">{{ form.content.length }}/1000</p>

          <!-- 4. 上传凭证（选填，最多 6 张） -->
          <p class="block-title">上传凭证<span class="block-hint">（选填，最多6张）</span></p>
          <div class="img-grid">
            <div v-for="(img, idx) in form.images" :key="idx" class="img-cell">
              <img :src="img" alt="凭证" />
              <button type="button" class="img-del" @click="removeImage(idx)">
                <i class="fas fa-times"></i>
              </button>
            </div>
            <label v-if="form.images.length < MAX_IMAGES" class="img-add">
              <input type="file" accept="image/*" multiple hidden @change="onPickImages" />
              <i class="fas fa-camera"></i>
              <span>上传凭证</span>
            </label>
          </div>
          <p v-if="uploading" class="upload-tip">
            <i class="fas fa-spinner fa-spin"></i> 图片上传中，请稍候...
          </p>

          <!-- 5. 联系方式（选填） -->
          <p class="block-title">联系方式<span class="block-hint">（选填）</span></p>
          <input v-model="form.contactPhone" class="inp" maxlength="11"
            type="tel" inputmode="numeric" placeholder="方便平台与你联系" />
        </div>

        <!-- 6. 提交按钮：固定在表单底部，整行撑满，点击区域足够大 -->
        <div class="form-actions">
          <button class="submit-btn" :disabled="submitting || uploading" @click="handleSubmit">
            {{ submitting ? '提交中...' : '提交投诉' }}
          </button>
          <button type="button" class="cancel-link" @click="view = 'list'">取消</button>
        </div>
      </template>

      <!-- ============ 详情态 ============ -->
      <template v-else-if="view === 'detail' && detail">
        <div class="card detail-card">
          <div class="c-top">
            <span class="c-type">{{ typeText(detail.complaint.type) }}</span>
            <span class="c-status" :class="detail.complaint.status">{{ statusText(detail.complaint.status) }}</span>
          </div>
          <p class="d-title">{{ detail.complaint.title }}</p>
          <p class="d-content">{{ detail.complaint.content }}</p>
          <p class="d-meta">提交时间：{{ formatTime(detail.complaint.createdAt) }}</p>
        </div>

        <div v-if="detail.complaint.adminReply" class="card reply-card">
          <p class="reply-title"><i class="fas fa-reply"></i> 平台回复</p>
          <p class="reply-text">{{ detail.complaint.adminReply }}</p>
          <p class="d-meta">{{ formatTime(detail.complaint.handledAt) }}</p>
        </div>

        <div v-if="detail.arbitration" class="card arb-card">
          <p class="reply-title"><i class="fas fa-gavel"></i> 平台仲裁</p>
          <p class="arb-line">升级原因：{{ detail.arbitration.reason }}</p>
          <p class="arb-line">仲裁状态：{{ arbStatusText(detail.arbitration.status) }}</p>
          <template v-if="detail.arbitration.status === 'DONE'">
            <p class="arb-line">裁决结果：{{ arbResultText(detail.arbitration.result) }}</p>
            <p class="arb-line">裁决说明：{{ detail.arbitration.decision }}</p>
            <p v-if="detail.arbitration.compensationAmount" class="arb-line">
              补偿金额：{{ detail.arbitration.compensationAmount }} 元
            </p>
          </template>
          <p class="d-meta">申请时间：{{ formatTime(detail.arbitration.createdAt) }}</p>
        </div>

        <button class="btn-ghost back" @click="view = 'list'">返回列表</button>
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import PageHeader from '@/components/PageHeader.vue'
import { message } from '@/utils/message'
import { governanceAPI } from '@/api/governanceAPI'
import { shareAPI } from '@/api/shareAPI'
import type { Complaint, ComplaintDetail, ComplaintType } from '@/api/governanceAPI'

/** 凭证图片上限（与后端 ComplaintService 一致：最多 6 张） */
const MAX_IMAGES = 6
/** 单张凭证大小上限 10MB */
const MAX_IMAGE_SIZE = 10 * 1024 * 1024

const TYPE_OPTS: { value: ComplaintType; label: string }[] = [
  { value: 'SERVICE', label: '服务态度' },
  { value: 'ORDER', label: '订单支付' },
  { value: 'ENTRY', label: '到店核销' },
  { value: 'CARD', label: '会员卡' },
  { value: 'OTHER', label: '其他' }
]

const view = ref<'list' | 'form' | 'detail'>('list')
const list = ref<Complaint[]>([])
const detail = ref<ComplaintDetail | null>(null)
const loading = ref(false)
const submitting = ref(false)
const uploading = ref(false)

const form = reactive({
  type: 'SERVICE' as ComplaintType,
  title: '',
  content: '',
  images: [] as string[],
  contactPhone: ''
})

const loadList = async () => {
  loading.value = true
  try {
    const res = await governanceAPI.myComplaints(1, 50)
    if (res.success && res.data) list.value = res.data.list
  } catch { /* 拦截器提示 */ } finally {
    loading.value = false
  }
}

const openForm = () => {
  form.type = 'SERVICE'; form.title = ''; form.content = ''
  form.images = []; form.contactPhone = ''
  view.value = 'form'
}

/**
 * 选择凭证图片后逐张上传（复用分享图片压缩接口，返回 /uploads/... URL）。
 * 超出剩余名额的文件直接忽略并提示。
 */
const onPickImages = async (e: Event) => {
  const input = e.target as HTMLInputElement
  const files = Array.from(input.files || [])
  // 允许同一文件重复选择
  input.value = ''
  if (!files.length) return

  const room = MAX_IMAGES - form.images.length
  if (files.length > room) {
    message.warning(`最多上传${MAX_IMAGES}张凭证，本次仅添加前${room}张`)
  }

  for (const file of files.slice(0, room)) {
    if (!file.type.startsWith('image/')) {
      message.warning('仅支持图片文件')
      continue
    }
    if (file.size > MAX_IMAGE_SIZE) {
      message.warning('单张图片不能超过10MB')
      continue
    }
    uploading.value = true
    try {
      const r = await shareAPI.uploadImage(file)
      if (r.success && r.data?.url) {
        form.images.push(r.data.url)
      } else {
        message.warning(r.message || '图片上传失败，请重试')
      }
    } catch {
      /* 请求拦截器已统一提示 */
    } finally {
      uploading.value = false
    }
  }
}

const removeImage = (idx: number) => {
  form.images.splice(idx, 1)
}

const openDetail = async (id: number) => {
  try {
    const res = await governanceAPI.complaintDetail(id)
    if (res.success && res.data) {
      detail.value = res.data
      view.value = 'detail'
    }
  } catch { /* ignore */ }
}

const handleSubmit = async () => {
  if (!form.title.trim() || !form.content.trim()) {
    message.warning('请填写标题和详细描述')
    return
  }
  if (form.contactPhone && !/^1\d{10}$/.test(form.contactPhone)) {
    message.warning('联系电话格式不正确')
    return
  }
  submitting.value = true
  try {
    const res = await governanceAPI.submitComplaint({
      type: form.type,
      title: form.title.trim(),
      content: form.content.trim(),
      images: form.images.length ? [...form.images] : undefined,
      contactPhone: form.contactPhone || undefined
    })
    if (res.success) {
      message.success('投诉已提交')
      view.value = 'list'
      loadList()
    }
  } catch { /* ignore */ } finally {
    submitting.value = false
  }
}

const typeText = (t: string) =>
  TYPE_OPTS.find(x => x.value === t)?.label || '其他'
const statusText = (s: string) => ({
  PENDING: '待受理', PROCESSING: '处理中', ARBITRATING: '仲裁中',
  RESOLVED: '已解决', CLOSED: '已关闭'
} as Record<string, string>)[s] || s
const arbStatusText = (s: string) =>
  ({ INVESTIGATING: '调查中', RULING: '裁决中', DONE: '已完成' } as Record<string, string>)[s] || s
const arbResultText = (r?: string | null) => ({
  SUPPORT_USER: '支持用户', SUPPORT_PLATFORM: '支持平台', PARTIAL: '各担其责'
} as Record<string, string>)[r || ''] || '-'

const formatTime = (t?: string | null) => {
  if (!t) return ''
  return t.replace('T', ' ').substring(0, 16)
}

onMounted(loadList)
</script>

<style scoped>
.complaints { min-height: 100vh; background: var(--color-bg-gray, #f5f6f8); }
.page-body { padding: 12px 14px 24px; }
.card { background: #fff; border-radius: 10px; padding: 14px; margin-bottom: 12px; }

.new-btn {
  width: 100%; border: none; border-radius: 22px; padding: 12px 0;
  background: var(--color-primary, #ff6a00); color: #fff;
  font-size: 15px; font-weight: 600; margin-bottom: 14px;
}
.tip, .empty { text-align: center; color: var(--color-text-placeholder, #999); padding: 40px 0; }
.empty i { font-size: 40px; margin-bottom: 10px; }
.empty p { font-size: 14px; }
.empty .empty-sub { font-size: 12px; margin-top: 6px; }

.c-list { display: flex; flex-direction: column; gap: 10px; }
.c-card { background: #fff; border-radius: 10px; padding: 13px 14px; }
.c-top { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; }
.c-type {
  font-size: 11px; background: #fff3ea; color: var(--color-primary, #ff6a00);
  padding: 2px 9px; border-radius: 9px;
}
.c-status { font-size: 12px; font-weight: 600; color: #999; }
.c-status.PENDING { color: #fa8c16; }
.c-status.PROCESSING, .c-status.ARBITRATING { color: #1677ff; }
.c-status.RESOLVED { color: #52c41a; }
.c-title { font-size: 15px; font-weight: 700; margin-bottom: 5px; }
.c-content {
  font-size: 13px; color: var(--color-text-secondary, #666);
  line-height: 1.6; overflow: hidden; text-overflow: ellipsis;
  display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical;
}
.c-foot {
  display: flex; align-items: center; gap: 10px; margin-top: 10px;
  font-size: 11.5px; color: var(--color-text-placeholder, #999);
}
.c-foot .fa-chevron-right { margin-left: auto; font-size: 11px; }
.arb-flag { color: #722ed1; }

.block-title { font-size: 13.5px; font-weight: 600; margin: 14px 0 8px; }
.block-title:first-child { margin-top: 0; }
.block-hint { font-size: 12px; font-weight: 400; color: var(--color-text-placeholder, #999); }
.type-row { display: flex; flex-wrap: wrap; gap: 8px; }
.type-opt {
  border: 1px solid var(--color-border, #e5e5e5); background: #fff;
  border-radius: 18px; padding: 7px 16px; font-size: 13px; color: #333;
}
.type-opt.active {
  border-color: var(--color-primary, #ff6a00); color: var(--color-primary, #ff6a00);
  background: rgba(255, 106, 0, 0.08); font-weight: 600;
}
.inp, .ta {
  width: 100%; border: 1px solid var(--color-border, #e5e5e5); border-radius: 8px;
  padding: 10px 12px; font-size: 14px; box-sizing: border-box; background: #fafafa;
}
.ta { resize: none; }
.inp:focus, .ta:focus { outline: none; border-color: var(--color-primary, #ff6a00); background: #fff; }
.counter { text-align: right; font-size: 11px; color: #bbb; margin-top: 4px; }

/* ---------- 凭证图片上传 ---------- */
.img-grid { display: flex; flex-wrap: wrap; gap: 8px; }
.img-cell, .img-add {
  width: 72px; height: 72px; border-radius: 8px; position: relative; overflow: hidden;
  box-sizing: border-box;
}
.img-cell img { width: 100%; height: 100%; object-fit: cover; display: block; }
.img-del {
  position: absolute; top: 3px; right: 3px; width: 18px; height: 18px;
  border: none; border-radius: 50%; padding: 0;
  background: rgba(0, 0, 0, 0.55); color: #fff;
  display: flex; align-items: center; justify-content: center;
  font-size: 10px; line-height: 1;
}
.img-add {
  border: 1px dashed #ccc; background: #fafafa;
  display: flex; flex-direction: column; align-items: center; justify-content: center;
  gap: 4px; color: #999; font-size: 11px;
}
.img-add i { font-size: 20px; }
.img-add:active { background: #f0f0f0; }
.upload-tip {
  margin: 8px 0 0; font-size: 12px; color: var(--color-primary, #ff6a00);
  display: flex; align-items: center; gap: 6px;
}

/* ---------- 表单底部操作区：主按钮整行撑满，取消为次要文字按钮 ---------- */
.form-actions {
  display: flex; flex-direction: column; align-items: stretch;
  gap: 8px; margin-top: 18px;
}
.submit-btn {
  width: 100%; min-height: 46px; border: none; border-radius: 24px;
  background: var(--color-primary, #ff6a00); color: #fff;
  font-size: 16px; font-weight: 600;
}
.submit-btn:disabled { opacity: 0.6; }
.cancel-link {
  align-self: center; min-height: 36px; padding: 6px 24px;
  background: none; border: none; color: #999; font-size: 13px;
}

/* 详情页返回按钮（ghost 样式保留） */
.btn-ghost {
  width: 100%; border-radius: 22px; padding: 12px 0; font-size: 15px;
  background: #fff; color: #666; border: 1px solid var(--color-border, #e5e5e5);
}

.d-title { font-size: 16px; font-weight: 700; margin: 10px 0 8px; }
.d-content { font-size: 14px; line-height: 1.7; color: #333; }
.d-meta { font-size: 11.5px; color: #aaa; margin-top: 10px; }
.reply-title, .reply-title { font-size: 14px; font-weight: 700; margin-bottom: 8px; color: #1677ff; }
.reply-text { font-size: 14px; line-height: 1.7; color: #333; }
.arb-card { border-left: 3px solid #722ed1; }
.arb-card .reply-title { color: #722ed1; }
.arb-line { font-size: 13.5px; line-height: 1.8; color: #444; }
.back { margin-top: 4px; }
</style>
