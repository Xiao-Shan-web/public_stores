<template>
  <div class="share-page page-container">
    <PageHeader title="分享广场" />
    <div class="page-body">
      <!-- 顶部发布入口 -->
      <div class="publish-bar card">
        <div class="pb-left" @click="openPublish">
          <i class="fas fa-edit"></i>
          <span>分享你的训练时刻...</span>
        </div>
        <button class="btn-publish" @click="openPublish">
          <i class="fas fa-plus"></i>
          <span>发布</span>
        </button>
      </div>

      <!-- Tab：广场 / 我的 -->
      <div class="seg-tabs">
        <button :class="{ active: tab === 'plaza' }" @click="switchTab('plaza')">
          <i class="fas fa-fire"></i> 广场
        </button>
        <button :class="{ active: tab === 'mine' }" @click="switchTab('mine')">
          <i class="fas fa-user"></i> 我的
        </button>
      </div>

      <!-- 骨架屏（首次加载，避免整页空白/转圈） -->
      <div v-if="loading && !shares.length" class="share-list">
        <div v-for="n in 3" :key="'sk' + n" class="share-item card skeleton-card">
          <div class="sk-author">
            <div class="sk-circle shimmer"></div>
            <div class="sk-lines">
              <div class="sk-line shimmer" style="width: 40%"></div>
              <div class="sk-line shimmer" style="width: 25%"></div>
            </div>
          </div>
          <div class="sk-line shimmer" style="width: 70%; height: 18px; margin-bottom: 8px"></div>
          <div class="sk-line shimmer" style="width: 100%"></div>
          <div class="sk-line shimmer" style="width: 90%"></div>
          <div class="sk-grid">
            <div v-for="m in 3" :key="m" class="sk-box shimmer"></div>
          </div>
        </div>
      </div>

      <!-- 分享列表 -->
      <div v-else-if="shares.length" class="share-list">
        <div
          class="share-item card"
          v-for="item in shares"
          :key="item.id"
          :data-share-id="item.id"
        >
          <!-- 作者信息 -->
          <div class="share-author">
            <!-- 头像点击放大预览（看主图），阻止冒泡避免触发卡片点击 -->
            <div class="author-avatar" @click.stop>
              <UserAvatar
                :src="resolveAvatar(item.authorAvatarThumb || item.authorAvatar)"
                :preview-src="resolveAvatar(item.authorAvatar)"
                previewable
              />
            </div>
            <div class="author-info">
              <p class="author-name">{{ item.authorPhone }}</p>
              <p class="share-time">{{ formatTime(item.createdAt) }}</p>
            </div>
            <button class="poster-btn" @click.stop="openPoster(item)" title="生成分享海报">
              <i class="fas fa-image"></i> 海报
            </button>
          </div>

          <!-- 标题 + 内容 -->
          <h3 class="share-title">{{ item.title }}</h3>
          <p v-if="item.content" class="share-content">{{ item.content }}</p>

          <!-- 媒体：视频封面 / 图片九宫格（列表只加载缩略图与封面，不加载视频） -->
          <div v-if="item.videoUrl || (item.images && item.images.length)" class="share-media">
            <div v-if="item.videoUrl" class="video-wrap" @click="openVideoPlayer(item)">
              <img
                v-if="item.coverUrl"
                :src="item.coverUrl"
                class="share-video"
                loading="lazy"
                alt="视频封面"
              />
              <div v-else class="video-poster-fallback">
                <i class="fas fa-film"></i>
              </div>
              <span class="video-play-hint"><i class="fas fa-play"></i></span>
            </div>
            <div v-else class="img-grid" :class="'grid-' + gridCols(item.images)">
              <div
                v-for="(img, idx) in displayImages(item)"
                :key="idx"
                class="img-cell"
                @click="openPreview(originalImage(item, idx))"
              >
                <img
                  :src="img"
                  loading="lazy"
                  alt="分享图片"
                  @error="(e) => onThumbError(e, item, idx)"
                />
              </div>
            </div>
          </div>

          <!-- 互动按钮 -->
          <div class="share-actions">
            <button class="action-btn" :class="{ liked: item.liked }" @click="handleLike(item)">
              <i class="fas fa-heart"></i>
              <span>{{ item.likeCount }}</span>
            </button>
            <button class="action-btn" @click="openComments(item)">
              <i class="fas fa-comment"></i>
              <span>{{ item.commentCount }}</span>
            </button>
            <span class="action-views">
              <i class="fas fa-eye"></i>
              <span>{{ item.viewCount }}</span>
            </span>
          </div>
        </div>

        <!-- 加载更多 -->
        <button v-if="hasMore" class="btn-load-more" @click="loadMore" :disabled="loading">
          {{ loading ? '加载中...' : '加载更多' }}
        </button>
        <p v-else class="load-end">- 没有更多了 -</p>
      </div>

      <!-- 空态 -->
      <div v-else-if="!loading" class="empty-state">
        <i class="fas fa-share-alt"></i>
        <p>{{ tab === 'mine' ? '你还没有发布过分享' : '还没有会员分享' }}</p>
        <p class="empty-sub">开通会员卡，成为第一个分享者</p>
      </div>
    </div>

    <!-- 发布弹窗 -->
    <div v-if="showPublish" class="modal-mask" @click.self="closePublish">
      <div class="modal-card">
        <div class="modal-header">
          <h3>发布分享</h3>
          <i class="fas fa-times modal-close" @click="closePublish"></i>
        </div>
        <div class="modal-body">
          <input
            v-model="publishForm.title"
            class="publish-input"
            placeholder="标题（必填）"
            maxlength="50"
          />
          <textarea
            v-model="publishForm.content"
            class="publish-textarea"
            placeholder="说点什么..."
            maxlength="500"
            rows="4"
          ></textarea>

          <!-- 视频选择（与图片互斥） -->
          <div class="media-block">
            <div class="media-head">
              <span><i class="fas fa-film"></i> 视频</span>
              <label
                v-if="!videoFile && !videoUploadedUrl && !imageUrls.length"
                class="media-add"
              >
                <i class="fas fa-plus"></i> 选择视频
                <input type="file" accept="video/*" hidden @change="onPickVideo" />
              </label>
              <button
                v-else-if="(videoFile || videoUploadedUrl) && !videoUploading"
                class="media-remove"
                @click="removeVideo"
              >
                <i class="fas fa-times"></i> 移除
              </button>
            </div>
            <template v-if="videoFile || videoUploadedUrl">
              <p class="media-name">{{ videoFile?.name || videoUploadedUrl }}</p>
              <div v-if="videoUploading" class="progress-track">
                <div class="progress-fill" :style="{ width: videoProgress + '%' }"></div>
              </div>
              <p class="media-status">{{ videoStageText }}</p>
            </template>
            <p v-else class="media-status">与图片二选一，支持断点续传</p>
          </div>

          <!-- 图片选择 -->
          <div class="media-block">
            <div class="media-head">
              <span><i class="fas fa-images"></i> 图片（{{ imageUrls.length }}/9）</span>
              <label v-if="!videoFile && !videoUploadedUrl && imageUrls.length < 9" class="media-add">
                <i class="fas fa-plus"></i> 添加图片
                <input type="file" accept="image/*" multiple hidden @change="onPickImages" />
              </label>
            </div>
            <div v-if="imageUrls.length" class="pub-img-grid">
              <div v-for="(u, i) in imageUrls" :key="i" class="pub-img-cell">
                <img :src="u" alt="预览" />
                <i
                  v-if="!uploadingImages"
                  class="fas fa-times-circle pub-img-remove"
                  @click="removeImage(i)"
                ></i>
              </div>
              <div v-for="n in uploadingCount" :key="'u' + n" class="pub-img-cell uploading">
                <i class="fas fa-spinner fa-spin"></i>
              </div>
            </div>
          </div>

          <p class="pub-tip">
            <i class="fas fa-circle-info"></i>
            视频与图片二选一 · 仅 ACTIVE 会员可发布 · 内容会经违规审核
          </p>
        </div>
        <div class="modal-footer">
          <button class="btn-cancel" @click="closePublish">取消</button>
          <button class="btn-confirm" :disabled="publishing" @click="handlePublish">
            {{ publishing ? stageText : '发布' }}
          </button>
        </div>
      </div>
    </div>

    <!-- 图片统一走全局 ImagePreview 预览（useImagePreview.open） -->

    <!-- 视频放大播放弹窗（原生 controls：播放/暂停、进度条、全屏） -->
    <div v-if="playerItem" class="video-player-mask" @click.self="closeVideoPlayer">
      <video
        :src="playerItem.videoUrl"
        :poster="playerItem.coverUrl"
        controls
        autoplay
        playsinline
        class="video-player-full"
        @play="countView(playerItem)"
      ></video>
      <i class="fas fa-times video-player-close" @click="closeVideoPlayer"></i>
    </div>

    <!-- 分享海报弹窗 -->
    <div v-if="showPoster" class="modal-mask" @click.self="showPoster = false">
      <div class="modal-card poster-modal">
        <div class="modal-header">
          <h3>分享海报</h3>
          <i class="fas fa-times modal-close" @click="showPoster = false"></i>
        </div>
        <div class="modal-body poster-body">
          <div v-if="posterGenerating" class="poster-loading">
            <i class="fas fa-spinner fa-spin"></i>
            <p>海报生成中...</p>
          </div>
          <img v-else-if="posterDataUrl" :src="posterDataUrl" class="poster-img" alt="分享海报" />
        </div>
        <div class="modal-footer">
          <button class="btn-cancel" @click="showPoster = false">关闭</button>
          <a
            v-if="posterDataUrl"
            class="btn-confirm poster-save"
            :href="posterDataUrl"
            :download="'share-' + posterShareId + '.png'"
          >
            保存图片
          </a>
        </div>
      </div>
    </div>

    <!-- 评论弹窗 -->
    <div v-if="showComments" class="modal-mask" @click.self="closeComments">
      <div class="modal-card comment-modal">
        <div class="modal-header">
          <h3>评论 {{ currentShare?.commentCount || 0 }}</h3>
          <i class="fas fa-times modal-close" @click="closeComments"></i>
        </div>
        <div class="modal-body comment-body">
          <div v-if="comments.length" class="comment-list">
            <div class="comment-item" v-for="c in comments" :key="c.id">
              <div class="cmt-avatar">
                <UserAvatar
                  :src="resolveAvatar(c.authorAvatarThumb || c.authorAvatar)"
                  :preview-src="resolveAvatar(c.authorAvatar)"
                  previewable
                />
              </div>
              <div class="cmt-main">
                <p class="cmt-name">{{ c.authorName || '山达会员' }}</p>
                <p class="cmt-content">{{ c.content }}</p>
                <p class="cmt-time">{{ formatTime(c.createdAt) }}</p>
              </div>
            </div>
          </div>
          <div v-else class="comment-empty">
            <p>还没有评论，来说点什么吧</p>
          </div>
        </div>
        <div class="comment-input-bar">
          <input
            v-model="commentText"
            placeholder="发表评论..."
            @keyup.enter="handleComment"
            maxlength="200"
          />
          <button :disabled="!commentText.trim() || commenting" @click="handleComment">
            发送
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { shareAPI, type ShareItem, type ShareCommentItem } from '@/api/shareAPI'
import { useAuthStore } from '@/stores/auth'
import message from '@/utils/message'
import PageHeader from '@/components/PageHeader.vue'
import UserAvatar from '@/components/UserAvatar.vue'
import { resolveAvatar } from '@/utils/avatar'
import { useImagePreview } from '@/composables/useImagePreview'

