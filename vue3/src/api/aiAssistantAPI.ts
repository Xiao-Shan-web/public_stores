/**
 * AI 助手接口：训练计划生成 + 智能客服
 */
import { request } from './request'

// ==================== AI 训练计划 ====================

export type TrainGoal = 'MUSCLE_GAIN' | 'FAT_LOSS' | 'SHAPE'
export type TrainLevel = 'BEGINNER' | 'INTERMEDIATE'
/** 器械条件：GYM 健身房 / HOME 居家徒手 / DUMBBELL 仅哑铃 */
export type TrainEquipment = 'GYM' | 'HOME' | 'DUMBBELL'

export interface TrainParams {
  goal: TrainGoal
  level: TrainLevel
  equipment: TrainEquipment
  /** 每周训练天数 3-6 */
  daysPerWeek: number
}

export interface TrainExercise {
  name: string
  sets: number
  reps: string
  rest: string
  note: string
}

export interface TrainDay {
  day: string
  focus: string
  warmup: string[]
  cooldown: string[]
  exercises: TrainExercise[]
}

export interface TrainPlan {
  goal: string
  level: string
  equipment: string
  daysPerWeek: number
  /** local=本地动作库，qianfan=大模型个性化 */
  provider: string
  summary: string
  days: TrainDay[]
  tips: string[]
}

// ==================== AI 智能客服 ====================

/** 应答来源：FAQ 知识库 / AI 大模型 / HANDOFF 转人工 */
export type CustomerSource = 'FAQ' | 'AI' | 'HANDOFF'

export interface CustomerReply {
  answer: string
  source: CustomerSource
}

export interface ChatMsg {
  id: number
  role: 'user' | 'assistant'
  content: string
  source?: CustomerSource
}

export const aiAssistantAPI = {
  /** 生成结构化一周训练计划 */
  generateTraining(params: TrainParams) {
    return request<TrainPlan>({
      method: 'post',
      url: '/user/ai-assistant/training/generate',
      data: params
    })
  },

  /** 智能客服问答 */
  customerAsk(message: string) {
    return request<CustomerReply>({
      method: 'post',
      url: '/user/ai-assistant/customer/chat',
      data: { message }
    })
  },

  /** 客服首屏推荐问题 */
  customerSuggested() {
    return request<{ list: string[] }>({
      method: 'get',
      url: '/user/ai-assistant/customer/suggested'
    })
  }
}

export default aiAssistantAPI
