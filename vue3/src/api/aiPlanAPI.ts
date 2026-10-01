/**
 * AI 饮食计划接口
 */
import { request } from './request'

export type Gender = 'MALE' | 'FEMALE'
export type Goal = 'MUSCLE_GAIN' | 'FAT_LOSS' | 'MAINTAIN'

export interface GenerateParams {
  height: number
  weight: number
  age: number
  gender: Gender
  goal: Goal
}

export interface MealItem {
  type: string
  foods: string[]
  calories: number
}

export interface PlanResult {
  dailyCalories: number
  protein: number
  carbs: number
  fat: number
  meals: MealItem[]
  provider?: string
}

export interface SaveParams extends GenerateParams {
  dailyCalories: number
  protein: number
  carbs: number
  fat: number
  meals: MealItem[]
}

export interface PlanHistoryItem {
  id: number
  height: number
  weight: number
  age: number
  gender: Gender
  goal: Goal
  dailyCalories: number
  protein: number
  carbs: number
  fat: number
  provider: string
  createdAt: string
}

export interface PlanDetail extends PlanHistoryItem {
  meals: MealItem[]
}

export interface PlanListResult {
  list: PlanHistoryItem[]
  total: number
}

// ==================== AI 对话式计划 ====================

/** 对话收集步骤 */
export type ChatStep = 'WEIGHT' | 'HEIGHT' | 'AGE' | 'GENDER' | 'GOAL' | 'ACTIVITY' | 'DONE'

/** 对话生成出的完整计划（含用户信息，可直接保存） */
export interface ChatPlanData {
  height: number
  weight: number
  age: number
  gender: Gender
  goal: Goal
  activityLevel?: string
  dailyCalories: number
  protein: number
  carbs: number
  fat: number
  meals: MealItem[]
  provider?: string
}

/** 对话接口出参 */
export interface ChatReply {
  sessionId: string
  /** AI 回复文案 */
  reply: string
  /** ASKING = 继续收集，PLAN_READY = 计划已生成 */
  status: 'ASKING' | 'PLAN_READY'
  /** 当前所在/下一个提问步骤（用于前端快捷回复 chips） */
  step?: ChatStep
  /** status = PLAN_READY 时返回 */
  planData?: ChatPlanData
}

export const aiPlanAPI = {
  /** AI 对话（对话式收集信息 → 生成计划） */
  chat(params: { sessionId: string; message: string }) {
    return request<ChatReply>({
      method: 'post',
      url: '/user/ai-plan/chat',
      data: params
    })
  },

  /** 生成饮食计划（不落库） */
  generate(params: GenerateParams) {
    return request<PlanResult>({
      method: 'post',
      url: '/ai/plan/generate',
      data: params
    })
  },

  /** 保存计划 */
  save(params: SaveParams) {
    return request<{ id: number; dailyCalories: number; createdAt: string }>({
      method: 'post',
      url: '/ai/plan/save',
      data: params
    })
  },

  /** 历史计划列表 */
  list(params: { page?: number; size?: number } = {}) {
    return request<PlanListResult>({
      method: 'get',
      url: '/ai/plan/list',
      params: { page: params.page || 1, size: params.size || 10 }
    })
  },

  /** 计划详情 */
  detail(id: number) {
    return request<PlanDetail>({
      method: 'get',
      url: `/ai/plan/${id}`
    })
  }
}

export default aiPlanAPI
