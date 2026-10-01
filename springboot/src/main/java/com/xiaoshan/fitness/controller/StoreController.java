package com.xiaoshan.fitness.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.xiaoshan.fitness.entity.Store;
import com.xiaoshan.fitness.mapper.StoreMapper;
import com.xiaoshan.fitness.util.CacheService;
import com.xiaoshan.fitness.util.Result;
import com.xiaoshan.fitness.util.SnowflakeIdGenerator;
import com.xiaoshan.fitness.vo.StoreVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 门店管理接口（管理端）
 * 列表 / 新建 / 编辑 / 删除（软删除，被卡类型引用时禁止删除）
 */
@RestController
@RequiredArgsConstructor
@Slf4j
public class StoreController {

    private final StoreMapper storeMapper;
    private final SnowflakeIdGenerator idGenerator;
    private final CacheService cacheService;

    /** 门店列表缓存 key */
    private static final String CACHE_KEY_STORES = "store:list:all";
    private static final Duration CACHE_TTL_STORES = Duration.ofMinutes(5);

    /**
     * 管理端：门店列表（未删除）
     * <p>
     * 缓存：写穿透模式，TTL=5 分钟。门店增删改会调用 evictByPrefix 清理缓存。
     */
    @GetMapping("/api/v1/admin/stores")
    public Result<Map<String, Object>> list() {
        // 缓存具体 VO 列表（禁止缓存 Map<String,Object>，否则 JSON 序列化会报类型 id 错误）
        List<StoreVO> stores = cacheService.cacheThrough(
                CACHE_KEY_STORES,
                CACHE_TTL_STORES,
                () -> storeMapper.findAll().stream()
                        .map(this::toVO)
                        .collect(Collectors.toList()),
                new TypeReference<List<StoreVO>>() {}
        );
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", stores);
        return Result.ok(data);
    }

    /**
     * 管理端：新建门店
     */
    @PostMapping("/api/v1/admin/stores")
    public Result<StoreVO> create(@RequestBody Map<String, Object> body) {
        String name = strOrEmpty(body.get("name"));
        if (name.isBlank()) {
            return Result.fail("门店名称不能为空");
        }
        if (name.length() > 100) {
            return Result.fail("门店名称不能超过100字");
        }

        Store store = new Store();
        store.setId(idGenerator.nextId());
        store.setName(name);
        store.setAddress(strOrNull(body.get("address")));
        storeMapper.insert(store);

        log.info("新建门店：{}（ID={}）", store.getName(), store.getId());
        cacheService.evictByPrefix("store:");
        return Result.ok(toVO(store), "新建成功");
    }

    /**
     * 管理端：编辑门店
     */
    @PutMapping("/api/v1/admin/stores/{id}")
    public Result<StoreVO> update(@PathVariable Long id,
                                  @RequestBody Map<String, Object> body) {
        Store existed = storeMapper.findById(id);
        if (existed == null || (existed.getIsDeleted() != null && existed.getIsDeleted() == 1)) {
            return Result.fail("门店不存在");
        }

        String name = strOrEmpty(body.get("name"));
        if (name.isBlank()) {
            return Result.fail("门店名称不能为空");
        }
        if (name.length() > 100) {
            return Result.fail("门店名称不能超过100字");
        }

        Store store = new Store();
        store.setId(id);
        store.setName(name);
        store.setAddress(strOrNull(body.get("address")));
        storeMapper.update(store);

        log.info("编辑门店：ID={}，名称={}", id, name);
        cacheService.evictByPrefix("store:");
        return Result.ok(toVO(storeMapper.findById(id)), "更新成功");
    }

    /**
     * 管理端：删除门店（软删除；被未删除卡类型引用时禁止删除）
     */
    @DeleteMapping("/api/v1/admin/stores/{id}")
    public Result<Map<String, Object>> delete(@PathVariable Long id) {
        Store existed = storeMapper.findById(id);
        if (existed == null || (existed.getIsDeleted() != null && existed.getIsDeleted() == 1)) {
            return Result.fail("门店不存在");
        }
        if (storeMapper.countReferencingCardTypes(id) > 0) {
            return Result.fail("该门店已被卡类型使用，无法删除");
        }
        storeMapper.softDelete(id);
        log.info("删除门店：ID={}，名称={}", id, existed.getName());
        cacheService.evictByPrefix("store:");
        // 门店变更也影响卡类型视图（storeName 字段），连带清理
        cacheService.evictByPrefix("cardtype:");
        return Result.ok(null, "删除成功");
    }

    // ==================== 私有辅助方法 ====================

    private StoreVO toVO(Store s) {
        StoreVO item = new StoreVO();
        item.setId(s.getId());
        item.setName(s.getName());
        item.setAddress(s.getAddress());
        item.setCreatedAt(s.getCreatedAt());
        item.setUpdatedAt(s.getUpdatedAt());
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

}
