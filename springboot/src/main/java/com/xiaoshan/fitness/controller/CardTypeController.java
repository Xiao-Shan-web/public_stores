package com.xiaoshan.fitness.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.xiaoshan.fitness.entity.CardType;
import com.xiaoshan.fitness.entity.Store;
import com.xiaoshan.fitness.mapper.ActivityMapper;
import com.xiaoshan.fitness.mapper.CardTypeMapper;
import com.xiaoshan.fitness.mapper.StoreMapper;
import com.xiaoshan.fitness.util.CacheService;
import com.xiaoshan.fitness.util.Result;
import com.xiaoshan.fitness.util.SnowflakeIdGenerator;
import com.xiaoshan.fitness.vo.CardTypeVO;
import com.xiaoshan.fitness.vo.UserCardTypeVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 卡类型管理接口
 * 管理端：列表 / 新建 / 编辑 / 删除（软删除） / 启用禁用
 * 用户端：可购买卡类型列表（启用 + 未删除，含 NORMAL 普通卡与 PT 私教课卡）
 * <p>
 * 分类 category：NORMAL-普通会员卡，PT-私教课卡（带 totalTimes 节数）
 * 适用范围 scope：ALL_STORE-全店通用，SINGLE_STORE-指定单店（storeId 必填）
 */
@RestController
@RequiredArgsConstructor
@Slf4j
public class CardTypeController {

    private static final String CATEGORY_NORMAL = "NORMAL";
    private static final String CATEGORY_PT = "PT";
    private static final String SCOPE_ALL = "ALL_STORE";
    private static final String SCOPE_SINGLE = "SINGLE_STORE";

    private final CardTypeMapper cardTypeMapper;
    private final StoreMapper storeMapper;
    private final ActivityMapper activityMapper;
    private final SnowflakeIdGenerator idGenerator;
    private final CacheService cacheService;

    /** 用户端可购买卡类型列表的缓存 key（按启用+未删除过滤） */
    private static final String CACHE_KEY_USER_CARD_TYPES = "cardtype:list:active";
    /** 缓存 TTL：5 分钟（卡类型变更频率低，5 分钟内可容忍最终一致） */
    private static final Duration CACHE_TTL_CARD_TYPES = Duration.ofMinutes(5);

    /**
     * 管理端：卡类型列表
     */
    @GetMapping("/api/v1/admin/card-types")
    public Result<Map<String, Object>> list() {
        List<CardTypeVO> voList = cardTypeMapper.findAll().stream()
                .map(this::toVO)
                .collect(Collectors.toList());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", voList);
        return Result.ok(data);
    }

    /**
     * 管理端：新建卡类型
     */
    @PostMapping("/api/v1/admin/card-types")
    public Result<CardTypeVO> create(@RequestBody Map<String, Object> body) {
        String name = strOrEmpty(body.get("name"));
        if (name.isBlank()) {
            return Result.fail("卡名称不能为空");
        }
        Integer durationDays = parseInt(body.get("durationDays"));
        if (durationDays == null || durationDays <= 0) {
            return Result.fail("有效天数需为正整数");
        }
        BigDecimal price = parseDecimal(body.get("price"));
        if (price == null || price.signum() < 0) {
            return Result.fail("价格格式不正确");
        }

        // 分类：NORMAL（默认）或 PT，严格白名单
        String category = normalizeCategory(strOrEmpty(body.get("category")), CATEGORY_NORMAL);
        if (category == null) {
            return Result.fail("分类参数不正确（NORMAL/PT）");
        }

        // 适用范围与绑定门店（非法值显式拒绝，不静默兜底）
        String scope = normalizeScope(strOrEmpty(body.get("scope")));
        if (scope == null) {
            return Result.fail("适用范围参数不正确（ALL_STORE/SINGLE_STORE）");
        }
        Long storeId = parseLong(body.get("storeId"));
        if (SCOPE_SINGLE.equals(scope)) {
            Result<CardTypeVO> storeCheck = checkStore(storeId);
            if (storeCheck != null) return storeCheck;
        } else {
            storeId = null;
        }

        // 节数：PT 必填正整数，NORMAL 不允许设置
        Integer totalTimes = parseInt(body.get("totalTimes"));
        if (CATEGORY_PT.equals(category)) {
            if (totalTimes == null || totalTimes <= 0) {
                return Result.fail("私教课卡需设置正整数节数");
            }
        } else {
            if (totalTimes != null && totalTimes > 0) {
                return Result.fail("普通卡无需设置节数");
            }
            totalTimes = null;
        }

        // 同 名称+范围+门店 唯一校验（不同门店允许同名）
        if (cardTypeMapper.countByNameScopeStore(name, scope, storeId) > 0) {
            return Result.fail("相同适用范围下已存在同名卡类型");
        }

        CardType card = new CardType();
        card.setId(idGenerator.nextId());
        card.setName(name);
        card.setCategory(category);
        card.setScope(scope);
        card.setStoreId(storeId);
        card.setDurationDays(durationDays);
        card.setTotalTimes(totalTimes);
        card.setPrice(price);
        card.setDescription(strOrNull(body.get("description")));
        card.setIsActive(intBool(body.get("isActive"), 1));
        cardTypeMapper.insert(card);

        log.info("新建卡类型：{}（分类={}，范围={}，门店={}，{}节，{}天，{}元，ID={}）",
                card.getName(), category, scope, storeId, totalTimes, durationDays, price, card.getId());

        cacheService.evictByPrefix("cardtype:");
        return Result.ok(toVO(cardTypeMapper.findById(card.getId())), "新建成功");
    }

