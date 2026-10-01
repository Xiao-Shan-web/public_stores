package com.xiaoshan.fitness.mapper;

import com.xiaoshan.fitness.entity.AdminAuditLog;
import com.xiaoshan.fitness.entity.Arbitration;
import com.xiaoshan.fitness.entity.Complaint;
import com.xiaoshan.fitness.entity.RiskEvent;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 平台治理聚合 Mapper：投诉 / 仲裁 / 风控 / 审计。
 * 列表查询统一用 <script> 动态 SQL，外部入参全部 #{} 参数化，杜绝拼接注入。
 */
@Mapper
public interface GovernanceMapper {

    // ==================== 投诉 ====================

    @Insert("INSERT INTO complaints " +
            "(id, user_id, type, title, content, images_json, biz_type, biz_id, store_id, " +
            " contact_phone, status, created_at, updated_at) " +
            "VALUES (#{id}, #{userId}, #{type}, #{title}, #{content}, #{imagesJson}, #{bizType}, #{bizId}, " +
            " #{storeId}, #{contactPhone}, #{status}, NOW(), NOW())")
    int insertComplaint(Complaint c);

    // 详情同样关联 user_profiles 取头像（管理端投诉详情需展示/预览投诉人头像）
    @Select("SELECT c.*, up.avatar AS avatar, up.avatar_thumb AS avatarThumb FROM complaints c " +
            "LEFT JOIN user_profiles up ON up.user_id = c.user_id " +
            "WHERE c.id = #{id}")
    Complaint findComplaintById(@Param("id") Long id);

    @Select("SELECT c.*, up.avatar AS avatar, up.avatar_thumb AS avatarThumb FROM complaints c " +
            "LEFT JOIN user_profiles up ON up.user_id = c.user_id " +
            "WHERE c.user_id = #{userId} " +
            "ORDER BY c.created_at DESC LIMIT #{offset}, #{size}")
    List<Complaint> listMyComplaints(@Param("userId") Long userId,
                                     @Param("offset") int offset, @Param("size") int size);

    @Select("SELECT COUNT(*) FROM complaints WHERE user_id = #{userId}")
    long countMyComplaints(@Param("userId") Long userId);

    @Select("<script>SELECT c.*, up.avatar AS avatar, up.avatar_thumb AS avatarThumb FROM complaints c " +
            "LEFT JOIN user_profiles up ON up.user_id = c.user_id" +
            "<where>" +
            "  <if test='status != null and status != \"\"'> AND c.status = #{status}</if>" +
            "  <if test='type != null and type != \"\"'> AND c.type = #{type}</if>" +
            "</where>" +
            " ORDER BY c.created_at DESC LIMIT #{offset}, #{size}</script>")
    List<Complaint> adminListComplaints(@Param("status") String status, @Param("type") String type,
                                        @Param("offset") int offset, @Param("size") int size);

    @Select("<script>SELECT COUNT(*) FROM complaints" +
            "<where>" +
            "  <if test='status != null and status != \"\"'> AND status = #{status}</if>" +
            "  <if test='type != null and type != \"\"'> AND type = #{type}</if>" +
            "</where></script>")
    long adminCountComplaints(@Param("status") String status, @Param("type") String type);

    @Update("UPDATE complaints SET status = #{status}, admin_reply = #{adminReply}, " +
            "handler_admin_id = #{handlerAdminId}, handled_at = NOW(), updated_at = NOW() WHERE id = #{id}")
    int updateComplaintHandle(@Param("id") Long id, @Param("status") String status,
                              @Param("adminReply") String adminReply,
                              @Param("handlerAdminId") Long handlerAdminId);

    @Update("UPDATE complaints SET status = #{status}, updated_at = NOW() WHERE id = #{id}")
    int updateComplaintStatus(@Param("id") Long id, @Param("status") String status);

    // ==================== 仲裁 ====================

    @Insert("INSERT INTO arbitrations " +
            "(id, complaint_id, user_id, reason, status, created_at, updated_at) " +
            "VALUES (#{id}, #{complaintId}, #{userId}, #{reason}, #{status}, NOW(), NOW())")
    int insertArbitration(Arbitration a);

    @Select("SELECT * FROM arbitrations WHERE complaint_id = #{complaintId}")
    Arbitration findArbitrationByComplaint(@Param("complaintId") Long complaintId);

    @Select("SELECT * FROM arbitrations WHERE id = #{id}")
    Arbitration findArbitrationById(@Param("id") Long id);

