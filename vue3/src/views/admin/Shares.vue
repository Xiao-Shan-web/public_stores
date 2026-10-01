<template>
  <div class="shares">
    <div class="filter-bar card">
      <select v-model="filter.status" @change="load(1)">
        <option value="">全部状态</option>
        <option value="NORMAL">正常（{{ normalCount }}）</option>
        <option value="HIDDEN">已隐藏（{{ hiddenCount }}）</option>
      </select>
      <input
        v-model="filter.keyword"
        class="kw-input"
        placeholder="标题 / 正文 / 作者手机号"
        @keyup.enter="load(1)"
      />
      <button class="op-btn" @click="load(1)">搜索</button>
      <button class="op-btn plain" @click="resetFilter">重置</button>
    </div>

    <div class="table-wrap card">
      <table class="data-table">
        <thead>
          <tr>
            <th>内容</th><th>作者</th><th>互动</th><th>状态</th><th>发布时间</th><th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="loading"><td colspan="6" class="empty-row">加载中…</td></tr>
          <tr v-for="s in list" :key="s.id">
            <td class="content-cell">
              <div class="content-wrap">
                <div v-if="thumb(s)" class="thumb-wrap" @click="openMedia(s)">
                  <img class="thumb" :src="thumb(s)!" alt="分享封面" loading="lazy" />
                  <span v-if="s.videoUrl" class="thumb-play"><i class="fas fa-play"></i></span>
                </div>
                <div
                  v-else
                  class="thumb thumb-empty"
                  :class="{ clickable: !!s.videoUrl }"
                  @click="s.videoUrl && openMedia(s)"
                >
                  <i :class="['fas', s.videoUrl ? 'fa-video' : 'fa-image']"></i>
                </div>
                <div class="content-text">
                  <div class="title-line">
                    {{ s.title }}
                    <span v-if="s.videoUrl" class="mini-tag">视频</span>
                    <span v-if="s.images?.length" class="mini-tag">图 {{ s.images.length }}</span>
                  </div>
                  <div class="excerpt">{{ s.content || '（无正文）' }}</div>
                  <div class="cell-sub">ID: {{ s.id }} ｜ 用户ID: {{ s.userId }}</div>
                </div>
              </div>
            </td>
            <td class="author-cell"><div class="s-avatar"><UserAvatar :src="resolveAvatar(s.authorAvatarThumb || s.authorAvatar)" :preview-src="resolveAvatar(s.authorAvatar)" previewable /></div><span>{{ s.authorPhone || '-' }}</span></td>
            <td>
              <div class="cell-sub">点赞 {{ s.likeCount }}</div>
              <div class="cell-sub">评论 {{ s.commentCount }}</div>
              <div class="cell-sub">浏览 {{ s.viewCount }}</div>
            </td>
            <td>
              <span class="status-tag" :class="s.userVisible ? 'tag-normal' : 'tag-hidden'">
                {{ s.userVisible ? '正常展示' : '已隐藏' }}
              </span>
            </td>
            <td class="time-cell">{{ fmt(s.createdAt) }}</td>
            <td>
              <div class="ops">
                <button class="op-btn" :class="s.userVisible ? 'warn' : 'primary'" @click="toggleStatus(s)">
                  {{ s.userVisible ? '隐藏' : '恢复' }}
                </button>
                <button class="op-btn plain" @click="openComments(s)">评论</button>
                <button class="op-btn danger" @click="remove(s)">删除</button>
              </div>
            </td>
          </tr>
          <tr v-if="!loading && !list.length">
            <td colspan="6" class="empty-row">暂无分享内容</td>
          </tr>
        </tbody>
      </table>

      <div class="pager">
        <button :disabled="page <= 1" @click="load(page - 1)">上一页</button>
        <span>第 {{ page }} 页 / 共 {{ Math.max(1, Math.ceil(total / 10)) }} 页（{{ total }} 条）</span>
        <button :disabled="page >= Math.ceil(total / 10)" @click="load(page + 1)">下一页</button>
      </div>
    </div>

    <!-- ============ 评论审核弹窗 ============ -->
    <div v-if="commentTarget" class="modal-mask" @click.self="closeComments">
      <div class="modal">
        <div class="modal-header">
          <span class="modal-title">评论审核 · {{ commentTarget.title }}</span>
          <button class="modal-close" @click="closeComments"><i class="fas fa-times"></i></button>
        </div>

        <div class="modal-body">
          <div v-for="c in comments" :key="c.id" class="comment-item">
            <div class="cmt-avatar"><UserAvatar :src="resolveAvatar(c.authorAvatarThumb || c.authorAvatar)" :preview-src="resolveAvatar(c.authorAvatar)" previewable /></div>
            <div class="comment-main">
              <div class="comment-head">
                <span class="comment-author">{{ c.authorName || c.authorPhone || '山达会员' }}</span>
                <span class="cell-sub">{{ fmt(c.createdAt) }}</span>
              </div>
              <div class="comment-content">{{ c.content }}</div>
            </div>
            <button class="op-btn danger" :disabled="busy" @click="removeComment(c.id)">删除</button>
          </div>
          <div v-if="!comments.length" class="empty-row">该分享暂无评论</div>
        </div>

        <div class="modal-footer">
          <div class="pager">
            <button :disabled="commentPage <= 1" @click="loadComments(commentPage - 1)">上一页</button>
            <span>第 {{ commentPage }} 页 / 共 {{ Math.max(1, Math.ceil(commentTotal / 20)) }} 页（{{ commentTotal }} 条）</span>
            <button :disabled="commentPage >= Math.ceil(commentTotal / 20)" @click="loadComments(commentPage + 1)">下一页</button>
          </div>
        </div>
      </div>
    </div>

    <!-- 图片放大预览统一走全局 ImagePreview（useImagePreview.open） -->

    <!-- ============ 视频播放弹窗（原生 controls：播放/暂停、进度条、全屏） ============ -->
    <div v-if="playerUrl" class="preview-mask" @click.self="closePlayer">
      <video :src="playerUrl" class="player-video" controls autoplay playsinline></video>
      <i class="fas fa-times preview-close" @click="closePlayer"></i>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { message } from '@/utils/message'