    /**
     * 管理端：编辑卡类型
     */
    @PutMapping("/api/v1/admin/card-types/{id}")
    public Result<CardTypeVO> update(@PathVariable Long id,
                                     @RequestBody Map<String, Object> body) {
        CardType existed = cardTypeMapper.findById(id);
        if (existed == null || (existed.getIsDeleted() != null && existed.getIsDeleted() == 1)) {
            return Result.fail("卡类型不存在");
        }

        String name = strOrEmpty(body.get("name"));
        if (name.isBlank()) {
            return Result.fail("卡名称不能为空");
        }
        Integer durationDays = parseInt(body.get("durationDays"));
        if (durationDays == null || durationDays <= 0) {
            return Result.fail("有效天数需为正整数");
        }
        BigDecimal price = parseDecimal(body.get("price"));
        if (price == null || price.signum() < 0) {
            return Result.fail("价格格式不正确");
        }

        // 分类创建后不允许修改：已售会员卡的激活互斥与刷脸核销规则依赖该分类，
        // 改动会导致存量卡行为漂移；如需调整请新建卡类型。
        String originalCategory = existed.getCategory() != null
                ? existed.getCategory() : CATEGORY_NORMAL;
        String categoryInput = strOrEmpty(body.get("category"));
        String category;
        if (categoryInput.isBlank()) {
            category = originalCategory;
        } else {
            category = normalizeCategory(categoryInput, originalCategory);
            if (category == null) {
                return Result.fail("分类参数不正确（NORMAL/PT）");
            }
            if (!originalCategory.equals(category)) {
                return Result.fail("卡种分类创建后不允许修改，如需调整请新建卡类型");
            }
        }

        // 适用范围与绑定门店（未传 scope 时保留原值）
        String scopeInput = strOrEmpty(body.get("scope"));
        String scope = scopeInput.isBlank()
                ? (existed.getScope() != null ? existed.getScope() : SCOPE_ALL)
                : normalizeScope(scopeInput);
        if (scope == null) {
            return Result.fail("适用范围参数不正确（ALL_STORE/SINGLE_STORE）");
        }
        Long storeId = body.containsKey("storeId") ? parseLong(body.get("storeId")) : existed.getStoreId();
        if (SCOPE_SINGLE.equals(scope)) {
            Result<CardTypeVO> storeCheck = checkStore(storeId);
            if (storeCheck != null) return storeCheck;
        } else {
            scope = SCOPE_ALL;
            storeId = null;
        }

        // 节数：未传时保留原值
        Integer totalTimes = body.containsKey("totalTimes")
                ? parseInt(body.get("totalTimes"))
                : existed.getTotalTimes();
        if (CATEGORY_PT.equals(category)) {
            if (totalTimes == null || totalTimes <= 0) {
                return Result.fail("私教课卡需设置正整数节数");
            }
        } else {
            if (totalTimes != null && totalTimes > 0) {
                return Result.fail("普通卡无需设置节数");
            }
            totalTimes = null;
        }

        // 同 名称+范围+门店 唯一校验（排除自身）
        if (cardTypeMapper.countByNameScopeStoreExcludeId(name, scope, storeId, id) > 0) {
            return Result.fail("相同适用范围下已存在同名卡类型");
        }

        CardType card = new CardType();
        card.setId(id);
        card.setName(name);
        card.setCategory(category);
        card.setScope(scope);
        card.setStoreId(storeId);
        card.setDurationDays(durationDays);
        card.setTotalTimes(totalTimes);
        card.setPrice(price);
        card.setDescription(strOrNull(body.get("description")));
        card.setIsActive(intBool(body.get("isActive"), existed.getIsActive()));
        cardTypeMapper.update(card);

        log.info("编辑卡类型：ID={}，名称={}，分类={}，范围={}，门店={}，节数={}",
                id, name, category, scope, storeId, totalTimes);

        cacheService.evictByPrefix("cardtype:");
        return Result.ok(toVO(cardTypeMapper.findById(id)), "更新成功");
    }