const authStore = useAuthStore()

/** 广场 / 我的 双列表 */
const tab = ref<'plaza' | 'mine'>('plaza')
const shares = ref<ShareItem[]>([])
const loading = ref(false)
const page = ref(1)
const size = 10
const total = ref(0)
const hasMore = computed(() => shares.value.length < total.value)

// 发布弹窗
const showPublish = ref(false)
const publishing = ref(false)
const publishForm = reactive({ title: '', content: '' })

// 媒体上传状态
const videoFile = ref<File | null>(null)
const videoUploadedUrl = ref('')
const videoTranscoding = ref(false)
const videoUploading = ref(false)
const videoProgress = ref(0)
const videoStage = ref('')
const imageUrls = ref<string[]>([])
const uploadingImages = ref(false)
const uploadingCount = ref(0)

/** 视频分片大小 5MB（与后端 MAX_CHUNK_BYTES=6MB 对应） */
const CHUNK_SIZE = 5 * 1024 * 1024
/** 视频大小上限 100MB（与后端 multipart 配置一致） */
const MAX_VIDEO_BYTES = 100 * 1024 * 1024
/** 图片上限 */
const MAX_IMAGES = 9

const stageText = computed(() => videoStage.value || '发布中...')
const videoStageText = computed(() => {
  if (videoUploading.value) return `上传中 ${videoProgress.value}%（断点续传，中断后可继续）`
  if (videoTranscoding.value) return '视频处理中，发布后即可播放'
  if (videoUploadedUrl.value) return '视频已就绪'
  return '待上传'
})