import { confirm } from '@/composables/useConfirm'
import { adminShareAPI } from '@/api/shareAPI'
import type { AdminShareItem, ShareCommentItem, ShareStatus } from '@/api/shareAPI'
import UserAvatar from '@/components/UserAvatar.vue'
import { resolveAvatar } from '@/utils/avatar'
import { useImagePreview } from '@/composables/useImagePreview'

const PAGE_SIZE = 10
const COMMENT_PAGE_SIZE = 20

const list = ref<AdminShareItem[]>([])
const page = ref(1)
const total = ref(0)
const normalCount = ref(0)
const hiddenCount = ref(0)
const loading = ref(false)
const busy = ref(false)

const filter = reactive<{ status: ShareStatus | ''; keyword: string }>({
  status: '',
  keyword: ''
})

/** 缩略图：优先封面，其次第一张图 */
const thumb = (s: AdminShareItem) => s.coverUrl || s.images?.[0] || ''

const load = async (p = 1) => {
  page.value = p
  loading.value = true
  try {
    const res = await adminShareAPI.list({
      status: filter.status,
      keyword: filter.keyword.trim(),
      page: p,
      size: PAGE_SIZE
    })
    if (res.success && res.data) {
      list.value = res.data.list
      total.value = res.data.total
      normalCount.value = res.data.normalCount
      hiddenCount.value = res.data.hiddenCount
      // 删除/筛选后当前页可能已越界，回退一页避免空白
      if (!list.value.length && p > 1) load(p - 1)
    }
  } finally {
    loading.value = false
  }
}

const resetFilter = () => {
  filter.status = ''
  filter.keyword = ''
  load(1)
}

/** 隐藏 / 恢复：失败原因由 request 拦截器统一提示 */
const toggleStatus = async (s: AdminShareItem) => {
  const next: ShareStatus = s.userVisible ? 'HIDDEN' : 'NORMAL'
  try {
    const res = await adminShareAPI.setStatus(s.id, next)
    if (res.success) {
      message.success(res.message || (next === 'NORMAL' ? '已恢复展示' : '已隐藏'))
      load(page.value)
    }
  } catch { /* 拦截器已提示 */ }
}