    @Select("<script>SELECT * FROM arbitrations" +
            "<where>" +
            "  <if test='status != null and status != \"\"'> AND status = #{status}</if>" +
            "</where>" +
            " ORDER BY created_at DESC LIMIT #{offset}, #{size}</script>")
    List<Arbitration> adminListArbitrations(@Param("status") String status,
                                            @Param("offset") int offset, @Param("size") int size);

    @Select("<script>SELECT COUNT(*) FROM arbitrations" +
            "<where>" +
            "  <if test='status != null and status != \"\"'> AND status = #{status}</if>" +
            "</where></script>")
    long adminCountArbitrations(@Param("status") String status);

    @Update("UPDATE arbitrations SET status = #{status}, result = #{result}, decision = #{decision}, " +
            "compensation_amount = #{compensationAmount}, arbitrator_admin_id = #{arbitratorAdminId}, " +
            "handled_at = NOW(), updated_at = NOW() WHERE id = #{id}")
    int completeArbitration(Arbitration a);

    // ==================== 风控事件 ====================

    @Insert("INSERT INTO risk_events " +
            "(id, event_type, risk_level, subject_type, subject_id, subject_name, detail_json, status, created_at) " +
            "VALUES (#{id}, #{eventType}, #{riskLevel}, #{subjectType}, #{subjectId}, #{subjectName}, " +
            " #{detailJson}, #{status}, NOW())")
    int insertRiskEvent(RiskEvent e);

    @Select("<script>SELECT * FROM risk_events" +
            "<where>" +
            "  <if test='status != null and status != \"\"'> AND status = #{status}</if>" +
            "  <if test='level != null and level != \"\"'> AND risk_level = #{level}</if>" +
            "  <if test='type != null and type != \"\"'> AND event_type = #{type}</if>" +
            "</where>" +
            " ORDER BY created_at DESC LIMIT #{offset}, #{size}</script>")
    List<RiskEvent> adminListRiskEvents(@Param("status") String status, @Param("level") String level,
                                        @Param("type") String type,
                                        @Param("offset") int offset, @Param("size") int size);

    @Select("<script>SELECT COUNT(*) FROM risk_events" +
            "<where>" +
            "  <if test='status != null and status != \"\"'> AND status = #{status}</if>" +
            "  <if test='level != null and level != \"\"'> AND risk_level = #{level}</if>" +
            "  <if test='type != null and type != \"\"'> AND event_type = #{type}</if>" +
            "</where></script>")
    long adminCountRiskEvents(@Param("status") String status, @Param("level") String level,
                              @Param("type") String type);

    @Select("SELECT COUNT(*) FROM risk_events WHERE status = 'OPEN'")
    long countOpenRiskEvents();

    @Update("UPDATE risk_events SET status = #{status}, handler_id = #{handlerId}, " +
            "handle_remark = #{handleRemark}, handled_at = NOW() WHERE id = #{id}")
    int handleRiskEvent(@Param("id") Long id, @Param("status") String status,
                        @Param("handlerId") Long handlerId, @Param("handleRemark") String handleRemark);

    // ==================== 审计日志 ====================

    @Insert("INSERT INTO admin_audit_logs " +
            "(id, admin_id, username, module, action, method, uri, param_summary, ip, result, cost_ms, created_at) " +
            "VALUES (#{id}, #{adminId}, #{username}, #{module}, #{action}, #{method}, #{uri}, " +
            " #{paramSummary}, #{ip}, #{result}, #{costMs}, NOW())")
    int insertAuditLog(AdminAuditLog log);

    @Select("<script>SELECT * FROM admin_audit_logs" +
            "<where>" +
            "  <if test='module != null and module != \"\"'> AND module = #{module}</if>" +
            "  <if test='adminId != null'> AND admin_id = #{adminId}</if>" +
            "  <if test='action != null and action != \"\"'> AND action = #{action}</if>" +
            "</where>" +
            " ORDER BY created_at DESC LIMIT #{offset}, #{size}</script>")
    List<AdminAuditLog> adminListAuditLogs(@Param("module") String module, @Param("adminId") Long adminId,
                                           @Param("action") String action,
                                           @Param("offset") int offset, @Param("size") int size);

    @Select("<script>SELECT COUNT(*) FROM admin_audit_logs" +
            "<where>" +
            "  <if test='module != null and module != \"\"'> AND module = #{module}</if>" +
            "  <if test='adminId != null'> AND admin_id = #{adminId}</if>" +
            "  <if test='action != null and action != \"\"'> AND action = #{action}</if>" +
            "</where></script>")
    long adminCountAuditLogs(@Param("module") String module, @Param("adminId") Long adminId,
                             @Param("action") String action);
}