// 评论弹窗
const showComments = ref(false)
const currentShare = ref<ShareItem | null>(null)
const comments = ref<ShareCommentItem[]>([])
const commentText = ref('')
const commenting = ref(false)

// 图片放大预览统一走全局 ImagePreview（遮罩 / 手势 / 失败回退由组件统一处理）
const { open: openImagePreview } = useImagePreview()

// 视频放大播放：点击流内视频封面进入弹窗，原生 controls 提供播放/暂停、进度条、全屏
const playerItem = ref<ShareItem | null>(null)
const openVideoPlayer = (item: ShareItem) => {
  playerItem.value = item
}
const closeVideoPlayer = () => {
  playerItem.value = null
}

// 海报
const showPoster = ref(false)
const posterGenerating = ref(false)
const posterDataUrl = ref('')
const posterShareId = ref<number>(0)

// 浏览数：会话内每个分享只计一次
const viewedIds = new Set<number>()
let feedObserver: IntersectionObserver | null = null

const formatTime = (t: string) => {
  if (!t) return ''
  return t.replace('T', ' ').substring(0, 16)
}

/** 九宫格列数：1 图 1 列、2/4 图 2 列、其余 3 列 */
const gridCols = (images: string[]) => {
  const n = images?.length || 0
  if (n === 1) return 1
  if (n === 2 || n === 4) return 2
  return 3
}

/** 列表展示用缩略图地址（后端 imagesThumb 优先，兜底原图） */
const displayImages = (item: ShareItem) => {
  const thumbs = item.imagesThumb
  return thumbs && thumbs.length ? thumbs : item.images
}
/** 详情/预览用原图地址 */
const originalImage = (item: ShareItem, idx: number) => item.images[idx] || ''
/** 缩略图加载失败（历史数据无缩略图）回退原图，避免破图；原图再失败则保留占位 */
const onThumbError = (e: Event, item: ShareItem, idx: number) => {
  const img = e.target as HTMLImageElement
  if (img.dataset.fallback) return
  const orig = item.images[idx]
  if (orig) {
    img.dataset.fallback = '1'
    img.src = orig
  }
}

const switchTab = (t: 'plaza' | 'mine') => {
  if (tab.value === t) return
  tab.value = t
  page.value = 1
  shares.value = []
  total.value = 0
  loadList()
}

const loadList = async () => {
  loading.value = true
  try {
    const res =
      tab.value === 'mine'
        ? await shareAPI.my({ page: page.value, size })
        : await shareAPI.list({ page: page.value, size })
    if (res.success) {
      const incoming = (res.data.list || []).map((it) => ({
        ...it,
        images: it.images || []
      }))
      if (page.value === 1) shares.value = incoming
      else shares.value.push(...incoming)
      total.value = res.data.total || 0
      // 新渲染的条目挂上浏览数观察
      nextTick(() => observeFeedItems())
    }
  } catch {
    /* 静默：拦截器已提示 */
  } finally {
    loading.value = false
  }
}

const loadMore = () => {
  page.value++
  loadList()
}

// ==================== 浏览数 ====================

/**
 * 列表条目进入视口即计一次浏览（会话内去重），服务端再按用户 60s 窗口兜底去重
 */
const observeFeedItems = () => {
  if (!feedObserver) {
    feedObserver = new IntersectionObserver(
      (entries) => {
        for (const en of entries) {
          if (!en.isIntersecting) continue
          const id = Number((en.target as HTMLElement).dataset.shareId)
          feedObserver?.unobserve(en.target)
          if (!id || viewedIds.has(id)) continue
          viewedIds.add(id)
          const item = shares.value.find((s) => s.id === id)
          if (item) {
            shareAPI
              .view(id)
              .then((r) => {
                if (r.success && r.data?.counted) item.viewCount++
              })
              .catch(() => {
                /* 浏览计数失败不影响浏览 */
              })
          }
        }
      },
      { threshold: 0.6 }
    )
  }
  document.querySelectorAll('.share-item[data-share-id]').forEach((el) => {
    const id = Number((el as HTMLElement).dataset.shareId)
    if (id && !viewedIds.has(id)) feedObserver?.observe(el)
  })
}

const countView = (item: ShareItem) => {
  // 视频起播时也补一次（若 IntersectionObserver 已计则服务端去重）
  if (viewedIds.has(item.id)) return
  viewedIds.add(item.id)
  shareAPI
    .view(item.id)
    .then((r) => {
      if (r.success && r.data?.counted) item.viewCount++
    })
    .catch(() => {})
}

// ==================== 发布 ====================

const openPublish = () => {
  if (!authStore.isLoggedIn) {
    message.warning('请先登录')
    return
  }
  publishForm.title = ''
  publishForm.content = ''
  resetMedia()
  showPublish.value = true
}

