/**
 * 平台治理接口：用户投诉 / 管理端投诉仲裁 / 风控事件 / 审计日志
 */
import { request } from './request'

// ==================== 投诉 ====================

export type ComplaintType = 'SERVICE' | 'ORDER' | 'ENTRY' | 'CARD' | 'OTHER'
export type ComplaintStatus = 'PENDING' | 'PROCESSING' | 'ARBITRATING' | 'RESOLVED' | 'CLOSED'

export interface Complaint {
  id: number
  userId: number
  type: ComplaintType
  title: string
  avatar?: string | null
  /** 投诉人头像缩略图（列表/详情关联 user_profiles 下发） */
  avatarThumb?: string | null
  content: string
  imagesJson?: string | null
  bizType?: string | null
  bizId?: string | null
  storeId?: number | null
  contactPhone?: string | null
  status: ComplaintStatus
  adminReply?: string | null
  handlerAdminId?: number | null
  handledAt?: string | null
  createdAt: string
  updatedAt?: string
}

export interface Arbitration {
  id: number
  complaintId: number
  userId: number
  reason: string
  status: 'INVESTIGATING' | 'RULING' | 'DONE'
  result?: 'SUPPORT_USER' | 'SUPPORT_PLATFORM' | 'PARTIAL' | null
  decision?: string | null
  compensationAmount?: number | null
  arbitratorAdminId?: number | null
  handledAt?: string | null
  createdAt: string
}

export interface ComplaintDetail {
  complaint: Complaint
  arbitration: Arbitration | null
}

export interface SubmitComplaintBody {
  type: ComplaintType
  title: string
  content: string
  images?: string[]
  bizType?: string
  bizId?: string
  contactPhone?: string
}

// ==================== 风控 ====================

export type RiskLevel = 'LOW' | 'MEDIUM' | 'HIGH'
export type RiskStatus = 'OPEN' | 'IGNORED' | 'HANDLED'

export interface RiskEvent {
  id: number
  eventType: string
  riskLevel: RiskLevel
  subjectType: 'USER' | 'ADMIN'
  subjectId?: number | null
  subjectName?: string | null
  detailJson?: string | null
  status: RiskStatus
  handlerId?: number | null
  handleRemark?: string | null
  handledAt?: string | null
  createdAt: string
}

// ==================== 审计 ====================

export interface AuditLog {
  id: number
  adminId?: number | null
  username?: string | null
  module: string
  action: string
  method: string
  uri: string
  paramSummary?: string | null
  ip?: string | null
  result: 'SUCCESS' | 'FAIL'
  costMs: number
  createdAt: string
}

interface PageData<T> { list: T[]; total: number }

export const governanceAPI = {
  // ---- 用户端 ----
  submitComplaint(body: SubmitComplaintBody) {
    return request<Complaint>({ method: 'post', url: '/user/complaints', data: body })
  },
  myComplaints(page = 1, size = 10) {
    return request<PageData<Complaint>>({ method: 'get', url: '/user/complaints', params: { page, size } })
  },
  complaintDetail(id: number) {
    return request<ComplaintDetail>({ method: 'get', url: `/user/complaints/${id}` })
  },

  // ---- 管理端：投诉 ----
  adminComplaints(params: { status?: string; type?: string; page?: number; size?: number }) {
    return request<PageData<Complaint>>({ method: 'get', url: '/admin/complaints', params })
  },
  adminComplaintDetail(id: number) {
    return request<ComplaintDetail>({ method: 'get', url: `/admin/complaints/${id}` })
  },
  acceptComplaint(id: number) {
    return request({ method: 'post', url: `/admin/complaints/${id}/accept` })
  },
  resolveComplaint(id: number, reply: string) {
    return request({ method: 'post', url: `/admin/complaints/${id}/resolve`, data: { reply } })
  },
  closeComplaint(id: number) {
    return request({ method: 'post', url: `/admin/complaints/${id}/close` })
  },
  arbitrateComplaint(id: number, reason: string) {
    return request<Arbitration>({ method: 'post', url: `/admin/complaints/${id}/arbitrate`, data: { reason } })
  },

  // ---- 管理端：仲裁 ----
  adminArbitrations(params: { status?: string; page?: number; size?: number }) {
    return request<PageData<Arbitration>>({ method: 'get', url: '/admin/arbitrations', params })
  },
  ruleArbitration(id: number, body: { result: string; decision: string; compensationAmount?: number | null }) {
    return request({ method: 'post', url: `/admin/arbitrations/${id}/rule`, data: body })
  },

  // ---- 管理端：风控 ----
  adminRiskEvents(params: { status?: string; level?: string; type?: string; page?: number; size?: number }) {
    return request<PageData<RiskEvent> & { openCount: number }>({ method: 'get', url: '/admin/risk-events', params })
  },
  handleRiskEvent(id: number, status: RiskStatus, remark?: string) {
    return request({ method: 'post', url: `/admin/risk-events/${id}/handle`, data: { status, remark } })
  },

  // ---- 管理端：审计 ----
  adminAuditLogs(params: { module?: string; adminId?: number; action?: string; page?: number; size?: number }) {
    return request<PageData<AuditLog>>({ method: 'get', url: '/admin/audit-logs', params })
  }
}

export default governanceAPI
