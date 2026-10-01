package com.xiaoshan.fitness.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaoshan.fitness.entity.AiPlan;
import com.xiaoshan.fitness.entity.Message;
import com.xiaoshan.fitness.mapper.AiPlanMapper;
import com.xiaoshan.fitness.mapper.MessageMapper;
import com.xiaoshan.fitness.util.BusinessException;
import com.xiaoshan.fitness.util.NutritionCalculator;
import com.xiaoshan.fitness.util.SnowflakeIdGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 千帆大模型版 AI 私教（provider=qianfan）。
 * <p>
 * <b>职责切分（模型管语言、后端管规则）：</b>
 * <ul>
 *   <li>大模型负责：自然语言理解、口语化教练式回复、从一句话中抽取身体数据与意图，
 *       以严格 JSON 返回（见 SYSTEM_PROMPT）；</li>
 *   <li>后端负责：字段合理范围校验、收集状态推进、TDEE/目标热量/三大营养素/食谱的
 *       <b>确定性</b>计算（{@link NutritionCalculator}），计划落库与消息推送。
 *       大模型<b>不参与任何数值计算</b>，避免幻觉导致热量/营养素错误。</li>
 * </ul>
 * 会话结构复用 {@link AiChatSession}（Redis 30 分钟），与 Mock 版完全一致，
 * 因此同一前端 / 同一契约切换 provider 无感知；切换通过 ai.chat.provider=qianfan 生效。
 * <p>
 * 降级：千帆未配置/超时/返回非法 JSON 时，不产生脏状态，返回固定友好提示并停留在当前步骤，
 * 用户可重试；配置缺失时应直接使用默认的 mock provider。
 */
@Service
@ConditionalOnProperty(name = "ai.chat.provider", havingValue = "qianfan")
@RequiredArgsConstructor
@Slf4j
public class QianfanAiChatService implements AiChatService {

    private final StringRedisTemplate stringRedisTemplate;
    private final AiPlanMapper aiPlanMapper;
    private final MessageMapper messageMapper;
    private final SnowflakeIdGenerator idGenerator;
    private final NotificationPushService notificationPushService;
    private final QianfanLlmClient llmClient;

    @Value("${ai.chat.provider:qianfan}")
    private String provider;

    private static final String KEY_PREFIX = "ai:plan:chat:session:";
    private static final Duration SESSION_TTL = Duration.ofMinutes(30);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 收集顺序（与 Mock 一致，决定"下一个待补字段"） */
    private static final List<String> STEP_ORDER = List.of(
            "WEIGHT", "HEIGHT", "AGE", "GENDER", "GOAL", "ACTIVITY");

    // 合理范围（后端确定性边界，模型抽中的越界值不写入，由模型负责追问）
    private static final int WEIGHT_MIN = 30, WEIGHT_MAX = 200;
    private static final int HEIGHT_MIN = 130, HEIGHT_MAX = 210;
    private static final int AGE_MIN = 14, AGE_MAX = 65;

