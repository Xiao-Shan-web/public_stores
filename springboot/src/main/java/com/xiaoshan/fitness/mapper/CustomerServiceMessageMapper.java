package com.xiaoshan.fitness.mapper;

import com.xiaoshan.fitness.entity.CustomerServiceMessage;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

/**
 * 客服消息 Mapper
 */
@Mapper
public interface CustomerServiceMessageMapper {

    /**
     * 新增客服消息
     */
    @Insert("INSERT INTO customer_service_messages (id, user_id, sender_type, content, is_read) " +
            "VALUES (#{id}, #{userId}, #{senderType}, #{content}, #{isRead})")
    int insert(CustomerServiceMessage msg);

    /**
     * 分页查询某用户的会话消息（按时间倒序，页码从1开始语义由调用方算 offset）
     */
    @Select("SELECT * FROM customer_service_messages " +
            "WHERE user_id = #{userId} " +
            "ORDER BY created_at DESC, id DESC " +
            "LIMIT #{offset}, #{size}")
    List<CustomerServiceMessage> findPageByUserId(@Param("userId") Long userId,
                                                  @Param("offset") int offset,
                                                  @Param("size") int size);

    /**
     * 统计某用户消息总数
     */
    @Select("SELECT COUNT(*) FROM customer_service_messages WHERE user_id = #{userId}")
    long countByUserId(@Param("userId") Long userId);

    /**
     * 客服端：会话列表（每个用户取最后一条消息 + 未读的 USER 消息数）
     */
    @Select("SELECT m.user_id AS userId, " +
            "       u.phone AS phone, " +
            "       up.avatar AS avatar, up.avatar_thumb AS avatarThumb, " +
            "       m.content AS lastContent, " +
            "       m.sender_type AS lastSenderType, " +
            "       m.created_at AS lastTime, " +
            "       (SELECT COUNT(*) FROM customer_service_messages x " +
            "         WHERE x.user_id = m.user_id AND x.sender_type = 'USER' AND x.is_read = 0) AS unread " +
            "FROM customer_service_messages m " +
            "INNER JOIN users u ON u.id = m.user_id " +
            "LEFT JOIN user_profiles up ON up.user_id = m.user_id " +
            "INNER JOIN (SELECT user_id, MAX(id) AS maxId FROM customer_service_messages GROUP BY user_id) t " +
            "    ON t.user_id = m.user_id AND t.maxId = m.id " +
            "ORDER BY m.created_at DESC, m.id DESC")
    List<Map<String, Object>> findConversations();

    /**
     * 用户端：未读客服消息数（发送方为 ADMIN 且未读）
     */
    @Select("SELECT COUNT(*) FROM customer_service_messages " +
            "WHERE user_id = #{userId} AND sender_type = 'ADMIN' AND is_read = 0")
    long countUnreadForUser(@Param("userId") Long userId);

    /**
     * 客服端：某用户的未读消息数（发送方为 USER 且未读）
     */
    @Select("SELECT COUNT(*) FROM customer_service_messages " +
            "WHERE user_id = #{userId} AND sender_type = 'USER' AND is_read = 0")
    long countUnreadForAdmin(@Param("userId") Long userId);

    /**
     * 客服端：某用户的未读总数（所有用户汇总，用于菜单角标）
     */
    @Select("SELECT COUNT(*) FROM customer_service_messages WHERE sender_type = 'USER' AND is_read = 0")
    long countUnreadForAdminAll();

    /**
     * 用户标记客服消息已读
     */
    @Update("UPDATE customer_service_messages SET is_read = 1 " +
            "WHERE user_id = #{userId} AND sender_type = 'ADMIN' AND is_read = 0")
    int markReadForUser(@Param("userId") Long userId);

    /**
     * 客服标记某用户发来的消息已读
     */
    @Update("UPDATE customer_service_messages SET is_read = 1 " +
            "WHERE user_id = #{userId} AND sender_type = 'USER' AND is_read = 0")
    int markReadForAdmin(@Param("userId") Long userId);

}