    /**
     * 管理端：删除卡类型（软删除）
     */
    @DeleteMapping("/api/v1/admin/card-types/{id}")
    public Result<Map<String, Object>> delete(@PathVariable Long id) {
        CardType existed = cardTypeMapper.findById(id);
        if (existed == null || (existed.getIsDeleted() != null && existed.getIsDeleted() == 1)) {
            return Result.fail("卡类型不存在");
        }
        cardTypeMapper.softDelete(id);
        log.info("删除卡类型：ID={}，名称={}", id, existed.getName());
        cacheService.evictByPrefix("cardtype:");
        return Result.ok(null, "删除成功");
    }

    /**
     * 管理端：启用 / 禁用卡类型
     * 请求体：{ "status": 1 } 或 { "status": 0 }
     */
    @PutMapping("/api/v1/admin/card-types/{id}/status")
    public Result<Map<String, Object>> toggleStatus(@PathVariable Long id,
                                                    @RequestBody Map<String, Object> body) {
        CardType existed = cardTypeMapper.findById(id);
        if (existed == null || (existed.getIsDeleted() != null && existed.getIsDeleted() == 1)) {
            return Result.fail("卡类型不存在");
        }
        Integer status = intBool(body.get("status"), null);
        if (status == null) {
            return Result.fail("状态参数不正确");
        }
        cardTypeMapper.updateStatus(id, status);
        log.info("切换卡类型状态：ID={}，status={}", id, status);
        cacheService.evictByPrefix("cardtype:");
        return Result.ok(null, status == 1 ? "已启用" : "已禁用");
    }

