package com.xiaoshan.fitness.mapper;

import com.xiaoshan.fitness.entity.SmsRecord;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SmsRecordMapper {

    @Insert("INSERT INTO sms_records " +
            "(id, user_id, phone, template_type, channel, content, status, fail_reason, biz_id, cost_ms, created_at) " +
            "VALUES " +
            "(#{id}, #{userId}, #{phone}, #{templateType}, #{channel}, #{content}, #{status}, " +
            " #{failReason}, #{bizId}, #{costMs}, NOW())")
    int insert(SmsRecord record);
}