const closePublish = () => {
  if (videoUploading.value || uploadingImages.value) {
    message.warning('上传进行中，请稍候')
    return
  }
  showPublish.value = false
}

const resetMedia = () => {
  videoFile.value = null
  videoUploadedUrl.value = ''
  videoTranscoding.value = false
  videoUploading.value = false
  videoProgress.value = 0
  videoStage.value = ''
  imageUrls.value = []
}

const removeVideo = () => {
  if (videoUploading.value) {
    message.warning('视频上传中，请稍候')
    return
  }
  videoFile.value = null
  videoUploadedUrl.value = ''
  videoProgress.value = 0
}

const removeImage = (idx: number) => {
  imageUrls.value.splice(idx, 1)
}

/**
 * 图片选择：逐张压缩后上传（客户端 canvas 压缩 + 服务端二次压缩）
 */
const onPickImages = async (e: Event) => {
  const input = e.target as HTMLInputElement
  const files = Array.from(input.files || [])
  input.value = ''
  if (!files.length) return
  if (videoFile.value || videoUploadedUrl.value) {
    message.warning('已选择视频，图片与视频二选一')
    return
  }
  const remain = MAX_IMAGES - imageUrls.value.length
  if (remain <= 0) {
    message.warning(`图片最多 ${MAX_IMAGES} 张`)
    return
  }
  const picked = files.slice(0, remain)
  uploadingImages.value = true
  uploadingCount.value = picked.length
  try {
    for (const f of picked) {
      if (f.size > 10 * 1024 * 1024) {
        message.warning(`「${f.name}」超过 10MB，已跳过`)
        uploadingCount.value--
        continue
      }
      const compressed = await compressImage(f)
      const res = await shareAPI.uploadImage(compressed)
      if (res.success && res.data?.url) {
        imageUrls.value.push(res.data.url)
      } else {
        message.error(res.message || '图片上传失败')
      }
      uploadingCount.value--
    }
  } catch (err: any) {
    message.error(err?.message || '图片上传失败')
  } finally {
    uploadingImages.value = false
    uploadingCount.value = 0
  }
}

/**
 * 客户端图片压缩：长边 ≤1600，JPEG 0.85；GIF 保留原样
 */
const compressImage = (file: File): Promise<File> =>
  new Promise((resolve) => {
    if (file.type === 'image/gif') {
      resolve(file)
      return
    }
    const url = URL.createObjectURL(file)
    const img = new Image()
    img.onload = () => {
      try {
        const MAX_EDGE = 1600
        const scale = Math.min(1, MAX_EDGE / Math.max(img.width, img.height))
        const canvas = document.createElement('canvas')
        canvas.width = Math.max(1, Math.round(img.width * scale))
        canvas.height = Math.max(1, Math.round(img.height * scale))
        const ctx = canvas.getContext('2d')!
        ctx.fillStyle = '#fff'
        ctx.fillRect(0, 0, canvas.width, canvas.height)
        ctx.drawImage(img, 0, 0, canvas.width, canvas.height)
        canvas.toBlob(
          (blob) => {
            URL.revokeObjectURL(url)
            if (blob && blob.size < file.size) {
              resolve(new File([blob], file.name.replace(/\.\w+$/, '') + '.jpg', { type: 'image/jpeg' }))
            } else {
              resolve(file)
            }
          },
          'image/jpeg',
          0.85
        )
      } catch {
        URL.revokeObjectURL(url)
        resolve(file)
      }
    }
    img.onerror = () => {
      URL.revokeObjectURL(url)
      resolve(file)
    }
    img.src = url
  })

/** 选择视频：截帧封面 → 分片上传（断点续传）→ 合并 */
const onPickVideo = async (e: Event) => {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  if (imageUrls.value.length) {
    message.warning('已选择图片，图片与视频二选一')
    return
  }
  if (file.size > MAX_VIDEO_BYTES) {
    message.warning('视频不能超过 100MB')
    return
  }
  videoFile.value = file
  videoUploadedUrl.value = ''
  videoProgress.value = 0

  // 1. 截帧作为封面（失败不阻断发布）
  videoStage.value = '截取封面...'
  const poster = await captureVideoPoster(file)
  let coverUrl: string | undefined
  if (poster) {
    try {
      const res = await shareAPI.uploadImage(poster)
      if (res.success && res.data?.url) coverUrl = res.data.url
    } catch {
      /* 封面上传失败可忽略 */
    }
  }
  publishFormCover.value = coverUrl

  // 2. 分片上传 + 断点续传
  try {
    const fileHash = await fileFingerprint(file)
    const totalChunks = Math.ceil(file.size / CHUNK_SIZE)

    videoUploading.value = true
    videoStage.value = '上传中'
    let uploadedSet = new Set<number>()
    try {
      const st = await shareAPI.videoStatus(fileHash)
      if (st.success && Array.isArray(st.data?.uploaded)) {
        uploadedSet = new Set(st.data.uploaded)
        if (uploadedSet.size) message.info(`检测到未完成上传，续传剩余 ${totalChunks - uploadedSet.size} 片`)
      }
    } catch {
      /* 状态查询失败按全新上传处理 */
    }

    let doneBytes = [...uploadedSet].length * CHUNK_SIZE
    for (let i = 0; i < totalChunks; i++) {
      if (uploadedSet.has(i)) continue
      const start = i * CHUNK_SIZE
      const end = Math.min(start + CHUNK_SIZE, file.size)
      const blob = file.slice(start, end)
      await shareAPI.uploadVideoChunk({
        fileHash,
        chunkIndex: i,
        totalChunks,
        fileName: file.name,
        file: blob,
        onProgress: (loaded) => {
          const total = Math.max(file.size, 1)
          videoProgress.value = Math.min(99, Math.floor(((doneBytes + loaded) / total) * 100))
        }
      })
      doneBytes += end - start
      videoProgress.value = Math.min(99, Math.floor((doneBytes / file.size) * 100))
    }

    // 3. 合并
    videoStage.value = '合并中...'
    videoUploading.value = false
    const merged = await shareAPI.mergeVideo({
      fileHash,
      fileName: file.name,
      totalChunks
    })
    if (merged.success && merged.data?.url) {
      videoUploadedUrl.value = merged.data.url
      videoTranscoding.value = !!merged.data.transcoding
      videoProgress.value = 100
    } else {
      message.error(merged.message || '视频合并失败')
      videoFile.value = null
    }
  } catch (err: any) {
    message.error(err?.message || '视频上传失败，可稍后重试（已传分片会保留）')
    videoUploading.value = false
  } finally {
    videoStage.value = ''
  }
}

