package com.xiaoshan.fitness.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 智能客服。
 * <p>
 * 三级应答，保证"答得准、不编造、能兜底"：
 * <ol>
 *   <li><b>FAQ 知识库优先</b>：高频、有确定口径的问题（办卡/激活/核销/营业时间/优惠券等）
 *       用内置标准答案，命中即返回，零成本、零幻觉；</li>
 *   <li><b>千帆兜底</b>：FAQ 未命中且已配置千帆时，由大模型在"山达健身客服"人设与知识边界内作答，
 *       明确要求不编造价格/承诺，不确定就引导人工；</li>
 *   <li><b>转人工</b>：涉及投诉、退费纠纷、受伤、安全等敏感意图，或模型不可用/无把握时，
 *       直接给出人工客服与到店引导。</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiCustomerService {

    private final QianfanLlmClient llmClient;

    /** 命中来源，前端可据此展示"常见问题/智能助手/人工"标识 */
    public static final String SRC_FAQ = "FAQ";
    public static final String SRC_AI = "AI";
    public static final String SRC_HANDOFF = "HANDOFF";

    /** 敏感/高风险意图：直接转人工，不由 AI 应答，避免不当承诺 */
    private static final List<String> URGENT_KEYWORDS = List.of(
            "投诉", "退费", "退款纠纷", "受伤", "受伤了", "受伤了怎么办", "晕倒", "120",
            "举报", "315", "工商", "律师", "起诉", "纠纷", "态度恶劣", "讹", "事故");

    private static final String AI_SYS = """
            你是「山达健身」连锁健身俱乐部的智能客服小珊，用简洁、亲切、专业的中文回答会员问题。
            业务范围：会员卡（普通卡/私教课卡、多门店、购买、激活、有效期）、刷脸到店核销、人脸录入、
            优惠券与限时活动、AI 饮食/训练计划、门店服务、App 使用问题。
            规则：
            1. 只回答与山达健身相关的问题；与健身或本俱乐部无关的问题，礼貌说明并引导回到业务。
            2. 不要编造具体价格、折扣、承诺、营业时间或工作人员言论；这些以门店和 App 公示为准，
               不确定时请用户在 App「我的-在线客服」联系人工或到店咨询。
            3. 不做医疗诊断或伤病处理建议；涉及受伤、身体不适，建议停止训练并及时就医、联系门店。
            4. 回答控制在 120 字以内，分点清晰，不使用 markdown 标题。
            """;

    /**
     * 回答用户问题
     *
     * @return answer / source(FAQ/AI/HANDOFF)
     */
    public Map<String, Object> answer(String message) {
        String q = message == null ? "" : message.trim();
        Map<String, Object> data = new LinkedHashMap<>();

        if (q.isEmpty()) {
            data.put("answer", "请描述一下你遇到的问题，比如「会员卡怎么激活」~");
            data.put("source", SRC_HANDOFF);
            return data;
        }

        // 1. 敏感意图：直接转人工
        for (String kw : URGENT_KEYWORDS) {
            if (q.contains(kw)) {
                data.put("answer", "这个情况我帮你转接人工客服处理，请稍候。你也可以直接到门店前台，"
                        + "或在 App「我的-在线客服」留言，工作人员会尽快跟进。为保障你的权益，沟通记录请注意保留。");
                data.put("source", SRC_HANDOFF);
                return data;
            }
        }

        // 2. FAQ 精确口径优先
        Faq hit = matchFaq(q);
        if (hit != null) {
            data.put("answer", hit.answer);
            data.put("source", SRC_FAQ);
            return data;
        }

        // 3. 千帆兜底
        if (llmClient.isConfigured()) {
            try {
                String reply = llmClient.chat(AI_SYS, q);
                if (reply != null && !reply.isBlank()) {
                    data.put("answer", reply.trim());
                    data.put("source", SRC_AI);
                    return data;
                }
            } catch (Exception e) {
                log.warn("AI 客服千帆应答失败，转人工兜底：{}", e.getMessage());
            }
        }

        // 4. 未命中 FAQ 且无可用模型：转人工
        data.put("answer", "这个问题我暂时没完全理解，建议你在 App「我的-在线客服」联系人工，"
                + "或直接到门店前台咨询。常见问题也可以试试问我「怎么激活会员卡」「怎么刷脸进门」。");
        data.put("source", SRC_HANDOFF);
        return data;
    }

    /** 推荐问题（首屏展示，降低提问门槛） */
    public List<String> suggestedQuestions() {
        return List.of(
                "会员卡买完怎么激活？",
                "到店怎么刷脸核销进门？",
                "人脸录入失败怎么办？",
                "优惠券怎么领取和使用？",
                "会员卡快到期了怎么办？");
    }

    // ==================== FAQ 知识库 ====================

    /**
     * 关键词匹配：对每条 FAQ 统计命中关键词数，取命中数最高者（至少命中 1 个）。
     * 关键词均为 2 字以上短语，避免单字误命中；顺序上更具体的问题放前面。
     */
    private Faq matchFaq(String q) {
        Faq best = null;
        int bestScore = 0;
        for (Faq f : FAQ) {
            int score = 0;
            for (String kw : f.keywords) {
                if (q.contains(kw)) score++;
            }
            if (score > bestScore) {
                bestScore = score;
                best = f;
            }
        }
        return bestScore > 0 ? best : null;
    }

    private record Faq(List<String> keywords, String answer) {
    }

    private static final List<Faq> FAQ = List.of(
            new Faq(List.of("怎么激活", "如何激活", "激活会员卡", "会员卡激活", "未激活", "立即激活"),
                    "会员卡购买成功后处于「未激活」状态。激活方式：打开 App「我的-我的会员卡」，找到对应卡片点「立即激活」即可。"
                            + "激活当天开始计算有效期；同一时间段同类型会员卡只能有一张生效，详情可看激活页提示。"),
            new Faq(List.of("刷脸", "核销", "进门", "入场", "人脸识别", "怎么进门", "闸门"),
                    "到店后在前台/闸机的人脸设备前正对屏幕，系统会自动识别并核销你的会员卡。"
                            + "请先在 App「我的-人脸录入」完成人脸采集；核销成功会有语音/页面提示，按次卡会自动扣减一次。"),
            new Faq(List.of("人脸录入", "录脸", "录入失败", "识别不了", "识别不出", "人脸失败"),
                    "人脸录入时请在光线充足处正对手机、露出五官、不要戴口罩墨镜。若多次失败可删除后重新录入；"
                            + "仍无法使用请联系门店前台人工处理，核销不受影响（可由前台核验）。"),
            new Faq(List.of("优惠券", "领券", "怎么领", "券怎么用", "用券", "抵扣"),
                    "在 App「首页-领券中心」可领取优惠券，下单办理会员卡时满足门槛即可勾选使用，系统自动抵扣。"
                            + "每张券有领取上限和有效期，未支付的订单会暂时锁定券，取消订单后自动返还。"),
            new Faq(List.of("限时活动", "活动价", "打折", "活动"),
                    "限时活动针对指定卡类型给出活动折扣，在活动时间内下单自动按活动价结算，名额有限、先到先得。"
                            + "可在 App「首页-限时活动」查看进行中的活动。"),
            new Faq(List.of("到期", "过期", "有效期", "续费", "延期"),
                    "会员卡有效期在激活后开始计算，可在「我的-我的会员卡」查看到期时间。到期前可重新购卡，"
                            + "系统会在到期前通过站内消息提醒；次卡以剩余次数为准，不受天数限制。"),
            new Faq(List.of("私教", "课卡", "节数", "次数", "次卡"),
                    "私教课卡按节数计，每次到店核销扣减一节，可在会员卡详情查看剩余节数。"
                            + "普通时长卡按有效期计、不扣次数，两类卡互不冲突，可同时持有。"),
            new Faq(List.of("门店", "多店", "通用", "分店", "哪家店", "门店列表"),
                    "会员卡分「单店卡」与「全店通用卡」：单店卡仅限绑定门店核销，全店通用卡可在任一连锁门店使用。"
                            + "具体适用范围以购卡页和卡面标注为准。"),
            new Faq(List.of("营业时间", "几点", "开门", "关门", "上班时间"),
                    "各门店营业时间略有差异，并可能随节假日调整，具体以你所购卡门店的公示或 App 门店信息为准，"
                            + "也可直接联系门店前台确认。"),
            new Faq(List.of("价格", "多少钱", "卡费", "收费", "价位"),
                    "不同卡类型、时长和门店的价格不同，最新价格请以 App「办理会员卡」页面实时展示为准，"
                            + "下单前可叠加优惠券或参与限时活动。"),
            new Faq(List.of("怎么买卡", "办卡", "购买", "下单", "支付", "支付宝"),
                    "在 App「首页-办理会员卡」选择卡类型后下单，支持支付宝支付。支付成功会自动生成一张「未激活」会员卡，"
                            + "再到「我的会员卡」激活即可。"),
            new Faq(List.of("AI", "饮食", "食谱", "训练计划", "怎么练", "减肥怎么吃"),
                    "App 提供 AI 饮食私教：在「AI 饮食计划」里像聊天一样报上身高体重等信息，就能生成每日热量、"
                            + "营养素和三餐搭配；AI 助手还能按你的器械条件生成一周训练计划。"),
            new Faq(List.of("退款", "退卡", "不想练了", "能不能退"),
                    "退卡/退款需要结合你的订单与会员卡状态人工审核，请在 App「我的-在线客服」联系人工或到门店前台办理。"),
            new Faq(List.of("核销记录", "记录", "到店记录", "历史"),
                    "每次刷脸核销都会生成记录，可在 App「我的-核销记录」查看到店时间、门店和结果。"),
            new Faq(List.of("你好", "在吗", "您好", "hi", "hello", "人工客服", "转人工"),
                    "你好，我是山达健身智能助手小珊～可以问我办卡、激活、刷脸进门、优惠券等问题，"
                            + "需要人工也可以去「我的-在线客服」。")
    );
}
