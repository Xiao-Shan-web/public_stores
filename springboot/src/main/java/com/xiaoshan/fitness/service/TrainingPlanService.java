package com.xiaoshan.fitness.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 训练计划生成服务。
 * <p>
 * 输出结构化一周训练安排（训练日 → 热身 / 动作[组数·次数·休息] / 拉伸），
 * 前端直接渲染，不依赖模型自由文本。
 * <p>
 * 双轨：
 * <ul>
 *   <li><b>local（永远可用）</b>：内置动作库按「目标 × 每周天数 × 器械条件 × 水平」做经典分化编排，
 *       无 API Key 也能即时给出可执行计划；</li>
 *   <li><b>qianfan（增强）</b>：配置千帆后优先让大模型按强约束 JSON 生成更个性化的计划，
 *       模型不可用或返回非法结构时自动回退 local，保证接口始终有结果。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TrainingPlanService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final QianfanLlmClient llmClient;

    /** 训练目标：增肌 / 减脂 / 塑形保持 */
    private static final String GOAL_MUSCLE = "MUSCLE_GAIN";
    private static final String GOAL_FAT = "FAT_LOSS";
    private static final String GOAL_SHAPE = "SHAPE";
    /** 器械条件：健身房 / 居家（徒手为主） / 仅哑铃 */
    private static final String EQ_GYM = "GYM";
    private static final String EQ_HOME = "HOME";
    private static final String EQ_DUMBBELL = "DUMBBELL";

    /**
     * 生成训练计划
     *
     * @param goal        MUSCLE_GAIN/FAT_LOSS/SHAPE
     * @param level       BEGINNER/INTERMEDIATE
     * @param equipment   GYM/HOME/DUMBBELL
     * @param daysPerWeek 每周训练天数 3-6
     */
    public Map<String, Object> generate(String goal, String level, String equipment, Integer daysPerWeek) {
        String g = normalizeGoal(goal);
        String lv = (level == null || level.isBlank()) ? "BEGINNER" : level.toUpperCase();
        String eq = (equipment == null || equipment.isBlank()) ? EQ_GYM : equipment.toUpperCase();
        int days = daysPerWeek == null ? 4 : Math.max(3, Math.min(6, daysPerWeek));

        // 配置了千帆则尝试个性化生成，失败回退本地
        if (llmClient.isConfigured()) {
            Map<String, Object> ai = tryQianfan(g, lv, eq, days);
            if (ai != null) {
                return ai;
            }
        }
        return buildLocal(g, lv, eq, days);
    }

    // ==================== 千帆增强 ====================

    private static final String TRAIN_SYS = """
            你是专业健身教练，根据用户条件制定一周力量训练计划。只输出 JSON，不要 markdown，不要多余文字，结构：
            {
              "summary": "2-3句总体安排与强度说明",
              "days": [
                {"day":"训练日1","focus":"部位，如 胸+三头","warmup":["热身动作"],"cooldown":["拉伸动作"],
                 "exercises":[{"name":"动作名","sets":4,"reps":"8-12","rest":"60-90秒","note":"要点或留空"}]}
              ],
              "tips": ["3-5条安全与执行建议"]
            }
            要求：动作数量与天数严格匹配用户每周训练天数；每个训练日 4-6 个动作；组数为整数；
            内容必须与用户器械条件相符（居家不得安排杠铃/固定器械）；减脂目标多安排复合动作与循环；
            初学者每组之间休息更充分、单动作组数不超过3。
            """;

    private Map<String, Object> tryQianfan(String goal, String level, String eq, int days) {
        try {
            String userPrompt = "目标=" + goalCn(goal)
                    + "，水平=" + ("BEGINNER".equals(level) ? "初学者" : "有一定基础")
                    + "，器械条件=" + eqCn(eq)
                    + "，每周训练" + days + "天。请输出 JSON 计划。";
            String raw = llmClient.chat(TRAIN_SYS, userPrompt);
            String json = stripJson(raw);
            JsonNode root = MAPPER.readTree(json);
            JsonNode daysNode = root.path("days");
            // 结构校验：必须有合法 days 且每天有 exercises，否则视为生成失败 → 回退
            if (!daysNode.isArray() || daysNode.isEmpty() || !daysNode.get(0).path("exercises").isArray()) {
                log.warn("千帆训练计划结构缺失，回退本地编排");
                return null;
            }
            Map<String, Object> data = baseMeta(goal, level, eq, days);
            data.put("provider", "qianfan");
            data.put("summary", root.path("summary").asText("已为你生成一周训练计划，注意循序渐进、动作标准优先。"));
            data.put("days", parseDays(daysNode, days));
            data.put("tips", parseStringList(root.path("tips"),
                    List.of("训练前充分热身，训练后拉伸放松", "动作质量优先于重量",
                            "保证蛋白质摄入与睡眠，给肌肉恢复时间")));
            return data;
        } catch (Exception e) {
            log.warn("千帆训练计划生成失败，回退本地编排：{}", e.getMessage());
            return null;
        }
    }

    private List<Map<String, Object>> parseDays(JsonNode daysNode, int expectedDays) {
        List<Map<String, Object>> days = new ArrayList<>();
        for (JsonNode d : daysNode) {
            Map<String, Object> day = new LinkedHashMap<>();
            day.put("day", d.path("day").asText("训练日"));
            day.put("focus", d.path("focus").asText("综合训练"));
            day.put("warmup", parseStringList(d.path("warmup"), List.of("快走/慢跑 5 分钟", "关节活动度激活")));
            day.put("cooldown", parseStringList(d.path("cooldown"), List.of("目标肌群静态拉伸 5-10 分钟")));
            List<Map<String, Object>> exs = new ArrayList<>();
            for (JsonNode ex : d.path("exercises")) {
                Map<String, Object> e = new LinkedHashMap<>();
                e.put("name", ex.path("name").asText(""));
                e.put("sets", Math.max(1, ex.path("sets").asInt(3)));
                e.put("reps", ex.path("reps").asText("8-12"));
                e.put("rest", ex.path("rest").asText("60秒"));
                e.put("note", ex.path("note").asText(""));
                if (!String.valueOf(e.get("name")).isBlank()) {
                    exs.add(e);
                }
            }
            day.put("exercises", exs);
            days.add(day);
            if (days.size() >= expectedDays) break;
        }
        return days;
    }

    private List<String> parseStringList(JsonNode node, List<String> fallback) {
        List<String> out = new ArrayList<>();
        if (node.isArray()) {
            node.forEach(n -> {
                String s = n.asText("");
                if (!s.isBlank()) out.add(s);
            });
        }
        return out.isEmpty() ? fallback : out;
    }

    private String stripJson(String raw) {
        String s = raw == null ? "" : raw.trim();
        if (s.startsWith("```")) {
            int a = s.indexOf('{'), b = s.lastIndexOf('}');
            if (a >= 0 && b > a) return s.substring(a, b + 1);
        }
        return s;
    }

    // ==================== 本地动作库编排（确定性兜底） ====================

    private Map<String, Object> buildLocal(String goal, String level, String eq, int days) {
        boolean beginner = "BEGINNER".equals(level);
        // 分化模板：每天一个 focus key
        List<String> split = splitFor(days);

        List<Map<String, Object>> dayList = new ArrayList<>();
        int round = 0;
        for (int i = 0; i < split.size(); i++) {
            String focus = split.get(i);
            // 全身日多轮时轮换动作变体，减少重复
            List<Exercise> pool = pickExercises(focus, eq, goal, round);
            List<Map<String, Object>> exs = new ArrayList<>();
            int take = switch (focus) {
                case "CARDIO_CORE" -> 5;
                case "FULL" -> 6;
                default -> beginner ? 5 : 6;
            };
            for (int k = 0; k < Math.min(take, pool.size()); k++) {
                Exercise ex = pool.get(k);
                Map<String, Object> e = new LinkedHashMap<>();
                int sets = beginner ? Math.max(2, ex.sets - 1) : ex.sets;
                // 减脂目标：力量动作略降组数，循环完成；增肌保持
                e.put("name", ex.name);
                e.put("sets", GOAL_FAT.equals(goal) && !"CARDIO_CORE".equals(focus) ? Math.max(3, sets - 1) : sets);
                e.put("reps", ex.reps);
                e.put("rest", GOAL_FAT.equals(goal) ? "45-60秒" : ex.rest);
                e.put("note", ex.note);
                exs.add(e);
            }
            if ("FULL".equals(focus)) round++;

            Map<String, Object> day = new LinkedHashMap<>();
            day.put("day", "训练日" + (i + 1));
            day.put("focus", focusCn(focus));
            day.put("warmup", List.of("快走/慢跑或开合跳 5 分钟", "肩、髋、膝关节动态激活"));
            day.put("exercises", exs);
            day.put("cooldown", List.of(focusCn(focus).replace("+核心", "") + "相关肌群静态拉伸 5-10 分钟", "深呼吸放松"));
            dayList.add(day);
        }

        Map<String, Object> data = baseMeta(goal, level, eq, days);
        data.put("provider", "local");
        data.put("summary", buildSummary(goal, level, eq, days));
        data.put("days", dayList);
        data.put("tips", buildTips(goal, eq));
        return data;
    }

    /** 按每周天数返回分化 focus 序列（FULL=全身，PUSH=推，PULL=拉，LEGS=腿，SHOULDER_CORE，CARDIO_CORE） */
    private List<String> splitFor(int days) {
        return switch (days) {
            case 3 -> new ArrayList<>(List.of("FULL", "FULL", "FULL"));
            case 4 -> new ArrayList<>(List.of("PUSH", "PULL", "LEGS", "CARDIO_CORE"));
            case 5 -> new ArrayList<>(List.of("PUSH", "PULL", "LEGS", "SHOULDER_CORE", "CARDIO_CORE"));
            default -> new ArrayList<>(List.of("PUSH", "PULL", "LEGS", "PUSH", "PULL", "LEGS"));
        };
    }

    /** 从动作库选动作：按 focus + 器械条件 + 是否轮换变体 */
    private List<Exercise> pickExercises(String focus, String eq, String goal, int round) {
        boolean home = EQ_HOME.equals(eq);
        boolean dumbbell = EQ_DUMBBELL.equals(eq);
        List<Exercise> raw = switch (focus) {
            case "FULL" -> home ? EX_FULL_HOME : (dumbbell ? EX_FULL_DB : EX_FULL_GYM);
            case "PUSH" -> home ? EX_PUSH_HOME : (dumbbell ? EX_PUSH_DB : EX_PUSH_GYM);
            case "PULL" -> home ? EX_PULL_HOME : (dumbbell ? EX_PULL_DB : EX_PULL_GYM);
            case "LEGS" -> home ? EX_LEG_HOME : (dumbbell ? EX_LEG_DB : EX_LEG_GYM);
            case "SHOULDER_CORE" -> home ? EX_SHOULDER_CORE_HOME : (dumbbell ? EX_SHOULDER_CORE_DB : EX_SHOULDER_CORE_GYM);
            default -> GOAL_FAT.equals(goal) ? EX_CARDIO_FAT : EX_CARDIO_CORE;
        };
        // 全身日多轮轮换：按 round 偏移取，保证有变化
        if ("FULL".equals(focus) && round > 0 && raw.size() > 6) {
            List<Exercise> rotated = new ArrayList<>();
            int offset = (round % 2) * 3;
            for (int i = 0; i < raw.size(); i++) {
                rotated.add(raw.get((i + offset) % raw.size()));
            }
            return rotated;
        }
        return raw;
    }

    private Map<String, Object> baseMeta(String goal, String level, String eq, int days) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("goal", goal);
        data.put("level", level);
        data.put("equipment", eq);
        data.put("daysPerWeek", days);
        return data;
    }

    private String buildSummary(String goal, String level, String eq, int days) {
        String base = "每周" + days + "练，" + eqCn(eq) + "，" + ("BEGINNER".equals(level) ? "以动作品质和建立习惯为主，重量循序渐进。" : "可逐步加重，保持渐进超负荷。");
        return switch (goal) {
            case GOAL_MUSCLE -> "增肌方向：以复合动作为核心，组间休息充分，配合热量盈余与足量蛋白。" + base;
            case GOAL_FAT -> "减脂方向：力量循环 + 有氧结合，控制组间休息提升心率，配合热量缺口。" + base;
            default -> "塑形/保持方向：上下肢均衡发展，力量与核心并重。" + base;
        };
    }

    private List<String> buildTips(String goal, String eq) {
        List<String> tips = new ArrayList<>(List.of(
                "训练前 5-10 分钟动态热身，训练后静态拉伸",
                "动作标准优先于重量，宁轻勿假，避免代偿受伤",
                "每周安排至少 1 天完全休息，保证 7-8 小时睡眠"));
        if (GOAL_MUSCLE.equals(goal)) {
            tips.add("增肌期每公斤体重摄入 1.6-2.0g 蛋白质，训练后及时补充碳水蛋白");
        } else if (GOAL_FAT.equals(goal)) {
            tips.add("减脂保持 300-500 大卡温和缺口，配合每日步行 8000 步以上");
        }
        if (EQ_HOME.equals(eq)) {
            tips.add("居家训练注意地面防滑、保持环境通透，必要时备一张瑜伽垫");
        }
        return tips;
    }

    private String normalizeGoal(String goal) {
        if (goal == null) return GOAL_SHAPE;
        return switch (goal.toUpperCase()) {
            case "MUSCLE_GAIN", "MUSCLE" -> GOAL_MUSCLE;
            case "FAT_LOSS", "FAT" -> GOAL_FAT;
            default -> GOAL_SHAPE;
        };
    }

    private String goalCn(String g) {
        return switch (g) {
            case GOAL_MUSCLE -> "增肌";
            case GOAL_FAT -> "减脂";
            default -> "塑形保持";
        };
    }

    private String eqCn(String eq) {
        return switch (eq) {
            case EQ_HOME -> "居家徒手";
            case EQ_DUMBBELL -> "仅有哑铃";
            default -> "健身房器械齐全";
        };
    }

    private String focusCn(String f) {
        return switch (f) {
            case "FULL" -> "全身综合";
            case "PUSH" -> "胸+肩+三头（推）";
            case "PULL" -> "背+二头（拉）";
            case "LEGS" -> "下肢+臀腿";
            case "SHOULDER_CORE" -> "肩+核心";
            default -> "有氧+核心";
        };
    }

    /** 动作模板 */
    private record Exercise(String name, int sets, String reps, String rest, String note) {
    }

    // ---- 健身房（器械齐全） ----
    private static final List<Exercise> EX_FULL_GYM = List.of(
            new Exercise("杠铃深蹲", 4, "8-10", "90秒", "核心收紧，膝盖与脚尖同向"),
            new Exercise("杠铃卧推/哑铃卧推", 4, "8-12", "90秒", "肩胛后缩稳定"),
            new Exercise("俯身杠铃划船", 4, "10-12", "90秒", "背发力带动肘"),
            new Exercise("坐姿肩推", 3, "10-12", "75秒", "不要过度挺腰"),
            new Exercise("硬拉（罗马尼亚）", 3, "8-10", "90秒", "髋铰链主导，背挺直"),
            new Exercise("平板支撑", 3, "45-60秒", "45秒", "臀部夹紧，腰不塌"));
    private static final List<Exercise> EX_PUSH_GYM = List.of(
            new Exercise("杠铃卧推", 4, "8-12", "90秒", ""),
            new Exercise("上斜哑铃卧推", 3, "10-12", "90秒", "侧重上胸"),
            new Exercise("坐姿肩推", 4, "8-12", "90秒", ""),
            new Exercise("哑铃侧平举", 4, "12-15", "60秒", "控制离心"),
            new Exercise("绳索下压（三头）", 3, "12-15", "60秒", "大臂固定"),
            new Exercise("双杠臂屈伸（辅助）", 3, "力竭", "75秒", "身体略前倾练胸"));
    private static final List<Exercise> EX_PULL_GYM = List.of(
            new Exercise("引体向上/高位下拉", 4, "8-12", "90秒", "沉肩，背阔发力"),
            new Exercise("坐姿绳索划船", 4, "10-12", "90秒", "夹肩胛"),
            new Exercise("俯身哑铃划船", 3, "10-12/侧", "75秒", ""),
            new Exercise("直臂下压", 3, "12-15", "60秒", "练背阔分离度"),
            new Exercise("杠铃弯举", 3, "10-12", "60秒", "不借惯性甩"),
            new Exercise("哑铃锤式弯举", 3, "12", "60秒", "兼顾肱肌"));
    private static final List<Exercise> EX_LEG_GYM = List.of(
            new Exercise("杠铃深蹲", 4, "8-10", "120秒", ""),
            new Exercise("腿举", 4, "10-12", "90秒", "膝盖不内扣"),
            new Exercise("罗马尼亚硬拉", 4, "8-10", "90秒", "练臀腿后链"),
            new Exercise("腿弯举", 3, "12-15", "60秒", ""),
            new Exercise("坐姿提踵", 4, "15-20", "45秒", "顶峰停顿"),
            new Exercise("箭步蹲", 3, "10/侧", "75秒", ""));
    private static final List<Exercise> EX_SHOULDER_CORE_GYM = List.of(
            new Exercise("杠铃/哑铃推举", 4, "8-12", "90秒", ""),
            new Exercise("哑铃侧平举", 4, "12-15", "60秒", ""),
            new Exercise("俯身飞鸟（后束）", 3, "15", "60秒", ""),
            new Exercise("面拉", 3, "15", "60秒", "改善圆肩"),
            new Exercise("悬垂举腿", 3, "12-15", "45秒", "核心控制"),
            new Exercise("平板支撑", 3, "60秒", "45秒", ""));

    // ---- 居家（徒手） ----
    private static final List<Exercise> EX_FULL_HOME = List.of(
            new Exercise("自重深蹲", 4, "15-20", "60秒", ""),
            new Exercise("俯卧撑（可跪姿）", 4, "力竭/12-15", "60秒", ""),
            new Exercise("反向划船/桌边划船", 4, "10-15", "60秒", "用结实桌子或低单杠"),
            new Exercise("臀桥", 4, "15-20", "45秒", "顶峰夹臀"),
            new Exercise("弓步蹲", 3, "12/侧", "60秒", ""),
            new Exercise("平板支撑", 3, "45-60秒", "45秒", ""));
    private static final List<Exercise> EX_PUSH_HOME = List.of(
            new Exercise("标准/上斜俯卧撑", 4, "12-15", "60秒", ""),
            new Exercise("下斜俯卧撑", 3, "10-12", "60秒", "侧重上胸肩"),
            new Exercise("派克俯卧撑", 3, "8-12", "60秒", "练肩替代推举"),
            new Exercise("窄距俯卧撑", 4, "10-12", "60秒", "练三头"),
            new Exercise("板凳臂屈伸", 3, "12-15", "60秒", "双手撑稳固椅面"));
    private static final List<Exercise> EX_PULL_HOME = List.of(
            new Exercise("毛巾/门把等长划船", 4, "保持30秒", "45秒", "肩胛后缩"),
            new Exercise("桌下反向划船", 4, "10-15", "60秒", "注意桌子稳固"),
            new Exercise("超人式（竖脊肌）", 3, "15", "45秒", ""),
            new Exercise("俯卧 Y-T-W 举", 3, "12", "45秒", "练上背与肩后束"),
            new Exercise("二头静力弯举（踩毛巾）", 3, "保持20秒", "45秒", ""));
    private static final List<Exercise> EX_LEG_HOME = List.of(
            new Exercise("自重深蹲", 4, "20", "60秒", ""),
            new Exercise("保加利亚分腿蹲", 3, "10/侧", "60秒", "后脚搭椅面"),
            new Exercise("单腿臀桥", 3, "12/侧", "45秒", ""),
            new Exercise("侧向箭步蹲", 3, "12/侧", "45秒", ""),
            new Exercise("单腿提踵", 4, "20/侧", "30秒", "扶墙平衡"),
            new Exercise("靠墙静蹲", 3, "保持45秒", "45秒", "大腿与地面平行"));
    private static final List<Exercise> EX_SHOULDER_CORE_HOME = List.of(
            new Exercise("派克俯卧撑", 4, "10-12", "60秒", ""),
            new Exercise("侧卧直腿抬举", 3, "15/侧", "45秒", "练中臀稳定肩带"),
            new Exercise("俯卧 Y-T-W", 3, "12", "45秒", ""),
            new Exercise("卷腹", 3, "15-20", "45秒", ""),
            new Exercise("死虫式", 3, "10/侧", "45秒", "腰贴地"),
            new Exercise("平板支撑", 3, "60秒", "45秒", ""));

    // ---- 仅哑铃 ----
    private static final List<Exercise> EX_FULL_DB = List.of(
            new Exercise("高脚杯深蹲（哑铃抱胸）", 4, "10-12", "90秒", ""),
            new Exercise("哑铃卧推（地面/凳）", 4, "10-12", "90秒", ""),
            new Exercise("哑铃划船", 4, "10-12/侧", "75秒", ""),
            new Exercise("哑铃硬拉", 3, "10-12", "90秒", ""),
            new Exercise("哑铃肩推", 3, "12", "75秒", ""),
            new Exercise("哑铃摇摆", 3, "15", "60秒", "髋发力"));
    private static final List<Exercise> EX_PUSH_DB = List.of(
            new Exercise("哑铃卧推", 4, "10-12", "90秒", ""),
            new Exercise("上斜哑铃卧推", 3, "12", "75秒", ""),
            new Exercise("哑铃肩推", 4, "10-12", "75秒", ""),
            new Exercise("哑铃侧平举", 4, "12-15", "60秒", ""),
            new Exercise("哑铃颈后臂屈伸", 3, "12-15", "60秒", "练三头"));
    private static final List<Exercise> EX_PULL_DB = List.of(
            new Exercise("单臂哑铃划船", 4, "10-12/侧", "75秒", ""),
            new Exercise("哑铃俯身划船（双臂）", 4, "10-12", "75秒", ""),
            new Exercise("哑铃反握划船", 3, "12", "60秒", ""),
            new Exercise("哑铃弯举", 4, "10-12", "60秒", ""),
            new Exercise("哑铃锤式弯举", 3, "12", "60秒", ""));
    private static final List<Exercise> EX_LEG_DB = List.of(
            new Exercise("哑铃高脚杯深蹲", 4, "10-12", "90秒", ""),
            new Exercise("哑铃罗马尼亚硬拉", 4, "10-12", "90秒", ""),
            new Exercise("哑铃箭步蹲", 3, "10/侧", "75秒", ""),
            new Exercise("哑铃保加利亚分腿蹲", 3, "10/侧", "75秒", ""),
            new Exercise("哑铃提踵", 4, "20", "45秒", ""));
    private static final List<Exercise> EX_SHOULDER_CORE_DB = List.of(
            new Exercise("哑铃肩推", 4, "10-12", "75秒", ""),
            new Exercise("哑铃侧平举", 4, "15", "60秒", ""),
            new Exercise("哑铃俯身飞鸟", 3, "15", "60秒", ""),
            new Exercise("哑铃负重卷腹", 3, "15", "45秒", ""),
            new Exercise("哑铃俄式转体", 3, "15/侧", "45秒", ""),
            new Exercise("平板支撑", 3, "60秒", "45秒", ""));

    // ---- 有氧 / 核心（减脂与心肺日） ----
    private static final List<Exercise> EX_CARDIO_CORE = List.of(
            new Exercise("开合跳", 4, "45秒", "15秒", "保持节奏"),
            new Exercise("登山跑", 4, "40秒", "20秒", ""),
            new Exercise("卷腹", 3, "20", "30秒", ""),
            new Exercise("死虫式", 3, "12/侧", "30秒", ""),
            new Exercise("平板支撑", 3, "60秒", "30秒", ""),
            new Exercise("快走/慢跑（稳态有氧）", 1, "20-30分钟", "-", "心率维持在能说话但略喘"));
    private static final List<Exercise> EX_CARDIO_FAT = List.of(
            new Exercise("波比跳（可退阶）", 4, "30-40秒", "20秒", "减脂循环"),
            new Exercise("高抬腿", 4, "40秒", "20秒", ""),
            new Exercise("壶铃/哑铃摇摆或深蹲跳", 4, "15", "30秒", ""),
            new Exercise("登山跑", 4, "40秒", "20秒", ""),
            new Exercise("战绳/空摇（或开合跳）", 3, "40秒", "20秒", ""),
            new Exercise("中低强度有氧", 1, "25-35分钟", "-", "循环训练后做稳态有氧"));
}