/** 视频封面暂存（不放在 publishForm 里以便重置） */
const publishFormCover = ref<string | undefined>(undefined)

/**
 * 视频截帧：取 0.8s 处画面转 JPEG
 */
const captureVideoPoster = (file: File): Promise<File | null> =>
  new Promise((resolve) => {
    const url = URL.createObjectURL(file)
    const video = document.createElement('video')
    let settled = false
    const finish = (f: File | null) => {
      if (settled) return
      settled = true
      URL.revokeObjectURL(url)
      resolve(f)
    }
    video.muted = true
    video.playsInline = true
    video.preload = 'auto'
    video.src = url
    video.onloadeddata = () => {
      const dur = Number.isFinite(video.duration) ? video.duration : 1
      video.currentTime = Math.min(0.8, dur / 3)
    }
    video.onseeked = () => {
      try {
        const canvas = document.createElement('canvas')
        canvas.width = video.videoWidth
        canvas.height = video.videoHeight
        if (!canvas.width || !canvas.height) return finish(null)
        canvas.getContext('2d')!.drawImage(video, 0, 0)
        canvas.toBlob(
          (b) => finish(b ? new File([b], 'cover.jpg', { type: 'image/jpeg' }) : null),
          'image/jpeg',
          0.85
        )
      } catch {
        finish(null)
      }
    }
    video.onerror = () => finish(null)
    setTimeout(() => finish(null), 8000)
  })

/**
 * 文件指纹：name|size|lastModified 的 SHA-256（同一文件重复选择可续传；
 * 相比全文件哈希无内存/耗时开销，碰撞概率对本场景可忽略）
 */
const fileFingerprint = async (file: File): Promise<string> => {
  const meta = `${file.name}|${file.size}|${file.lastModified}`
  try {
    if (window.crypto?.subtle) {
      const digest = await window.crypto.subtle.digest('SHA-256', new TextEncoder().encode(meta))
      return Array.from(new Uint8Array(digest))
        .map((b) => b.toString(16).padStart(2, '0'))
        .join('')
        .slice(0, 40)
    }
  } catch {
    /* 降级简单哈希 */
  }
  let h = 5381
  for (let i = 0; i < meta.length; i++) {
    h = ((h << 5) + h + meta.charCodeAt(i)) | 0
  }
  return 'fb' + Math.abs(h).toString(16).padStart(10, '0')
}

const handlePublish = async () => {
  if (!publishForm.title.trim()) {
    message.warning('请填写标题')
    return
  }
  if (!imageUrls.value.length && !videoUploadedUrl.value) {
    message.warning('请添加图片或视频')
    return
  }
  if (videoFile.value && !videoUploadedUrl.value) {
    message.warning('视频还在上传中，请等待完成')
    return
  }
  publishing.value = true
  videoStage.value = ''
  try {
    const res = await shareAPI.create({
      title: publishForm.title.trim(),
      content: publishForm.content.trim(),
      videoUrl: videoUploadedUrl.value || undefined,
      coverUrl: publishFormCover.value,
      images: imageUrls.value.length ? [...imageUrls.value] : undefined
    })
    if (res.success) {
      message.success('发布成功')
      showPublish.value = false
      resetMedia()
      publishFormCover.value = undefined
      page.value = 1
      tab.value = 'mine'
      shares.value = []
      loadList()
    } else {
      message.error(res.message || '发布失败')
    }
  } catch (e: any) {
    message.error(e.message || '发布失败')
  } finally {
    publishing.value = false
  }
}

// ==================== 点赞 / 评论 ====================

const handleLike = async (item: ShareItem) => {
  try {
    const res = await shareAPI.toggleLike(item.id)
    if (res.success) {
      item.liked = res.data.liked
      item.likeCount += res.data.liked ? 1 : -1
      if (item.likeCount < 0) item.likeCount = 0
    }
  } catch (e: any) {
    message.error(e.message || '操作失败')
  }
}

const openComments = async (item: ShareItem) => {
  currentShare.value = item
  showComments.value = true
  comments.value = []
  commentText.value = ''
  await loadComments(item.id)
}

const closeComments = () => {
  showComments.value = false
  currentShare.value = null
}

const loadComments = async (shareId: number) => {
  try {
    const res = await shareAPI.comments(shareId, { page: 1, size: 50 })
    if (res.success) comments.value = res.data.list || []
  } catch {
    /* 静默 */
  }
}

const handleComment = async () => {
  if (!currentShare.value) return
  if (!commentText.value.trim()) {
    message.warning('请填写评论内容')
    return
  }
  commenting.value = true
  try {
    const res = await shareAPI.comment(currentShare.value.id, commentText.value.trim())
    if (res.success) {
      comments.value.unshift(res.data)
      currentShare.value.commentCount++
      commentText.value = ''
      message.success('评论成功')
    } else {
      message.error(res.message || '评论失败')
    }
  } catch (e: any) {
    message.error(e.message || '评论失败')
  } finally {
    commenting.value = false
  }
}

// ==================== 图片预览 / 海报 ====================

const openPreview = (url: string) => {
  openImagePreview(url)
}

const openPoster = async (item: ShareItem) => {
  posterShareId.value = item.id
  posterDataUrl.value = ''
  showPoster.value = true
  posterGenerating.value = true
  try {
    posterDataUrl.value = await renderPoster(item)
  } catch {
    message.error('海报生成失败')
    showPoster.value = false
  } finally {
    posterGenerating.value = false
  }
}

