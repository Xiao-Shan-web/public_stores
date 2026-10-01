package com.xiaoshan.fitness.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaoshan.fitness.entity.AiPlan;
import com.xiaoshan.fitness.entity.Message;
import com.xiaoshan.fitness.mapper.AiPlanMapper;
import com.xiaoshan.fitness.mapper.MessageMapper;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Mock AI 私教（默认实现，provider=mock）
 * <p>
 * 本地规则状态机：按 体重→身高→年龄→性别→目标→活动量 逐步收集，
 * 每步命中即追问下一项；六项齐全后由 NutritionCalculator 精确生成计划。
 * <p>
 * 意图识别层：所有消息先过 {@link #detectIntent}，支持
 * 换一份 / 指定热量生成 / 指定目标生成 / 保存计划 / 重新开始 等意图；
 * 语气按教练风格回复，带热量对比与建议，不做客服式空话。
 * <p>
 * 预留大模型接入：后续新增 DeepSeekAiChatService / QwenAiChatService / KimiAiChatService
 * 等实现并配置 ai.chat.provider 即可切换，对话上下文结构（AiChatSession）可直接复用。
 */
@Service
@ConditionalOnProperty(name = "ai.chat.provider", havingValue = "mock", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class MockAiChatService implements AiChatService {

    private final StringRedisTemplate stringRedisTemplate;
    private final AiPlanMapper aiPlanMapper;
    private final MessageMapper messageMapper;
    private final SnowflakeIdGenerator idGenerator;
    private final NotificationPushService notificationPushService;

    /** 预留：切换 provider 时由配置驱动（mock/deepseek/qwen/kimi...） */
    @Value("${ai.chat.provider:mock}")
    private String provider;

    private static final String KEY_PREFIX = "ai:plan:chat:session:";
    private static final Duration SESSION_TTL = Duration.ofMinutes(30);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    /** 收集顺序 */
    private static final List<String> STEP_ORDER = List.of(
            "WEIGHT", "HEIGHT", "AGE", "GENDER", "GOAL", "ACTIVITY");

    // ==================== 随机文案模板 ====================

    private static final Map<String, List<String>> ASK_TEMPLATES = Map.of(
            "WEIGHT", List.of(
                    "先跟我说说您的体重吧？多少公斤~",
                    "咱们从体重基数开始！您现在体重多少呀？",
                    "体重报一下呗，按公斤说，比如 70~",
                    "来啦？我们先从体重基数开始吧~多少公斤？"),
            "HEIGHT", List.of(
                    "身高多少呀？按厘米说~",
                    "告诉我您的身高，我帮您算算基础消耗~",
                    "您多高呢？光脚量那种哈哈"),
            "AGE", List.of(
                    "今年多大啦？",
                    "年龄也说一声，代谢计算要用到哦~",
                    "您多大了？直接说数字就行~"),
            "GENDER", List.of(
                    "男生还是女生呀？",
                    "性别告诉我一下，这决定用哪个计算公式哦~",
                    "小哥哥还是小姐姐？"),
            "GOAL", List.of(
                    "想达到什么目标呢？增肌、减脂还是保持？",
                    "您的健身目标是啥？我按目标给您配餐~",
                    "说说目标呗：练壮、瘦下来，还是维持现状？"),
            "ACTIVITY", List.of(
                    "平时运动量大吗？久坐、偶尔动动，还是经常练？",
                    "每周大概运动几次呀？我好估算您的日常消耗~",
                    "日常活动量怎么样？坐班为主还是挺能动的？"));

    private static final Map<String, List<String>> RETRY_TEMPLATES = Map.of(
            "WEIGHT", List.of(
                    "这个数字我没太看懂呢，直接告诉我体重多少公斤好吗？比如 70",
                    "体重按公斤说哈，比如 70~",
                    "来，认真报一下体重多少公斤？直接说数字就行~",
                    "体重这步快过了，多少公斤告诉我呗~"),
            "HEIGHT", List.of(
                    "身高按厘米告诉我哈，比如 175",
                    "我没太理解，身高多少厘米呢？",
                    "来，认真报一下身高，按厘米说~比如 175",
                    "身高多少厘米呀？直接报数字就行~"),
            "AGE", List.of(
                    "年龄直接说数字就好啦，比如 25",
                    "这个我没看懂，您今年多少岁呀？",
                    "来，认真说一下年龄，数字就行~",
                    "今年多大啦？直接报数字我就能往下算了~"),
            "GENDER", List.of(
                    "哈哈这个我没猜出来，男生还是女生？",
                    "性别就两个选项：男 or 女~",
                    "小哥哥还是小姐姐？说一声我就记下了~",
                    "这个我真猜不了哈哈，男生还是女生？"),
            "GOAL", List.of(
                    "目标说清楚点呗：增肌、减脂，还是保持？",
                    "我这边有三个方向：增肌 / 减脂 / 保持，选一个~",
                    "想增肌、减脂还是保持？说一个我就能配餐了~",
                    "目标挑一个呗：增肌 / 减脂 / 保持~"),
            "ACTIVITY", List.of(
                    "描述得有点模糊，久坐不动、偶尔运动、中等运动量、高强度训练，哪个更接近？",
                    "回答得具体一点嘛，我好给您算准消耗~",
                    "日常活动量怎么样？久坐、偶尔动、中等、高强度，挑一个~",
                    "每周大概运动几次呀？说个大概我就能估消耗了~"));

    /** 连续 2 次以上校验失败时，用更轻松直接的语气追问（不复读同一句） */
    private static final Map<String, List<String>> CONSECUTIVE_FAIL_TEMPLATES = Map.of(
            "WEIGHT", List.of(
                    "来，认真点，体重多少公斤？",
                    "咱们先认真报个体重，后面都好算~",
                    "体重这步别蒙哈，多少公斤直接说~"),
            "HEIGHT", List.of(
                    "来，认真点，身高多少厘米？",
                    "咱们先认真报个身高，后面都好算~",
                    "身高别蒙哈，多少厘米直接说~"),
            "AGE", List.of(
                    "来，认真点，今年多大？",
                    "咱们先认真报个年龄，后面都好算~",
                    "年龄直接说数字就行，别纠结~"),
            "GENDER", List.of(
                    "来，男生还是女生？就两个选项~",
                    "认真说一声，男 or 女？",
                    "别绕啦，小哥哥还是小姐姐？"),
            "GOAL", List.of(
                    "来，认真选一个：增肌 / 减脂 / 保持",
                    "目标别纠结，挑一个就行~",
                    "增肌、减脂、保持，三选一，说一个~"),
            "ACTIVITY", List.of(
                    "来，认真选一个：久坐不动 / 偶尔运动 / 中等运动量 / 高强度训练",
                    "活动量别蒙哈，挑一个最接近的~",
                    "久坐、偶尔动、中等、高强度，四选一~"));

    /** 数字明显超出合理范围时的轻松追问（{v}=用户报的数），不推进、不接受，等用户重新报 */
    private static final Map<String, List<String>> RANGE_NUDGE_TEMPLATES = Map.of(
            "AGE", List.of(
                    "{v} 岁？那我得叫你一声老哥了哈哈，实际年龄多少？",
                    "年龄我先按正常范围来哈，你重新说一个~",
                    "这岁数有点超纲了，真实年龄多大？一般 14 到 65 之间我都能给你算准。",
                    "来，认真报一下年龄呗，{v} 这个数我先存个疑，14 到 65 之间~"),
            "WEIGHT", List.of(
                    "这个体重我先确认一下，{v} 你说的是公斤还是斤？按公斤再报一次给我~",
                    "{v} 这数有点超出我能算的范围了，体重按公斤说是多少？一般 30 到 200 公斤之间。",
                    "体重咱按公斤来哈，{v} 对不太上；要是说的是斤，折成公斤告诉我~",
                    "来，认真报一下体重多少公斤？{v} 这个数我先存个疑，30 到 200 之间~"),
            "HEIGHT", List.of(
                    "哈哈，{v} 厘米那是手办吧？身高按厘米说，比如 175~",
                    "{v} 这身高有点超出范围了，按厘米报一下，一般 130 到 210 之间。",
                    "身高按厘米算是多少？{v} 这个数我先存个疑，你再确认下~",
                    "哥，{v} 这个数我有点懵，认真报一下身高呗，130 到 210 之间~",
                    "这个身高有点特别，咱们按厘米说哈~比如 175 就直接说 175。"));

    /** 用户"不说/不想说/跳过"等拒答时，轻松解释为什么需要 + 继续追问（后接当前步骤的提问） */
    private static final List<String> REFUSE_ACK_TEMPLATES = List.of(
            "哈哈这个还真省不掉，得靠它算你的消耗，给个大概数就行~",
            "这一项是算热量的底子，绕不过去哈，你随便报个差不多的我就能配。",
            "别呀，没这个数我配出来的餐不准，给个大概就行，差一点不碍事~");

    private static final List<String> RESTART_ACK_TEMPLATES = List.of(
            "好嘞，咱们重新开始！先说说您的体重吧？多少公斤~",
            "没问题，翻篇重来~体重多少公斤？",
            "行，重新来一遍！您的体重是？");

    /** 性别确认（男生）：轻松接一句，不严肃纠正 */
    private static final List<String> GENDER_MALE_ACK = List.of(
            "行，男生，记下了哥。",
            "好嘞，小哥哥，用男版公式给你算~",
            "OK，男生这边代谢基数会高一点，记下了。");

    /** 性别确认（女生） */
    private static final List<String> GENDER_FEMALE_ACK = List.of(
            "好，女生，记下了~",
            "行，小姐姐，这边按女版公式算代谢哈。",
            "收到，女生代谢会稍微低一点，我给你算准。");

    /** 收集到目标后的回应（按目标分组，接下一问之前） */
    private static final Map<String, List<String>> GOAL_ACK_TEMPLATES = Map.of(
            "MUSCLE_GAIN", List.of(
                    "增肌是吧，好方向！",
                    "行，练壮就得吃够！",
                    "增肌没问题，吃和练都得跟上~"),
            "FAT_LOSS", List.of(
                    "减脂啊，那吃的这块是关键。",
                    "好，减脂咱们慢慢来，不搞极端节食。",
                    "减脂是吧，安排！"),
            "MAINTAIN", List.of(
                    "保持现状也挺好，吃对就行。",
                    "维持体重，讲究一个稳字。",
                    "OK，那就往均衡里配。"));

    /** 计划生成完成时的回复（按目标分组，融合点评+下一步建议，不说空话） */
    private static final Map<String, List<String>> PLAN_READY_TEMPLATES = Map.of(
            "MUSCLE_GAIN", List.of(
                    "好嘞，算出来了！这份蛋白质给得挺足，训练前后那两顿一定要吃够。先吃两天，训练时觉得没劲咱再往上调。",
                    "齐了！按这份吃，练完那顿碳水蛋白都别省，长肌肉全靠它。先按这个吃两天，体重不动咱再加量。",
                    "算好啦！增肌期就怕吃不够，这份热量是有盈余的。先吃两天，练后那顿一定要吃，没力气加餐跟我说。",
                    "搞定！主食和蛋白都拉到位了。先按这个吃两天，训练状态不对咱再调到高一点的热量。"),
            "FAT_LOSS", List.of(
                    "算好了！热量控住了，但每顿都有蛋白质，不会让您饿得难受。先吃两天，饿了先垫蔬菜别硬扛。",
                    "齐了！这份偏减脂，粗粮多、饱腹感强。先按这个吃两天，体重掉太快咱就往上调到 1300 左右。",
                    "好嘞！缺口控制得比较温和，掉秤稳，不容易反弹。先吃两天，没劲咱就加 200 大卡。",
                    "搞定！碳水和脂肪压下来一点，蛋白质够量。先按这个吃两天，训练没力气跟我说，咱往上调。"),
            "MAINTAIN", List.of(
                    "齐活！这份营养挺均衡的。保持这个节奏就很好，先吃两天，体重有波动是正常的。",
                    "算好了！不增不减，吃对比例就行。先按这个吃，下周看趋势再调。",
                    "好嘞！维持期讲究一个稳，这份搭配挺省心的。先吃两天，有变化再说。",
                    "搞定！一日三餐安排得明明白白。先按这个吃，体重稳就行，不用天天纠结。"));

    /** DONE 后首次"换一份"的回复（按目标分组） */
    private static final Map<String, List<String>> REGEN_ACK_TEMPLATES = Map.of(
            "MUSCLE_GAIN", List.of(
                    "可以啊，我再给你换一份，蛋白质给你拉高一点。",
                    "行，换一套，这次换个口味，练后那顿记得吃够。",
                    "没问题，重排了一份，增肌期加餐千万别落下~"),
            "FAT_LOSS", List.of(
                    "行，这次我稍微调整一下碳水和脂肪比例，看看更合不合你口味。",
                    "好嘞，换了一份，粗粮多了点，饱腹感更强~",
                    "没问题，这套更清爽，热量还是控住的，放心~"),
            "MAINTAIN", List.of(
                    "可以，再给你换一套，配比不变，换换口味。",
                    "行，这份换了几样菜，量还是按你的消耗算的~",
                    "没问题，换好啦，保持这个节奏就很好。"));

    /** DONE 后连续"换一份"（第 2 次及以上）：提醒还是同热量，只是换搭配 */
    private static final List<String> REGEN_MORE_TEMPLATES = List.of(
            "还是同热量，我继续给你换食物搭配。",
            "行，再换一版，营养配比不变，你试试看。",
            "换个花样，热量没动，吃不饱跟我说。");

    /** 指定热量生成时的教练点评（对比默认目标热量，占位符 {cal}=用户热量，{def}=默认热量） */
    private static final List<String> CALORIE_LOW_TEMPLATES = List.of(
            "{cal} 大卡是真低了啊，短期冲一下可以，长期这么吃训练状态会掉。我先按 {cal} 给你配，蛋白保够。",
            "{cal} 比你正常消耗 {def} 低不少，掉秤会快，但别长期这么吃。先按这个来，蛋白一点没少。",
            "行，{cal} 就 {cal}，比你正常消耗低不少，我按蛋白质优先给你排的。吃两天觉得没劲咱就往上调。");
    private static final List<String> CALORIE_MID_TEMPLATES = List.of(
            "{cal} 跟你的消耗差不多（正常在 {def} 上下），这样吃很稳。",
            "{cal} 这个量正好，按这个吃体重基本稳住。",
            "好，{cal}，和你的消耗基本持平，节奏稳的。");
    private static final List<String> CALORIE_HIGH_TEMPLATES = List.of(
            "{cal} 比你的消耗还高（正常在 {def} 左右），这么吃是往增重方向走，先按这个看看效果~",
            "{cal} 略高于你的日常消耗，吃一周看看体重变化，涨太快咱再往下调。",
            "行，{cal}，提醒一下这个量比你消耗大，体重会慢慢往上走。");

    /** 热量超出合理范围被钳制时的提示 */
    private static final List<String> CALORIE_CLAMP_TEMPLATES = List.of(
            "{raw} 这个数身体扛不住，我帮你调到 {cal}，这个量是安全的下限。",
            "{raw} 太低了会掉肌肉掉代谢，先按 {cal} 给你配，这个不能再低了。");

    /** 收集阶段用户指定热量时的确认（记下，回头按这个配） */
    private static final List<String> CALORIE_NOTED_TEMPLATES = List.of(
            "行，{cal} 大卡对吧？记下了，等下就按这个给你配。",
            "好，{cal}，我先记下，配的时候用这个数。",
            "{cal} 收到~一会儿按这个量给你排。");

    /** 用户改目标时的确认 */
    private static final List<String> GOAL_CHANGE_TEMPLATES = List.of(
            "改成{goal}了，没问题。",
            "好，{goal}方向，记下了。",
            "行，那就按{goal}来。");

    /** DONE 后用户修改个人资料（体重/身高/年龄/性别/活动量/目标）时，各字段的确认前缀 */
    private static final List<String> MOD_WEIGHT_ACK = List.of(
            "收到，体重 {v} 公斤，",
            "行，{v} 公斤是吧，",
            "好，按 {v} 公斤算，");
    private static final List<String> MOD_HEIGHT_ACK = List.of(
            "收到，身高 {v}，",
            "好，{v} 厘米，",
            "行，身高按 {v} 来，");
    private static final List<String> MOD_AGE_ACK = List.of(
            "收到，{v} 岁，",
            "行，{v} 岁，",
            "好，{v} 岁记下了，");
    private static final List<String> MOD_ACTIVITY_ACK = List.of(
            "活动量按{level}来，",
            "行，日常消耗按{level}算，",
            "好，{level}，");
    private static final List<String> MOD_GOAL_ACK = List.of(
            "目标改成{goal}，",
            "行，换成{goal}，",
            "好，按{goal}方向，");

    /** 资料修改后、重新给出计划的收尾语（确认前缀 + 此收尾） */
    private static final List<String> REPLAN_TAIL_TEMPLATES = List.of(
            "我按这个重新算一下~",
            "重新给你算一份哈。",
            "我重新排一份，看下面。");

    /** 保存计划的确认 */
    private static final List<String> SAVE_ACK_TEMPLATES = List.of(
            "存好了，{cal} 大卡这套已经在你的计划列表里，历史计划随时翻。",
            "记下了，{cal} 这套保存好了，照着吃就行。",
            "保存完毕~{cal} 大卡这套归档了，先按这个吃。");

    /** DONE 后用户闲聊时的兜底回复（按目标分组，教练口吻，不带客服话术） */
    private static final Map<String, List<String>> DONE_TEMPLATES = Map.of(
            "MUSCLE_GAIN", List.of(
                    "计划就在上面啦，照着吃，练完那顿别省。",
                    "先按这个吃两天，体重不动咱再调。",
                    "练后那顿是关键，其他都好说。"),
            "FAT_LOSS", List.of(
                    "照着吃，别让自己饿过头，掉太快反而不好。",
                    "这份先吃几天，下周咱们看变化再调。",
                    "体重是波动的，看一周的趋势就行，别天天纠结。"),
            "MAINTAIN", List.of(
                    "保持这个吃法就行，别暴饮暴食也别节食。",
                    "上面这份够稳了，先吃着。",
                    "就按这个节奏走，有变化再说。"));

    // ==================== 意图识别 ====================

    /** 重新生成计划（计划已存在时）：换一份/再生成一个… */
    private static final Pattern REGEN_PATTERN =
            Pattern.compile("再生成|重新生成|重新配|重新算|换一份|换一个|换份|再来一份|再来一个|再算一次|再换|^换$|帮我换");

    /** 重新开始收集（清空重填）：重来/重新开始… */
    private static final Pattern RESTART_PATTERN =
            Pattern.compile("重新开始|重新来|重来|重开|从头开始|从头来|清空|重新问|再来一次");

    /** 保存计划 */
    private static final Pattern SAVE_PATTERN =
            Pattern.compile("保存|存一下|收藏|记下来|留存|帮我存");

    /** 指定热量："1200 热量 / 1500 大卡 / 1200kcal / 热量 1200"（400~4999 的数字） */
    private static final Pattern CALORIE_PATTERN = Pattern.compile(
            "(\\d{3,4})\\s*(?:大卡|千卡|kcal|KCAL|卡|热量)|(?:热量|大卡|千卡)\\s*(?:是|为|要|等于)?\\s*(\\d{3,4})");

    /** 指定目标："我想减脂 / 我想增肌 / 按增肌来" */
    private static final Pattern GOAL_MUSCLE_PATTERN = Pattern.compile("增肌|增重|长肌肉|练壮|壮一点|变壮|长肉");
    private static final Pattern GOAL_FAT_PATTERN = Pattern.compile("减脂|减肥|瘦|掉秤|减重|刷脂");
    private static final Pattern GOAL_MAINTAIN_PATTERN = Pattern.compile("保持|维持|塑形|不变");

    /** 资料修改：年龄关键词（"20岁 / 年龄 / 多大"） */
    private static final Pattern AGE_KW_PATTERN = Pattern.compile("岁|年龄|多大");
    /** 资料修改：体重单位关键词（"68公斤 / 70kg / 150斤 / 体重"） */
    private static final Pattern WEIGHT_KW_PATTERN = Pattern.compile("体重|公斤|千克|kg|KG|Kg|斤");
    /** 资料修改：身高单位关键词（"身高175 / 175cm / 175厘米"） */
    private static final Pattern HEIGHT_KW_PATTERN = Pattern.compile("身高|厘米|cm|CM|Cm");
    /** 资料修改：更正/变化语气（无单位裸数字时，只有出现这类语气才推断为改资料） */
    private static final Pattern CHANGE_CUE_PATTERN = Pattern.compile("那我|改成|改为|换成|其实|实际|应该是|变成|调整|更正|纠正");
    /** 食物/营养语境防护：含这些词时，裸数字不当作身高/体重/年龄（避免"蛋白质120克"误判） */
    private static final Pattern FOOD_GUARD_PATTERN = Pattern.compile("克|热量|大卡|千卡|kcal|蛋白|碳|脂肪|餐|饭|吃|饿|卡");

    /** 用户拒答："不说/不想说/保密/跳过"等（收集阶段轻松追问，不当作无效报错） */
    private static final Pattern REFUSE_PATTERN =
            Pattern.compile("不说|不想说|不想告诉|不告诉|保密|不填|不想填|不想回答|不愿意|跳过|略过|凭什么|干嘛要");

    // ==================== 身体数据合理范围（超出则轻松追问，不接受、不推进） ====================
    private static final int WEIGHT_MIN = 30, WEIGHT_MAX = 200;   // 公斤
    private static final int HEIGHT_MIN = 130, HEIGHT_MAX = 210;  // 厘米
    private static final int AGE_MIN = 14, AGE_MAX = 65;          // 岁
    // 语法层放宽边界：只用于判断"这像不像该字段的数字"，真正是否接受由上面的合理范围决定
    private static final double WEIGHT_PARSE_LO = 20, WEIGHT_PARSE_HI = 500;
    private static final double HEIGHT_PARSE_LO = 80, HEIGHT_PARSE_HI = 260;
    private static final double AGE_PARSE_LO = 5, AGE_PARSE_HI = 120;

    // ==================== 解析正则 ====================

    private static final Pattern NUM_PATTERN = Pattern.compile("-?\\d+(?:\\.\\d+)?");
    private static final Pattern METER_PATTERN = Pattern.compile("(\\d(?:\\.\\d)?)\\s*[米mM](?:\\s*(\\d{1,2}))?");

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

        String text = message == null ? "" : message.trim();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sessionId", sessionId);

        // ===== 意图识别层：所有消息先做意图判断 =====
        Intent intent = detectIntent(text);
        String step = session.getStep();

        // ============ 计划已生成后的后续对话 ============
        if ("DONE".equals(step)) {
            // 1. 保存计划：按会话当前参数重生成同一份计划并落库
            if (intent.save()) {
                Map<String, Object> planData;
                try {
                    planData = generateForSession(session, session.getVariant() == null ? 0 : session.getVariant());
                    savePlan(userId, session, planData);
                } catch (Exception e) {
                    log.error("AI 对话保存计划失败", e);
                    String failReply = "保存的时候出了点岔子，你再发一次试试。";
                    session.setLastReply(failReply);
                    saveSession(sessionId, session);
                    result.put("reply", failReply);
                    result.put("status", "ASKING");
                    result.put("step", "DONE");
                    return result;
                }
                String reply = pick(session, SAVE_ACK_TEMPLATES)
                        .replace("{cal}", String.valueOf(planData.get("dailyCalories")));
                saveSession(sessionId, session);
                result.put("reply", reply);
                result.put("status", "PLAN_READY");
                result.put("planData", planData);
                log.info("AI 对话保存计划：用户{} 每日{}kcal", userId, planData.get("dailyCalories"));
                return result;
            }

            // 2. 指定热量生成（可同时带目标，如"按 1200 减脂"）
            if (intent.calories() != null) {
                if (intent.goal() != null) {
                    session.setGoal(intent.goal());
                }
                int variant = nextVariant(session);
                Map<String, Object> planData;
                try {
                    planData = generateForSession(session, variant, intent.calories());
                } catch (IllegalArgumentException e) {
                    return restartCollect(userId, sessionId, result);
                }
                session.setVariant(variant);
                session.setRegenCount(0);
                String reply = calorieComment(session, intent.calories(), planData);
                saveSession(sessionId, session);
                result.put("reply", reply);
                result.put("status", "PLAN_READY");
                result.put("planData", planData);
                log.info("AI 对话指定热量生成：用户{} {}kcal 每日{}kcal", userId, intent.calories(), planData.get("dailyCalories"));
                return result;
            }

            // 3. 修改资料时数字超合理范围（108岁/300kg/230cm）：轻松追问，不更新、不重算
            String modRangeNudge = rangeNudgeForModification(session, intent);
            if (modRangeNudge != null) {
                saveSession(sessionId, session);
                result.put("reply", modRangeNudge);
                result.put("status", "ASKING");
                result.put("step", "DONE");
                return result;
            }

            // 4. 修改个人资料（体重/身高/年龄/性别/活动量/目标）：先自然确认，再按新数据重算 TDEE 与计划
            String modAck = applyProfileModifications(session, intent);
            if (modAck != null) {
                int variant = nextVariant(session);
                Map<String, Object> planData;
                try {
                    planData = generateForSession(session, variant);
                } catch (IllegalArgumentException e) {
                    return restartCollect(userId, sessionId, result);
                }
                session.setVariant(variant);
                session.setRegenCount(0);
                String reply = modAck + pick(session, REPLAN_TAIL_TEMPLATES);
                saveSession(sessionId, session);
                result.put("reply", reply);
                result.put("status", "PLAN_READY");
                result.put("planData", planData);
                log.info("AI 对话修改资料重算：用户{} 每日{}kcal", userId, planData.get("dailyCalories"));
                return result;
            }

            // 4. 换一份：首次与连续换用不同回复，连续换提醒"还是同热量"
            if (intent.regen()) {
                int variant = nextVariant(session);
                Map<String, Object> planData;
                try {
                    planData = generateForSession(session, variant);
                } catch (IllegalArgumentException e) {
                    return restartCollect(userId, sessionId, result);
                }
                int regenCount = (session.getRegenCount() == null ? 0 : session.getRegenCount()) + 1;
                session.setVariant(variant);
                session.setRegenCount(regenCount);
                String reply = regenCount == 1
                        ? pick(session, REGEN_ACK_TEMPLATES.get(goalOf(session.getGoal())))
                        : pick(session, REGEN_MORE_TEMPLATES);
                saveSession(sessionId, session);
                result.put("reply", reply);
                result.put("status", "PLAN_READY");
                result.put("planData", planData);
                log.info("AI 对话换一份（第{}次）：用户{} 每日{}kcal", regenCount, userId, planData.get("dailyCalories"));
                return result;
            }

            // 5. 重新开始：清空重填
            if (intent.restart()) {
                return restartCollect(userId, sessionId, result);
            }

            // 6. 其他闲聊：按目标给教练式回复
            String reply = pick(session, DONE_TEMPLATES.get(goalOf(session.getGoal())));
            saveSession(sessionId, session);
            result.put("reply", reply);
            result.put("status", "ASKING");
            result.put("step", "DONE");
            return result;
        }

        // ============ 收集阶段 ============
        // 还没有计划可换，"换一份/再生成"视为重新开始
        if (intent.regen() || intent.restart()) {
            return restartCollect(userId, sessionId, result);
        }

        // 收集阶段就报目标：GOAL 步直接当作答案；其他步预填并确认
        if (intent.goal() != null && !"GOAL".equals(step)) {
            boolean changed = session.getGoal() != null;
            session.setGoal(intent.goal());
            String ack = changed
                    ? pick(session, GOAL_CHANGE_TEMPLATES).replace("{goal}", goalName(intent.goal()))
                    : pick(session, GOAL_ACK_TEMPLATES.get(intent.goal()));
            saveSession(sessionId, session);
            result.put("reply", ack + pick(session, ASK_TEMPLATES.get(step)));
            result.put("status", "ASKING");
            result.put("step", step);
            return result;
        }

        // 收集阶段就指定热量：先记下，信息齐了按这个配
        if (intent.calories() != null) {
            session.setTargetCalories(NutritionCalculator.clampCalories(intent.calories()));
            String noted = pick(session, CALORIE_NOTED_TEMPLATES)
                    .replace("{cal}", String.valueOf(session.getTargetCalories()));
            saveSession(sessionId, session);
            result.put("reply", noted + pick(session, ASK_TEMPLATES.get(step)));
            result.put("status", "ASKING");
            result.put("step", step);
            return result;
        }

        // 用户"不说/不想说/跳过"等拒答：轻松解释为什么需要，再继续追问当前项（不推进、不报错）
        if (REFUSE_PATTERN.matcher(text).find()) {
            String refuseReply = pick(session, REFUSE_ACK_TEMPLATES) + pick(session, ASK_TEMPLATES.get(step));
            saveSession(sessionId, session);
            result.put("reply", refuseReply);
            result.put("status", "ASKING");
            result.put("step", step);
            return result;
        }

        // 解析当前步骤答案
        Object value = parseAnswer(step, text);

        if (value != null) {
            // 数字明显超合理范围（108岁/300kg/230cm）：轻松追问一次，不接受、不进入下一步
            String rangeNudge = rangeNudgeForAnswer(session, step, value);
            if (rangeNudge != null) {
                saveSession(sessionId, session);
                result.put("reply", rangeNudge);
                result.put("status", "ASKING");
                result.put("step", step);
                return result;
            }
            // 答案有效，重置连续失败计数
            session.setFailureCount(0);
            applyAnswer(session, step, value);
            String nextStep = nextStep(step);
            if (nextStep == null) {
                // 六项齐全 → 后端精确计算计划（大模型不参与计算）
                int variant = ThreadLocalRandom.current().nextInt(3);
                Map<String, Object> planData;
                try {
                    planData = generateForSession(session, variant);
                } catch (IllegalArgumentException e) {
                    log.warn("AI 对话生成计划参数异常：{}", e.getMessage());
                    return restartCollect(userId, sessionId, result);
                }
                session.setStep("DONE");
                session.setVariant(variant);
                session.setRegenCount(0);
                // 用户中途指定过热量 → 用热量点评语；否则按目标点评语
                String reply = session.getTargetCalories() != null
                        ? calorieComment(session, session.getTargetCalories(), planData)
                        : pick(session, PLAN_READY_TEMPLATES.get(goalOf(session.getGoal())));
                saveSession(sessionId, session);
                result.put("reply", reply);
                result.put("status", "PLAN_READY");
                result.put("planData", planData);
                log.info("AI 对话生成计划成功：用户{} 每日{}kcal", userId, planData.get("dailyCalories"));
                return result;
            }
            session.setStep(nextStep);
            // 目标刚收集完时，先回应目标再问活动量，更像真人私教
            // 性别刚收集完时，先轻松接一句再问下一项
            String reply;
            if ("GOAL".equals(step)) {
                reply = pick(session, GOAL_ACK_TEMPLATES.get((String) value)) + pick(session, ASK_TEMPLATES.get(nextStep));
            } else if ("GENDER".equals(step)) {
                reply = pick(session, "MALE".equals(value) ? GENDER_MALE_ACK : GENDER_FEMALE_ACK)
                        + pick(session, ASK_TEMPLATES.get(nextStep));
            } else {
                reply = pick(session, ASK_TEMPLATES.get(nextStep));
            }
            saveSession(sessionId, session);
            result.put("reply", reply);
            result.put("status", "ASKING");
            result.put("step", nextStep);
            return result;
        }

        // 没解析出来 → 连续失败用更轻松直接的语气，首次用标准 RETRY
        int fails = (session.getFailureCount() == null ? 0 : session.getFailureCount()) + 1;
        session.setFailureCount(fails);
        List<String> retryPool = fails >= 2 ? CONSECUTIVE_FAIL_TEMPLATES.get(step) : RETRY_TEMPLATES.get(step);
        if (retryPool == null) retryPool = RETRY_TEMPLATES.get(step);
        String retryReply = pick(session, retryPool);
        saveSession(sessionId, session);
        result.put("reply", retryReply);
        result.put("status", "ASKING");
        result.put("step", step);
        return result;
    }

    // ==================== 意图识别层 ====================

    /**
     * 用户消息意图
     *
     * @param regen     换一份 / 再生成
     * @param restart   重新开始收集
     * @param save      保存当前计划
     * @param calories  指定每日热量（原始值，处理时做钳制）
     * @param goal      指定目标（MUSCLE_GAIN/FAT_LOSS/MAINTAIN）
     * @param weight    修改后的体重（公斤），null 表示未提及
     * @param height    修改后的身高（厘米），null 表示未提及
     * @param age       修改后的年龄，null 表示未提及
     * @param gender    修改后的性别（MALE/FEMALE），null 表示未提及
     * @param activity  修改后的活动量（SEDENTARY/LIGHT/MODERATE/ACTIVE），null 表示未提及
     */
    private record Intent(boolean regen, boolean restart, boolean save, Integer calories, String goal,
                          Integer weight, Integer height, Integer age, String gender, String activity) {
    }

    /**
     * 意图识别：正则规则判断（Mock 实现，大模型接入后由模型 function-call 替代）
     */
    private Intent detectIntent(String text) {
        boolean regen = REGEN_PATTERN.matcher(text).find();
        boolean restart = RESTART_PATTERN.matcher(text).find();
        boolean save = SAVE_PATTERN.matcher(text).find();

        Integer calories = null;
        Matcher m = CALORIE_PATTERN.matcher(text);
        if (m.find()) {
            String raw = m.group(1) != null ? m.group(1) : m.group(2);
            try {
                calories = Integer.parseInt(raw);
            } catch (NumberFormatException ignored) {
            }
        }

        String goal = null;
        // "换成增肌/不减脂了改增肌"这类明确转向增肌的，优先识别为增肌（句中同时出现减脂也不影响）
        if (GOAL_MUSCLE_PATTERN.matcher(text).find()) {
            goal = "MUSCLE_GAIN";
        } else if (GOAL_FAT_PATTERN.matcher(text).find()) {
            goal = "FAT_LOSS";
        } else if (GOAL_MAINTAIN_PATTERN.matcher(text).find()) {
            goal = "MAINTAIN";
        }

        // 资料修改：体重/身高/年龄（数字字段）+ 性别/活动量（关键词字段）
        NumField nf = detectNumericProfile(text);
        Integer weight = null, height = null, age = null;
        if (nf != null) {
            switch (nf.field()) {
                case "WEIGHT" -> weight = nf.value();
                case "HEIGHT" -> height = nf.value();
                case "AGE" -> age = nf.value();
                default -> { }
            }
        }
        // 性别/活动量为关键词识别，容易在疑问句（"女生也能练吗？"）里误触，
        // 仅当出现第一人称（我/咱）或更正语气（改成/其实…）才认作"修改自己的资料"
        boolean selfOrCue = CHANGE_CUE_PATTERN.matcher(text).find()
                || text.contains("我") || text.contains("咱");
        String gender = selfOrCue ? (String) parseAnswer("GENDER", text) : null;
        String activity = selfOrCue ? (String) parseAnswer("ACTIVITY", text) : null;

        return new Intent(regen, restart, save, calories, goal, weight, height, age, gender, activity);
    }

    /** 数字字段识别结果：field = WEIGHT/HEIGHT/AGE，value 为取整后的数值 */
    private record NumField(String field, Integer value) {
    }

    /**
     * 从自由文本识别用户在修改哪个数字资料（体重/身高/年龄）。
     * <p>
     * 识别优先级：
     * 1. 关键词优先：岁/年龄→年龄；身高/cm/厘米/米→身高；体重/公斤/kg/斤→体重；
     * 2. 无单位裸数字：身高区间(140~220)直接判身高；体重/年龄区间需出现更正语气（那我/改成/其实…），
     *    且处于食物/营养语境（克/卡/蛋白/吃…）时不推断，避免把"蛋白质120克"误判成身高。
     */
    private NumField detectNumericProfile(String text) {
        Double n = firstNumber(text);
        if (n == null) {
            return null;
        }
        int v = n.intValue();

        // 1. 关键词优先（语法层放宽，让超合理范围但明显在说该字段的数字也能被识别，再由范围拦截追问）
        if (AGE_KW_PATTERN.matcher(text).find()) {
            if (v >= AGE_PARSE_LO && v <= AGE_PARSE_HI && n == Math.floor(n)) {
                return new NumField("AGE", v);
            }
            return null;
        }
        if (METER_PATTERN.matcher(text).find() || HEIGHT_KW_PATTERN.matcher(text).find()) {
            Double h = extractHeight(text);
            if (h != null && h >= HEIGHT_PARSE_LO && h <= HEIGHT_PARSE_HI) {
                return new NumField("HEIGHT", h.intValue());
            }
            return null;
        }
        if (WEIGHT_KW_PATTERN.matcher(text).find()) {
            Double w = extractWeight(text);
            if (w != null && w >= WEIGHT_PARSE_LO && w <= WEIGHT_PARSE_HI) {
                return new NumField("WEIGHT", w.intValue());
            }
            return null;
        }

        // 2. 无单位裸数字：食物语境直接排除，其余按区间 + 更正语气推断
        if (FOOD_GUARD_PATTERN.matcher(text).find()) {
            return null;
        }
        // 身高区间最稳定（成人身高集中在此），无需更正语气也可判定
        if (v >= 140 && v <= 220) {
            return new NumField("HEIGHT", v);
        }
        boolean cue = CHANGE_CUE_PATTERN.matcher(text).find();
        if (cue && v >= 30 && v <= 200) {
            return new NumField("WEIGHT", v);
        }
        if (cue && v >= 10 && v <= 99 && n == Math.floor(n)) {
            return new NumField("AGE", v);
        }
        return null;
    }

    /**
     * 收集阶段：当前步骤答案是否在合理范围。
     * 超范围返回轻松追问语（{v} 已替换），不推进；在范围内返回 null。
     * 连续 2 次以上异常时自动切换更轻松直接的 CONSECUTIVE_FAIL 语气。
     */
    private String rangeNudgeForAnswer(AiChatSession session, String step, Object value) {
        if (!(value instanceof Integer v)) {
            return null;
        }
        List<String> templates = switch (step) {
            case "WEIGHT" -> (v < WEIGHT_MIN || v > WEIGHT_MAX) ? RANGE_NUDGE_TEMPLATES.get("WEIGHT") : null;
            case "HEIGHT" -> (v < HEIGHT_MIN || v > HEIGHT_MAX) ? RANGE_NUDGE_TEMPLATES.get("HEIGHT") : null;
            case "AGE" -> (v < AGE_MIN || v > AGE_MAX) ? RANGE_NUDGE_TEMPLATES.get("AGE") : null;
            default -> null;
        };
        if (templates == null) return null;
        // 连续 2 次以上异常：用更轻松直接的语气
        int fails = (session.getFailureCount() == null ? 0 : session.getFailureCount()) + 1;
        session.setFailureCount(fails);
        List<String> pool = fails >= 2 ? CONSECUTIVE_FAIL_TEMPLATES.get(step) : templates;
        if (pool == null) pool = templates;
        return pick(session, pool).replace("{v}", String.valueOf(v));
    }

    /**
     * DONE 阶段资料修改：意图中的数字字段超合理范围时返回追问语（拦截、不更新、不重算），否则 null。
     */
    private String rangeNudgeForModification(AiChatSession session, Intent intent) {
        if (intent.age() != null && (intent.age() < AGE_MIN || intent.age() > AGE_MAX)) {
            return pick(session, RANGE_NUDGE_TEMPLATES.get("AGE")).replace("{v}", String.valueOf(intent.age()));
        }
        if (intent.weight() != null && (intent.weight() < WEIGHT_MIN || intent.weight() > WEIGHT_MAX)) {
            return pick(session, RANGE_NUDGE_TEMPLATES.get("WEIGHT")).replace("{v}", String.valueOf(intent.weight()));
        }
        if (intent.height() != null && (intent.height() < HEIGHT_MIN || intent.height() > HEIGHT_MAX)) {
            return pick(session, RANGE_NUDGE_TEMPLATES.get("HEIGHT")).replace("{v}", String.valueOf(intent.height()));
        }
        return null;
    }

    // ==================== 生成与保存辅助 ====================

    /** 下一个食谱变体（轮换 0-2） */
    private int nextVariant(AiChatSession session) {
        return session.getVariant() == null
                ? ThreadLocalRandom.current().nextInt(3)
                : (session.getVariant() + 1) % 3;
    }

    /** 按会话数据生成计划（热量用会话记住的指定值，无则按 TDEE） */
    private Map<String, Object> generateForSession(AiChatSession session, int variant) {
        return generateForSession(session, variant, null);
    }

    /** 按会话数据生成计划（overrideCalories 非 null 时优先，并写回会话） */
    private Map<String, Object> generateForSession(AiChatSession session, int variant, Integer overrideCalories) {
        Integer target = overrideCalories != null ? overrideCalories : session.getTargetCalories();
        Map<String, Object> planData = NutritionCalculator.generate(session.getHeight(), session.getWeight(),
                session.getAge(), session.getGender(), goalOf(session.getGoal()),
                session.getActivityLevel(), variant, target);
        session.setTargetCalories((Integer) planData.get("dailyCalories"));
        return planData;
    }

    /**
     * DONE 后用户修改个人资料：识别意图中相对会话发生变化的字段，更新会话并拼接自然确认语。
     * <p>
     * 资料（体重/身高/年龄/性别/活动量/目标）一旦变化，清除用户此前手动指定的热量，
     * 改按新身体数据重新计算 TDEE 与计划。
     *
     * @return 确认语前缀（调用方再拼接收尾语并重新生成计划）；无任何有效变化时返回 null
     */
    private String applyProfileModifications(AiChatSession session, Intent intent) {
        StringBuilder ack = new StringBuilder();

        if (intent.weight() != null && !intent.weight().equals(session.getWeight())) {
            session.setWeight(intent.weight());
            ack.append(pick(session, MOD_WEIGHT_ACK).replace("{v}", String.valueOf(intent.weight())));
        }
        if (intent.height() != null && !intent.height().equals(session.getHeight())) {
            session.setHeight(intent.height());
            ack.append(pick(session, MOD_HEIGHT_ACK).replace("{v}", String.valueOf(intent.height())));
        }
        if (intent.age() != null && !intent.age().equals(session.getAge())) {
            session.setAge(intent.age());
            ack.append(pick(session, MOD_AGE_ACK).replace("{v}", String.valueOf(intent.age())));
        }
        if (intent.gender() != null && !intent.gender().equals(session.getGender())) {
            session.setGender(intent.gender());
            ack.append("MALE".equals(intent.gender()) ? "好，男生，" : "好，女生，");
        }
        if (intent.activity() != null && !intent.activity().equals(session.getActivityLevel())) {
            session.setActivityLevel(intent.activity());
            ack.append(pick(session, MOD_ACTIVITY_ACK).replace("{level}", activityName(intent.activity())));
        }
        if (intent.goal() != null && !intent.goal().equals(goalOf(session.getGoal()))) {
            session.setGoal(intent.goal());
            ack.append(pick(session, MOD_GOAL_ACK).replace("{goal}", goalName(intent.goal())));
        }

        if (ack.isEmpty()) {
            return null;
        }
        // 身体数据变了，之前手动指定的热量不再适用，清掉后按新 TDEE 重算
        session.setTargetCalories(null);
        return ack.toString();
    }

    /** 活动量中文名（用于资料修改确认语） */
    private String activityName(String level) {
        return switch (level) {
            case "SEDENTARY" -> "久坐不动";
            case "LIGHT" -> "轻度活动";
            case "MODERATE" -> "中等活动量";
            case "ACTIVE" -> "经常运动";
            default -> "日常活动";
        };
    }

    /**
     * 指定热量后的教练点评：与默认目标热量对比，偏低/接近/偏高用不同话术；
     * 用户给的热量超出安全范围被钳制时，先说明调整
     */
    private String calorieComment(AiChatSession session, int requestedCalories, Map<String, Object> planData) {
        int actual = (Integer) planData.get("dailyCalories");
        // 需要默认 TDEE 与目标热量做对比
        double bmr = 10 * session.getWeight() + 6.25 * session.getHeight() - 5 * session.getAge();
        bmr = "MALE".equals(session.getGender()) ? bmr + 5 : bmr - 161;
        int defaultTarget = NutritionCalculator.calcTargetCalories(
                bmr * NutritionCalculator.factorOf(session.getActivityLevel()), goalOf(session.getGoal()));

        StringBuilder sb = new StringBuilder();
        // 用户给的数超出安全范围被钳制时，先说明调整
        if (NutritionCalculator.clampCalories(requestedCalories) != requestedCalories) {
            sb.append(pick(session, CALORIE_CLAMP_TEMPLATES)
                    .replace("{raw}", String.valueOf(requestedCalories))
                    .replace("{cal}", String.valueOf(actual)));
        }
        String comment;
        if (actual < defaultTarget * 0.85) {
            comment = pick(session, CALORIE_LOW_TEMPLATES);
        } else if (actual > defaultTarget * 1.15) {
            comment = pick(session, CALORIE_HIGH_TEMPLATES);
        } else {
            comment = pick(session, CALORIE_MID_TEMPLATES);
        }
        sb.append(comment.replace("{cal}", String.valueOf(actual)).replace("{def}", String.valueOf(defaultTarget)));
        return sb.toString();
    }

    /**
     * 保存计划：与 AiPlanController /save 落库逻辑一致（计划 + AI_PLAN 消息通知）
     */
    private void savePlan(Long userId, AiChatSession session, Map<String, Object> planData) throws Exception {
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
        plan.setMealsJson(JSON_MAPPER.writeValueAsString(planData.get("meals")));
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

        log.info("用户{}在对话中保存 AI 计划：每日{}kcal（ID={}）", userId, plan.getDailyCalories(), plan.getId());
    }

    /** 清空会话重新收集（统一出口） */
    private Map<String, Object> restartCollect(Long userId, String sessionId, Map<String, Object> result) {
        AiChatSession fresh = new AiChatSession();
        fresh.setUserId(userId);
        fresh.setStep("WEIGHT");
        String restartReply = pick(fresh, RESTART_ACK_TEMPLATES);
        saveSession(sessionId, fresh);
        result.put("reply", restartReply);
        result.put("status", "ASKING");
        result.put("step", "WEIGHT");
        return result;
    }

    /** 目标中文名（用于文案） */
    private String goalName(String goal) {
        return switch (goal) {
            case "MUSCLE_GAIN" -> "增肌";
            case "FAT_LOSS" -> "减脂";
            default -> "维持";
        };
    }

    /** 当前步骤答案解析，解析失败返回 null */
    private Object parseAnswer(String step, String text) {
        switch (step) {
            case "WEIGHT": {
                // 语法层放宽（20~500），让"300kg"这类能被识别出来再走范围拦截；是否接受由 numericRangeNudge 决定
                Double v = extractWeight(text);
                return (v != null && v >= WEIGHT_PARSE_LO && v <= WEIGHT_PARSE_HI) ? v.intValue() : null;
            }
            case "HEIGHT": {
                Double v = extractHeight(text);
                return (v != null && v >= HEIGHT_PARSE_LO && v <= HEIGHT_PARSE_HI) ? v.intValue() : null;
            }
            case "AGE": {
                Double v = firstNumber(text);
                return (v != null && v >= AGE_PARSE_LO && v <= AGE_PARSE_HI && v == Math.floor(v)) ? v.intValue() : null;
            }
            case "GENDER": {
                if (text.matches(".*(女|小姐姐|姑娘|妹子|妹子|女生).*")) return "FEMALE";
                if (text.matches(".*(男|哥哥|小哥|汉子|男生|哥).*")) return "MALE";
                return null;
            }
            case "GOAL": {
                if (text.matches(".*(增肌|增重|长肌肉|练壮|壮一点|变壮|长肉).*")) return "MUSCLE_GAIN";
                if (text.matches(".*(减脂|减肥|瘦|掉秤|减重|刷脂).*")) return "FAT_LOSS";
                if (text.matches(".*(保持|维持|塑形|不变).*")) return "MAINTAIN";
                return null;
            }
            case "ACTIVITY": {
                if (text.matches(".*(久坐|不动|很少动|不运动|办公|开车|躺).*")) return "SEDENTARY";
                if (text.matches(".*(偶尔|少量|一点|一两次|1-2|每周一|每周两|轻松).*")) return "LIGHT";
                if (text.matches(".*(中等|三五次|3-5|正常|一般|还行|[4-6]\\s*次|四次|五次|六次).*")) return "MODERATE";
                if (text.matches(".*(经常|每天|天天|高强度|大量|频繁|教练|职业|[7-9]\\s*次|七次|八次|九次|挺多|很多|爱运动|多运动|常运动|经常练).*")) return "ACTIVE";
                return null;
            }
            default:
                return null;
        }
    }

    private void applyAnswer(AiChatSession session, String step, Object value) {
        switch (step) {
            case "WEIGHT":   session.setWeight((Integer) value); break;
            case "HEIGHT":   session.setHeight((Integer) value); break;
            case "AGE":      session.setAge((Integer) value); break;
            case "GENDER":   session.setGender((String) value); break;
            case "GOAL":     session.setGoal((String) value); break;
            case "ACTIVITY": session.setActivityLevel((String) value); break;
            default:
        }
    }

    private String nextStep(String step) {
        int idx = STEP_ORDER.indexOf(step);
        if (idx < 0 || idx + 1 >= STEP_ORDER.size()) return null;
        return STEP_ORDER.get(idx + 1);
    }

    /** 体重：支持 "70"、"70kg"、"150斤" */
    private Double extractWeight(String text) {
        Double n = firstNumber(text);
        if (n == null) return null;
        if (text.contains("斤") && !text.contains("公斤")) {
            return n * 0.5;
        }
        return n;
    }

    /** 身高：支持 "175"、"175cm"、"1米75"、"1.75m" */
    private Double extractHeight(String text) {
        Matcher m = METER_PATTERN.matcher(text);
        if (m.find()) {
            double meters = Double.parseDouble(m.group(1));
            if (m.group(2) != null) {
                return meters * 100 + Double.parseDouble(m.group(2));
            }
            return meters * 100;
        }
        return firstNumber(text);
    }

    private Double firstNumber(String text) {
        Matcher m = NUM_PATTERN.matcher(text);
        if (m.find()) {
            try {
                return Double.parseDouble(m.group());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    /** 目标空值兜底 */
    private String goalOf(String goal) {
        return goal == null ? "MAINTAIN" : goal;
    }

    /**
     * 带去重的随机文案：避开最近 3 轮说过的句子，防止复读；
     * 选中的模板记录到会话 recentReplies（保留最近 3 条）
     */
    private String pick(AiChatSession session, List<String> templates) {
        List<String> pool = templates;
        List<String> recent = session.getRecentReplies();
        if (recent != null && !recent.isEmpty() && templates.size() > 1) {
            List<String> filtered = templates.stream()
                    .filter(t -> !recent.contains(t))
                    .toList();
            if (!filtered.isEmpty()) {
                pool = filtered;
            }
        }
        String picked = pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
        recordReply(session, picked);
        return picked;
    }

    /** 记录最近回复模板到 recentReplies（保留最近 3 条，用于 3 轮防复读） */
    private void recordReply(AiChatSession session, String reply) {
        List<String> recent = session.getRecentReplies();
        if (recent == null) {
            recent = new ArrayList<>();
        }
        recent.add(reply);
        while (recent.size() > 3) {
            recent.remove(0);
        }
        session.setRecentReplies(recent);
        session.setLastReply(reply);
    }

    private AiChatSession loadSession(String sessionId) {
        try {
            String json = stringRedisTemplate.opsForValue().get(KEY_PREFIX + sessionId);
            if (json == null || json.isEmpty()) return null;
            return MAPPER.readValue(json, AiChatSession.class);
        } catch (Exception e) {
            log.warn("AI 会话读取失败，视为新会话：{}", e.getMessage());
            return null;
        }
    }

    private void saveSession(String sessionId, AiChatSession session) {
        try {
            stringRedisTemplate.opsForValue().set(KEY_PREFIX + sessionId,
                    MAPPER.writeValueAsString(session), SESSION_TTL);
        } catch (Exception e) {
            log.error("AI 会话保存失败", e);
        }
    }

}
