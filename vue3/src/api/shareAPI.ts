/**
 * 会员分享接口（含媒体上传：图片压缩直传 / 视频分片断点续传）
 */
import { request } from './request'
import axios from 'axios'

export interface ShareItem {
  id: number
  userId: number
  authorPhone: string
  /** 作者头像地址（后端下发后用于展示，空则显示默认头像） */
  authorAvatar?: string | null
  /** 作者头像缩略图（列表展示用，预览时取主图 authorAvatar） */
  authorAvatarThumb?: string | null
  title: string
  content?: string
  videoUrl?: string
  coverUrl?: string
  /** 图片URL列表（/uploads/...） */
  images: string[]
  /** 缩略图URL列表（列表展示用，约定 _thumb.jpg；历史数据/GIF 回退原图） */
  imagesThumb?: string[]
  likeCount: number
  commentCount: number
  viewCount: number
  liked: boolean
  createdAt: string
}

export interface ShareListResult {
  list: ShareItem[]
  total: number
}

export interface ShareCommentItem {
  id: number
  shareId: number
  userId: number
  /** 评论者昵称（可能为空，展示用 authorName） */
  authorNickname?: string | null
  /** 展示名：昵称优先，无昵称回退打码手机号，再无则「山达会员」（后端已组装） */
  authorName?: string
  authorPhone?: string | null
  /** 评论者头像地址（后端下发后用于展示，空则显示默认头像） */
  authorAvatar?: string | null
  /** 评论者头像缩略图（列表展示用，预览时取主图 authorAvatar） */
  authorAvatarThumb?: string | null
  content: string
  createdAt: string
}

export interface ShareCommentListResult {
  list: ShareCommentItem[]
  total: number
}

export interface CreateShareParams {
  title: string
  content?: string
  videoUrl?: string
  coverUrl?: string
  images?: string[]
}

// ==================== 管理端：分享内容管理 ====================

export type ShareStatus = 'NORMAL' | 'HIDDEN'

/** 管理端分享条目（含状态，手机号不脱敏） */
export interface AdminShareItem {
  id: number
  userId: number
  authorPhone?: string | null
  /** 作者头像地址（后端下发后用于展示，空则显示默认头像） */
  authorAvatar?: string | null
  /** 作者头像缩略图（列表展示用，预览时取主图 authorAvatar） */
  authorAvatarThumb?: string | null
  title: string
  content?: string | null
  videoUrl?: string | null
  coverUrl?: string | null
  images: string[]
  likeCount: number
  commentCount: number
  viewCount: number
  status: ShareStatus
  /** 是否对用户端可见（HIDDEN → false） */
  userVisible: boolean
  createdAt: string
}

export interface AdminShareListResult {
  list: AdminShareItem[]
  total: number
  normalCount: number
  hiddenCount: number
}

/** 上传鉴权/进度直传 axios 实例：与 request.ts 同源 baseURL，但保留 multipart 与进度回调 */
const uploadHttp = axios.create({ baseURL: '/api/v1', timeout: 600000 })

uploadHttp.interceptors.request.use((config) => {
  const token = localStorage.getItem('token')
  if (token) config.headers['Authorization'] = `Bearer ${token}`
  return config
})