/**
 * 文本换行排版：按 maxWidth 折行，最多 maxLines 行，超出加省略号
 */
const wrapText = (ctx: CanvasRenderingContext2D, text: string, maxWidth: number, maxLines: number) => {
  const lines: string[] = []
  let current = ''
  for (const ch of text || '') {
    if (ch === '\n') {
      lines.push(current)
      current = ''
      if (lines.length >= maxLines) break
      continue
    }
    if (ctx.measureText(current + ch).width > maxWidth) {
      lines.push(current)
      current = ch
      if (lines.length >= maxLines) break
    } else {
      current += ch
    }
  }
  if (lines.length < maxLines && current) lines.push(current)
  if (lines.length >= maxLines && (current || (text || '').length > lines.join('').length)) {
    const last = lines[maxLines - 1]
    if (last) lines[maxLines - 1] = last.slice(0, -1) + '…'
  }
  return lines
}

const loadImageEl = (src: string): Promise<HTMLImageElement | null> =>
  new Promise((resolve) => {
    const img = new Image()
    img.crossOrigin = 'anonymous'
    img.onload = () => resolve(img)
    img.onerror = () => resolve(null)
    img.src = src
  })

/**
 * 生成分享海报：750x1200，品牌头 + 媒体图 + 标题/正文 + 互动数据 + 底部口号
 */
const renderPoster = async (item: ShareItem): Promise<string> => {
  const W = 750
  const H = 1200
  const canvas = document.createElement('canvas')
  canvas.width = W
  canvas.height = H
  const ctx = canvas.getContext('2d')!

  // 背景
  ctx.fillStyle = '#ffffff'
  ctx.fillRect(0, 0, W, H)

  // 品牌头（渐变）
  const grad = ctx.createLinearGradient(0, 0, W, 220)
  grad.addColorStop(0, '#ff6b35')
  grad.addColorStop(1, '#ff3d6e')
  ctx.fillStyle = grad
  ctx.fillRect(0, 0, W, 220)
  ctx.fillStyle = '#ffffff'
  ctx.font = '800 56px sans-serif'
  ctx.textAlign = 'center'
  ctx.fillText('山达健身', W / 2, 100)
  ctx.font = '600 26px sans-serif'
  ctx.globalAlpha = 0.92
  ctx.fillText('燃动每一刻 · 会员分享', W / 2, 150)
  ctx.globalAlpha = 1

  // 媒体图（封面 > 第一张图 > 纯色占位）
  const mediaSrc = item.coverUrl || (item.images && item.images[0])
  let mediaY = 260
  if (mediaSrc) {
    const img = await loadImageEl(mediaSrc)
    const mw = W - 80
    const mh = 520
    if (img) {
      const scale = Math.max(mw / img.width, mh / img.height)
      const sw = mw / scale
      const sh = mh / scale
      const sx = (img.width - sw) / 2
      const sy = (img.height - sh) / 2
      ctx.save()
      roundRectPath(ctx, 40, mediaY, mw, mh, 24)
      ctx.clip()
      ctx.drawImage(img, sx, sy, sw, sh, 40, mediaY, mw, mh)
      ctx.restore()
    } else {
      ctx.fillStyle = '#f5f5f5'
      roundRectPath(ctx, 40, mediaY, mw, mh, 24)
      ctx.fill()
    }
    mediaY += mh + 40
  }

  // 标题（最多 2 行）
  ctx.textAlign = 'left'
  ctx.fillStyle = '#1a1a2e'
  ctx.font = '800 40px sans-serif'
  const titleLines = wrapText(ctx, item.title, W - 100, 2)
  titleLines.forEach((l, i) => ctx.fillText(l, 50, mediaY + 40 + i * 54))
  let y = mediaY + 40 + titleLines.length * 54 + 16

  // 正文（最多 4 行）
  if (item.content) {
    ctx.fillStyle = '#595959'
    ctx.font = '400 28px sans-serif'
    const contentLines = wrapText(ctx, item.content, W - 100, 4)
    contentLines.forEach((l, i) => ctx.fillText(l, 50, y + 30 + i * 42))
    y += 30 + contentLines.length * 42 + 10
  }

  // 互动数据
  y = Math.max(y + 30, 960)
  ctx.fillStyle = '#ff6b35'
  ctx.font = '600 30px sans-serif'
  const stats = `❤ ${item.likeCount}   💬 ${item.commentCount}   👁 ${item.viewCount}`
  ctx.fillText(stats, 50, y)

  // 底部口号
  ctx.fillStyle = '#bfbfbf'
  ctx.font = '400 24px sans-serif'
  ctx.textAlign = 'center'
  ctx.fillText('山达健身 APP · 开卡入场 · 燃动每一刻', W / 2, H - 50)

  return canvas.toDataURL('image/png')
}

const roundRectPath = (ctx: CanvasRenderingContext2D, x: number, y: number, w: number, h: number, r: number) => {
  ctx.beginPath()
  ctx.moveTo(x + r, y)
  ctx.arcTo(x + w, y, x + w, y + h, r)
  ctx.arcTo(x + w, y + h, x, y + h, r)
  ctx.arcTo(x, y + h, x, y, r)
  ctx.arcTo(x, y, x + w, y, r)
  ctx.closePath()
}

// ==================== 生命周期 ====================

onMounted(() => {
  // 前置条件用 token（同步可得）而非 isLoggedIn（整页刷新时异步恢复），否则直接打开本页会空列表
  if (authStore.token) loadList()
})

onBeforeUnmount(() => {
  feedObserver?.disconnect()
  feedObserver = null
})
</script>

<style scoped>
.share-page {
  display: flex;
  flex-direction: column;
}

.page-body {
  padding: var(--spacing-md);
}

/* 发布入口 */
.publish-bar {
  display: flex;
  align-items: center;
  padding: var(--spacing-md);
  margin-bottom: var(--spacing-sm);
}

.pb-left {
  flex: 1;
  display: flex;
  align-items: center;
  color: var(--color-text-placeholder);
  font-size: 14px;
  cursor: pointer;
}