    /**
     * 用户端：可购买卡类型列表（启用 + 未删除，含普通卡 NORMAL 与私教课卡 PT）
     * <p>
     * 价格展示（与下单口径完全一致）：
     * <ul>
     *   <li>基础卡信息走写穿透缓存（TTL=5 分钟，卡类型变更主动 evict）</li>
     *   <li>限时活动信息<b>不缓存</b>，每次请求实时查 activities：
     *       管理端上架/下架、活动到期、名额售罄后用户端下一次请求即生效/恢复原价</li>
     *   <li>同一卡类型同时存在多个有效活动时，取折扣最低（优惠力度最大）的一个</li>
     * </ul>
     */
    @GetMapping("/api/v1/user/card-types")
    public Result<Map<String, Object>> userCardTypes() {
        // 缓存具体 VO 列表（禁止缓存 Map<String,Object>，否则 JSON 序列化会报类型 id 错误）
        List<CardTypeVO> cardTypes = cacheService.cacheThrough(
                CACHE_KEY_USER_CARD_TYPES,
                CACHE_TTL_CARD_TYPES,
                () -> cardTypeMapper.findAllActive().stream()
                        .map(this::toVO)
                        .collect(Collectors.toList()),
                new TypeReference<List<CardTypeVO>>() {}
        );

        // 实时查询进行中活动（status=1 且在时间窗内 且名额未满），按卡类型归组取最大力度
        Map<Long, Map<String, Object>> bestActivityByCardType = new HashMap<>();
        for (Map<String, Object> activity : activityMapper.findOngoing()) {
            Long cardTypeId = parseLong(activity.get("cardTypeId"));
            BigDecimal discount = parseDecimal(activity.get("discount"));
            if (cardTypeId == null || discount == null) {
                continue;
            }
            Map<String, Object> current = bestActivityByCardType.get(cardTypeId);
            if (current == null || discount.compareTo(parseDecimal(current.get("discount"))) < 0) {
                bestActivityByCardType.put(cardTypeId, activity);
            }
        }

        // 组装请求级 VO（不修改缓存对象本身，避免请求间活动数据串写）
        List<UserCardTypeVO> result = cardTypes.stream()
                .map(vo -> toUserVO(vo, bestActivityByCardType.get(vo.getId())))
                .collect(Collectors.toList());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", result);
        return Result.ok(data);
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 校验单店绑定的门店存在且未删除；通过返回 null，不通过返回失败 Result
     */
    private Result<CardTypeVO> checkStore(Long storeId) {
        if (storeId == null) {
            return Result.fail("指定单店卡需选择门店");
        }
        Store store = storeMapper.findById(storeId);
        if (store == null || (store.getIsDeleted() != null && store.getIsDeleted() == 1)) {
            return Result.fail("绑定门店不存在或已删除");
        }
        return null;
    }

    /**
     * 分类严格白名单校验，非法返回 null
     */
    private String normalizeCategory(String input, String defaultIfBlank) {
        if (input == null || input.isBlank()) {
            return defaultIfBlank;
        }
        if (CATEGORY_NORMAL.equals(input) || CATEGORY_PT.equals(input)) {
            return input;
        }
        return null;
    }

    /**
     * 范围严格白名单校验，空值默认全店通用，非法返回 null
     */
    private String normalizeScope(String input) {
        if (input == null || input.isBlank()) {
            return SCOPE_ALL;
        }
        if (SCOPE_ALL.equals(input) || SCOPE_SINGLE.equals(input)) {
            return input;
        }
        return null;
    }

    /**
     * 实体转视图对象（不暴露 is_deleted 字段）
     */
    private CardTypeVO toVO(CardType c) {
        CardTypeVO item = new CardTypeVO();
        item.setId(c.getId());
        item.setName(c.getName());
        item.setCategory(c.getCategory() != null ? c.getCategory() : CATEGORY_NORMAL);
        item.setScope(c.getScope() != null ? c.getScope() : SCOPE_ALL);
        item.setStoreId(c.getStoreId());
        item.setStoreName(c.getStoreName());
        item.setDurationDays(c.getDurationDays());
        item.setTotalTimes(c.getTotalTimes());
        item.setPrice(c.getPrice() != null ? c.getPrice().toPlainString() : null);
        item.setDescription(c.getDescription());
        item.setIsActive(c.getIsActive());
        item.setCreatedAt(c.getCreatedAt());
        item.setUpdatedAt(c.getUpdatedAt());
        return item;
    }

    /**
     * 用户端 VO：复制缓存中的基础卡信息，并实时附加活动价。
     * <p>
     * 活动价计算与 {@code OrderController.create} 完全一致：
     * 原价 × discount，HALF_UP 保留两位。
     * 折扣 ≥ 1（无实际优惠）时不附加活动，前端只展示原价。
     *
     * @param base     缓存的基础卡 VO（只读，不修改）
     * @param activity 该卡当前力度最大的进行中活动，null 表示无活动
     */
    private UserCardTypeVO toUserVO(CardTypeVO base, Map<String, Object> activity) {
        UserCardTypeVO item = new UserCardTypeVO();
        item.setId(base.getId());
        item.setName(base.getName());
        item.setCategory(base.getCategory());
        item.setScope(base.getScope());
        item.setStoreId(base.getStoreId());
        item.setStoreName(base.getStoreName());
        item.setDurationDays(base.getDurationDays());
        item.setTotalTimes(base.getTotalTimes());
        item.setPrice(base.getPrice());
        item.setDescription(base.getDescription());
        item.setIsActive(base.getIsActive());
        item.setCreatedAt(base.getCreatedAt());
        item.setUpdatedAt(base.getUpdatedAt());
        item.setOriginalPrice(base.getPrice());

        if (activity != null && base.getPrice() != null) {
            BigDecimal original = parseDecimal(base.getPrice());
            BigDecimal discount = parseDecimal(activity.get("discount"));
            if (original != null && discount != null && discount.signum() > 0
                    && discount.compareTo(BigDecimal.ONE) < 0) {
                BigDecimal activityPrice = original.multiply(discount).setScale(2, RoundingMode.HALF_UP);
                item.setActivityPrice(activityPrice.toPlainString());
                item.setActivityId(parseLong(activity.get("id")));
                item.setActivityTitle(activity.get("title") == null ? null : activity.get("title").toString());
                item.setDiscount(discount.stripTrailingZeros().toPlainString());
                Object endTime = activity.get("endTime");
                if (endTime instanceof LocalDateTime) {
                    item.setActivityEndTime((LocalDateTime) endTime);
                }
            }
        }
        return item;
    }

    private String strOrEmpty(Object o) {
        return o == null ? "" : o.toString().trim();
    }

    private String strOrNull(Object o) {
        if (o == null) return null;
        String s = o.toString().trim();
        return s.isEmpty() ? null : s;
    }

    private Integer parseInt(Object o) {
        if (o == null) return null;
        try {
            if (o instanceof Number) return ((Number) o).intValue();
            return Integer.parseInt(o.toString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Long parseLong(Object o) {
        if (o == null) return null;
        try {
            if (o instanceof Number) return ((Number) o).longValue();
            return Long.parseLong(o.toString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private BigDecimal parseDecimal(Object o) {
        if (o == null) return null;
        try {
            return new BigDecimal(o.toString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Integer intBool(Object o, Integer defaultValue) {
        Integer parsed = parseInt(o);
        if (parsed == null) return defaultValue;
        return parsed == 1 ? 1 : 0;
    }

}