    private static final String SYSTEM_PROMPT = """
            你是「山达健身」的在线私教，正在通过对话帮用户定制饮食计划。你要用简洁、亲切、专业的中文口语回复，每次只问一个问题，像真人教练，不要客套啰嗦。

            你必须按顺序收集 6 项信息：体重(kg)、身高(cm)、年龄、性别(男/女)、目标(增肌/减脂/保持)、日常活动量(久坐/轻度/中等/高强度)。用户可以一次回答，也可以提前说后面的信息，你要能识别并记录。

            你还必须识别用户意图：重新生成一份(REGEN)、重新开始(RESTART)、保存计划(SAVE)、指定每日热量、修改已填资料，或普通对话(CHAT)。

            只输出一个 JSON 对象，不要输出 markdown 代码块，不要有任何多余文字，字段如下：
            {
              "reply": "给用户看的一句话回复",
              "weight": 数字(公斤)或null,
              "height": 数字(厘米)或null,
              "age": 数字或null,
              "gender": "MALE"或"FEMALE"或null,
              "goal": "MUSCLE_GAIN"或"FAT_LOSS"或"MAINTAIN"或null,
              "activity": "SEDENTARY"或"LIGHT"或"MODERATE"或"ACTIVE"或null,
              "calories": 数字(用户明确指定的每日热量)或null,
              "intent": "ANSWER"或"REGEN"或"RESTART"或"SAVE"或"CHAT"
            }
            规则：
            - 体重说"斤"要换算成公斤（除以2）；身高说"1米75"换算成175。
            - 性别只能 MALE/FEMALE；目标只能 MUSCLE_GAIN/FAT_LOSS/MAINTAIN；活动量：久坐=SEDENTARY，偶尔运动=LIGHT，每周3-5次=MODERATE，高频/高强度=ACTIVE。
            - 若用户给的数字明显离谱（如年龄108、体重300公斤、身高230厘米），对应字段返回 null，并在 reply 里轻松地请用户重新提供合理数值。
            - 信息没收集全时，intent 用 ANSWER，并在 reply 里追问当前最该补的那一项；不要追问已经拿到的信息。
            - 不要自行计算热量、营养素或食谱，这些由系统完成；你只负责对话与信息抽取。
            """;

