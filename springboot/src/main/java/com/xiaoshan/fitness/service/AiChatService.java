package com.xiaoshan.fitness.service;

import java.util.Map;

/**
 * AI 对话服务（对话式收集信息 → 生成饮食计划）
 * <p>
 * 设计为可插拔实现，通过配置 ai.chat.provider 切换：
 * <ul>
 *   <li>mock（默认）：MockAiChatService，本地规则状态机，无需外部 API 即可跑通完整对话流程</li>
 *   <li>后续可新增免费/低价大模型实现（DeepSeek、通义千问、Kimi 等），
 *       只需实现本接口并注册对应 provider 名称，无需改动前端与控制器</li>
 * </ul>
 * <p>
 * 约定：大模型只负责自然语言对话、信息提取、口语化回复；
 * TDEE、目标热量、三大营养素、食谱克数由后端 NutritionCalculator 精确计算。
 */
public interface AiChatService {

    /**
     * 处理用户一句话对话
     *
     * @param userId    当前登录用户ID（用于会话归属校验）
     * @param sessionId 会话ID（前端生成，后端以 Redis 保存上下文，30 分钟过期）
     * @param message   用户消息
     * @return sessionId / reply / status(ASKING|PLAN_READY) / step / planData(PLAN_READY 时存在)
     */
    Map<String, Object> sendMessage(Long userId, String sessionId, String message);

}
