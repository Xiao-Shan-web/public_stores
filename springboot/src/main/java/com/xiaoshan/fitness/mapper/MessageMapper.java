package com.xiaoshan.fitness.mapper;

import com.xiaoshan.fitness.entity.Message;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

/**
 * 消息通知 Mapper
 */
@Mapper
public interface MessageMapper {

    /** 插入消息（is_read 未设置时默认 0；sender/batch/scope 系统消息为 NULL） */
    @Insert("INSERT INTO messages(id, user_id, sender_id, batch_id, scope, card_type_id, store_id, type, title, content, ref_id, is_read) " +
            "VALUES(#{id}, #{userId}, #{senderId}, #{batchId}, #{scope}, #{cardTypeId}, #{storeId}, #{type}, #{title}, #{content}, #{refId}, COALESCE(#{isRead}, 0))")
    int insert(Message msg);

    /** 批量插入消息（管理员群发/指定发送；每个接收用户一条） */
    @Insert("<script>" +
            "INSERT INTO messages(id, user_id, sender_id, batch_id, scope, card_type_id, store_id, type, title, content, ref_id, is_read) VALUES " +
            "<foreach collection='list' item='m' separator=','>" +
            "(#{m.id}, #{m.userId}, #{m.senderId}, #{m.batchId}, #{m.scope}, #{m.cardTypeId}, #{m.storeId}, #{m.type}, #{m.title}, #{m.content}, #{m.refId}, 0)" +
            "</foreach></script>")
    int insertBatch(@Param("list") List<Message> list);

    /**
     * 到期提醒幂等校验：同一用户、同一会员卡（ref_id）、同一标题的 EXPIRE 消息是否已存在。
     * 用于到期前 7 天 / 1 天提醒重复投递时跳过。
     */
    @Select("SELECT COUNT(*) FROM messages " +
            "WHERE user_id = #{userId} AND ref_id = #{refId} AND type = 'EXPIRE' AND title = #{title}")
    long countReminder(@Param("userId") Long userId,
                       @Param("refId") Long refId,
                       @Param("title") String title);

    /** 查询用户未读消息数（红点展示） */
    @Select("SELECT COUNT(*) FROM messages WHERE user_id = #{userId} AND is_read = 0")
    long countUnread(@Param("userId") Long userId);

    /** 查询用户消息总数 */
    @Select("SELECT COUNT(*) FROM messages WHERE user_id = #{userId}")
    long countByUserId(@Param("userId") Long userId);

    /** 查询用户消息列表（按创建时间倒序，分页） */
    @Select("SELECT id, user_id AS userId, sender_id AS senderId, batch_id AS batchId, scope, " +
            "type, title, content, ref_id AS refId, is_read AS isRead, created_at AS createdAt " +
            "FROM messages WHERE user_id = #{userId} " +
            "ORDER BY created_at DESC LIMIT #{offset}, #{size}")
    List<Message> findPageByUserId(@Param("userId") Long userId,
                                   @Param("offset") int offset,
                                   @Param("size") int size);

    /** 查询系统消息列表（管理端首页展示） */
    @Select("SELECT id, user_id AS userId, sender_id AS senderId, batch_id AS batchId, scope, " +
            "type, title, content, ref_id AS refId, is_read AS isRead, created_at AS createdAt " +
            "FROM messages WHERE type = 'SYSTEM' " +
            "ORDER BY created_at DESC LIMIT #{limit}")
    List<Message> findSystemMessages(@Param("limit") int limit);

    /**
     * 管理端：查询某管理员已发通知批次列表（同一次群发按 batch_id 聚合为一行）
     */
    @Select("<script>" +
            "SELECT batch_id AS batchId, MAX(title) AS title, MAX(type) AS type, MAX(scope) AS scope, " +
            "MAX(card_type_id) AS cardTypeId, MAX(store_id) AS storeId, " +
            "MAX(created_at) AS createdAt, COUNT(DISTINCT user_id) AS receiverCount " +
            "FROM messages WHERE sender_id = #{adminId} AND batch_id IS NOT NULL " +
            "<if test='type != null and type != \"\"'> AND type = #{type} </if>" +
            "GROUP BY batch_id ORDER BY MAX(created_at) DESC " +
            "LIMIT #{offset}, #{size}</script>")
    List<Map<String, Object>> findAdminBatches(@Param("adminId") Long adminId,
                                               @Param("type") String type,
                                               @Param("offset") int offset,
                                               @Param("size") int size);

    /** 管理端：统计某管理员已发通知批次数 */
    @Select("<script>" +
            "SELECT COUNT(DISTINCT batch_id) FROM messages WHERE sender_id = #{adminId} AND batch_id IS NOT NULL " +
            "<if test='type != null and type != \"\"'> AND type = #{type} </if>" +
            "</script>")
    long countAdminBatches(@Param("adminId") Long adminId, @Param("type") String type);

    /** 管理端：按批次删除整组通知（删除该批次下所有用户的副本） */
    @Delete("DELETE FROM messages WHERE batch_id = #{batchId} AND sender_id = #{adminId}")
    int deleteByBatchId(@Param("batchId") Long batchId, @Param("adminId") Long adminId);

    /** 标记单条消息已读 */
    @Update("UPDATE messages SET is_read = 1 WHERE id = #{id} AND user_id = #{userId}")
    int markAsRead(@Param("id") Long id, @Param("userId") Long userId);

    /** 标记单条消息已读（别名，兼容现有调用） */
    @Update("UPDATE messages SET is_read = 1 WHERE id = #{id} AND user_id = #{userId}")
    int markRead(@Param("id") Long id, @Param("userId") Long userId);

    /** 标记全部已读 */
    @Update("UPDATE messages SET is_read = 1 WHERE user_id = #{userId} AND is_read = 0")
    int markAllRead(@Param("userId") Long userId);

}