const remove = async (s: AdminShareItem) => {
  const ok = await confirm({
    title: '删除分享',
    content: `确定删除「${s.title}」吗？该分享的评论与点赞记录会一并删除，用户端不再展示。`
  })
  if (!ok) return
  try {
    const res = await adminShareAPI.remove(s.id)
    if (res.success) {
      message.success(res.message || '已删除')
      load(page.value)
    }
  } catch { /* 拦截器已提示 */ }
}

// ==================== 评论审核 ====================

const commentTarget = ref<AdminShareItem | null>(null)
const comments = ref<ShareCommentItem[]>([])
const commentPage = ref(1)
const commentTotal = ref(0)

const loadComments = async (p = 1) => {
  if (!commentTarget.value) return
  commentPage.value = p
  const res = await adminShareAPI.comments(commentTarget.value.id, { page: p, size: COMMENT_PAGE_SIZE })
  if (res.success && res.data) {
    comments.value = res.data.list
    commentTotal.value = res.data.total
  }
}

const openComments = (s: AdminShareItem) => {
  commentTarget.value = s
  comments.value = []
  commentTotal.value = 0
  loadComments(1)
}

const closeComments = () => {
  commentTarget.value = null
  comments.value = []
  // 清单里评论数可能已变化，刷新列表
  load(page.value)
}

const removeComment = async (commentId: number) => {
  if (!commentTarget.value) return
  const ok = await confirm({ title: '删除评论', content: '删除后用户端不再展示该评论，确定继续吗？' })
  if (!ok) return
  busy.value = true
  try {
    const res = await adminShareAPI.removeComment(commentTarget.value.id, commentId)
    if (res.success) {
      message.success(res.message || '评论已删除')
      await loadComments(commentPage.value)
    }
  } catch { /* 拦截器已提示 */ } finally {
    busy.value = false
  }
}

// ==================== 展示工具 ====================

/** 图片放大预览统一走全局 ImagePreview（遮罩 / 手势 / 失败回退由组件统一处理） */
const { open: openImagePreview } = useImagePreview()

/** 视频弹窗播放：点击封面/占位时，视频优先进入播放弹窗，纯图文走图片预览 */
const playerUrl = ref('')
const openMedia = (s: AdminShareItem) => {
  if (s.videoUrl) {
    playerUrl.value = s.videoUrl
  } else if (thumb(s)) {
    openImagePreview(thumb(s)!)
  }
}
const closePlayer = () => { playerUrl.value = '' }

const fmt = (t?: string | null) => (t ? t.replace('T', ' ').substring(0, 16) : '-')

onMounted(() => load(1))
</script>

<style scoped>
.filter-bar { display: flex; gap: 10px; padding: 12px; margin-bottom: 12px; flex-wrap: wrap; align-items: center; }
.filter-bar select, .kw-input {
  padding: 7px 10px; border: 1px solid #e5e5e5; border-radius: 6px; font-size: 13px;
}
.kw-input { width: 240px; }