.pb-left i {
  margin-right: var(--spacing-sm);
  color: var(--color-primary);
}

.btn-publish {
  background: var(--gradient-primary);
  color: #fff;
  border: none;
  border-radius: var(--radius-md);
  padding: 8px 16px;
  font-size: 14px;
  font-weight: 600;
  display: flex;
  align-items: center;
  gap: 4px;
  box-shadow: var(--shadow-active);
}

.btn-publish:active {
  transform: scale(0.97);
}

/* Tab 切换 */
.seg-tabs {
  display: flex;
  background: var(--color-bg-gray);
  border-radius: var(--radius-md);
  padding: 4px;
  margin-bottom: var(--spacing-md);
}

.seg-tabs button {
  flex: 1;
  border: none;
  background: transparent;
  padding: 9px 0;
  font-size: 14px;
  font-weight: 600;
  color: var(--color-text-secondary);
  border-radius: 9px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  transition: all 0.2s;
}

.seg-tabs button.active {
  background: #fff;
  color: var(--color-primary);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
}

/* 分享列表 */
.share-list {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-md);
}

.share-item {
  padding: var(--spacing-md);
}

.share-author {
  display: flex;
  align-items: center;
  margin-bottom: var(--spacing-sm);
}

.author-avatar {
  width: 38px;
  height: 38px;
  border-radius: 50%;
  overflow: hidden;
  background: var(--color-bg-gray);
  color: var(--color-primary);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  margin-right: var(--spacing-sm);
}

.author-info {
  flex: 1;
}

.author-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--color-text);
}

.share-time {
  font-size: 12px;
  color: var(--color-text-placeholder);
  margin-top: 2px;
}

.poster-btn {
  background: var(--color-bg-gray);
  border: none;
  border-radius: var(--radius-sm);
  padding: 6px 10px;
  font-size: 12px;
  color: var(--color-text-secondary);
  display: flex;
  align-items: center;
  gap: 4px;
}

.poster-btn:active {
  color: var(--color-primary);
}

.share-title {
  font-size: 16px;
  font-weight: 700;
  color: var(--color-dark);
  margin-bottom: var(--spacing-xs);
}

.share-content {
  font-size: 14px;
  color: var(--color-text-secondary);
  line-height: 1.5;
  margin-bottom: var(--spacing-sm);
  white-space: pre-wrap;
  word-break: break-word;
}

.share-media {
  margin-bottom: var(--spacing-sm);
  border-radius: var(--radius-md);
  overflow: hidden;
}

.share-video {
  width: 100%;
  display: block;
  max-height: 320px;
  background: #000;
}

/* 视频封面容器：点击进入放大播放弹窗 */
.video-wrap {
  position: relative;
  cursor: pointer;
}

.video-play-hint {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 44px;
  color: #fff;
  background: rgba(0, 0, 0, 0.22);
  pointer-events: none;
}

/* 视频放大播放弹窗：原生 controls 提供播放/暂停、进度条、全屏，移动端手势友好 */
.video-player-mask {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.92);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1200;
  padding: 16px;
}

.video-player-full {
  max-width: 96vw;
  max-height: 84vh;
  width: auto;
  height: auto;
  border-radius: 8px;
  background: #000;
  outline: none;
  display: block;
}

.video-player-close {
  position: absolute;
  top: 14px;
  right: 18px;
  font-size: 26px;
  color: #fff;
  cursor: pointer;
  opacity: 0.85;
}

.video-player-close:hover {
  opacity: 1;
}

/* 图片九宫格 */
.img-grid {
  display: grid;
  gap: 4px;
}

.img-grid.grid-1 {
  grid-template-columns: 1fr;
}

.img-grid.grid-2 {
  grid-template-columns: repeat(2, 1fr);
}

.img-grid.grid-3 {
  grid-template-columns: repeat(3, 1fr);
}

.img-cell {
  aspect-ratio: 1;
  overflow: hidden;
  background: var(--color-bg-gray);
  border-radius: 4px;
  position: relative;
}

.img-grid.grid-1 .img-cell {
  aspect-ratio: 4 / 3;
}

/* 图片加载前显示 shimmer 占位，加载完成后 img 自然覆盖 */
.img-cell::before {
  content: '';
  position: absolute;
  inset: 0;
  background: linear-gradient(90deg, #f0f0f0 25%, #e6e6e6 50%, #f0f0f0 75%);
  background-size: 200% 100%;
  animation: shimmer 1.4s infinite linear;
  z-index: 0;
}

.img-cell img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
  position: relative;
  z-index: 1;
}

/* 视频无封面兜底占位 */
.video-poster-fallback {
  width: 100%;
  height: 200px;
  background: #000;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #555;
  font-size: 40px;
}

/* 骨架屏 shimmer 动画 */
.shimmer {
  background: linear-gradient(90deg, #f0f0f0 25%, #e6e6e6 50%, #f0f0f0 75%);
  background-size: 200% 100%;
  animation: shimmer 1.4s infinite linear;
  border-radius: 4px;
}

@keyframes shimmer {
  0% { background-position: 200% 0; }
  100% { background-position: -200% 0; }
}

.skeleton-card {
  padding: var(--spacing-md);
}

.sk-author {
  display: flex;
  align-items: center;
  margin-bottom: var(--spacing-sm);
}

.sk-circle {
  width: 38px;
  height: 38px;
  border-radius: 50%;
  margin-right: var(--spacing-sm);
  flex-shrink: 0;
}

.sk-lines {
  flex: 1;
}

.sk-line {
  height: 12px;
  margin-bottom: 6px;
}

.sk-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 4px;
  margin-top: var(--spacing-sm);
}

.sk-box {
  aspect-ratio: 1;
  border-radius: 4px;
}

.share-actions {
  display: flex;
  align-items: center;
  gap: var(--spacing-lg);
  padding-top: var(--spacing-sm);
  border-top: 1px solid var(--color-border);
}