    @Override
    public Map<String, Object> sendMessage(Long userId, String sessionId, String message) {
        AiChatSession session = loadSession(sessionId);
        if (session == null) {
            session = new AiChatSession();
            session.setUserId(userId);
            session.setStep("WEIGHT");
        } else if (!userId.equals(session.getUserId())) {
            throw new IllegalArgumentException("会话状态异常，请刷新页面重新开始");
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sessionId", sessionId);

        // 1. 调用大模型做理解与抽取
        Extraction ext;
        try {
            ext = extract(session, message);
        } catch (BusinessException e) {
            // AI 不可用：保持会话状态不变，友好提示后停留在当前步骤
            log.warn("千帆 AI 不可用，保持步骤{}：{}", session.getStep(), e.getMessage());
            saveSession(sessionId, session);
            result.put("reply", "AI 私教暂时开小差了，请稍后再发一次~你的信息都还在。");
            result.put("status", "ASKING");
            result.put("step", session.getStep());
            return result;
        }

        // 2. 按当前阶段驱动确定性状态机
        if ("DONE".equals(session.getStep())) {
            return handleDone(userId, sessionId, session, ext, result);
        }
        return handleCollecting(userId, sessionId, session, ext, result);
    }

    // ==================== 收集阶段 ====================

    private Map<String, Object> handleCollecting(Long userId, String sessionId, AiChatSession session,
                                                  Extraction ext, Map<String, Object> result) {
        String intent = ext.intent == null ? "ANSWER" : ext.intent;

        if ("RESTART".equals(intent)) {
            return restart(userId, sessionId, result);
        }

        // 收集阶段"保存/换一份"无意义：交给模型在 reply 引导，这里不做状态变更
        // 记录用户提前指定的热量
        if (ext.calories != null) {
            session.setTargetCalories(NutritionCalculator.clampCalories(ext.calories));
        }

        // 抽中的字段写入（带范围校验，越界不写入）
        fillProfile(session, ext);

        String missing = firstMissing(session);
        if (missing == null) {
            // 六项齐全 → 确定性生成计划
            int variant = ThreadLocalRandom.current().nextInt(3);
            Map<String, Object> planData = NutritionCalculator.generate(
                    session.getHeight(), session.getWeight(), session.getAge(),
                    session.getGender(), session.getGoal(), session.getActivityLevel(),
                    variant, session.getTargetCalories());
            session.setTargetCalories((Integer) planData.get("dailyCalories"));
            session.setStep("DONE");
            session.setVariant(variant);
            session.setRegenCount(0);
            saveSession(sessionId, session);
            result.put("reply", ext.reply);
            result.put("status", "PLAN_READY");
            result.put("planData", planData);
            log.info("千帆 AI 对话生成计划：用户{} 每日{}kcal", userId, planData.get("dailyCalories"));
            return result;
        }

        session.setStep(missing);
        saveSession(sessionId, session);
        result.put("reply", ext.reply);
        result.put("status", "ASKING");
        result.put("step", missing);
        return result;
    }

    // ==================== 计划已生成阶段 ====================

    private Map<String, Object> handleDone(Long userId, String sessionId, AiChatSession session,
                                            Extraction ext, Map<String, Object> result) {
        String intent = ext.intent == null ? "CHAT" : ext.intent;

        switch (intent) {
            case "RESTART" -> {
                return restart(userId, sessionId, result);
            }
            case "SAVE" -> {
                int variant = session.getVariant() == null ? 0 : session.getVariant();
                Map<String, Object> planData = NutritionCalculator.generate(
                        session.getHeight(), session.getWeight(), session.getAge(),
                        session.getGender(), session.getGoal(), session.getActivityLevel(),
                        variant, session.getTargetCalories());
                try {
                    savePlan(userId, planData);
                } catch (Exception e) {
                    log.error("千帆 AI 对话保存计划失败", e);
                    saveSession(sessionId, session);
                    result.put("reply", "保存时出了点小问题，你再发一次「保存」试试~");
                    result.put("status", "ASKING");
                    result.put("step", "DONE");
                    return result;
                }
                saveSession(sessionId, session);
                result.put("reply", ext.reply);
                result.put("status", "PLAN_READY");
                result.put("planData", planData);
                return result;
            }
            case "REGEN" -> {
                int variant = nextVariant(session);
                Map<String, Object> planData = NutritionCalculator.generate(
                        session.getHeight(), session.getWeight(), session.getAge(),
                        session.getGender(), session.getGoal(), session.getActivityLevel(),
                        variant, session.getTargetCalories());
                session.setVariant(variant);
                saveSession(sessionId, session);
                result.put("reply", ext.reply);
                result.put("status", "PLAN_READY");
                result.put("planData", planData);
                return result;
            }
            default -> {
                // ANSWER / CHAT / 指定热量 / 修改资料
                boolean changed = fillProfile(session, ext);
                boolean calorieOverride = false;
                if (ext.calories != null) {
                    session.setTargetCalories(NutritionCalculator.clampCalories(ext.calories));
                    calorieOverride = true;
                }
                if (changed || calorieOverride) {
                    // 身体数据变化后，若非用户显式指定热量，则清掉旧热量按新 TDEE 重算
                    if (changed && !calorieOverride) {
                        session.setTargetCalories(null);
                    }
                    int variant = nextVariant(session);
                    Map<String, Object> planData = NutritionCalculator.generate(
                            session.getHeight(), session.getWeight(), session.getAge(),
                            session.getGender(), session.getGoal(), session.getActivityLevel(),
                            variant, session.getTargetCalories());
                    session.setTargetCalories((Integer) planData.get("dailyCalories"));
                    session.setVariant(variant);
                    saveSession(sessionId, session);
                    result.put("reply", ext.reply);
                    result.put("status", "PLAN_READY");
                    result.put("planData", planData);
                    log.info("千帆 AI 对话调整重算：用户{} 每日{}kcal", userId, planData.get("dailyCalories"));
                    return result;
                }
                // 纯闲聊：模型回复即可
                saveSession(sessionId, session);
                result.put("reply", ext.reply);
                result.put("status", "ASKING");
                result.put("step", "DONE");
                return result;
            }
        }
    }

    // ==================== 模型抽取 ====================

    /**
     * 调用千帆并解析为结构化抽取结果。模型被约束只输出 JSON。
     */
    private Extraction extract(AiChatSession session, String userMessage) {
        String userPrompt = "【当前已收集信息】\n" + sessionSnapshot(session)
                + "\n【当前阶段】" + ("DONE".equals(session.getStep())
                    ? "计划已生成，可处理保存/换一份/改资料/指定热量或闲聊"
                    : "正在收集，下一项优先问：" + session.getStep())
                + "\n【用户这句话】" + userMessage
                + "\n请只输出 JSON。";

        String raw = llmClient.chat(SYSTEM_PROMPT, userPrompt);
        return parseExtraction(raw);
    }

    private Extraction parseExtraction(String raw) {
        try {
            String json = stripJson(raw);
            JsonNode node = MAPPER.readTree(json);
            Extraction e = new Extraction();
            e.reply = node.path("reply").asText("好的，我记下了~");
            e.weight = intOrNull(node, "weight");
            e.height = intOrNull(node, "height");
            e.age = intOrNull(node, "age");
            e.gender = enumOrNull(node, "gender", "MALE", "FEMALE");
            e.goal = enumOrNull(node, "goal", "MUSCLE_GAIN", "FAT_LOSS", "MAINTAIN");
            e.activity = enumOrNull(node, "activity", "SEDENTARY", "LIGHT", "MODERATE", "ACTIVE");
            e.calories = intOrNull(node, "calories");
            String it = node.path("intent").asText("ANSWER");
            e.intent = List.of("ANSWER", "REGEN", "RESTART", "SAVE", "CHAT").contains(it) ? it : "ANSWER";
            return e;
        } catch (Exception parseErr) {
            // 模型未遵守 JSON 约定：记录原始返回，抛出由上层降级，不把异常内容回显用户
            log.warn("千帆返回非合法 JSON：{}", raw == null ? "" : raw.substring(0, Math.min(raw.length(), 200)));
            throw new BusinessException(503, "AI 返回格式异常");
        }
    }

    /** 去除模型可能误加的 ```json 包裹 */
    private String stripJson(String raw) {
        String s = raw == null ? "" : raw.trim();
        if (s.startsWith("```")) {
            int firstBrace = s.indexOf('{');
            int lastBrace = s.lastIndexOf('}');
            if (firstBrace >= 0 && lastBrace > firstBrace) {
                return s.substring(firstBrace, lastBrace + 1);
            }
        }
        return s;
    }

    private Integer intOrNull(JsonNode node, String field) {
        JsonNode n = node.path(field);
        if (n.isMissingNode() || n.isNull() || !n.isNumber()) return null;
        return n.asInt();
    }

    private String enumOrNull(JsonNode node, String field, String... allowed) {
        String v = node.path(field).asText(null);
        if (v == null) return null;
        for (String a : allowed) {
            if (a.equalsIgnoreCase(v)) return a;
        }
        return null;
    }

    /** 模型抽取结果 */
    private static final class Extraction {
        String reply;
        Integer weight;
        Integer height;
        Integer age;
        String gender;
        String goal;
        String activity;
        Integer calories;
        String intent;
    }

    // ==================== 会话 / 资料 / 生成 辅助 ====================

    /**
     * 将抽中的字段写入会话（带合理范围校验）。
     *
     * @return 是否有任一字段被有效写入或变更
     */
    private boolean fillProfile(AiChatSession session, Extraction e) {
        boolean changed = false;
        if (e.weight != null && e.weight >= WEIGHT_MIN && e.weight <= WEIGHT_MAX
                && !e.weight.equals(session.getWeight())) {
            session.setWeight(e.weight);
            changed = true;
        }
        if (e.height != null && e.height >= HEIGHT_MIN && e.height <= HEIGHT_MAX
                && !e.height.equals(session.getHeight())) {
            session.setHeight(e.height);
            changed = true;
        }
        if (e.age != null && e.age >= AGE_MIN && e.age <= AGE_MAX
                && !e.age.equals(session.getAge())) {
            session.setAge(e.age);
            changed = true;
        }
        if (e.gender != null && !e.gender.equals(session.getGender())) {
            session.setGender(e.gender);
            changed = true;
        }
        if (e.goal != null && !e.goal.equals(session.getGoal())) {
            session.setGoal(e.goal);
            changed = true;
        }
        if (e.activity != null && !e.activity.equals(session.getActivityLevel())) {
            session.setActivityLevel(e.activity);
            changed = true;
        }
        return changed;
    }

    /** 第一个尚未收集到的字段（STEP_ORDER 顺序），全齐返回 null */
    private String firstMissing(AiChatSession s) {
        if (s.getWeight() == null) return "WEIGHT";
        if (s.getHeight() == null) return "HEIGHT";
        if (s.getAge() == null) return "AGE";
        if (s.getGender() == null) return "GENDER";
        if (s.getGoal() == null) return "GOAL";
        if (s.getActivityLevel() == null) return "ACTIVITY";
        return null;
    }

    private int nextVariant(AiChatSession session) {
        return session.getVariant() == null
                ? ThreadLocalRandom.current().nextInt(3)
                : (session.getVariant() + 1) % 3;
    }

    private String sessionSnapshot(AiChatSession s) {
        List<String> parts = new ArrayList<>();
        parts.add("体重=" + (s.getWeight() == null ? "未提供" : s.getWeight() + "kg"));
        parts.add("身高=" + (s.getHeight() == null ? "未提供" : s.getHeight() + "cm"));
        parts.add("年龄=" + (s.getAge() == null ? "未提供" : s.getAge()));
        parts.add("性别=" + (s.getGender() == null ? "未提供" : s.getGender()));
        parts.add("目标=" + (s.getGoal() == null ? "未提供" : s.getGoal()));
        parts.add("活动量=" + (s.getActivityLevel() == null ? "未提供" : s.getActivityLevel()));
        return String.join("，", parts);
    }

    private Map<String, Object> restart(Long userId, String sessionId, Map<String, Object> result) {
        AiChatSession fresh = new AiChatSession();
        fresh.setUserId(userId);
        fresh.setStep("WEIGHT");
        saveSession(sessionId, fresh);
        result.put("reply", "好嘞，我们重新开始～先告诉我你的体重是多少公斤？");
        result.put("status", "ASKING");
        result.put("step", "WEIGHT");
        return result;
    }

    /** 落库计划 + 站内消息（与 Mock 版保存语义一致） */
    private void savePlan(Long userId, Map<String, Object> planData) throws Exception {
        AiPlan plan = new AiPlan();
        plan.setId(idGenerator.nextId());
        plan.setUserId(userId);
        plan.setHeight((Integer) planData.get("height"));
        plan.setWeight((Integer) planData.get("weight"));
        plan.setAge((Integer) planData.get("age"));
        plan.setGender((String) planData.get("gender"));
        plan.setGoal((String) planData.get("goal"));
        plan.setDailyCalories((Integer) planData.get("dailyCalories"));
        plan.setProtein((Integer) planData.get("protein"));
        plan.setCarbs((Integer) planData.get("carbs"));
        plan.setFat((Integer) planData.get("fat"));
        plan.setMealsJson(MAPPER.writeValueAsString(planData.get("meals")));
        plan.setProvider(provider);
        aiPlanMapper.insert(plan);

        Message msg = new Message();
        msg.setId(idGenerator.nextId());
        msg.setUserId(userId);
        msg.setType("AI_PLAN");
        msg.setTitle("AI 计划生成完成");
        msg.setContent("您的新饮食计划已保存，每日目标 " + plan.getDailyCalories() + " kcal");
        msg.setRefId(plan.getId());
        messageMapper.insert(msg);
        notificationPushService.push(msg);
        log.info("用户{}在千帆对话中保存 AI 计划：每日{}kcal（ID={}）", userId, plan.getDailyCalories(), plan.getId());
    }

    private AiChatSession loadSession(String sessionId) {
        try {
            String json = stringRedisTemplate.opsForValue().get(KEY_PREFIX + sessionId);
            if (json == null || json.isEmpty()) return null;
            return MAPPER.readValue(json, AiChatSession.class);
        } catch (Exception e) {
            log.warn("千帆 AI 会话读取失败，视为新会话：{}", e.getMessage());
            return null;
        }
    }

    private void saveSession(String sessionId, AiChatSession session) {
        try {
            stringRedisTemplate.opsForValue().set(KEY_PREFIX + sessionId,
                    MAPPER.writeValueAsString(session), SESSION_TTL);
        } catch (Exception e) {
            log.error("千帆 AI 会话保存失败", e);
        }
    }
}