.op-btn {
  padding: 6px 12px; font-size: 13px; border-radius: 6px; cursor: pointer;
  border: 1px solid var(--color-primary, #ff6a00); background: var(--color-primary, #ff6a00); color: #fff;
}
.op-btn.plain { background: #fff; color: #555; border-color: #e5e5e5; }
.op-btn.warn { background: #fff; color: #fa8c16; border-color: #fa8c16; }
.op-btn.danger { background: #fff; color: #f5222d; border-color: #f5222d; }
.op-btn:disabled { opacity: 0.5; cursor: not-allowed; }

/* ---------- 内容列 ---------- */
.content-cell { min-width: 260px; }
.content-wrap { display: flex; gap: 10px; align-items: flex-start; }
.thumb {
  width: 64px; height: 64px; object-fit: cover; border-radius: 8px;
  border: 1px solid #eee; flex-shrink: 0; cursor: zoom-in; display: block;
}
.thumb:hover { border-color: var(--color-primary, #ff6a00); }
.thumb-empty {
  display: flex; align-items: center; justify-content: center;
  background: #fafafa; color: #bbb; font-size: 20px; cursor: default;
}
.thumb-empty.clickable { cursor: pointer; }
.thumb-empty.clickable:hover { color: var(--color-primary, #ff6a00); }
/* 视频封面容器：封面 + 播放角标，点击进弹窗播放 */

/* 作者列：头像 + 手机号 */
.author-cell { display: flex; align-items: center; gap: 8px; }
.s-avatar {
  width: 30px; height: 30px; border-radius: 50%; overflow: hidden;
  background: var(--color-bg-gray); color: var(--color-text-placeholder);
  display: flex; align-items: center; justify-content: center; flex-shrink: 0;
}
.thumb-wrap { position: relative; flex-shrink: 0; cursor: pointer; }
.thumb-play {
  position: absolute; inset: 0; display: flex; align-items: center; justify-content: center;
  font-size: 18px; color: #fff; background: rgba(0, 0, 0, 0.28); border-radius: 8px;
}
/* 视频播放弹窗：原生 controls 提供播放/暂停、进度条、全屏 */
.player-video {
  max-width: min(92vw, 880px); max-height: 82vh; width: auto; height: auto;
  border-radius: 8px; background: #000; outline: none; display: block;
}
.content-text { min-width: 0; }
.title-line { font-weight: 600; color: #333; word-break: break-all; }
.excerpt {
  font-size: 12.5px; color: #888; margin-top: 4px; line-height: 1.5;
  display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical;
  overflow: hidden; word-break: break-all;
}
.cell-sub { font-size: 11.5px; color: #999; margin-top: 4px; }
.mini-tag {
  display: inline-block; margin-left: 6px; padding: 0 6px; border-radius: 4px;
  background: #e6f4ff; color: #1677ff; font-size: 11px; font-weight: 400;
}

.status-tag { padding: 2px 9px; border-radius: 9px; font-size: 12px; white-space: nowrap; }
.tag-normal { background: #f0fff0; color: #52c41a; }
.tag-hidden { background: #fff1f0; color: #f5222d; }

.time-cell { font-size: 12.5px; color: #666; white-space: nowrap; }
.ops { display: flex; gap: 6px; flex-wrap: wrap; }
.empty-row { text-align: center; color: #999; padding: 28px 0; font-size: 13px; }

.pager { display: flex; align-items: center; justify-content: center; gap: 14px; padding: 14px 0 4px; font-size: 13px; color: #888; }
.pager button { border: 1px solid #e5e5e5; background: #fff; border-radius: 6px; padding: 5px 14px; font-size: 13px; cursor: pointer; }
.pager button:disabled { color: #ccc; cursor: not-allowed; }

/* ---------- 评论弹窗 ---------- */
.modal-mask {
  position: fixed; inset: 0; z-index: 300; background: rgba(0, 0, 0, 0.45);
  display: flex; align-items: center; justify-content: center; padding: 24px;
}
.modal { width: 620px; max-width: 94vw; background: #fff; border-radius: 10px; overflow: hidden; display: flex; flex-direction: column; max-height: 82vh; }
.modal-header {
  display: flex; align-items: center; justify-content: space-between;
  padding: 14px 18px; border-bottom: 1px solid #f0f0f0;
}
.modal-title { font-size: 15px; font-weight: 700; color: #333; }
.modal-close { width: 28px; height: 28px; border-radius: 50%; border: none; background: none; color: #999; cursor: pointer; }
.modal-close:hover { background: #f5f5f5; color: #333; }
.modal-body { padding: 12px 18px; overflow-y: auto; flex: 1; }
.modal-footer { padding: 6px 18px 14px; border-top: 1px solid #f0f0f0; }

.comment-item {
  display: flex; align-items: flex-start; gap: 10px;
  padding: 10px 0; border-bottom: 1px dashed #f0f0f0;
}
.cmt-avatar { width: 34px; height: 34px; flex-shrink: 0; border-radius: 50%; overflow: hidden; background: #f0f0f0; }
.comment-main { flex: 1; min-width: 0; }
.comment-head { display: flex; align-items: center; gap: 10px; }
.comment-author { font-weight: 600; font-size: 13px; color: #333; }
.comment-content { margin-top: 4px; font-size: 13px; color: #555; line-height: 1.6; word-break: break-all; }

/* ---------- 图片放大预览 ---------- */
.preview-mask {
  position: fixed; inset: 0; z-index: 400; background: rgba(0, 0, 0, 0.85);
  display: flex; align-items: center; justify-content: center; padding: 24px;
}
.preview-close { position: absolute; top: 18px; right: 22px; font-size: 26px; color: #fff; cursor: pointer; opacity: 0.85; }
.preview-close:hover { opacity: 1; }
</style>
