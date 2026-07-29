package com.plagod.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.plagod.entity.device.TrafficLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface TrafficLogMapper extends BaseMapper<TrafficLog> {

    /**
     * 所有字段已经在 Service 层完成校验。
     * IGNORE 在此只用于吸收唯一键冲突产生的 QoS 重投。
     */
    int insertIgnore(TrafficLog trafficLog);

    /**
     * 锁定已存在事件，保证并发重复消息能够读取最新提交结果。
     */
    @Select("select * from t_traffic_log " +
            "where device_code = #{deviceCode} and event_id = #{eventId} " +
            "limit 1 for update")
    TrafficLog selectByEventIdentityForUpdate(@Param("deviceCode") String deviceCode, @Param("eventId") String eventId);
}