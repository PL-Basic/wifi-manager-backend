package com.plagod.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.plagod.entity.device.DeviceWifiConfigRecord;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;

@Mapper
public interface DeviceWifiConfigRecordMapper
        extends BaseMapper<DeviceWifiConfigRecord> {

    @Select("select * from t_device_wifi_config where request_id = #{requestId} limit 1 for update")
    DeviceWifiConfigRecord selectByRequestIdForUpdate(@Param("requestId") String requestId);

    @Select("select * from t_device_wifi_config where node_id = #{nodeId} order by config_version desc limit 1")
    DeviceWifiConfigRecord selectLatestByNodeId(@Param("nodeId") Long nodeId);

    @Select("select * from t_device_wifi_config where binary device_code = #{deviceCode} and binary request_id = #{requestId} limit 1")
    DeviceWifiConfigRecord selectByDeviceCodeAndRequestId(@Param("deviceCode") String deviceCode, @Param("requestId") String requestId);

    @Update("update t_device_wifi_config " +
            "set status = #{supersededStatus}, update_time = #{now} " +
            "where wifi_config_id = #{wifiConfigId} " +
            "and status in (#{stagedStatus}, #{unknownStatus})")
    int supersedeReplaceable(@Param("wifiConfigId") Long wifiConfigId, @Param("stagedStatus") Integer stagedStatus, @Param("unknownStatus") Integer unknownStatus, @Param("supersededStatus") Integer supersededStatus, @Param("now") LocalDateTime now);
}