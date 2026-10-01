package com.xiaoshan.fitness.mapper;

import com.xiaoshan.fitness.entity.AiPlan;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * AI 饮食计划 Mapper
 */
@Mapper
public interface AiPlanMapper {

    /**
     * 插入计划
     */
    @Insert("INSERT INTO ai_plans(id, user_id, height, weight, age, gender, goal, " +
            "daily_calories, protein, carbs, fat, meals_json, provider) " +
            "VALUES(#{id}, #{userId}, #{height}, #{weight}, #{age}, #{gender}, #{goal}, " +
            "#{dailyCalories}, #{protein}, #{carbs}, #{fat}, #{mealsJson}, #{provider})")
    int insert(AiPlan plan);

    /**
     * 查询用户历史计划（按时间倒序，分页）
     */
    @Select("SELECT id, user_id AS userId, height, weight, age, gender, goal, " +
            "daily_calories AS dailyCalories, protein, carbs, fat, " +
            "meals_json AS mealsJson, provider, created_at AS createdAt " +
            "FROM ai_plans WHERE user_id = #{userId} " +
            "ORDER BY created_at DESC LIMIT #{offset}, #{size}")
    List<AiPlan> findPageByUserId(@Param("userId") Long userId,
                                  @Param("offset") int offset,
                                  @Param("size") int size);

    /**
     * 查询用户历史计划总数
     */
    @Select("SELECT COUNT(*) FROM ai_plans WHERE user_id = #{userId}")
    long countByUserId(@Param("userId") Long userId);

    /**
     * 根据ID查询计划详情
     */
    @Select("SELECT id, user_id AS userId, height, weight, age, gender, goal, " +
            "daily_calories AS dailyCalories, protein, carbs, fat, " +
            "meals_json AS mealsJson, provider, created_at AS createdAt " +
            "FROM ai_plans WHERE id = #{id} AND user_id = #{userId}")
    AiPlan findByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

}