export const shareAPI = {
  /** 分享列表（广场） */
  list(params: { page?: number; size?: number } = {}) {
    return request<ShareListResult>({
      method: 'get',
      url: '/share/list',
      params: { page: params.page || 1, size: params.size || 10 }
    })
  },

  /** 我的分享 */
  my(params: { page?: number; size?: number } = {}) {
    return request<ShareListResult>({
      method: 'get',
      url: '/share/my',
      params: { page: params.page || 1, size: params.size || 10 }
    })
  },

  /** 发布分享（仅 ACTIVE 会员卡可发布） */
  create(params: CreateShareParams) {
    return request<ShareItem>({
      method: 'post',
      url: '/share',
      data: params
    })
  },

  /** 点赞 / 取消点赞（toggle） */
  toggleLike(id: number) {
    return request<{ liked: boolean }>({
      method: 'post',
      url: `/share/${id}/like`
    })
  },

  /** 浏览数 +1（服务端按用户 60s 去重），返回 counted=false 表示窗口期内重复未计数 */
  view(id: number) {
    return request<{ counted: boolean }>({
      method: 'post',
      url: `/share/${id}/view`
    })
  },

  /** 评论列表 */
  comments(id: number, params: { page?: number; size?: number } = {}) {
    return request<ShareCommentListResult>({
      method: 'get',
      url: `/share/${id}/comments`,
      params: { page: params.page || 1, size: params.size || 20 }
    })
  },

  /** 发表评论 */
  comment(id: number, content: string) {
    return request<ShareCommentItem>({
      method: 'post',
      url: `/share/${id}/comment`,
      data: { content }
    })
  },

  // ==================== 媒体上传 ====================

  /**
   * 图片上传（分享图 / 视频封面帧），服务端会再压缩一次
   */
  uploadImage(file: File, onProgress?: (percent: number) => void) {
    const form = new FormData()
    form.append('file', file)
    return uploadHttp
      .request<{ success: boolean; message: string; data: { url: string; thumbUrl?: string | null } }>({
        method: 'post',
        url: '/share/upload/image',
        data: form,
        onUploadProgress: (e) => {
          if (onProgress && e.total) onProgress(Math.round((e.loaded / e.total) * 100))
        }
      })
      .then((r) => r.data)
  },

  /** 查询视频已上传分片（断点续传） */
  videoStatus(fileHash: string) {
    return uploadHttp
      .request<{ success: boolean; data: { uploaded: number[] } }>({
        method: 'get',
        url: '/share/upload/video/status',
        params: { fileHash }
      })
      .then((r) => r.data)
  },

  /** 上传单个分片 */
  uploadVideoChunk(params: {
    fileHash: string
    chunkIndex: number
    totalChunks: number
    fileName: string
    file: Blob
    onProgress?: (loadedBytes: number) => void
  }) {
    const form = new FormData()
    form.append('fileHash', params.fileHash)
    form.append('chunkIndex', String(params.chunkIndex))
    form.append('totalChunks', String(params.totalChunks))
    form.append('fileName', params.fileName)
    form.append('file', params.file)
    return uploadHttp
      .request<{ success: boolean; message?: string }>({
        method: 'post',
        url: '/share/upload/video/chunk',
        data: form,
        onUploadProgress: (e) => {
          if (params.onProgress) params.onProgress(e.loaded)
        }
      })
      .then((r) => r.data)
  },

  /** 合并分片成视频文件（服务端按需异步转码） */
  mergeVideo(params: { fileHash: string; fileName: string; totalChunks: number }) {
    return uploadHttp
      .request<{ success: boolean; message?: string; data: { url: string; transcoding: boolean } }>({
        method: 'post',
        url: '/share/upload/video/merge',
        data: params
      })
      .then((r) => r.data)
  }
}

/**
 * 管理端：分享广场 / 分享评论审核
 * 隐藏（HIDDEN）后用户端列表与「我的分享」都不再展示，实现违规内容下架
 */
export const adminShareAPI = {
  /** 分享列表（含已隐藏；status 传空表示全部） */
  list(params: { status?: ShareStatus | ''; keyword?: string; page?: number; size?: number } = {}) {
    return request<AdminShareListResult>({
      method: 'get',
      url: '/admin/shares',
      params: {
        status: params.status || undefined,
        keyword: params.keyword || undefined,
        page: params.page || 1,
        size: params.size || 10
      }
    })
  },

  /** 隐藏 / 恢复 */
  setStatus(id: number, status: ShareStatus) {
    return request<{ userVisible: boolean; message: string }>({
      method: 'put',
      url: `/admin/shares/${id}/status`,
      data: { status }
    })
  },

  /** 删除分享（连带清理评论与点赞） */
  remove(id: number) {
    return request({ method: 'delete', url: `/admin/shares/${id}` })
  },

  /** 某分享的评论列表 */
  comments(id: number, params: { page?: number; size?: number } = {}) {
    return request<ShareCommentListResult>({
      method: 'get',
      url: `/admin/shares/${id}/comments`,
      params: { page: params.page || 1, size: params.size || 20 }
    })
  },

  /** 删除单条评论 */
  removeComment(shareId: number, commentId: number) {
    return request({ method: 'delete', url: `/admin/shares/${shareId}/comments/${commentId}` })
  }
}

export default shareAPI