.action-btn {
  background: none;
  border: none;
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--color-text-secondary);
  font-size: 14px;
  padding: 4px 0;
  cursor: pointer;
}

.action-btn.liked {
  color: var(--color-danger, #ff4d4f);
}

.action-views {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--color-text-placeholder);
  font-size: 13px;
}

/* 加载更多 */
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

.empty-sub {
  font-size: 12px;
  margin-top: var(--spacing-xs);
}

/* 弹窗 */
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
  max-height: 85vh;
  background: #fff;
  border-radius: var(--radius-lg);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.modal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: var(--spacing-md) var(--spacing-lg);
  border-bottom: 1px solid var(--color-border);
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

.publish-input {
  width: 100%;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  padding: 10px;
  font-size: 14px;
  margin-bottom: var(--spacing-sm);
  outline: none;
}

.publish-input:focus {
  border-color: var(--color-primary);
}

.publish-textarea {
  width: 100%;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  padding: 10px;
  font-size: 14px;
  margin-bottom: var(--spacing-sm);
  outline: none;
  resize: none;
  font-family: inherit;
}

.publish-textarea:focus {
  border-color: var(--color-primary);
}

/* 发布媒体区 */
.media-block {
  border: 1px dashed var(--color-border);
  border-radius: var(--radius-md);
  padding: var(--spacing-sm) var(--spacing-md);
  margin-bottom: var(--spacing-sm);
}

.media-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text);
}

.media-head i {
  color: var(--color-primary);
  margin-right: 4px;
}

.media-add {
  font-size: 12px;
  color: var(--color-primary);
  cursor: pointer;
  font-weight: 400;
  display: flex;
  align-items: center;
  gap: 3px;
}

.media-remove {
  background: none;
  border: none;
  color: var(--color-text-placeholder);
  font-size: 12px;
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 3px;
}

.media-name {
  font-size: 12px;
  color: var(--color-text-secondary);
  margin: 6px 0;
  word-break: break-all;
}

.media-status {
  font-size: 11px;
  color: var(--color-text-placeholder);
}

.progress-track {
  height: 6px;
  border-radius: 3px;
  background: var(--color-bg-gray);
  overflow: hidden;
  margin: 6px 0;
}

.progress-fill {
  height: 100%;
  background: var(--gradient-primary);
  border-radius: 3px;
  transition: width 0.2s;
}

.pub-img-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 6px;
  margin-top: 8px;
}

.pub-img-cell {
  position: relative;
  aspect-ratio: 1;
  border-radius: 6px;
  overflow: hidden;
  background: var(--color-bg-gray);
}

.pub-img-cell img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.pub-img-cell.uploading {
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--color-primary);
}

.pub-img-remove {
  position: absolute;
  top: 2px;
  right: 2px;
  font-size: 16px;
  color: rgba(0, 0, 0, 0.55);
  background: #fff;
  border-radius: 50%;
  cursor: pointer;
}

.pub-tip {
  font-size: 11px;
  color: var(--color-text-placeholder);
  line-height: 1.5;
}

.pub-tip i {
  margin-right: 4px;
}

.modal-footer {
  padding: var(--spacing-md) var(--spacing-lg);
  border-top: 1px solid var(--color-border);
  display: flex;
  gap: var(--spacing-sm);
}

.btn-cancel {
  flex: 1;
  background: var(--color-bg-gray);
  color: var(--color-text-secondary);
  border: none;
  border-radius: var(--radius-md);
  padding: 10px;
  font-size: 14px;
}

.btn-confirm {
  flex: 1;
  background: var(--gradient-primary);
  color: #fff;
  border: none;
  border-radius: var(--radius-md);
  padding: 10px;
  font-size: 14px;
  font-weight: 600;
  box-shadow: var(--shadow-active);
}

.btn-confirm:disabled {
  background: var(--color-text-placeholder);
  box-shadow: none;
}

/* 海报 */
.poster-modal {
  max-width: 380px;
}

.poster-body {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 300px;
}

.poster-loading {
  text-align: center;
  color: var(--color-text-placeholder);
  font-size: 13px;
}

.poster-loading i {
  font-size: 28px;
  color: var(--color-primary);
  margin-bottom: 8px;
  display: block;
}

.poster-img {
  width: 100%;
  border-radius: var(--radius-sm);
  box-shadow: var(--shadow-card);
}

.poster-save {
  text-align: center;
  text-decoration: none;
}

/* 评论弹窗 */
.comment-modal {
  max-width: 420px;
}

.comment-body {
  padding: 0;
}

.comment-list {
  padding: var(--spacing-md) var(--spacing-lg);
}

.comment-item {
  display: flex;
  padding: var(--spacing-sm) 0;
  border-bottom: 1px solid var(--color-border);
}

.comment-item:last-child {
  border-bottom: none;
}

.cmt-avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  overflow: hidden;
  background: var(--color-bg-gray);
  color: var(--color-primary);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  margin-right: var(--spacing-sm);
}

.cmt-name {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text);
}

.cmt-content {
  font-size: 14px;
  color: var(--color-text-secondary);
  margin: 4px 0;
  line-height: 1.5;
}

.cmt-time {
  font-size: 11px;
  color: var(--color-text-placeholder);
}

.comment-empty {
  text-align: center;
  padding: var(--spacing-xl) 0;
  color: var(--color-text-placeholder);
  font-size: 13px;
}

.comment-input-bar {
  display: flex;
  padding: var(--spacing-md) var(--spacing-lg);
  border-top: 1px solid var(--color-border);
  gap: var(--spacing-sm);
}

.comment-input-bar input {
  flex: 1;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  padding: 8px 12px;
  font-size: 14px;
  outline: none;
}

.comment-input-bar input:focus {
  border-color: var(--color-primary);
}

.comment-input-bar button {
  background: var(--gradient-primary);
  color: #fff;
  border: none;
  border-radius: var(--radius-md);
  padding: 0 16px;
  font-size: 14px;
  font-weight: 600;
}

.comment-input-bar button:disabled {
  background: var(--color-text-placeholder);
}
</style>
